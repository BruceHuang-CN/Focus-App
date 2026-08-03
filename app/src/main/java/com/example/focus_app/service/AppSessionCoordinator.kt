package com.example.focus_app.service

import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.time.Clock
import kotlinx.coroutines.flow.first
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
    private val reminderScheduler: SessionReminderScheduler
) {
    private val eventMutex = Mutex()
    private var initialized = false
    @Volatile private var foregroundPackage: String? = null
    private var openSession: AppUsageSession? = null

    suspend fun onPackageChanged(packageName: String?) = eventMutex.withLock {
        val now = clock.nowMillis()
        recoverStaleSession(now)

        if (packageName == foregroundPackage) return@withLock

        openSession?.let { session ->
            reminderScheduler.cancel(session.id)
            repository.closeSession(session.id, now)
            openSession = null
        }

        if (packageName == null) {
            foregroundPackage = null
            return@withLock
        }
        val context = contextProvider.currentContext()
        val appName = context.targetApps[packageName]
        if (appName == null) {
            foregroundPackage = packageName
            return@withLock
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
            foregroundPackage == session.packageName
        }
    }

    private suspend fun recoverStaleSession(now: Long) {
        if (initialized) return

        repository.currentOpenSession()?.let { stale ->
            val cappedEnd = minOf(now, stale.startedAt + MAX_SESSION_DURATION_MS)
                .coerceAtLeast(stale.startedAt)
            repository.closeSession(stale.id, cappedEnd)
        }
        initialized = true
    }

    private companion object {
        const val MAX_SESSION_DURATION_MS = 24 * 60 * 60 * 1_000L
    }
}
