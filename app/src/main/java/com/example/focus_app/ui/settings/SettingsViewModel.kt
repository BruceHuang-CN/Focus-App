package com.example.focus_app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.domain.model.AiProvider
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.domain.model.ReminderTone
import com.example.focus_app.domain.model.ReturnDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.getSettingsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

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
    fun updateDetectionMode(mode: DetectionMode) { update { it.copy(detectionMode = mode) } }
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
    fun updateApiEndpoint(e: String) { update { it.copy(apiEndpoint = e, aiProvider = AiProvider.CUSTOM) } }
    fun updateApiKey(k: String) {
        viewModelScope.launch { settingsRepository.saveApiKey(k) }
    }

    fun clearApiKey() {
        viewModelScope.launch { settingsRepository.clearApiKey() }
    }
    fun updateAiModel(m: String) { update { it.copy(aiModel = m) } }
    fun updatePersonality(p: String) { update { it.copy(aiPersonality = p, toneKey = ReminderTone.fromKey(p)) } }
    fun toggleBreathingPause() { update { it.copy(enableBreathingPause = !it.enableBreathingPause) } }
    fun toggleAccessibility() { update { it.copy(enableAccessibility = !it.enableAccessibility) } }
    fun updateTargetApps(apps: List<AppInfo>) { update { it.copy(targetApps = apps) } }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }
}
