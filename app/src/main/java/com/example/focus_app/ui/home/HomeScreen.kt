package com.example.focus_app.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focus_app.R
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.domain.model.FocusTask
import com.example.focus_app.ui.mood.MoodViewModel
import com.example.focus_app.ui.tasks.scheduleLabel
import com.example.focus_app.ui.theme.FocusAppTheme
import com.example.focus_app.util.PermissionHelper
import kotlinx.coroutines.launch

internal data class HomePermissionItem(
    val id: String,
    val title: String,
    val status: String,
    val ready: Boolean
)

@Composable
fun HomeScreen(
    navigateToMood: () -> Unit,
    navigateToTasks: () -> Unit,
    navigateToTutorial: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    moodViewModel: MoodViewModel = hiltViewModel()
) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var permissionRevision by remember { mutableIntStateOf(0) }
    val tutorialStore = remember(context) { com.example.focus_app.ui.onboarding.TutorialStore(context) }
    val showTutorial = remember(permissionRevision) { tutorialStore.hasProgress && !tutorialStore.experienceDone }

    val permissions = remember(context, permissionRevision, uiState.detectionMode, uiState.accessibilityServiceBound) {
        val accessibility = PermissionHelper.isAccessibilityServiceEnabled(context)
        buildList {
            val overlay = PermissionHelper.hasOverlayPermission(context)
            add(HomePermissionItem("overlay", textContext.getString(R.string.core_overlay_permission), if (overlay) textContext.getString(R.string.core_enabled) else textContext.getString(R.string.core_disabled), overlay))
            if (uiState.detectionMode == DetectionMode.REALTIME) {
                val ready = accessibility && uiState.accessibilityServiceBound
                add(HomePermissionItem("accessibility", textContext.getString(R.string.core_accessibility),
                    if (!accessibility) textContext.getString(R.string.core_disabled) else if (ready) textContext.getString(R.string.core_connected) else textContext.getString(R.string.core_disconnected), ready))
            }
            val usage = PermissionHelper.hasUsageStatsPermission(context)
            add(HomePermissionItem("usage", textContext.getString(R.string.core_usage_access), if (usage) textContext.getString(R.string.core_enabled) else textContext.getString(R.string.core_disabled), usage))
            val notification = PermissionHelper.notificationsEnabled(context)
            add(HomePermissionItem("notification", textContext.getString(R.string.core_notification), if (notification) textContext.getString(R.string.core_enabled) else textContext.getString(R.string.core_disabled), notification))
            val battery = PermissionHelper.isIgnoringBatteryOptimizations(context)
            add(HomePermissionItem("battery", textContext.getString(R.string.core_battery), if (battery) textContext.getString(R.string.core_battery_allowed) else textContext.getString(R.string.core_battery_not_allowed), battery))
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbar.showSnackbar(when (event) {
                HomeEvent.ReminderQuotaReset -> textContext.getString(R.string.core_quota_reset_message)
                is HomeEvent.ReminderMessagesRegenerated -> when (val result = event.result) {
                    is com.example.focus_app.domain.usecase.ReminderRegenerationResult.Success -> textContext.getString(R.string.core_ai_updated, result.targetCount)
                    com.example.focus_app.domain.usecase.ReminderRegenerationResult.NoActiveTask -> textContext.getString(R.string.core_create_task_first)
                    com.example.focus_app.domain.usecase.ReminderRegenerationResult.NoTargetApps -> textContext.getString(R.string.core_choose_apps_first)
                    is com.example.focus_app.domain.usecase.ReminderRegenerationResult.Failed -> textContext.getString(R.string.core_ai_failed, result.reason)
                }
            })
        }
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRevision++
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    HomeContent(
        uiState = uiState,
        onRecordMood = navigateToMood,
        onManageTasks = navigateToTasks,
        onContinueTutorial = if (showTutorial) navigateToTutorial else null,
        onCompleteCurrentTask = viewModel::completeCurrentTask,
        onGuardianEnabledChange = viewModel::setGuardianEnabled,
        onResetReminderQuota = viewModel::resetReminderQuota,
        onRegenerateReminderMessages = viewModel::regenerateReminderMessages,
        permissions = permissions,
        onPermissionClick = { id ->
            try {
                when (id) {
                    "overlay" -> PermissionHelper.openOverlaySettings(context)
                    "accessibility" -> context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    "usage" -> PermissionHelper.openUsageStatsSettings(context)
                    "notification" -> PermissionHelper.openNotificationSettings(context)
                    "battery" -> PermissionHelper.openBatteryOptimizationSettings(context)
                }
            } catch (_: ActivityNotFoundException) {
                scope.launch { snackbar.showSnackbar(textContext.getString(R.string.core_settings_unavailable)) }
            } catch (_: SecurityException) {
                scope.launch { snackbar.showSnackbar(textContext.getString(R.string.core_settings_restricted)) }
            }
        },
        onSaveMood = { mood, note, onSaved ->
            moodViewModel.saveMood(mood, note.ifBlank { null }) {
                onSaved()
                viewModel.refresh()
                scope.launch { snackbar.showSnackbar(textContext.getString(R.string.core_mood_saved)) }
            }
        },
        snackbarHostState = snackbar
    )
}

