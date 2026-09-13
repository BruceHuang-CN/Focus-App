package com.example.focus_app.service
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.focus_app.R
import com.example.focus_app.data.language.localizedText

import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.ReminderCacheRepository
import com.example.focus_app.data.repository.ReminderDisplayKind
import com.example.focus_app.data.repository.ReminderDisplayRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.data.returnapp.CustomReturnAppStore
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.reminder.ReminderPolicy
import com.example.focus_app.domain.time.Clock
import com.example.focus_app.domain.time.SystemClock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SessionReminderScheduler {
    fun onSessionStarted(
        session: AppUsageSession,
        appStillForeground: suspend () -> Boolean
    )

    fun cancel(sessionId: Long)

    fun scheduleFollowUp(sessionId: Long, delayMillis: Long) = Unit
}

@Singleton
class ReminderScheduler(
    private val repository: AppSessionRepository,
    private val launcher: ReminderLauncher,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val settingsProvider: suspend () -> AppSettings,
    private val launchDataProvider: suspend (AppUsageSession, AppSettings) -> ReminderLaunchData,
    private val displayRepository: ReminderDisplayRepository,
    private val attemptIdProvider: () -> String,
    private val followUpScheduler: FollowUpScheduler = NoOpFollowUpScheduler,
    private val taskEligible: suspend (AppUsageSession) -> Boolean = { true }
) : SessionReminderScheduler {
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val quotaMutex = Mutex()
    private val policy = ReminderPolicy(clock)

    @Inject
    constructor(
        repository: AppSessionRepository,
        settingsRepository: SettingsRepository,
        taskRepository: TaskRepository,
        cacheRepository: ReminderCacheRepository,
        launcher: ReminderLauncher,
        customReturnAppStore: CustomReturnAppStore,
        followUpScheduler: FollowUpScheduler,
        displayRepository: ReminderDisplayRepository,
        attemptIdGenerator: ReminderAttemptIdGenerator,
        @ApplicationContext context: Context
    ) : this(
        repository = repository,
        launcher = launcher,
        clock = SystemClock,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        settingsProvider = settingsRepository::getSettings,
        displayRepository = displayRepository,
        attemptIdProvider = attemptIdGenerator::newId,
        followUpScheduler = followUpScheduler,
        taskEligible = taskRepository::isSessionEligible,
        launchDataProvider = { session, settings ->
            val taskTitle = session.taskId?.let { taskId ->
                taskRepository.observeAll().first().firstOrNull { it.id == taskId }?.title
            }
            val cachedMessage = session.taskId?.let { taskId ->
                cacheRepository.next(taskId, session.packageName, session.toneKey)
            }
            val since = SystemClock.nowMillis() - settings.reminderWindowMinutes * 60_000L
            ReminderLaunchData(
                sessionId = session.id,
                taskId = session.taskId,
                taskContextStartedAt = session.reminderContextStart(),
                taskTitle = taskTitle,
                appName = session.appName,
                message = cachedMessage ?: if (taskTitle.isNullOrBlank()) {
                    context.localizedText(R.string.service_no_task_reminder, session.appName)
                } else {
                    context.localizedText(R.string.service_task_reminder, taskTitle.take(40), session.appName)
                },
                showBreathing = settings.enableBreathingPause,
                returnDestination = settings.returnDestination,
                targetPackageName = session.packageName,
                windowReminderCount = displayRepository.countSince(since),
                windowLimit = settings.maxRemindersPerWindow,
                windowMinutes = settings.reminderWindowMinutes,
                returnPackageName = customReturnAppStore.read(),
                forceReminder = settings.forceReminder
            )
        }
    )

    override fun onSessionStarted(
        session: AppUsageSession,
        appStillForeground: suspend () -> Boolean
    ) {
        val job = scope.launch {
            val initialSettings = settingsProvider()
            if (!guardianAllows(session, initialSettings)) return@launch
            val elapsed = (clock.nowMillis() - session.startedAt).coerceAtLeast(0L)
            delay((initialSettings.reminderDelaySeconds * 1_000L - elapsed).coerceAtLeast(0L))
            if (!appStillForeground()) return@launch
            if (repository.currentOpenSession()?.id != session.id) return@launch

            quotaMutex.withLock {
                val settings = settingsProvider()
                if (!guardianAllows(session, settings) || !taskEligible(session)) return@withLock
                val reminderTimes = displayRepository.timesSince(
                    clock.nowMillis() - settings.reminderWindowMinutes * 60_000L
                )
                if (!policy.canShow(reminderTimes, settings)) return@withLock
                if (!repository.markReminderForContext(session, clock.nowMillis())) return@withLock
                val data = launchDataProvider(session, settings).copy(
                    attemptId = attemptIdProvider(),
                    displayKind = ReminderDisplayKind.INITIAL
                )
                // Settings may change while text/cache data is being read.
                val latestSettings = settingsProvider()
                if (guardianAllows(session, latestSettings) && taskEligible(session) &&
                    repository.currentOpenSession()?.id == session.id && appStillForeground()) launcher.show(data)
            }
        }
        jobs.put(session.id, job)?.cancel()
        job.invokeOnCompletion { jobs.remove(session.id, job) }
    }

    override fun cancel(sessionId: Long) {
        jobs.remove(sessionId)?.cancel()
        followUpScheduler.cancel(sessionId)
        launcher.dismiss(sessionId)
    }

    /**
     * 用户点击「仍要使用」后，在同一会话内按间隔再次提醒（受窗口额度限制）。
     */
    override fun scheduleFollowUp(sessionId: Long, delayMillis: Long) = followUpScheduler.schedule(sessionId, delayMillis)

    private companion object {
        fun guardianAllows(session: AppUsageSession, settings: AppSettings): Boolean =
            settings.guardianEnabled && settings.targetApps.any { it.packageName == session.packageName }

    }
}

