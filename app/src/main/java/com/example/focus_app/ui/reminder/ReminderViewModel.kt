package com.example.focus_app.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.domain.model.ReturnDestination
import com.example.focus_app.service.ReminderLaunchData
import com.example.focus_app.service.ReminderLauncher
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
    val returnPackageName: String = ""
)

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val sessionRepository: AppSessionRepository,
    private val launcher: ReminderLauncher
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
        viewModelScope.launch {
            sessionRepository.markUserAction(sessionId, "returned_to_focus")
            launcher.returnToFocus(data.taskId)
            onComplete()
        }
    }

    fun returnHome(sessionId: Long, onComplete: () -> Unit = {}) {
        if (launchData?.sessionId != sessionId) return
        viewModelScope.launch {
            sessionRepository.markUserAction(sessionId, "returned_home")
            launcher.returnHome()
            onComplete()
        }
    }

    fun returnToCustom(sessionId: Long, onComplete: () -> Unit = {}) {
        val data = launchData?.takeIf { it.sessionId == sessionId } ?: return
        viewModelScope.launch {
            sessionRepository.markUserAction(sessionId, "returned_to_custom")
            launcher.returnToCustom(data.returnPackageName)
            onComplete()
        }
    }

    fun continueTargetApp(sessionId: Long, onComplete: () -> Unit = {}) {
        if (launchData?.sessionId != sessionId) return
        viewModelScope.launch {
            sessionRepository.markUserAction(sessionId, "continued")
            onComplete()
        }
    }
}
