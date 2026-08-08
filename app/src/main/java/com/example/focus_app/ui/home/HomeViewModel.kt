package com.example.focus_app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.MoodRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.model.FocusTask
import com.example.focus_app.domain.task.StreakCalculator
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
    val recentReminders: List<AppUsageSession> = emptyList(),
    val activeTask: FocusTask? = null,
    val completedToday: Int = 0,
    val streakDays: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appSessionRepository: AppSessionRepository,
    private val moodRepository: MoodRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { loadStats() }

    private fun loadStats() {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val tomorrowStart = todayStart + DAY_MILLIS
            val todaySessions = appSessionRepository.sessionsBetween(todayStart, tomorrowStart)
            val recentStart = System.currentTimeMillis() - DAY_MILLIS
            val recentReminders = appSessionRepository.sessionsBetween(recentStart, tomorrowStart)
                .filter { it.remindedAt != null }
                .sortedByDescending { it.startedAt }
                .take(20)
            val completedToday = taskRepository.completedCountBetween(todayStart, tomorrowStart)
            _uiState.update {
                it.copy(
                    openCountToday = todaySessions.size,
                    remindedCountToday = todaySessions.count { it.remindedAt != null },
                    exitedCountToday = todaySessions.count { it.userAction in ACTIVE_EXIT_ACTIONS },
                    latestMood = moodRepository.getLatestMood()?.mood,
                    activeTask = taskRepository.observeActive().first(),
                    completedToday = completedToday,
                    recentReminders = recentReminders,
                    streakDays = StreakCalculator.streakDays(today) { day ->
                        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
                        taskRepository.completedCountBetween(start, start + DAY_MILLIS)
                    }
                )
            }
        }
    }

    fun completeCurrentTask() {
        _uiState.value.activeTask?.let { task ->
            viewModelScope.launch { taskRepository.setCompleted(task.id) }
        }
    }

    fun refresh() { loadStats() }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        val ACTIVE_EXIT_ACTIONS = setOf("returned_to_focus", "returned_home")
    }
}
