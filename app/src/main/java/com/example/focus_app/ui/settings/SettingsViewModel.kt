package com.example.focus_app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.domain.model.AiProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.getSettingsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun updateRemindDelay(m: Int) { update { it.copy(remindDelayMinutes = m) } }
    fun updateMaxReminds(m: Int) { update { it.copy(maxRemindsPerHour = m) } }
    fun updateAiProvider(p: AiProvider) { update { it.copy(aiProvider = p, apiEndpoint = p.defaultEndpoint, aiModel = p.defaultModel) } }
    fun updateApiEndpoint(e: String) { update { it.copy(apiEndpoint = e, aiProvider = AiProvider.CUSTOM) } }
    fun updateApiKey(k: String) { update { it.copy(apiKey = k) } }
    fun updateAiModel(m: String) { update { it.copy(aiModel = m) } }
    fun updatePersonality(p: String) { update { it.copy(aiPersonality = p) } }
    fun toggleBreathingPause() { update { it.copy(enableBreathingPause = !it.enableBreathingPause) } }
    fun toggleAccessibility() { update { it.copy(enableAccessibility = !it.enableAccessibility) } }
    fun updateTargetApps(apps: List<AppInfo>) { update { it.copy(targetApps = apps) } }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.updateSettings(transform(settingsRepository.getSettings())) }
    }
}
