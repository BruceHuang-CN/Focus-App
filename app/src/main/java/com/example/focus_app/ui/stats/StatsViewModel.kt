package com.example.focus_app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.domain.usecase.GetTodayStatsUseCase
import com.example.focus_app.domain.usecase.TodayStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatsUiState(val openCount: Int = 0, val exitedCount: Int = 0, val exitRate: Float = 0f)

@HiltViewModel
class StatsViewModel @Inject constructor(private val getTodayStatsUseCase: GetTodayStatsUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()
    init { refresh() }
    fun refresh() {
        viewModelScope.launch {
            val s: TodayStats = getTodayStatsUseCase()
            _uiState.value = StatsUiState(s.openCount, s.exitedCount, if (s.openCount > 0) s.exitedCount.toFloat() / s.openCount else 0f)
        }
    }
}
