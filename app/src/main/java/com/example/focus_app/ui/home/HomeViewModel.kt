package com.example.focus_app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.repository.AppUsageRepository
import com.example.focus_app.data.repository.MoodRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.domain.model.AppUsageEvent
import com.example.focus_app.domain.model.FocusTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HomeUiState(
    val latestMood: String? = null,
    val openCountToday: Int = 0,
    val remindedCountToday: Int = 0,
    val exitedCountToday: Int = 0,
    val recentReminders: List<AppUsageEvent> = emptyList(),
    val activeTask: FocusTask? = null,
    val completedToday: Int = 0,
    val streakDays: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appUsageRepository: AppUsageRepository,
    private val moodRepository: MoodRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { loadStats(); observeRecentReminders() }

    private fun loadStats() {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val completedToday = taskRepository.completedCountBetween(todayStart, todayStart + DAY_MILLIS)
            _uiState.update {
                it.copy(
                    openCountToday = appUsageRepository.getOpenCountToday(),
                    remindedCountToday = appUsageRepository.getRemindedCountThisHour(),
                    exitedCountToday = appUsageRepository.getExitedCountToday(),
                    latestMood = moodRepository.getLatestMood()?.mood,
                    activeTask = taskRepository.observeActive().first(),
                    completedToday = completedToday,
                    streakDays = computeStreak(taskRepository, today)
                )
            }
        }
    }

    fun completeCurrentTask() {
        _uiState.value.activeTask?.let { task ->
            viewModelScope.launch { taskRepository.setCompleted(task.id) }
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

    private suspend fun computeStreak(repository: TaskRepository, today: LocalDate): Int {
        val zone = ZoneId.systemDefault()
        var streak = 0
        var daysBack = 0
        while (daysBack <= MAX_STREAK_DAYS) {
            val day = today.minusDays(daysBack.toLong())
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = start + DAY_MILLIS
            if (repository.completedCountBetween(start, end) > 0) {
                streak++
            } else {
                break
            }
            daysBack++
        }
        return streak
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val MAX_STREAK_DAYS = 3_650
    }
}
