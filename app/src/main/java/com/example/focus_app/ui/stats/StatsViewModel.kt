package com.example.focus_app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.domain.model.FocusStats
import com.example.focus_app.domain.model.StatsRange
import com.example.focus_app.domain.usecase.GetStatsUseCase
import com.example.focus_app.domain.usecase.StreakStatus
import com.example.focus_app.domain.usecase.StreakStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatsUiState(
    val range: StatsRange = StatsRange.TODAY,
    val stats: FocusStats? = null,
    val streak: StreakStatus? = null
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val getStatsUseCase: GetStatsUseCase,
    private val streakStatusUseCase: StreakStatusUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun selectRange(range: StatsRange) {
        _uiState.update { it.copy(range = range) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val range = _uiState.value.range
            _uiState.update {
                it.copy(
                    stats = getStatsUseCase(range),
                    streak = streakStatusUseCase()
                )
            }
        }
    }
}
