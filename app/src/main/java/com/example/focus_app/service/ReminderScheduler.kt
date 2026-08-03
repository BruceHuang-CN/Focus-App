package com.example.focus_app.service

import com.example.focus_app.data.local.dao.AiReminderCacheDao
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.repository.TaskRepository
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
}

@Singleton
class ReminderScheduler(
    private val repository: AppSessionRepository,
    private val launcher: ReminderLauncher,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val settingsProvider: suspend () -> AppSettings,
    private val launchDataProvider: suspend (AppUsageSession, AppSettings) -> ReminderLaunchData
) : SessionReminderScheduler {
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val quotaMutex = Mutex()
    private val policy = ReminderPolicy(clock)

    @Inject
    constructor(
        repository: AppSessionRepository,
        settingsRepository: SettingsRepository,
        taskRepository: TaskRepository,
        cacheDao: AiReminderCacheDao,
        launcher: ReminderLauncher
    ) : this(
        repository = repository,
        launcher = launcher,
        clock = SystemClock,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        settingsProvider = settingsRepository::getSettings,
        launchDataProvider = { session, settings ->
            val taskTitle = session.taskId?.let { taskId ->
                taskRepository.observeAll().first().firstOrNull { it.id == taskId }?.title
            }
            val cachedMessage = session.taskId?.let { taskId ->
                cacheDao.next(taskId, session.packageName, session.toneKey)?.text
            }
            ReminderLaunchData(
                sessionId = session.id,
                taskId = session.taskId,
                taskTitle = taskTitle,
                appName = session.appName,
                message = cachedMessage ?: localFallback(session.appName, taskTitle),
                showBreathing = settings.enableBreathingPause,
                returnDestination = settings.returnDestination
            )
        }
    )

    override fun onSessionStarted(
        session: AppUsageSession,
        appStillForeground: suspend () -> Boolean
    ) {
        val job = scope.launch {
            val settings = settingsProvider()
            delay(settings.reminderDelaySeconds * 1_000L)
            if (!appStillForeground()) return@launch

            val data = launchDataProvider(session, settings)
            quotaMutex.withLock {
                val reminderTimes = repository.reminderTimesSince(
                    clock.nowMillis() - settings.reminderWindowMinutes * 60_000L
                )
                if (!policy.canShow(reminderTimes, settings)) return@withLock
                if (!repository.markRemindedIfNeeded(session.id, clock.nowMillis())) return@withLock
                launcher.show(data)
            }
        }
        jobs.put(session.id, job)?.cancel()
        job.invokeOnCompletion { jobs.remove(session.id, job) }
    }

    override fun cancel(sessionId: Long) {
        jobs.remove(sessionId)?.cancel()
    }

    private companion object {
        fun localFallback(appName: String, taskTitle: String?): String =
            if (taskTitle.isNullOrBlank()) {
                "先放下 $appName，回到 Focus 选一件真正想完成的事。"
            } else {
                "你原本准备完成「${taskTitle.take(24)}」。先放下 $appName，现在就回去继续。"
            }
    }
}
