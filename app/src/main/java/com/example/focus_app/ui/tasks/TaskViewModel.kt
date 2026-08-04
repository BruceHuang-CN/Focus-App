package com.example.focus_app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.repository.ScheduleValidation
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.domain.model.FocusTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val repository: TaskRepository
) : ViewModel() {
    val tasks: StateFlow<List<FocusTask>> = repository.observeAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val activeTask: StateFlow<FocusTask?> = repository.observeActive().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    fun create(task: FocusTask, onResult: (ScheduleValidation) -> Unit = {}) {
        viewModelScope.launch { onResult(repository.create(task)) }
    }

    fun update(task: FocusTask, onResult: (ScheduleValidation) -> Unit = {}) {
        viewModelScope.launch { onResult(repository.update(task)) }
    }

    fun delete(task: FocusTask) {
        viewModelScope.launch { repository.delete(task) }
    }

    fun complete(id: Long) {
        viewModelScope.launch { repository.setCompleted(id) }
    }

    fun restore(id: Long) {
        viewModelScope.launch { repository.setCompleted(id, isCompleted = false) }
    }

    fun setManualActive(id: Long) {
        viewModelScope.launch { repository.setManualActive(id) }
    }
}