@Composable
internal fun HomeContent(
    uiState: HomeUiState,
    onRecordMood: () -> Unit,
    onManageTasks: () -> Unit,
    onCompleteCurrentTask: () -> Unit,
    onGuardianEnabledChange: (Boolean) -> Unit,
    onResetReminderQuota: () -> Unit,
    onRegenerateReminderMessages: () -> Unit,
    permissions: List<HomePermissionItem>,
    onPermissionClick: (String) -> Unit,
    onSaveMood: (String, String, () -> Unit) -> Unit,
    onContinueTutorial: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null
) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val snackbar = snackbarHostState ?: remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
                .background(Brush.verticalGradient(listOf(
                    colors.primaryContainer.copy(alpha = 0.32f), colors.background
                ))).imePadding(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "brand") {
                Row(
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(painterResource(R.drawable.ic_app_logo), contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(CircleShape))
                    Text(textContext.getString(R.string.core_brand), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                }
            }
            if (onContinueTutorial != null) item(key = "tutorial") {
                OutlinedButton(onClick = onContinueTutorial, modifier = Modifier.fillMaxWidth()) {
                    Text(textContext.getString(R.string.core_continue_tutorial))
                }
            }
            item(key = "task") {
                CurrentTaskCard(uiState.activeTask, onManageTasks, onCompleteCurrentTask)
            }
            item(key = "mood") {
                HomeMoodCard(uiState.latestMood, onRecordMood, onSaveMood)
            }
            item(key = "guardian") {
                HomeGuardianCard(uiState, permissions, onGuardianEnabledChange,
                    onResetReminderQuota, onRegenerateReminderMessages, onPermissionClick)
            }
        }
    }
}

