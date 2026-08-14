package com.example.focus_app.service

import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AppSessionContext(
    val targetApps: Map<String, String>,
    val activeTaskId: Long?,
    val toneKey: String
)

interface AppSessionContextProvider {
    suspend fun currentContext(): AppSessionContext
}

class RepositoryAppSessionContextProvider(
    private val settingsRepository: SettingsRepository,
    private val taskRepository: TaskRepository
) : AppSessionContextProvider {
    override suspend fun currentContext(): AppSessionContext {
        val settings = settingsRepository.getSettings()
        return AppSessionContext(
            targetApps = settings.targetApps.associate { it.packageName to it.appName },
            activeTaskId = taskRepository.observeActive().first()?.id,
            toneKey = settings.toneKey.key
        )
    }
}

class AppSessionCoordinator(
    private val repository: AppSessionRepository,
    private val contextProvider: AppSessionContextProvider,
    private val clock: Clock,
    private val reminderScheduler: SessionReminderScheduler,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val departureConfirmationDelayMillis: Long = DEFAULT_DEPARTURE_CONFIRMATION_DELAY_MS
) {
    private data class PendingDeparture(
        val sessionId: Long,
        val candidatePackage: String?,
        val revision: Long,
        val job: Job
    )

    private val eventMutex = Mutex()
    private var initialized = false
    @Volatile private var foregroundPackage: String? = null
    private var openSession: AppUsageSession? = null
    private var departureRevision = 0L
    private var pendingDeparture: PendingDeparture? = null

    suspend fun onPackageChanged(
        packageName: String?,
        foregroundVerifier: (suspend (String) -> Boolean)? = null,
        isReminderPresentation: Boolean = false
    ) = eventMutex.withLock {
        val now = clock.nowMillis()
        recoverStaleSession(now, packageName)

        val session = openSession
        if (session != null) {
            if (isReminderPresentation || packageName == session.packageName) {
                cancelPendingDepartureLocked()
                foregroundPackage = session.packageName
                return@withLock
            }
            val pending = pendingDeparture
            if (
                pending?.sessionId == session.id &&
                pending.candidatePackage == packageName
            ) {
                return@withLock
            }
            scheduleDepartureConfirmationLocked(
                session = session,
                candidatePackage = packageName,
                foregroundVerifier = foregroundVerifier
            )
            return@withLock
        }

        cancelPendingDepartureLocked()
        if (packageName == foregroundPackage) return@withLock
        transitionToPackageLocked(packageName, foregroundVerifier, now)
    }

    suspend fun stopCurrentSession() = eventMutex.withLock {
        cancelPendingDepartureLocked()
        val session = openSession ?: repository.currentOpenSession()
        session?.let {
            reminderScheduler.cancel(it.id)
            repository.closeSession(it.id, clock.nowMillis())
        }
        openSession = null
        foregroundPackage = null
        initialized = true
    }

    private fun scheduleDepartureConfirmationLocked(
        session: AppUsageSession,
        candidatePackage: String?,
        foregroundVerifier: (suspend (String) -> Boolean)?
    ) {
        cancelPendingDepartureLocked()
        val revision = departureRevision
        val job = scope.launch {
            delay(departureConfirmationDelayMillis)
            val targetStillForeground = verifyForeground(
                foregroundVerifier = foregroundVerifier,
                expectedPackage = session.packageName
            )
            eventMutex.withLock {
                val pending = pendingDeparture ?: return@withLock
                if (
                    pending.revision != revision ||
                    pending.sessionId != session.id ||
                    pending.candidatePackage != candidatePackage ||
                    openSession?.id != session.id
                ) {
                    return@withLock
                }
                pendingDeparture = null
                if (targetStillForeground) {
                    foregroundPackage = session.packageName
                    return@withLock
                }

                reminderScheduler.cancel(session.id)
                repository.closeSession(session.id, clock.nowMillis())
                openSession = null
                foregroundPackage = null
                transitionToPackageLocked(
                    packageName = candidatePackage,
                    foregroundVerifier = foregroundVerifier,
                    now = clock.nowMillis()
                )
            }
        }
        pendingDeparture = PendingDeparture(
            sessionId = session.id,
            candidatePackage = candidatePackage,
            revision = revision,
            job = job
        )
    }

    private suspend fun verifyForeground(
        foregroundVerifier: (suspend (String) -> Boolean)?,
        expectedPackage: String
    ): Boolean {
        if (foregroundVerifier == null) return false
        return try {
            foregroundVerifier(expectedPackage)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    private fun cancelPendingDepartureLocked() {
        departureRevision++
        pendingDeparture?.job?.cancel()
        pendingDeparture = null
    }

    private suspend fun transitionToPackageLocked(
        packageName: String?,
        foregroundVerifier: (suspend (String) -> Boolean)?,
        now: Long
    ) {
        if (packageName == null) {
            foregroundPackage = null
            return
        }
        val context = contextProvider.currentContext()
        val appName = context.targetApps[packageName]
        if (appName == null) {
            foregroundPackage = packageName
            return
        }
        val session = repository.openSession(
            packageName = packageName,
            appName = appName,
            startedAt = now,
            taskId = context.activeTaskId,
            toneKey = context.toneKey
        )
        openSession = session
        foregroundPackage = packageName
        reminderScheduler.onSessionStarted(session) {
            foregroundVerifier?.invoke(session.packageName)
                ?: (foregroundPackage == session.packageName)
        }
    }

    private suspend fun recoverStaleSession(now: Long, observedPackageName: String?) {
        if (initialized) return

        repository.currentOpenSession()?.let { stale ->
            val recentEnough = now - stale.startedAt <= MAX_SESSION_DURATION_MS
            if (recentEnough && stale.packageName == observedPackageName) {
                openSession = stale
                foregroundPackage = stale.packageName
                initialized = true
                return
            }
            val cappedEnd = minOf(now, stale.startedAt + MAX_SESSION_DURATION_MS)
                .coerceAtLeast(stale.startedAt)
            repository.closeSession(stale.id, cappedEnd)
        }
        initialized = true
    }

    private companion object {
        const val MAX_SESSION_DURATION_MS = 24 * 60 * 60 * 1_000L
        const val DEFAULT_DEPARTURE_CONFIRMATION_DELAY_MS = 1_500L
    }
}
