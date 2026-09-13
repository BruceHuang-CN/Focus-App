package com.example.focus_app.ui.tasks

import com.example.focus_app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focus_app.domain.model.*
import com.example.focus_app.ui.theme.FocusAppTheme

@Composable
fun TasksContent(
    groups: List<TaskGroup>, tasks: List<FocusTask>, activeTaskId: Long?, modifier: Modifier = Modifier,
    onBack: () -> Unit = {}, onNewGroup: () -> Unit = {}, onEditGroup: (Long) -> Unit = {},
    onDeleteGroup: (Long) -> Unit = {}, onActivateGroup: (Long) -> Unit = {}, onAddTask: (Long) -> Unit = {},
    onEditTask: (FocusTask) -> Unit = {}, onCompleteTask: (FocusTask) -> Unit = {},
    onDeleteTask: (FocusTask) -> Unit = {}, onMoveTask: (FocusTask) -> Unit = {},
    onActivateTask: (FocusTask) -> Unit = {}
) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, textContext.getString(R.string.core_back)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(textContext.getString(R.string.core_tasks), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = onNewGroup) { Icon(Icons.Default.Add, null); Text(textContext.getString(R.string.core_new_group)) }
                }
                Text(textContext.getString(R.string.core_tasks_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
        }
        if (groups.isEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(textContext.getString(R.string.core_start_group), style = MaterialTheme.typography.titleLarge)
                    Text(textContext.getString(R.string.core_start_group_help))
                    TextButton(onClick = onNewGroup) { Text(textContext.getString(R.string.core_new_group)) }
                }
            }
        }
        items(groups, key = { it.id }) { group ->
            val children = tasks.filter { it.groupId == group.id }.sortedWith(compareBy<FocusTask> { it.sortOrder }.thenBy { it.id })
            TaskGroupCard(group, children, children.any { it.id == activeTaskId }, activeTaskId,
                onEdit = { onEditGroup(group.id) }, onDelete = { onDeleteGroup(group.id) },
                onActivate = { onActivateGroup(group.id) }, onAdd = { onAddTask(group.id) },
                onEditTask = onEditTask, onComplete = onCompleteTask, onDeleteTask = onDeleteTask, onMove = onMoveTask, onActivateTask = onActivateTask)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskGroupCard(group: TaskGroup, tasks: List<FocusTask>, active: Boolean, activeTaskId: Long?,
    onEdit: () -> Unit, onDelete: () -> Unit, onActivate: () -> Unit, onAdd: () -> Unit,
    onEditTask: (FocusTask) -> Unit, onComplete: (FocusTask) -> Unit,
    onDeleteTask: (FocusTask) -> Unit, onMove: (FocusTask) -> Unit, onActivateTask: (FocusTask) -> Unit) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    var expanded by rememberSaveable(group.id) { mutableStateOf(active || tasks.isEmpty()) }
    var completedExpanded by rememberSaveable(group.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (active) expanded = true }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.List, null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(group.displayName(textContext), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    if (active) Text(textContext.getString(R.string.core_current_group), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (expanded) textContext.getString(R.string.core_collapse_group) else textContext.getString(R.string.core_expand_group))
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, textContext.getString(R.string.core_group_more)) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(textContext.getString(R.string.core_edit_name_schedule)) }, onClick = { menu = false; onEdit() })
                        if (group.id != 1L) DropdownMenuItem(text = { Text(textContext.getString(R.string.core_delete_group)) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Text(FocusTask(title = group.name, scheduleStartMinute = group.scheduleStartMinute,
                scheduleEndMinute = group.scheduleEndMinute, repeatDaysMask = group.repeatDaysMask).scheduleLabel(),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AssistChip(onClick = { expanded = !expanded }, label = { Text(textContext.getString(R.string.core_tasks_count, tasks.count { !it.isCompleted })) })
                FilledTonalButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(textContext.getString(R.string.core_set_schedule)) }
                FilledTonalButton(onClick = onActivate, enabled = tasks.any { !it.isCompleted } && !active,
                    contentPadding = PaddingValues(horizontal = 12.dp)) { Text(if (active) textContext.getString(R.string.core_focusing) else textContext.getString(R.string.core_make_current)) }
            }
            if (expanded) {
                tasks.filterNot { it.isCompleted }.forEach { task -> key(task.id) {
                    TaskRow(task, task.id == activeTaskId, { onComplete(task) }, { onEditTask(task) }, { onDeleteTask(task) }, { onMove(task) }, { onActivateTask(task) })
                } }
                if (tasks.none { !it.isCompleted }) Text(textContext.getString(R.string.core_no_pending), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(onClick = onAdd, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(12.dp)); Text(textContext.getString(R.string.core_add_task)) }
                }
                if (tasks.any { it.isCompleted }) {
                    TextButton(onClick = { completedExpanded = !completedExpanded }) { Text(textContext.getString(R.string.core_completed_count, tasks.count { it.isCompleted }, textContext.getString(if (completedExpanded) R.string.core_collapse else R.string.core_expand))) }
                    if (completedExpanded) tasks.filter { it.isCompleted }.forEach { task -> key(task.id) {
                        TaskRow(task, false, { onComplete(task) }, { onEditTask(task) }, { onDeleteTask(task) }, { onMove(task) }, { onActivateTask(task) })
                    } }
                }
            }
        }
    }
}

