package com.example.focus_app.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.domain.model.ReturnDestination
import com.example.focus_app.domain.reminder.SnoozeDurationPolicy
import com.example.focus_app.service.ReminderLaunchData
import com.example.focus_app.service.ReminderLauncher
import com.example.focus_app.service.ReminderPresentationRegistry
import com.example.focus_app.service.SessionReminderScheduler
import com.example.focus_app.service.CustomReturnResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReminderUiState(
    val message: String = "",
    val taskTitle: String? = null,
    val appName: String = "目标应用",
    val showBreathing: Boolean = false,
    val breathingStep: Int = 5,
    val returnDestination: ReturnDestination = ReturnDestination.FOCUS,
    val windowReminderCount: Int = 0,
    val windowLimit: Int = 0,
    val windowMinutes: Int = 0,
    val returnPackageName: String = "",
    val customReturnError: String? = null
)

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val sessionRepository: AppSessionRepository,
    private val launcher: ReminderLauncher,
    private val scheduler: SessionReminderScheduler,
    private val reminderPresentationRegistry: ReminderPresentationRegistry
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReminderUiState())
    val uiState: StateFlow<ReminderUiState> = _uiState.asStateFlow()
    private var launchData: ReminderLaunchData? = null

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
            returnPackageName = data.returnPackageName
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
        reminderPresentationRegistry.hide(sessionId)
        viewModelScope.launch {
            sessionRepository.markUserAction(sessionId, "returned_to_focus")
            launcher.returnToFocus(data.taskId)
            onComplete()
        }
    }

    fun returnHome(sessionId: Long, onComplete: () -> Unit = {}) {
        if (launchData?.sessionId != sessionId) return
        reminderPresentationRegistry.hide(sessionId)
        viewModelScope.launch {
            sessionRepository.markUserAction(sessionId, "returned_home")
            launcher.returnHome()
            onComplete()
        }
    }

    fun returnToCustom(sessionId: Long, onComplete: () -> Unit = {}) {
        val data = launchData?.takeIf { it.sessionId == sessionId } ?: return
        viewModelScope.launch {
            when (launcher.returnToCustom(data.returnPackageName)) {
                CustomReturnResult.SUCCESS -> {
                    reminderPresentationRegistry.hide(sessionId)
                    sessionRepository.markUserAction(sessionId, "returned_to_custom")
                    onComplete()
                }
                CustomReturnResult.NO_APP_CONFIGURED -> showCustomReturnError("还没有选择要跳转的应用")
                CustomReturnResult.APP_UNAVAILABLE -> showCustomReturnError("指定应用不可用，请重新选择")
                CustomReturnResult.LAUNCH_FAILED -> showCustomReturnError("无法打开指定应用，请稍后重试")
            }
        }
    }

    private fun showCustomReturnError(message: String) {
        _uiState.value = _uiState.value.copy(customReturnError = message)
    }

    fun snooze(sessionId: Long, minutes: Int, onComplete: () -> Unit = {}) {
        if (launchData?.sessionId != sessionId) return
        if (!SnoozeDurationPolicy.isValid(minutes)) return
        reminderPresentationRegistry.keepSnoozeTransition(sessionId)
        viewModelScope.launch {
            val delayMillis = minutes * 60_000L
            sessionRepository.markUserAction(sessionId, "snoozed_${minutes}m")
            sessionRepository.setSnoozeUntil(
                sessionId = sessionId,
                snoozeUntil = System.currentTimeMillis() + delayMillis
            )
            scheduler.scheduleFollowUp(sessionId, delayMillis)
            onComplete()
        }
    }
    fun continueTargetApp(sessionId: Long, onComplete: () -> Unit = {}) {
        snooze(sessionId, 10, onComplete)
    }
}
