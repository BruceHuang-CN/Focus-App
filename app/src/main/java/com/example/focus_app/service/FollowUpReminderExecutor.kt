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
    private val environment: FollowUpEnvironment,
    private val displayRepository: ReminderDisplayRepository,
    private val attemptIdProvider: () -> String,
    private val countdownNotifier: FollowUpCountdownNotifier = NoOpFollowUpCountdownNotifier,
    private val taskEligible: suspend (AppUsageSession) -> Boolean = { true },
    private val refreshContext: suspend (AppUsageSession) -> AppUsageSession = { it }
) : FollowUpExecutor {
    override suspend fun execute(sessionId: Long): FollowUpDecision {
        countdownNotifier.cancel(sessionId)
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
                foregroundSnapshot = environment.foregroundSnapshot(),
                remindedCountSinceWindow = displayRepository.countSince(since),
                maxRemindersPerWindow = settings.maxRemindersPerWindow
            )
        )
        if (decision != FollowUpDecision.SHOW) {
            if (decision == FollowUpDecision.SKIP) {
                sessionRepository.setSnoozeUntil(sessionId, null)
            }
            return decision
        }

        val original = sessionRepository.sessionById(sessionId) ?: run {
            sessionRepository.setSnoozeUntil(sessionId, null)
            return FollowUpDecision.SKIP
        }
        val session = refreshContext(original)
        // A task expiring changes the text context, never the guardian entitlement.
        if (!taskEligible(session)) return FollowUpDecision.RETRY
        if ((session.snoozeUntil ?: Long.MIN_VALUE) > SystemClock.nowMillis()) return FollowUpDecision.RETRY
        val taskTitle = taskTitleProvider(session.taskId)
        val message = messageProvider(session) ?: if (taskTitle.isNullOrBlank()) {
            "先放下 ${session.appName}。回到回神 选一件真正想完成的事。"
        } else {
            "你原本准备完成「${taskTitle.take(24)}」。现在回去继续。"
        }
        val latestSettings = settingsProvider()
        val finalDecision = gate.decide(FollowUpGateInput(
            guardianEnabled = latestSettings.guardianEnabled,
            session = sessionRepository.sessionById(sessionId),
            currentOpenSessionId = sessionRepository.currentOpenSession()?.id,
            targetPackages = latestSettings.targetApps.map { it.packageName },
            deviceInteractive = environment.isDeviceInteractive(),
            foregroundSnapshot = environment.foregroundSnapshot(),
            remindedCountSinceWindow = 0,
            maxRemindersPerWindow = latestSettings.maxRemindersPerWindow
        ))
        if (finalDecision != FollowUpDecision.SHOW) {
            if (finalDecision == FollowUpDecision.SKIP) sessionRepository.setSnoozeUntil(sessionId, null)
            return finalDecision
        }
        if (!taskEligible(session)) return FollowUpDecision.RETRY
        if (!sessionRepository.claimSnoozeForContext(session, SystemClock.nowMillis())) {
            return FollowUpDecision.SKIP
        }
        launcher.show(
            ReminderLaunchData(
                sessionId = session.id,
                taskId = session.taskId,
                taskContextStartedAt = session.reminderContextStart(),
                taskTitle = taskTitle,
                appName = session.appName,
                message = message,
                showBreathing = latestSettings.enableBreathingPause,
                returnDestination = latestSettings.returnDestination,
                targetPackageName = session.packageName,
                windowReminderCount = displayRepository.countSince(SystemClock.nowMillis() - latestSettings.reminderWindowMinutes * 60_000L),
                windowLimit = latestSettings.maxRemindersPerWindow,
                windowMinutes = latestSettings.reminderWindowMinutes,
                returnPackageName = returnPackageProvider(),
                forceReminder = latestSettings.forceReminder,
                attemptId = attemptIdProvider(),
                displayKind = ReminderDisplayKind.FOLLOW_UP
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
        environment: FollowUpEnvironment,
        displayRepository: ReminderDisplayRepository,
        attemptIdGenerator: ReminderAttemptIdGenerator,
        countdownNotifier: FollowUpCountdownNotifier,
        @ApplicationContext context: Context
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
            } ?: kotlin.run {
                val title = session.taskId?.let { id -> taskRepository.observeAll().first().firstOrNull { it.id == id }?.title }
                if (title.isNullOrBlank()) context.localizedText(R.string.service_no_task_reminder, session.appName)
                else context.localizedText(R.string.service_task_reminder, title.take(40), session.appName)
            }
        },
        returnPackageProvider = customReturnAppStore::read,
        launcher = launcher,
        gate = gate,
        environment = environment,
        displayRepository = displayRepository,
        attemptIdProvider = attemptIdGenerator::newId,
        countdownNotifier = countdownNotifier,
        taskEligible = taskRepository::isSessionEligible,
        refreshContext = { session ->
            if (taskRepository.isSessionEligible(session)) session else {
                val task = taskRepository.observeActive().first()
                sessionRepository.bindTask(session.id, task?.id, SystemClock.nowMillis())
                sessionRepository.sessionById(session.id) ?: session
            }
        }
    )
}