@Composable
private fun HomeCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun CurrentTaskCard(task: FocusTask?, onManage: () -> Unit, onComplete: () -> Unit) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    HomeCard {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onManage).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(textContext.getString(R.string.core_current_task), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, textContext.getString(R.string.core_manage_tasks), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(task?.title ?: textContext.getString(R.string.core_focus_one), style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        if (task != null) {
            Text(task.scheduleLabel(), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                    Text(textContext.getString(R.string.core_in_progress), Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = onComplete) { Text(textContext.getString(R.string.core_complete_task)) }
            }
        } else {
            Text(textContext.getString(R.string.core_choose_task_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onManage) { Text(textContext.getString(R.string.core_choose_task)) }
        }
    }
}

private val homeMoods = listOf("🔵" to "平静", "🌿" to "专注", "😐" to "有点累", "🙁" to "烦躁", "🙂" to "开心")

@Composable
private fun HomeMoodCard(
    latestMood: String?,
    onOpen: () -> Unit,
    onSave: (String, String, () -> Unit) -> Unit
) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    HomeCard {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(textContext.getString(R.string.core_mood_record), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(latestMood?.let { textContext.getString(R.string.core_latest_mood, com.example.focus_app.ui.mood.moodDisplayLabel(textContext, it)) } ?: textContext.getString(R.string.core_record_state),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, textContext.getString(R.string.core_more_moods))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            homeMoods.forEach { (symbol, label) ->
                val active = selected == label
                Surface(
                    onClick = { selected = label }, enabled = !saving,
                    shape = RoundedCornerShape(16.dp),
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                    border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.widthIn(min = 60.dp).semantics { stateDescription = if (active) textContext.getString(R.string.core_selected) else textContext.getString(R.string.core_unselected) }
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(symbol, fontSize = 24.sp)
                        Text(com.example.focus_app.ui.mood.moodDisplayLabel(textContext, label), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        OutlinedTextField(
            value = note, onValueChange = { note = it.take(100) }, enabled = !saving,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            placeholder = { Text(textContext.getString(R.string.core_today_note)) },
            supportingText = { Text("${note.length}/100") }, maxLines = 3
        )
        if (selected != null) {
            Button(
                enabled = !saving,
                onClick = {
                    selected?.let { mood ->
                        saving = true
                        onSave(mood, note) { selected = null; note = ""; saving = false }
                    }
                }, modifier = Modifier.align(Alignment.End)
            ) { Text(if (saving) textContext.getString(R.string.core_saving) else textContext.getString(R.string.core_record_mood)) }
        }
    }
}

@Composable
internal fun HomeGuardianCard(
    uiState: HomeUiState,
    permissions: List<HomePermissionItem>,
    onToggle: (Boolean) -> Unit,
    onResetQuota: () -> Unit,
    onRegenerate: () -> Unit,
    onPermissionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var resetWindow by rememberSaveable { mutableStateOf(false) }
    HomeCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(textContext.getString(R.string.core_guardian), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (uiState.guardianEnabled) textContext.getString(R.string.core_guardian_on) else textContext.getString(R.string.core_guardian_paused),
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = uiState.guardianEnabled, onCheckedChange = onToggle,
                modifier = Modifier.semantics { stateDescription = if (uiState.guardianEnabled) textContext.getString(R.string.core_guardian_on) else textContext.getString(R.string.core_guardian_off) })
        }
        Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(textContext.getString(R.string.core_quota, uiState.reminderWindowMinutes,
                    uiState.windowReminderCount.coerceAtLeast(0), uiState.windowReminderLimit.coerceAtLeast(0),
                    (uiState.windowReminderLimit.coerceAtLeast(0) - uiState.windowReminderCount.coerceAtLeast(0)).coerceAtLeast(0)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(textContext.getString(R.string.core_app_group, uiState.activeGroupName.ifBlank { textContext.getString(R.string.core_no_app_group) }), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = { resetWindow = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (uiState.isRegeneratingMessages) textContext.getString(R.string.core_generating_ai) else textContext.getString(R.string.core_reset_update))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .semantics { stateDescription = if (expanded) textContext.getString(R.string.core_expanded) else textContext.getString(R.string.core_collapsed) }
                .clickable(role = Role.Button) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(textContext.getString(R.string.core_permissions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val missing = permissions.count { !it.ready }
                Text(if (missing == 0) textContext.getString(R.string.core_permissions_ready) else textContext.getString(R.string.core_permission_count, missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (missing == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            }
            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                if (expanded) textContext.getString(R.string.core_collapse_permissions) else textContext.getString(R.string.core_expand_permissions))
        }
        if (expanded) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)) {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    permissions.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onPermissionClick(item.id) }
                            .padding(vertical = 12.dp).heightIn(min = 32.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(item.status, Modifier.widthIn(max = 140.dp), style = MaterialTheme.typography.labelMedium,
                                color = if (item.ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (resetWindow) {
        AlertDialog(
            onDismissRequest = { resetWindow = false }, title = { Text(textContext.getString(R.string.core_reset_update)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(textContext.getString(R.string.core_reset_help))
                    OutlinedButton(onClick = { resetWindow = false; onResetQuota() }, modifier = Modifier.fillMaxWidth()) {
                        Text(textContext.getString(R.string.core_reset_quota))
                    }
                    OutlinedButton(onClick = { resetWindow = false; onRegenerate() },
                        enabled = !uiState.isRegeneratingMessages, modifier = Modifier.fillMaxWidth()) {
                        Text(if (uiState.isRegeneratingMessages) textContext.getString(R.string.core_generating) else textContext.getString(R.string.core_reset_ai))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { resetWindow = false }) { Text(textContext.getString(R.string.core_close)) } }
        )
    }
}

// Legacy pure formatting helpers retained for existing tests; rendered UI uses resources above.
internal fun accessibilityStatusLabel(systemEnabled: Boolean, serviceBound: Boolean): String = when {
    !systemEnabled -> "无障碍：系统未开启"
    serviceBound -> "无障碍：系统已开启 · 服务运行中"
    else -> "无障碍：系统已开启 · 服务未连接"
}

internal fun reminderQuotaLabel(minutes: Int, count: Int, limit: Int): String {
    val safeCount = count.coerceAtLeast(0)
    val safeLimit = limit.coerceAtLeast(0)
    val remaining = (safeLimit - safeCount).coerceAtLeast(0)
    return "本时间段（$minutes 分钟）已提醒 $safeCount/$safeLimit 次，剩余 $remaining 次"
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Preview(showBackground = true, widthDp = 320, heightDp = 740, fontScale = 1.3f)
@Preview(showBackground = true, widthDp = 393, heightDp = 852, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeContentPreview() {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    FocusAppTheme {
        HomeContent(
            uiState = HomeUiState(activeTask = FocusTask(title = "写下这次北京之行的感想",
                scheduleStartMinute = 1010, scheduleEndMinute = 1050), reminderWindowMinutes = 30,
                windowReminderLimit = 5, activeGroupName = "短视频应用"),
            onRecordMood = {}, onManageTasks = {}, onCompleteCurrentTask = {},
            onGuardianEnabledChange = {}, onResetReminderQuota = {}, onRegenerateReminderMessages = {},
            permissions = listOf(HomePermissionItem("overlay", textContext.getString(R.string.core_overlay_permission), textContext.getString(R.string.core_enabled), true),
                HomePermissionItem("accessibility", textContext.getString(R.string.core_accessibility), textContext.getString(R.string.core_disabled), false)),
            onPermissionClick = {}, onSaveMood = { _, _, done -> done() }
        )
    }
}
