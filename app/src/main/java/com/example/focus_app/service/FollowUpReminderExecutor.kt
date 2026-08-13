package com.example.focus_app.service

import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.ReminderCacheRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.data.returnapp.CustomReturnAppStore
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.time.SystemClock
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 稍后提醒到期后的统一执行入口：先经过 [FollowUpReminderGate] 决策，
 * 决策为 SHOW 时更新已提醒时间并展示提醒。内存准时调度与 WorkManager 兜底共用此逻辑。
 */
@Singleton
class FollowUpReminderExecutor(
    private val sessionRepository: AppSessionRepository,
    private val settingsProvider: suspend () -> AppSettings,
    private val taskTitleProvider: suspend (Long?) -> String?,
    private val messageProvider: suspend (AppUsageSession) -> String?,
    private val returnPackageProvider: () -> String,
    private val launcher: ReminderLauncher,
    private val gate: FollowUpReminderGate,
    private val environment: FollowUpEnvironment
) : FollowUpExecutor {
    override suspend fun execute(sessionId: Long): FollowUpDecision {
        val settings = settingsProvider()
        val now = SystemClock.nowMillis()
        val since = now - settings.reminderWindowMinutes * 60_000L
        val decision = gate.decide(
            FollowUpGateInput(
                guardianEnabled = settings.guardianEnabled,
                session = sessionRepository.sessionById(sessionId),
                currentOpenSessionId = sessionRepository.currentOpenSession()?.id,
                targetPackages = settings.targetApps.map { it.packageName },
                deviceInteractive = environment.isDeviceInteractive(),
                latestForegroundPackage = environment.latestForegroundPackage(),
                remindedCountSinceWindow = sessionRepository.reminderTimesSince(since).size,
                maxRemindersPerWindow = settings.maxRemindersPerWindow
            )
        )
        if (decision != FollowUpDecision.SHOW) return decision

        val session = sessionRepository.sessionById(sessionId) ?: return FollowUpDecision.SKIP
        val taskTitle = taskTitleProvider(session.taskId)
        val message = messageProvider(session) ?: if (taskTitle.isNullOrBlank()) {
            "先放下 ${session.appName}。回到 Focus 选一件真正想完成的事。"
        } else {
            "你原本准备完成「${taskTitle.take(24)}」。现在回去继续。"
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
                returnPackageName = returnPackageProvider(),
                forceReminder = settings.forceReminder
            )
        )
        return FollowUpDecision.SHOW
    }

    @Inject
    constructor(
        sessionRepository: AppSessionRepository,
        settingsRepository: SettingsRepository,
        taskRepository: TaskRepository,
        cacheRepository: ReminderCacheRepository,
        launcher: ReminderLauncher,
        customReturnAppStore: CustomReturnAppStore,
        gate: FollowUpReminderGate,
        environment: FollowUpEnvironment
    ) : this(
        sessionRepository = sessionRepository,
        settingsProvider = settingsRepository::getSettings,
        taskTitleProvider = { taskId ->
            taskId?.let { id ->
                taskRepository.observeAll().first().firstOrNull { it.id == id }?.title
            }
        },
        messageProvider = { session ->
            session.taskId?.let { taskId ->
                cacheRepository.next(taskId, session.packageName, session.toneKey)
            }
        },
        returnPackageProvider = customReturnAppStore::read,
        launcher = launcher,
        gate = gate,
        environment = environment
    )
}





