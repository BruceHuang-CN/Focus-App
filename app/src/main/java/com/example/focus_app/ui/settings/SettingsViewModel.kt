package com.example.focus_app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.repository.AiRepository
import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.ConnectionTestResult
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.domain.model.AiProvider
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.domain.model.ReminderTone
import com.example.focus_app.domain.model.ReturnDestination
import com.example.focus_app.domain.reminder.ReminderTonePreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** AI 连接测试的界面状态。 */
sealed interface AiConnectionUiState {
    data object Idle : AiConnectionUiState
    data object Loading : AiConnectionUiState
    data class Success(val modelIds: List<String>) : AiConnectionUiState
    data class Error(val message: String) : AiConnectionUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val aiRepository: AiRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.getSettingsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _aiConnection = MutableStateFlow<AiConnectionUiState>(AiConnectionUiState.Idle)
    val aiConnection: StateFlow<AiConnectionUiState> = _aiConnection.asStateFlow()

    private val _tonePreview = MutableStateFlow("")
    val tonePreview: StateFlow<String> = _tonePreview.asStateFlow()

    private val _notificationPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val notificationPermissionRequests: SharedFlow<Unit> = _notificationPermissionRequests.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.getSettingsFlow(),
                taskRepository.observeActive()
            ) { currentSettings, activeTask ->
                ReminderTonePreview.sampleMessage(
                    tone = currentSettings.toneKey,
                    customInstruction = currentSettings.customToneInstruction,
                    taskTitle = activeTask?.title
                )
            }
                .distinctUntilChanged()
                .collect { preview -> _tonePreview.value = preview }
        }
    }

    fun updateReminderDelaySeconds(seconds: Int) {
        update { it.copy(reminderDelaySeconds = seconds.coerceIn(1, 300)) }
    }

    fun updateReminderWindowMinutes(minutes: Int) {
        update { it.copy(reminderWindowMinutes = minutes.coerceIn(5, 1_440)) }
    }

    fun updateMaxRemindersPerWindow(count: Int) {
        update { it.copy(maxRemindersPerWindow = count.coerceIn(1, 20)) }
    }

    fun updateDailyShortVideoLimitMinutes(minutes: Int) {
        update { it.copy(dailyShortVideoLimitMinutes = minutes.coerceIn(1, 1_440)) }
    }

    fun updateReminderTone(tone: ReminderTone) { update { it.copy(toneKey = tone) } }
    fun updateReturnDestination(destination: ReturnDestination) { update { it.copy(returnDestination = destination) } }
    fun updateDetectionMode(mode: DetectionMode) {
        update { it.copy(detectionMode = mode) }
        if (mode == DetectionMode.COMPATIBILITY) {
            _notificationPermissionRequests.tryEmit(Unit)
        }
    }
    fun updateCustomToneInstruction(instruction: String) { update { it.copy(customToneInstruction = instruction) } }

    fun updateRemindDelay(minutes: Int) {
        val seconds = if (minutes == 0) 3 else minutes * 60
        update {
            it.copy(
                remindDelayMinutes = minutes,
                reminderDelaySeconds = seconds.coerceIn(1, 300)
            )
        }
    }

    fun updateMaxReminds(count: Int) {
        update {
            it.copy(
                maxRemindsPerHour = count,
                maxRemindersPerWindow = count.coerceIn(1, 20)
            )
        }
    }
    fun updateAiProvider(p: AiProvider) { update { it.copy(aiProvider = p, apiEndpoint = p.defaultEndpoint, aiModel = p.defaultModel) } }
    fun updateAiConnection(endpoint: String, model: String) {
        val savedEndpoint = endpoint.trim()
        val savedModel = model.trim()
        update {
            val provider = AiProvider.entries.firstOrNull { candidate ->
                candidate != AiProvider.CUSTOM &&
                    candidate.defaultEndpoint.trimEnd('/') == savedEndpoint.trimEnd('/')
            } ?: AiProvider.CUSTOM
            it.copy(
                aiProvider = provider,
                apiEndpoint = savedEndpoint,
                aiModel = savedModel
            )
        }
    }
    fun updateApiKey(k: String) {
        viewModelScope.launch { settingsRepository.saveApiKey(k) }
    }

    fun testAiConnection() {
        viewModelScope.launch {
            _aiConnection.value = AiConnectionUiState.Loading
            val currentSettings = settingsRepository.getSettings()
            _aiConnection.value = when (val result = aiRepository.testConnection(currentSettings)) {
                is ConnectionTestResult.Success -> AiConnectionUiState.Success(result.modelIds)
                is ConnectionTestResult.Error -> AiConnectionUiState.Error(result.message)
                ConnectionTestResult.Loading -> AiConnectionUiState.Loading
            }
        }
    }

    fun clearApiKey() {
        viewModelScope.launch { settingsRepository.clearApiKey() }
    }
    fun updatePersonality(p: String) { update { it.copy(aiPersonality = p, toneKey = ReminderTone.fromKey(p)) } }
    fun toggleBreathingPause() { update { it.copy(enableBreathingPause = !it.enableBreathingPause) } }
    fun toggleAccessibility() { update { it.copy(enableAccessibility = !it.enableAccessibility) } }
    fun updateTargetApps(apps: List<AppInfo>) { update { it.copy(targetApps = apps) } }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }
}
