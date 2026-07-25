package com.example.focus_app.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.repository.AppUsageRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.domain.usecase.BuildReminderContextUseCase
import com.example.focus_app.domain.usecase.GenerateAiReminderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReminderUiState(val aiMessage: String = "正在分析...", val isLoading: Boolean = true, val showBreathing: Boolean = false, val breathingStep: Int = 5, val remindedCount: Int = 0, val maxReminds: Int = 3)

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val generateAiReminderUseCase: GenerateAiReminderUseCase, private val buildReminderContextUseCase: BuildReminderContextUseCase,
    private val appUsageRepository: AppUsageRepository, private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReminderUiState())
    val uiState: StateFlow<ReminderUiState> = _uiState.asStateFlow()

    fun init(eventId: Long, appName: String, personality: String, showBreathing: Boolean) {
        _uiState.value = _uiState.value.copy(showBreathing = showBreathing)
        viewModelScope.launch {
            val s = settingsRepository.getSettings()
            _uiState.value = _uiState.value.copy(remindedCount = appUsageRepository.getRemindedCountThisHour(), maxReminds = s.maxRemindsPerHour)
            try {
                val ctx = buildReminderContextUseCase(appName, personality)
                val res = generateAiReminderUseCase(ctx)
                _uiState.value = _uiState.value.copy(aiMessage = res.getOrDefault("嘿，注意到你又打开 $appName 了。深呼吸一下，想想你本来想做什么？"), isLoading = false)
            } catch (_: Exception) { _uiState.value = _uiState.value.copy(aiMessage = "嘿，注意到你又打开 $appName 了。深呼吸一下，想想你本来想做什么？", isLoading = false) }
        }
    }

    fun onBreathingTick(step: Int) { _uiState.value = _uiState.value.copy(breathingStep = step) }
    fun fadeBreathing() { _uiState.value = _uiState.value.copy(showBreathing = false) }
    fun onExited(eventId: Long) { viewModelScope.launch { appUsageRepository.markReminded(eventId); appUsageRepository.markUserActionById(eventId, "exited") } }
    fun onContinued(eventId: Long) { viewModelScope.launch { appUsageRepository.markReminded(eventId); appUsageRepository.markUserActionById(eventId, "continued") } }
}
