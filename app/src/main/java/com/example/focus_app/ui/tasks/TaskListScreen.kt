package com.example.focus_app.ui.tasks

import com.example.focus_app.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focus_app.data.repository.ScheduleValidation
import com.example.focus_app.domain.model.FocusTask
import com.example.focus_app.domain.model.TaskGroup

@Composable
fun TaskListScreen(
    onBack: () -> Unit,
    onCelebrate: () -> Unit,
    viewModel: TaskViewModel = hiltViewModel()
) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val active by viewModel.activeTask.collectAsStateWithLifecycle()
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var editId by rememberSaveable { mutableStateOf(0L) }
    var targetGroup by rememberSaveable { mutableStateOf(1L) }
    var inherit by rememberSaveable { mutableStateOf(true) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var moveId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteGroupId by rememberSaveable { mutableStateOf<Long?>(null) }
    var activateTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var activateGroupId by rememberSaveable { mutableStateOf<Long?>(null) }
    val result: (ScheduleValidation) -> Unit = {
        message = when (it) {
            ScheduleValidation.VALID -> null
            ScheduleValidation.END_NOT_AFTER_START -> textContext.getString(R.string.core_schedule_invalid)
            ScheduleValidation.OVERLAP -> textContext.getString(R.string.core_schedule_overlap)
        }
        if (it == ScheduleValidation.VALID) editor = null
    }
    if (editor != null) {
        val group = groups.firstOrNull { it.id == editId }
        val task = tasks.firstOrNull { it.id == editId }
        val isGroup = editor == "group"
        val initial = if (isGroup) group?.let { FocusTask(id = it.id, title = it.name,
            scheduleStartMinute = it.scheduleStartMinute, scheduleEndMinute = it.scheduleEndMinute,
            repeatDaysMask = it.repeatDaysMask) } else task
        TaskEditorScreen(initial, message,
            onSave = { title, start, end, mask ->
                if (isGroup) viewModel.saveGroup((group ?: TaskGroup(name = title)).copy(name = title,
                    scheduleStartMinute = start, scheduleEndMinute = end, repeatDaysMask = mask), result)
                else {
                    val value = (task ?: FocusTask(title = title, groupId = targetGroup,
                        sortOrder = System.currentTimeMillis())).copy(title = title,
                        inheritsGroupSchedule = inherit, scheduleStartMinute = start,
                        scheduleEndMinute = end, repeatDaysMask = mask)
                    if (task == null) viewModel.create(value, result) else viewModel.update(value, result)
                }
            }, onDismiss = { editor = null; message = null },
            editorTitle = if (isGroup) { if (group == null) textContext.getString(R.string.core_new_group) else textContext.getString(R.string.core_edit_group_schedule) }
                else if (task == null) textContext.getString(R.string.core_add_task) else textContext.getString(R.string.core_edit_task),
            inheritsGroupSchedule = if (isGroup) null else inherit, onInheritChange = { inherit = it },
            titleLabel = if (isGroup) textContext.getString(R.string.core_group_title) else textContext.getString(R.string.core_task_title),
            initialDisplayTitle = if (isGroup) group?.displayName(textContext) else null)
        return
    }
    TasksContent(groups, tasks, active?.id, onBack = onBack,
        onNewGroup = { editor = "group"; editId = 0; message = null },
        onEditGroup = { editor = "group"; editId = it; message = null },
        onDeleteGroup = { deleteGroupId = it }, onActivateGroup = { activateGroupId = it },
        onAddTask = { targetGroup = it; editId = 0; editor = "task"; inherit = true; message = null },
        onEditTask = { editId = it.id; targetGroup = it.groupId; inherit = it.inheritsGroupSchedule; editor = "task"; message = null },
        onCompleteTask = { if (it.isCompleted) viewModel.restore(it.id) else viewModel.complete(it.id, onCompleted = onCelebrate, onFailure = { message = textContext.getString(R.string.polish_complete_failed) }) },
        onDeleteTask = viewModel::delete, onMoveTask = { moveId = it.id }, onActivateTask = { activateTaskId = it.id })
    if (message != null) AlertDialog(onDismissRequest = { message = null },
        text = { Text(message.orEmpty()) }, confirmButton = {
            TextButton(onClick = { message = null }) { Text(textContext.getString(R.string.core_close)) }
        })
    (activateGroupId ?: activateTaskId)?.let { id ->
        AlertDialog(onDismissRequest = { activateGroupId = null; activateTaskId = null }, title = { Text(if (activateTaskId != null) textContext.getString(R.string.core_activate_task) else textContext.getString(R.string.core_activate_group)) },
            text = { Column {
                Text(textContext.getString(R.string.core_activation_help))
                listOf(15, 30, 60).forEach { minutes ->
                    TextButton(onClick = { if (activateTaskId != null) viewModel.setManualActive(id, minutes) else viewModel.activateGroup(id, minutes); activateGroupId = null; activateTaskId = null }) { Text(textContext.getString(R.string.core_focus_minutes, minutes)) }
                }
            } }, confirmButton = {}, dismissButton = { TextButton(onClick = { activateGroupId = null; activateTaskId = null }) { Text(textContext.getString(R.string.core_cancel)) } })
    }
    deleteGroupId?.let { id ->
        AlertDialog(onDismissRequest = { deleteGroupId = null }, title = { Text(textContext.getString(R.string.core_delete_group_question)) },
            text = { Text(textContext.getString(R.string.core_delete_group_help)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteGroup(id); deleteGroupId = null }) { Text(textContext.getString(R.string.core_delete_group_keep)) } },
            dismissButton = { TextButton(onClick = { deleteGroupId = null }) { Text(textContext.getString(R.string.core_cancel)) } })
    }
    moveId?.let { id ->
        var follow by rememberSaveable(id) { mutableStateOf(true) }
        AlertDialog(onDismissRequest = { moveId = null }, title = { Text(textContext.getString(R.string.core_move_task)) }, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Row { Checkbox(follow, { follow = it }); Text(textContext.getString(R.string.core_follow_new_group)) }
                groups.filter { it.id != tasks.firstOrNull { t -> t.id == id }?.groupId }.forEach { group ->
                    TextButton(onClick = { viewModel.move(id, group.id, follow); moveId = null }) { Text(group.displayName(textContext)) }
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { moveId = null }) { Text(textContext.getString(R.string.core_cancel)) } })
    }
}

// Only the original built-in name is translated. Renamed groups retain user input.
internal fun TaskGroup.displayName(context: android.content.Context): String =
    if (id == 1L && name == "未分组") context.getString(R.string.core_ungrouped) else name
