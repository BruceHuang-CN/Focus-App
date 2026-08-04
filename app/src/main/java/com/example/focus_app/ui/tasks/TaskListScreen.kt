package com.example.focus_app.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.focus_app.data.repository.ScheduleValidation
import com.example.focus_app.domain.model.FocusTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onBack: () -> Unit,
    viewModel: TaskViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    val activeTask by viewModel.activeTask.collectAsState()
    var editing by remember { mutableStateOf<FocusTask?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    if (showEditor) {
        TaskEditorScreen(
            initial = editing,
            validationMessage = validationMessage,
            onSave = { title, start, end, mask ->
                val task = editing?.copy(
                    title = title,
                    scheduleStartMinute = start,
                    scheduleEndMinute = end,
                    repeatDaysMask = mask,
                    isCompleted = false
                ) ?: FocusTask(title = title, scheduleStartMinute = start, scheduleEndMinute = end, repeatDaysMask = mask)
                val onResult: (ScheduleValidation) -> Unit = { result ->
                    when (result) {
                        ScheduleValidation.VALID -> {
                            showEditor = false
                            editing = null
                            validationMessage = null
                        }
                        ScheduleValidation.END_NOT_AFTER_START ->
                            validationMessage = "结束时间必须晚于开始时间"
                        ScheduleValidation.OVERLAP ->
                            validationMessage = "与已有任务的时间段冲突，请调整"
                    }
                }
                if (editing == null) {
                    viewModel.create(task, onResult)
                } else {
                    viewModel.update(task, onResult)
                }
            },
            onDismiss = {
                showEditor = false
                editing = null
                validationMessage = null
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("任务") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editing = null
                    validationMessage = null
                    showEditor = true
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新建任务")
            }
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("还没有任务", style = MaterialTheme.typography.titleMedium)
                Text(
                    "点击右下角 + 创建第一个专注任务",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        isActive = task.id == activeTask?.id,
                        onComplete = { viewModel.complete(task.id) },
                        onRestore = { viewModel.restore(task.id) },
                        onSetActive = { viewModel.setManualActive(task.id) },
                        onEdit = {
                            editing = task
                            validationMessage = null
                            showEditor = true
                        },
                        onDelete = { viewModel.delete(task) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: FocusTask,
    isActive: Boolean,
    onComplete: () -> Unit,
    onRestore: () -> Unit,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )
                if (isActive) {
                    AssistChip(onClick = {}, label = { Text("当前") })
                }
            }
            Text(
                task.scheduleLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { if (task.isCompleted) onRestore() else onComplete() }
                ) {
                    Text(if (task.isCompleted) "恢复" else "完成")
                }
                TextButton(
                    onClick = onSetActive,
                    enabled = !task.isCompleted && !isActive
                ) { Text("设为当前") }
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}
