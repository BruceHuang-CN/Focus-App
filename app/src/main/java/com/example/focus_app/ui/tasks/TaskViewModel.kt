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
import kotlinx.coroutines.flow.map
import com.example.focus_app.data.local.entity.toDomain
import com.example.focus_app.domain.model.TaskGroup
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val groupRepository: com.example.focus_app.data.repository.TaskGroupRepository
) : ViewModel() {
    val groups = groupRepository.observeSnapshot().map { rows -> rows.map { it.group.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveGroup(group: TaskGroup, onResult: (ScheduleValidation) -> Unit) {
        viewModelScope.launch { onResult(groupRepository.save(group)) }
    }
    fun deleteGroup(id: Long) { viewModelScope.launch { groupRepository.deleteGroup(id) } }
    fun move(taskId: Long, groupId: Long, inherit: Boolean) {
        viewModelScope.launch { groupRepository.moveTask(taskId, groupId, inherit) }
    }
    fun activateGroup(id: Long, minutes: Int) {
        val task = tasks.value.filter { it.groupId == id && !it.isCompleted }
            .minWithOrNull(compareBy<FocusTask> { it.sortOrder }.thenBy { it.id }) ?: return
        viewModelScope.launch { repository.setManualActive(task.id, minutes) }
    }
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

    fun setManualActive(id: Long, minutes: Int = 30) {
        viewModelScope.launch { repository.setManualActive(id, minutes) }
    }
}
