package com.example.focus_app.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import android.os.PowerManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.ReminderCacheRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.data.returnapp.CustomReturnAppStore
import com.example.focus_app.domain.reminder.ReminderPolicy
import com.example.focus_app.domain.time.SystemClock
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 后续提醒只使用一次性 WorkManager 任务：进程被普通回收后仍能恢复，但不会轮询或常驻。
 */
interface FollowUpReminderWorkScheduler {
    fun schedule(sessionId: Long, delayMillis: Long)
    fun cancel(sessionId: Long)
}

@Singleton
class AndroidFollowUpReminderWorkScheduler @Inject constructor(
    private val workManager: WorkManager
) : FollowUpReminderWorkScheduler {
    override fun schedule(sessionId: Long, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<FollowUpReminderWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(FollowUpReminderWorker.INPUT_SESSION_ID to sessionId))
            .build()
        workManager.enqueueUniqueWork(workName(sessionId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(sessionId: Long) {
        workManager.cancelUniqueWork(workName(sessionId))
    }

    private fun workName(sessionId: Long) = "focus_follow_up_$sessionId"
}

object NoOpFollowUpReminderWorkScheduler : FollowUpReminderWorkScheduler {
    override fun schedule(sessionId: Long, delayMillis: Long) = Unit
    override fun cancel(sessionId: Long) = Unit
}

@HiltWorker
class FollowUpReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sessionRepository: AppSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val taskRepository: TaskRepository,
    private val cacheRepository: ReminderCacheRepository,
    private val launcher: ReminderLauncher,
    private val customReturnAppStore: CustomReturnAppStore
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(INPUT_SESSION_ID, 0L)
        if (sessionId <= 0L) return Result.failure()

        val settings = settingsRepository.getSettings()
        if (!isDeviceInteractive()) return Result.success()
        if (!settings.guardianEnabled) return Result.success()
        val session = sessionRepository.sessionById(sessionId) ?: return Result.success()
        if (session.endedAt != null) return Result.success()
        if (sessionRepository.currentOpenSession()?.id != sessionId) return Result.success()
        if (settings.targetApps.none { it.packageName == session.packageName }) return Result.success()
        if (latestForegroundPackage() != session.packageName) return Result.success()

        val now = SystemClock.nowMillis()
        val policy = ReminderPolicy(SystemClock)
        val since = now - settings.reminderWindowMinutes * 60_000L
        if (!policy.canShow(sessionRepository.reminderTimesSince(since), settings)) {
            return Result.success()
        }

        val taskTitle = session.taskId?.let { taskId ->
            taskRepository.observeAll().first().firstOrNull { it.id == taskId }?.title
        }
        val message = session.taskId?.let { taskId ->
            cacheRepository.next(taskId, session.packageName, session.toneKey)
        } ?: if (taskTitle.isNullOrBlank()) {
            "\u5148\u653e\u4e0b ${session.appName}\u3002\u56de\u5230 Focus \u9009\u4e00\u4ef6\u771f\u6b63\u60f3\u5b8c\u6210\u7684\u4e8b\u3002"
        } else {
            "\u4f60\u539f\u672c\u51c6\u5907\u5b8c\u6210\u300c${taskTitle.take(24)}\u300d\u3002\u73b0\u5728\u56de\u53bb\u7ee7\u7eed\u3002"
        }

        sessionRepository.updateRemindedAt(sessionId, now)
        launcher.show(
            ReminderLaunchData(
                sessionId = session.id,
                taskId = session.taskId,
                taskTitle = taskTitle,
                appName = session.appName,
                message = message,
                showBreathing = settings.enableBreathingPause,
                returnDestination = settings.returnDestination,
                windowReminderCount = sessionRepository.countShownRemindersSince(since),
                windowLimit = settings.maxRemindersPerWindow,
                windowMinutes = settings.reminderWindowMinutes,
                returnPackageName = customReturnAppStore.read(),
                forceReminder = settings.forceReminder
            )
        )
        return Result.success()
    }

    private fun isDeviceInteractive(): Boolean {
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isInteractive != false
    }

    @Suppress("DEPRECATION")
    private fun latestForegroundPackage(): String? = try {
        val usageStats = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(now - FOREGROUND_LOOKBACK_MS, now)
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTime = Long.MIN_VALUE
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val becameForeground =
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            if (becameForeground && event.timeStamp >= latestTime) {
                latestPackage = event.packageName
                latestTime = event.timeStamp
            }
        }
        latestPackage
    } catch (_: SecurityException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    companion object {
        const val INPUT_SESSION_ID = "follow_up_session_id"
        private const val FOREGROUND_LOOKBACK_MS = 24 * 60 * 60 * 1_000L
    }
}
