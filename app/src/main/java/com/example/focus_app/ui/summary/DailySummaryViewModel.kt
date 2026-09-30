package com.example.focus_app.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.repository.MoodRepository
import com.example.focus_app.data.summary.DailySummaryRepository
import com.example.focus_app.data.summary.DailySummaryStore
import com.example.focus_app.service.DailySummaryScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class DailySummaryViewModel @Inject constructor(
    private val repository: DailySummaryRepository,
    private val store: DailySummaryStore,
    private val scheduler: DailySummaryScheduler,
    moods: MoodRepository
) : ViewModel() {
    val settings = store.settings
    val record = repository.record
    val generating = repository.generating
    val latestMood = moods.observeLatestMood().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val mutableFailed = MutableStateFlow(false)
    val failed = mutableFailed.asStateFlow()
    private var requested = false

    fun configure(enabled: Boolean, minute: Int) {
        store.updateSettings(enabled, minute)
        scheduler.scheduleNext(reset = true)
    }

    fun generate() {
        if (requested || generating.value) return
        requested = true
        viewModelScope.launch {
            mutableFailed.value = false
            try { repository.generate(force = true) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableFailed.value = true }
            finally { requested = false }
        }
    }
}
