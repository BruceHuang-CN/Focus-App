package com.example.focus_app.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
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
    onCelebrate: () -> Unit,
    navigateToTutorial: () -> Unit = {},
    navigateToAppGroups: () -> Unit = {},
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
        onManageApps = navigateToAppGroups,
        onContinueTutorial = if (showTutorial) navigateToTutorial else null,
        onCompleteCurrentTask = {
            viewModel.completeCurrentTask(onCompleted = onCelebrate, onFailure = {
                scope.launch { snackbar.showSnackbar(textContext.getString(R.string.polish_complete_failed)) }
            })
        },
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
    onManageApps: () -> Unit = {},
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
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(colors.primaryContainer.copy(alpha = 0.18f), colors.background)
                    )
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = textContext.getString(R.string.core_brand),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground
                )
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().imePadding()) {
                // Only the minimum height follows the viewport. Expanded content can grow
                // beyond it, so every permission and action remains scrollable.
                Box(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        HomeTaskGuardianCard(
                            uiState = uiState,
                            permissions = permissions,
                            onManageTasks = onManageTasks,
                            onManageApps = onManageApps,
                            onCompleteCurrentTask = onCompleteCurrentTask,
                            onGuardianEnabledChange = onGuardianEnabledChange,
                            onResetReminderQuota = onResetReminderQuota,
                            onRegenerateReminderMessages = onRegenerateReminderMessages,
                            onPermissionClick = onPermissionClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTaskGuardianCard(
    uiState: HomeUiState,
    permissions: List<HomePermissionItem>,
    onManageTasks: () -> Unit,
    onManageApps: () -> Unit,
    onCompleteCurrentTask: () -> Unit,
    onGuardianEnabledChange: (Boolean) -> Unit,
    onResetReminderQuota: () -> Unit,
    onRegenerateReminderMessages: () -> Unit,
    onPermissionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val colors = MaterialTheme.colorScheme
    var guardianExpanded by rememberSaveable { mutableStateOf(false) }
    var permissionDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    var resetWindow by rememberSaveable { mutableStateOf(false) }
    val missingPermissions = permissions.count { !it.ready }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = textContext.getString(R.string.core_current_task),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
                textAlign = TextAlign.Center
            )
            val task = uiState.activeTask
            if (task == null) {
                Text(
                    text = textContext.getString(R.string.core_focus_one),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 38.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = textContext.getString(R.string.core_choose_task_help),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onManageTasks,
                    modifier = Modifier.widthIn(min = 180.dp).heightIn(min = 52.dp)
                ) {
                    Text(textContext.getString(R.string.core_choose_task))
                }
            } else {
                Text(
                    text = task.title,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 38.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = task.scheduleLabel(),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Surface(color = colors.primaryContainer, shape = CircleShape) {
                    Text(
                        text = textContext.getString(R.string.core_in_progress),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        color = colors.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Button(
                    onClick = onCompleteCurrentTask,
                    modifier = Modifier.widthIn(min = 180.dp).heightIn(min = 52.dp)
                ) {
                    Text(textContext.getString(R.string.core_complete_task))
                }
                TextButton(
                    onClick = onManageTasks,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = textContext.getString(R.string.core_manage_tasks),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.65f))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { guardianExpanded = !guardianExpanded }
                    .semantics {
                        stateDescription = if (guardianExpanded) {
                            textContext.getString(R.string.core_expanded)
                        } else {
                            textContext.getString(R.string.core_collapsed)
                        }
                    }
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Settings, contentDescription = null, tint = colors.primary)
                        Text(
                            text = textContext.getString(R.string.polish_guardian_settings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface
                        )
                    }
                    Text(
                        text = if (uiState.guardianEnabled) {
                            textContext.getString(R.string.core_guardian_on)
                        } else {
                            textContext.getString(R.string.core_guardian_paused)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.guardianEnabled) colors.primary else colors.onSurfaceVariant
                    )
                    if (missingPermissions > 0) {
                        Text(
                            text = textContext.getString(R.string.polish_pending_checks, permissions.filterNot { it.ready }.joinToString("、") { it.title }),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error
                        )
                    }
                }
                Icon(
                    imageVector = if (guardianExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant
                )
            }

            if (uiState.targetAppCount == 0) {
                Text(
                    textContext.getString(R.string.polish_no_guarded_apps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onManageApps) {
                    Text(textContext.getString(R.string.polish_choose_guarded_apps))
                }
            }

            AnimatedVisibility(
                visible = guardianExpanded,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                GuardianControlsContent(
                    uiState = uiState,
                    permissions = permissions,
                    missingPermissions = missingPermissions,
                    permissionDetailsExpanded = permissionDetailsExpanded,
                    onPermissionDetailsExpandedChange = { permissionDetailsExpanded = it },
                    onGuardianEnabledChange = onGuardianEnabledChange,
                    onResetAndUpdate = { resetWindow = true },
                    onManageApps = onManageApps,
                    onPermissionClick = onPermissionClick
                )
            }
        }
    }

    if (resetWindow) {
        AlertDialog(
            onDismissRequest = { resetWindow = false },
            title = { Text(textContext.getString(R.string.core_reset_update)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(textContext.getString(R.string.core_reset_help))
                    OutlinedButton(
                        onClick = { resetWindow = false; onResetReminderQuota() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(textContext.getString(R.string.core_reset_quota))
                    }
                    OutlinedButton(
                        onClick = { resetWindow = false; onRegenerateReminderMessages() },
                        enabled = !uiState.isRegeneratingMessages,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(
                            if (uiState.isRegeneratingMessages) {
                                textContext.getString(R.string.core_generating)
                            } else {
                                textContext.getString(R.string.core_reset_ai)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { resetWindow = false }) {
                    Text(textContext.getString(R.string.core_close))
                }
            }
        )
    }
}

@Composable
private fun GuardianControlsContent(
    uiState: HomeUiState,
    permissions: List<HomePermissionItem>,
    missingPermissions: Int,
    permissionDetailsExpanded: Boolean,
    onPermissionDetailsExpandedChange: (Boolean) -> Unit,
    onGuardianEnabledChange: (Boolean) -> Unit,
    onResetAndUpdate: () -> Unit,
    onManageApps: () -> Unit,
    onPermissionClick: (String) -> Unit
) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = textContext.getString(R.string.core_guardian),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Text(
                    text = if (uiState.guardianEnabled) {
                        textContext.getString(R.string.core_guardian_on)
                    } else {
                        textContext.getString(R.string.core_guardian_paused)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Switch(
                checked = uiState.guardianEnabled,
                onCheckedChange = onGuardianEnabledChange,
                modifier = Modifier.semantics {
                    stateDescription = if (uiState.guardianEnabled) {
                        textContext.getString(R.string.core_guardian_on)
                    } else {
                        textContext.getString(R.string.core_guardian_off)
                    }
                }
            )
        }

        if (permissions.any { it.id == "accessibility" &&
                it.status == textContext.getString(R.string.core_disconnected) }) {
            Text(
                textContext.getString(R.string.polish_accessibility_reconnect),
                color = colors.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) {
                    onPermissionDetailsExpandedChange(!permissionDetailsExpanded)
                }
                .semantics {
                    stateDescription = if (permissionDetailsExpanded) {
                        textContext.getString(R.string.core_expanded)
                    } else {
                        textContext.getString(R.string.core_collapsed)
                    }
                }
                .heightIn(min = 56.dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = textContext.getString(R.string.core_permissions),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface
                )
                Text(
                    text = if (missingPermissions == 0) {
                        textContext.getString(R.string.core_permissions_ready)
                    } else {
                        textContext.getString(R.string.polish_pending_checks, permissions.filterNot { it.ready }.joinToString("、") { it.title })
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (missingPermissions == 0) colors.onSurfaceVariant else colors.error
                )
            }
            Icon(
                imageVector = if (permissionDetailsExpanded) {
                    Icons.Filled.KeyboardArrowUp
                } else {
                    Icons.Filled.KeyboardArrowDown
                },
                contentDescription = null,
                tint = colors.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = permissionDetailsExpanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                permissions.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.55f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button) { onPermissionClick(item.id) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = item.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface
                        )
                        Text(
                            text = item.status,
                            modifier = Modifier.widthIn(max = 120.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (item.ready) colors.primary else colors.error
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.55f))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = textContext.getString(
                    R.string.core_quota,
                    uiState.reminderWindowMinutes,
                    uiState.windowReminderCount.coerceAtLeast(0),
                    uiState.windowReminderLimit.coerceAtLeast(0),
                    (uiState.windowReminderLimit.coerceAtLeast(0) - uiState.windowReminderCount.coerceAtLeast(0))
                        .coerceAtLeast(0)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            Text(
                text = textContext.getString(
                    R.string.core_app_group,
                    uiState.activeGroupName.ifBlank { textContext.getString(R.string.core_no_app_group) }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        TextButton(onClick = onManageApps, modifier = Modifier.fillMaxWidth()) {
            Text(textContext.getString(R.string.polish_manage_guarded_apps, uiState.targetAppCount))
        }
        OutlinedButton(
            onClick = onResetAndUpdate,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(
                if (uiState.isRegeneratingMessages) {
                    textContext.getString(R.string.core_generating_ai)
                } else {
                    textContext.getString(R.string.core_reset_update)
                }
            )
        }
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

@Preview(name = "当前任务", showBackground = true, widthDp = 393, heightDp = 852)
@Preview(name = "窄屏和放大文字", showBackground = true, widthDp = 320, heightDp = 740, fontScale = 1.3f)
@Preview(name = "深色主题", showBackground = true, widthDp = 393, heightDp = 852, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeContentPreview() {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    FocusAppTheme {
        HomeContent(
            uiState = HomeUiState(
                activeTask = FocusTask(
                    title = "写下这次北京之行的感想，以及接下来想完成的几件事",
                    scheduleStartMinute = 1010,
                    scheduleEndMinute = 1050
                ),
                reminderWindowMinutes = 30,
                windowReminderLimit = 5,
                activeGroupName = "短视频应用"
            ),
            onRecordMood = {},
            onManageTasks = {},
            onCompleteCurrentTask = {},
            onGuardianEnabledChange = {},
            onResetReminderQuota = {},
            onRegenerateReminderMessages = {},
            permissions = listOf(
                HomePermissionItem(
                    "overlay",
                    textContext.getString(R.string.core_overlay_permission),
                    textContext.getString(R.string.core_enabled),
                    true
                ),
                HomePermissionItem(
                    "accessibility",
                    textContext.getString(R.string.core_accessibility),
                    textContext.getString(R.string.core_disabled),
                    false
                )
            ),
            onPermissionClick = {},
            onSaveMood = { _, _, done -> done() }
        )
    }
}

@Preview(name = "无当前任务", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HomeContentEmptyPreview() {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    FocusAppTheme {
        HomeContent(
            uiState = HomeUiState(guardianEnabled = false),
            onRecordMood = {},
            onManageTasks = {},
            onCompleteCurrentTask = {},
            onGuardianEnabledChange = {},
            onResetReminderQuota = {},
            onRegenerateReminderMessages = {},
            permissions = listOf(
                HomePermissionItem(
                    "overlay",
                    textContext.getString(R.string.core_overlay_permission),
                    textContext.getString(R.string.core_disabled),
                    false
                )
            ),
            onPermissionClick = {},
            onSaveMood = { _, _, done -> done() }
        )
    }
}