@Composable
internal fun TaskRow(task: FocusTask, active: Boolean, onComplete: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onMove: () -> Unit, onActivate: () -> Unit) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconToggleButton(checked = task.isCompleted, onCheckedChange = { onComplete() },
                modifier = Modifier.semantics { contentDescription = if (task.isCompleted) textContext.getString(R.string.core_restore_named, task.title) else textContext.getString(R.string.core_complete_named, task.title) }) {
                if (task.isCompleted) Icon(Icons.Default.CheckCircle, textContext.getString(R.string.core_restore_task), tint = MaterialTheme.colorScheme.primary)
                else Surface(Modifier.size(23.dp), shape = CircleShape, border = androidx.compose.foundation.BorderStroke(1.5.dp,
                    MaterialTheme.colorScheme.outline), color = MaterialTheme.colorScheme.surface) {}
            }
            Column(Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 6.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null)
                Text(task.scheduleLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (active) Text(textContext.getString(R.string.core_in_progress), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(if (task.inheritsGroupSchedule) textContext.getString(R.string.core_group_schedule) else textContext.getString(R.string.core_own_schedule), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, textContext.getString(R.string.core_task_more)) }
                DropdownMenu(menu, { menu = false }) {
                    if (!task.isCompleted && !active) DropdownMenuItem(text = { Text(textContext.getString(R.string.core_activate_task)) }, onClick = { menu = false; onActivate() })
                    DropdownMenuItem(text = { Text(textContext.getString(R.string.core_edit)) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text(textContext.getString(R.string.core_move_group)) }, onClick = { menu = false; onMove() })
                    DropdownMenuItem(text = { Text(textContext.getString(R.string.core_delete_task)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Preview(name = "任务 · 日间", widthDp = 393, heightDp = 850)
@Preview(name = "任务 · 窄屏大字", widthDp = 320, heightDp = 850, fontScale = 1.3f)
@Composable private fun TasksDayPreview() {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    TasksPreview(AppThemeMode.DAY) }
@Preview(name = "任务 · 夜间", widthDp = 393, heightDp = 850)
@Composable private fun TasksNightPreview() {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    TasksPreview(AppThemeMode.NIGHT) }
@Preview(name = "任务 · 空数据", widthDp = 393)
@Composable private fun TasksEmptyPreview() {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    FocusAppTheme { TasksContent(emptyList(), emptyList(), null) } }
@Composable private fun TasksPreview(mode: AppThemeMode) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    FocusAppTheme(mode) {
        TasksContent(listOf(TaskGroup(1, "学习与阅读", 1010, 1050, 31), TaskGroup(2, "生活安排", 1140, 1200, 31)),
            listOf(FocusTask(1, "看 Fundamentals of Power Electronics，整理重要公式与课堂笔记", groupId = 1,
                scheduleStartMinute = 1010, scheduleEndMinute = 1050, repeatDaysMask = 31),
                FocusTask(2, "复习 Buck / Boost 电路", groupId = 1)), 1)
    }
}
