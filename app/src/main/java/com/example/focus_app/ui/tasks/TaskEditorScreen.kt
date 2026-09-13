package com.example.focus_app.ui.tasks

import com.example.focus_app.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorScreen(
    initial: FocusTask?,
    validationMessage: String?,
    onSave: (title: String, startMinute: Int?, endMinute: Int?, repeatDaysMask: Int) -> Unit,
    onDismiss: () -> Unit,
    editorTitle: String = androidx.compose.ui.res.stringResource(if (initial == null) R.string.core_new_task else R.string.core_edit_task),
    inheritsGroupSchedule: Boolean? = null,
    onInheritChange: (Boolean) -> Unit = {},
    titleLabel: String = androidx.compose.ui.res.stringResource(R.string.core_task_title),
    initialDisplayTitle: String? = null
) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    var editedTitle by rememberSaveable(initial?.id) { mutableStateOf<String?>(null) }
    val title = editedTitle ?: initialDisplayTitle ?: initial?.title.orEmpty()
    var startMinute by rememberSaveable(initial?.id) { mutableStateOf(initial?.scheduleStartMinute) }
    var endMinute by rememberSaveable(initial?.id) { mutableStateOf(initial?.scheduleEndMinute) }
    var mask by rememberSaveable(initial?.id) { mutableStateOf(initial?.repeatDaysMask ?: 0) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    if (pickStart) {
        TimePickerDialog(
            title = textContext.getString(R.string.core_start_time),
            initialMinute = startMinute ?: 9 * 60,
            onDismiss = { pickStart = false },
            onConfirm = { startMinute = it; pickStart = false }
        )
    }
    if (pickEnd) {
        TimePickerDialog(
            title = textContext.getString(R.string.core_end_time),
            initialMinute = endMinute ?: 18 * 60,
            onDismiss = { pickEnd = false },
            onConfirm = { endMinute = it; pickEnd = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(editorTitle) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = textContext.getString(R.string.core_back))
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
                onValueChange = { editedTitle = it },
                label = { Text(titleLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (inheritsGroupSchedule != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = inheritsGroupSchedule, onCheckedChange = onInheritChange)
                    Text(textContext.getString(R.string.core_follow_group))
                }
            }
            if (inheritsGroupSchedule != true) {
                Text(textContext.getString(R.string.core_optional_schedule), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickStart = true }) {
                        Text(startMinute?.let { textContext.getString(R.string.core_start_at, formatMinute(it)) } ?: textContext.getString(R.string.core_set_start))
                    }
                    OutlinedButton(onClick = { pickEnd = true }) {
                        Text(endMinute?.let { textContext.getString(R.string.core_end_at, formatMinute(it)) } ?: textContext.getString(R.string.core_set_end))
                    }
                }
                if (startMinute != null && endMinute != null && endMinute!! <= startMinute!!) {
                    Text(
                        textContext.getString(R.string.core_end_after_start),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                TextButton(onClick = { startMinute = null; endMinute = null; mask = 0 }) { Text(textContext.getString(R.string.core_clear_schedule)) }
                Text(textContext.getString(R.string.core_repeat), style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    (0..6).forEach { index ->
                        val label = java.time.DayOfWeek.of(index + 1).getDisplayName(java.time.format.TextStyle.SHORT, textContext.resources.configuration.locales[0])
                        FilterChip(
                            selected = RepeatWeekday.has(mask, index),
                            onClick = { mask = RepeatWeekday.toggle(mask, index) },
                            label = { Text(label) }
                        )
                    }
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
                onClick = { onSave(editedTitle ?: initial?.title.orEmpty(), startMinute, endMinute, mask) },
                enabled = title.isNotBlank() && (inheritsGroupSchedule == true ||
                    (startMinute == null && endMinute == null) ||
                    (startMinute != null && endMinute != null && endMinute!! > startMinute!! && mask != 0)),
                modifier = Modifier.fillMaxWidth()
            ) { Text(textContext.getString(R.string.core_save)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
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
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(textContext.getString(R.string.core_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(textContext.getString(R.string.core_cancel)) }
        }
    )
}

internal fun formatMinute(minute: Int): String = if (minute == 1440) "24:00" else String.format(
    Locale.ROOT,
    "%02d:%02d",
    (minute / 60) % 24,
    minute % 60
)

@Composable
internal fun FocusTask.scheduleLabel(): String {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val start = scheduleStartMinute ?: return textContext.getString(R.string.core_no_schedule)
    val end = scheduleEndMinute ?: return textContext.getString(R.string.core_no_schedule)
    return "${repeatDaysLabel(textContext, repeatDaysMask)} ${formatMinute(start)}-${formatMinute(end)}"
}

@Preview(showBackground = true)
@Composable
private fun TaskEditorPreview() {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    MaterialTheme {
        TaskEditorScreen(
            initial = FocusTask(title = "写作业", scheduleStartMinute = 9 * 60, scheduleEndMinute = 10 * 60),
            validationMessage = null,
            onSave = { _, _, _, _ -> },
            onDismiss = {}
        )
    }
}

internal fun repeatDaysLabel(context: android.content.Context, mask: Int): String {
    if (mask and RepeatWeekday.ALL == RepeatWeekday.ALL) return context.getString(R.string.core_every_day)
    if (mask == 0) return context.getString(R.string.core_not_set)
    val locale = context.resources.configuration.locales[0]
    return (0..6).filter { RepeatWeekday.has(mask, it) }.joinToString(", ") {
        java.time.DayOfWeek.of(it + 1).getDisplayName(java.time.format.TextStyle.SHORT, locale)
    }
}
