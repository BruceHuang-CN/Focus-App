package com.example.focus_app.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.focus_app.domain.model.FocusTask
import com.example.focus_app.domain.task.RepeatWeekday
import java.util.Locale

/**
 * 任务编辑器：标题、时间段（可选）、重复星期、冲突提示。
 * 无状态，由 TaskListScreen 提供初始值与保存回调，便于测试与预览。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    initial: FocusTask?,
    validationMessage: String?,
    onSave: (title: String, startMinute: Int?, endMinute: Int?, repeatDaysMask: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var startMinute by remember(initial?.id) { mutableStateOf(initial?.scheduleStartMinute) }
    var endMinute by remember(initial?.id) { mutableStateOf(initial?.scheduleEndMinute) }
    var mask by remember(initial?.id) { mutableStateOf(initial?.repeatDaysMask ?: 0) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    if (pickStart) {
        TimePickerDialog(
            title = "开始时间",
            initialMinute = startMinute ?: 9 * 60,
            onDismiss = { pickStart = false },
            onConfirm = { startMinute = it; pickStart = false }
        )
    }
    if (pickEnd) {
        TimePickerDialog(
            title = "结束时间",
            initialMinute = endMinute ?: 18 * 60,
            onDismiss = { pickEnd = false },
            onConfirm = { endMinute = it; pickEnd = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "新建任务" else "编辑任务") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("任务标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("时间段（可选）", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickStart = true }) {
                    Text(startMinute?.let { "开始 ${formatMinute(it)}" } ?: "设置开始时间")
                }
                OutlinedButton(onClick = { pickEnd = true }) {
                    Text(endMinute?.let { "结束 ${formatMinute(it)}" } ?: "设置结束时间")
                }
            }
            if (startMinute != null && endMinute != null && endMinute!! <= startMinute!!) {
                Text(
                    "结束时间必须晚于开始时间",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text("重复", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RepeatWeekday.ORDER.forEachIndexed { index, label ->
                    FilterChip(
                        selected = RepeatWeekday.has(mask, index),
                        onClick = { mask = RepeatWeekday.toggle(mask, index) },
                        label = { Text(label) }
                    )
                }
            }

            validationMessage?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Button(
                onClick = { onSave(title.trim(), startMinute, endMinute, mask) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = (initialMinute / 60) % 24,
        initialMinute = initialMinute % 60,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

internal fun formatMinute(minute: Int): String = String.format(
    Locale.ROOT,
    "%02d:%02d",
    (minute / 60) % 24,
    minute % 60
)

internal fun FocusTask.scheduleLabel(): String {
    val start = scheduleStartMinute ?: return "未设置时间段"
    val end = scheduleEndMinute ?: return "未设置时间段"
    return "${RepeatWeekday.label(repeatDaysMask)} ${formatMinute(start)}-${formatMinute(end)}"
}

@Preview(showBackground = true)
@Composable
private fun TaskEditorPreview() {
    MaterialTheme {
        TaskEditorScreen(
            initial = FocusTask(title = "写作业", scheduleStartMinute = 9 * 60, scheduleEndMinute = 10 * 60),
            validationMessage = null,
            onSave = { _, _, _, _ -> },
            onDismiss = {}
        )
    }
}
