package com.example.focus_app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.repository.StatisticsRepository
import com.example.focus_app.data.repository.StatisticsSnapshot
import com.example.focus_app.domain.model.StatsRange
import com.example.focus_app.domain.stats.ActivityPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class StatsUiState(
    val range: StatsRange = StatsRange.TODAY,
    val period: ActivityPeriod = ActivityPeriod.YEAR,
    val anchor: LocalDate = LocalDate.now(),
    val snapshot: StatisticsSnapshot? = null,
    val loading: Boolean = true,
    val error: Int? = null,
    val followingToday: Boolean = true,
    val revision: Int = 0
)
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(private val repository: StatisticsRepository) : ViewModel() {
    private val selection = MutableStateFlow(StatsUiState())
    private val ticks = flow { while (currentCoroutineContext().isActive) { emit(ZonedDateTime.now()); delay(30_000) } }
    val uiState = combine(selection, ticks) { state, now -> (if (state.followingToday) state.copy(anchor = now.toLocalDate()) else state) to now }.flatMapLatest { (state, now) ->
        repository.observe(state.range, state.period, state.anchor, now)
            .map { state.copy(snapshot = it, loading = false) }
            .catch { if (it is CancellationException) throw it; emit(state.copy(loading = false, error = com.example.focus_app.R.string.core_stats_error)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
    fun selectRange(range: StatsRange) { selection.update { it.copy(range = range) } }
    fun selectPeriod(period: ActivityPeriod) { selection.update { it.copy(period = period, anchor = LocalDate.now(), followingToday = true) } }
    fun shiftPeriod(direction: Int) {
        selection.update { state ->
            val today = LocalDate.now()
            val anchor = if (state.followingToday) today else state.anchor
            val next = com.example.focus_app.domain.stats.ReminderStatistics.shiftedAnchor(anchor, state.period, direction)
            if (direction > 0 && next >= com.example.focus_app.domain.stats.ReminderStatistics.bounds(today, state.period).first) state.copy(anchor = today, followingToday = true) else state.copy(anchor = next, followingToday = false)
        }
    }
    fun refresh() { selection.update { it.copy(error = null, revision = it.revision + 1) } }
}
