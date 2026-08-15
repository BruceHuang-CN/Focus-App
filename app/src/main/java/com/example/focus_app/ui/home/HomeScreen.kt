package com.example.focus_app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.focus_app.R
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.domain.model.FocusTask
import com.example.focus_app.ui.tasks.scheduleLabel
import com.example.focus_app.ui.theme.InkBlue
import com.example.focus_app.ui.theme.SuccessGreen
import com.example.focus_app.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navigateToMood: () -> Unit,
    navigateToTasks: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event == HomeEvent.ReminderQuotaReset) {
                snackbarHostState.showSnackbar(
                    "提醒额度已重置；下次进入目标应用后会按延迟提醒"
                )
            }
        }
    }

    // 返回本页时刷新统计（例如提醒操作或设置变更后）
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    HomeContent(
        uiState = uiState,
        onRecordMood = navigateToMood,
        onManageTasks = navigateToTasks,
        onCompleteCurrentTask = viewModel::completeCurrentTask,
        onGuardianEnabledChange = viewModel::setGuardianEnabled,
        onResetReminderQuota = viewModel::resetReminderQuota,
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    uiState: HomeUiState,
    onRecordMood: () -> Unit,
    onManageTasks: () -> Unit,
    onCompleteCurrentTask: () -> Unit,
    onGuardianEnabledChange: (Boolean) -> Unit,
    onResetReminderQuota: () -> Unit,
    snackbarHostState: SnackbarHostState? = null
) {
    val effectiveSnackbarHostState = snackbarHostState ?: remember { SnackbarHostState() }
    Scaffold(
        snackbarHost = { SnackbarHost(effectiveSnackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_app_logo),
                            contentDescription = "Focus 应用图标",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Focus 专注助手")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 当前任务优先
            item {
                val task = uiState.activeTask
                if (task != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "当前任务",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                task.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                task.scheduleLabel(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onCompleteCurrentTask) { Text("完成") }
                                TextButton(onClick = onManageTasks) { Text("管理任务") }
                            }
                        }
                    }
                } else {
                    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onManageTasks)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("还没有进行中的任务", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "创建或选择一个任务，Focus 才能提醒你",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // 今日短视频摘要
            item {
                GuardianControlCard(
                    guardianEnabled = uiState.guardianEnabled,
                    activeGroupName = uiState.activeGroupName,
                    reminderWindowMinutes = uiState.reminderWindowMinutes,
                    windowReminderCount = uiState.windowReminderCount,
                    windowReminderLimit = uiState.windowReminderLimit,
                    detectionMode = uiState.detectionMode,
                    accessibilitySystemEnabled = uiState.accessibilitySystemEnabled,
                    accessibilityServiceBound = uiState.accessibilityServiceBound,
                    onGuardianEnabledChange = onGuardianEnabledChange,
                    onResetReminderQuota = onResetReminderQuota
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("打开次数", uiState.openCountToday.toString(), InkBlue, Modifier.weight(1f))
                    StatCard("已提醒", uiState.remindedCountToday.toString(), WarningAmber, Modifier.weight(1f))
                    StatCard("主动退出", uiState.exitedCountToday.toString(), SuccessGreen, Modifier.weight(1f))
                }
            }

            // 轻量连续达标信息
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "连续达标 ${uiState.streakDays} 天",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "今日完成 ${uiState.completedToday} 个任务",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // 主要操作：记录心情
            item {
                Card(modifier = Modifier.fillMaxWidth().clickable { onRecordMood() }) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("当前状态", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.latestMood ?: "点击记录心情 →",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (uiState.latestMood != null) InkBlue else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            item { Text("最近提醒记录", style = MaterialTheme.typography.titleMedium) }
            items(uiState.recentReminders) { event ->
                ReminderRecordCard(event)
            }
        }
    }
}

@Composable
private fun GuardianControlCard(
    guardianEnabled: Boolean,
    activeGroupName: String,
    reminderWindowMinutes: Int,
    windowReminderCount: Int,
    windowReminderLimit: Int,
    detectionMode: DetectionMode,
    accessibilitySystemEnabled: Boolean,
    accessibilityServiceBound: Boolean,
    onGuardianEnabledChange: (Boolean) -> Unit,
    onResetReminderQuota: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "守护控制",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = guardianEnabled, onCheckedChange = onGuardianEnabledChange)
            }
            Text(
                text = if (guardianEnabled) "守护已开启" else "已暂停检测和提醒",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = reminderQuotaLabel(
                    minutes = reminderWindowMinutes,
                    count = windowReminderCount,
                    limit = windowReminderLimit
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            if (detectionMode == DetectionMode.REALTIME) {
                Text(
                    text = accessibilityStatusLabel(
                        systemEnabled = accessibilitySystemEnabled,
                        serviceBound = accessibilityServiceBound
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (accessibilitySystemEnabled && accessibilityServiceBound) {
                        SuccessGreen
                    } else {
                        WarningAmber
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("当前应用组：$activeGroupName", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onResetReminderQuota) { Text("重置提醒额度") }
        }
    }
}

internal fun accessibilityStatusLabel(systemEnabled: Boolean, serviceBound: Boolean): String =
    when {
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

@Composable
private fun ReminderRecordCard(session: AppUsageSession) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(session.appName, style = MaterialTheme.typography.labelLarge)
            Text(
                "${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.startedAt))}  ·  已提醒",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    MaterialTheme {
        HomeContent(
            uiState = HomeUiState(
                latestMood = "有点累",
                openCountToday = 12,
                remindedCountToday = 3,
                exitedCountToday = 2,
                activeTask = FocusTask(title = "写作业", scheduleStartMinute = 9 * 60, scheduleEndMinute = 10 * 60),
                completedToday = 1,
                streakDays = 2,
                reminderWindowMinutes = 30,
                windowReminderCount = 2,
                windowReminderLimit = 5
            ),
            onRecordMood = {},
            onManageTasks = {},
            onCompleteCurrentTask = {},
            onGuardianEnabledChange = {},
            onResetReminderQuota = {}
        )
    }
}
