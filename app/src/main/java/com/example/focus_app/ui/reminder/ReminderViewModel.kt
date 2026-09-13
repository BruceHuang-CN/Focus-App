package com.example.focus_app.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.reminder.DefaultReminderActionOrderStore
import com.example.focus_app.data.reminder.ReminderActionOrderStore
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.domain.model.ReturnDestination
import com.example.focus_app.domain.reminder.SnoozeDurationPolicy
import com.example.focus_app.service.AppSessionCoordinator
import com.example.focus_app.service.ReminderLaunchData
import com.example.focus_app.service.ReminderLauncher
import com.example.focus_app.service.ReminderPresentationRegistry
import com.example.focus_app.service.ReturnToFocusGrace
import com.example.focus_app.service.SessionReminderScheduler
import com.example.focus_app.service.CustomReturnResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ReminderUiState(
    val message: String = "",
    val taskTitle: String? = null,
    val appName: String = "",
    val showBreathing: Boolean = false,
    val breathingStep: Int = 5,
    val returnDestination: ReturnDestination = ReturnDestination.FOCUS,
    val windowReminderCount: Int = 0,
    val windowLimit: Int = 0,
    val windowMinutes: Int = 0,
    val returnPackageName: String = "",
    val customReturnError: Int? = null,
    val randomizeActions: Boolean? = null
)

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val sessionRepository: AppSessionRepository,
    private val launcher: ReminderLauncher,
    private val scheduler: SessionReminderScheduler,
    private val reminderPresentationRegistry: ReminderPresentationRegistry,
    private val appSessionCoordinator: AppSessionCoordinator,
    private val returnToFocusGrace: ReturnToFocusGrace,
    private val returnNavigationGuard: com.example.focus_app.service.ReturnNavigationGuard,
    private val actionOrderStore: ReminderActionOrderStore = DefaultReminderActionOrderStore,
    private val eventRepository: com.example.focus_app.data.repository.ReminderEventRepository? = null,
    private val taskRepository: com.example.focus_app.data.repository.TaskRepository? = null,
    private val settingsRepository: com.example.focus_app.data.repository.SettingsRepository? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReminderUiState())
    val uiState: StateFlow<ReminderUiState> = _uiState.asStateFlow()
    private var launchData: ReminderLaunchData? = null
    private val actionMutex = Mutex()
    private val completedAttempts = mutableSetOf<String>()
    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch { actionMutex.withLock { block() } }
    }

    fun init(data: ReminderLaunchData) {
        launchData = data
        _uiState.value = ReminderUiState(
            message = data.message,
            taskTitle = data.taskTitle,
            appName = data.appName,
            showBreathing = data.showBreathing,
            returnDestination = data.returnDestination,
            windowReminderCount = data.windowReminderCount,
            windowLimit = data.windowLimit,
            windowMinutes = data.windowMinutes,
            returnPackageName = data.returnPackageName,
            randomizeActions = actionOrderStore.randomizeEnabled.value
        )
    }

    fun onBreathingTick(step: Int) {
        _uiState.value = _uiState.value.copy(breathingStep = step)
    }

    fun fadeBreathing() {
        _uiState.value = _uiState.value.copy(showBreathing = false)
    }

    fun returnToFocus(sessionId: Long, onComplete: () -> Unit = {}) {
        val data = launchData?.takeIf { it.sessionId == sessionId } ?: return
        launchAction {
            if (!recordAction(data, "returned_to_focus")) return@launchAction
            // Block transition events before closing the old session can yield to queued events.
            returnNavigationGuard.beginReturn()
            try {
                reminderPresentationRegistry.hide(sessionId, data.attemptId)
                appSessionCoordinator.stopCurrentSession()
                returnToFocusGrace.start(data)
                launcher.returnToFocus(data.taskId)
                onComplete()
            } catch (failure: Exception) {
                returnNavigationGuard.cancelReturn()
                throw failure
            }
        }
    }

    fun returnHome(sessionId: Long, onComplete: () -> Unit = {}) {
        val data = launchData?.takeIf { it.sessionId == sessionId } ?: return
        launchAction {
            if (!recordAction(data, "returned_home")) return@launchAction
            reminderPresentationRegistry.hide(sessionId, data.attemptId)
            launcher.returnHome()
            onComplete()
        }
    }

    fun returnToCustom(sessionId: Long, onComplete: () -> Unit = {}) {
        val data = launchData?.takeIf { it.sessionId == sessionId } ?: return
        launchAction {
            if (!validAction(data)) return@launchAction
            when (launcher.returnToCustom(data.returnPackageName)) {
                CustomReturnResult.SUCCESS -> {
                    reminderPresentationRegistry.hide(sessionId, data.attemptId)
                    if (!persistAction(data, "returned_to_custom")) return@launchAction
                    onComplete()
                }
                CustomReturnResult.NO_APP_CONFIGURED -> showCustomReturnError(com.example.focus_app.R.string.core_no_return_app)
                CustomReturnResult.APP_UNAVAILABLE -> showCustomReturnError(com.example.focus_app.R.string.core_return_app_unavailable)
                CustomReturnResult.LAUNCH_FAILED -> showCustomReturnError(com.example.focus_app.R.string.core_return_app_failed)
            }
        }
    }

    private fun showCustomReturnError(message: Int) {
        _uiState.value = _uiState.value.copy(customReturnError = message)
    }

    fun returnToConfiguredDestination(sessionId: Long, onComplete: () -> Unit = {}) {
        val data = launchData?.takeIf { it.sessionId == sessionId } ?: return
        when (data.returnDestination) {
            ReturnDestination.FOCUS -> returnToFocus(sessionId, onComplete)
            ReturnDestination.HOME -> returnHome(sessionId, onComplete)
            ReturnDestination.CUSTOM -> returnToCustom(sessionId, onComplete)
        }
    }

    fun snooze(sessionId: Long, minutes: Int, onComplete: () -> Unit = {}) {
        scheduleTimedDecision(sessionId, minutes, "snoozed", onComplete)
    }

    fun useIntentionally(sessionId: Long, minutes: Int, onComplete: () -> Unit = {}) {
        scheduleTimedDecision(sessionId, minutes, "intentional", onComplete)
    }

    fun takeBreak(sessionId: Long, minutes: Int, onComplete: () -> Unit = {}) {
        scheduleTimedDecision(sessionId, minutes, "rest", onComplete)
    }

    private fun scheduleTimedDecision(
        sessionId: Long,
        minutes: Int,
        actionKey: String,
        onComplete: () -> Unit
    ) {
        val data = launchData?.takeIf { it.sessionId == sessionId } ?: return
        if (!SnoozeDurationPolicy.isValid(minutes)) return
        launchAction {
            val delayMillis = minutes * 60_000L
            if (!recordAction(data, "${actionKey}_${minutes}m")) return@launchAction
            reminderPresentationRegistry.keepSnoozeTransition(sessionId, data.attemptId)
            sessionRepository.setSnoozeUntil(
                sessionId = sessionId,
                snoozeUntil = System.currentTimeMillis() + delayMillis
            )
            scheduler.scheduleFollowUp(sessionId, delayMillis)
            launcher.dismiss(sessionId)
            onComplete()
        }
    }

    private suspend fun validAction(data: ReminderLaunchData): Boolean {
        if (launchData?.attemptId != data.attemptId || data.attemptId in completedAttempts) return false
        if (eventRepository != null && !eventRepository.canDecide(data.attemptId, data.sessionId)) return false
        settingsRepository?.getSettings()?.let { settings ->
            if (!settings.guardianEnabled || settings.targetApps.none { it.packageName == data.targetPackageName }) return false
        }
        if (taskRepository != null) {
            val session = sessionRepository.sessionById(data.sessionId) ?: return false
            if (sessionRepository.currentOpenSession()?.id != session.id ||
                !com.example.focus_app.service.matchesReminderContext(data, session) ||
                !taskRepository.isSessionEligible(session)) return false
        }
        return true
    }

    private suspend fun recordAction(data: ReminderLaunchData, action: String): Boolean {
        if (!validAction(data)) return false
        return persistAction(data, action)
    }

    private suspend fun persistAction(data: ReminderLaunchData, action: String): Boolean {
        val recorded = if (eventRepository != null) eventRepository.record(data.attemptId, data.sessionId, action)
            else { sessionRepository.markUserAction(data.sessionId, action); true }
        if (recorded) completedAttempts.add(data.attemptId)
        return recorded
    }

    fun continueTargetApp(sessionId: Long, onComplete: () -> Unit = {}) {
        snooze(sessionId, 10, onComplete)
    }
}
