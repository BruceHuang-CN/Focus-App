package com.example.focus_app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.repository.AppUsageRepository
import com.example.focus_app.data.repository.MoodRepository
import com.example.focus_app.domain.model.AppUsageEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(val latestMood: String? = null, val openCountToday: Int = 0, val remindedCountToday: Int = 0, val exitedCountToday: Int = 0, val recentReminders: List<AppUsageEvent> = emptyList())

@HiltViewModel
class HomeViewModel @Inject constructor(private val appUsageRepository: AppUsageRepository, private val moodRepository: MoodRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { loadStats(); observeRecentReminders() }

    private fun loadStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(openCountToday = appUsageRepository.getOpenCountToday(), remindedCountToday = appUsageRepository.getRemindedCountThisHour(), exitedCountToday = appUsageRepository.getExitedCountToday(), latestMood = moodRepository.getLatestMood()?.mood) }
        }
    }

    private fun observeRecentReminders() {
        viewModelScope.launch {
            appUsageRepository.getRemindedEventsSince(System.currentTimeMillis() - 86400000L).collect { list ->
                _uiState.update { it.copy(recentReminders = list.take(20)) }
            }
        }
    }

    fun refresh() { loadStats() }
}
