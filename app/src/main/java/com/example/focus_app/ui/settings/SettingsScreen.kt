package com.example.focus_app.ui.settings

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.focus_app.domain.model.AppThemeColor
import com.example.focus_app.domain.model.AppThemeMode
import com.example.focus_app.domain.model.AiProvider
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.domain.model.ReminderTone
import com.example.focus_app.domain.model.ReturnDestination
import com.example.focus_app.domain.permission.PermissionCheckAction
import com.example.focus_app.ui.components.PresetSelector
import com.example.focus_app.util.PermissionHelper
import com.example.focus_app.util.loadInstalledApps
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    navigateToCustomReturnPicker: () -> Unit,
    navigateToAppGroups: () -> Unit,
    navigateToFeedbackAndSupport: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val s by viewModel.settings.collectAsState()
    val connectionState by viewModel.aiConnection.collectAsState()
    val tonePreview by viewModel.tonePreview.collectAsState()
    val permissionItems by viewModel.permissionStatus.collectAsState()
    val customReturnPackage by viewModel.customReturnPackage.collectAsState()
    val followUpInterval by viewModel.followUpInterval.collectAsState()
    val keepAliveEnabled by viewModel.keepAliveEnabled.collectAsState()
    val themeSettings by viewModel.themeSettings.collectAsState()
    val appGroups by viewModel.appGroups.collectAsState()
    val activeAppGroupId by viewModel.activeAppGroupId.collectAsState()
    val activeAppGroup = appGroups.firstOrNull { it.id == activeAppGroupId }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayGranted by remember { mutableStateOf(PermissionHelper.hasOverlayPermission(context)) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var settingsEdited by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    var endpointDraft by remember(s.aiProvider, s.apiEndpoint, s.aiModel) {
        mutableStateOf(s.apiEndpoint)
    }
    var modelDraft by remember(s.aiProvider, s.apiEndpoint, s.aiModel) {
        mutableStateOf(s.aiModel)
    }
    var customToneDraft by remember(s.customToneInstruction) {
        mutableStateOf(s.customToneInstruction)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 授权结果由系统设置页兜底处理 */ }
    LaunchedEffect(Unit) {
        viewModel.notificationPermissionRequests.collect {
            if (PermissionHelper.needsNotificationPermission(context)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // 从系统设置返回时刷新悬浮窗状态；实时模式下若系统无障碍已开启，自动恢复应用内开关
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = PermissionHelper.hasOverlayPermission(context)
                viewModel.refreshPermissions()
                if (viewModel.settings.value.detectionMode == DetectionMode.REALTIME &&
                    PermissionHelper.isAccessibilityServiceEnabled(context)
                ) {
                    viewModel.ensureAccessibilityEnabled()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasUnsavedChanges = settingsEdited

    fun onSettingChanged(label: String) {
        settingsEdited = true
    }

    fun requestExit() {
        if (hasUnsavedChanges) showExitConfirmation = true else onBack()
    }

    BackHandler(enabled = !showExitConfirmation) { requestExit() }

    fun handlePermissionAction(action: PermissionCheckAction) {
        when (action) {
            PermissionCheckAction.OPEN_ACCESSIBILITY ->
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            PermissionCheckAction.OPEN_USAGE_STATS ->
                PermissionHelper.openUsageStatsSettings(context)
            PermissionCheckAction.OPEN_OVERLAY ->
                PermissionHelper.openOverlaySettings(context)
            PermissionCheckAction.REQUEST_NOTIFICATION ->
                if (PermissionHelper.needsNotificationPermission(context)) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            PermissionCheckAction.OPEN_TARGET_APPS -> navigateToAppGroups()
            PermissionCheckAction.ENABLE_ACCESSIBILITY -> {
                if (!s.enableAccessibility) {
                    viewModel.toggleAccessibility()
                    onSettingChanged("开启无障碍检测")
                }
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            PermissionCheckAction.NONE -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = ::requestExit) {
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
            // ── 保存状态（半手动保存）──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (hasUnsavedChanges) "有未保存的修改" else "所有设置已保存",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasUnsavedChanges) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        settingsEdited = false
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                "设置已保存",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                ) { Text("保存设置") }
            }

            // ── 检测状态（权限检查窗口）──
            PermissionCheckCard(
                mode = s.detectionMode,
                items = permissionItems,
                onAction = ::handlePermissionAction
            )

            // ── 外观主题 ──
            SectionTitle("外观主题")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("界面模式", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeSettings.mode == mode,
                                onClick = {
                                    viewModel.setThemeMode(mode)
                                    onSettingChanged("界面模式")
                                },
                                label = { Text(themeModeLabel(mode)) }
                            )
                        }
                    }
                    Text("主题配色", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppThemeColor.entries.forEach { color ->
                            ThemeColorSwatch(
                                color = color,
                                selected = themeSettings.color == color,
                                onClick = {
                                    viewModel.setThemeColor(color)
                                    onSettingChanged("主题配色")
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── 目标应用 ──
            SectionTitle("目标应用")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navigateToAppGroups() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (s.targetApps.isEmpty()) "未选择任何 App（检测不会触发）"
                    else "已选择 ${s.targetApps.size} 个 App",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (s.targetApps.isEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
            }
            Divider()

            // ── 提醒时间 ──
            SectionTitle("应用组管理")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navigateToAppGroups() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    activeAppGroup?.let { "${it.name} · ${it.apps.size} 个 App" } ?: "管理应用组",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
            }
            Divider()

            SectionTitle("提醒时间")
            Text("提醒延迟", style = MaterialTheme.typography.titleMedium)
            PresetSelector(
                presets = listOf(3, 10, 30),
                customRange = 1..300,
                value = s.reminderDelaySeconds,
                formatPreset = { "${it} 秒" },
                onValueChange = {
                    viewModel.updateReminderDelaySeconds(it)
                    onSettingChanged("提醒延迟 ${it} 秒")
                }
            )
            Text("统计窗口", style = MaterialTheme.typography.titleMedium)
            PresetSelector(
                presets = listOf(30, 60, 120),
                customRange = 5..1_440,
                value = s.reminderWindowMinutes,
                formatPreset = { "${it} 分钟" },
                onValueChange = {
                    viewModel.updateReminderWindowMinutes(it)
                    onSettingChanged("统计窗口 ${it} 分钟")
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("提醒前呼吸停顿", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "弹窗先引导深呼吸，再显示操作按钮",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = s.enableBreathingPause,
                    onCheckedChange = { enabled ->
                        viewModel.toggleBreathingPause()
                        onSettingChanged(if (enabled) "开启呼吸停顿" else "关闭呼吸停顿")
                    }
                )
            }
            /* Legacy follow-up interval label retained temporarily. The overlay now owns this choice.
            Text("再次提醒间隔（点“仍要使用”后）", style = MaterialTheme.typography.titleMedium)
            */
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("\u5f3a\u5236\u63d0\u9192", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "\u5f00\u542f\u540e\uff0c\u8fd4\u56de\u952e\u3001Home \u952e\u6216\u4e34\u65f6\u7cfb\u7edf\u7a97\u53e3\u4e0d\u4f1a\u89c6\u4e3a\u5df2\u5904\u7406\uff1b\u8bf7\u5728\u5f39\u7a97\u4e2d\u660e\u786e\u9009\u62e9\u3002",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = s.forceReminder,
                    onCheckedChange = { enabled ->
                        viewModel.setForceReminder(enabled)
                        onSettingChanged(
                            if (enabled) "\u5f00\u542f\u5f3a\u5236\u63d0\u9192" else "\u5173\u95ed\u5f3a\u5236\u63d0\u9192"
                        )
                    }
                )
            }
            /* Legacy follow-up interval selector retained temporarily.
            PresetSelector(
                presets = listOf(1, 5, 10, 15, 30),
                customRange = 1..120,
                value = followUpInterval,
                formatPreset = { "${it} 分钟" },
                onValueChange = {
                    viewModel.updateFollowUpInterval(it)
                    onSettingChanged("再次提醒间隔 ${it} 分钟")
                }
            )
            Divider()
            */

            // ── 提醒次数 ──
            SectionTitle("提醒次数")
            Text("窗口内提醒次数", style = MaterialTheme.typography.titleMedium)
            PresetSelector(
                presets = listOf(1, 3, 5),
                customRange = 1..20,
                value = s.maxRemindersPerWindow,
                formatPreset = { "${it} 次" },
                onValueChange = {
                    viewModel.updateMaxRemindersPerWindow(it)
                    onSettingChanged("窗口内 ${it} 次")
                }
            )
            Divider()

            // ── AI 服务 ──
            SectionTitle("AI 服务")
            AiProvider.entries.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = s.aiProvider == p,
                        onClick = {
                            viewModel.updateAiProvider(p)
                            onSettingChanged("AI: ${p.displayName}")
                        }
                    )
                    Text(p.displayName)
                }
            }
            OutlinedTextField(
                value = endpointDraft,
                onValueChange = { endpointDraft = it },
                label = { Text("API 端点") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = modelDraft,
                onValueChange = { modelDraft = it },
                label = { Text("模型名") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    viewModel.updateAiConnection(endpointDraft, modelDraft)
                    onSettingChanged("AI 连接设置")
                },
                enabled = endpointDraft.isNotBlank() && modelDraft.isNotBlank()
            ) { Text("保存连接设置") }
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("API Key（保存后不会再次显示）") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.updateApiKey(apiKeyInput)
                        apiKeyInput = ""
                        onSettingChanged("API Key")
                    },
                    enabled = apiKeyInput.isNotBlank()
                ) { Text("保存 Key") }
                TextButton(
                    onClick = {
                        viewModel.clearApiKey()
                        apiKeyInput = ""
                        onSettingChanged("已清除 API Key")
                    }
                ) { Text("清除") }
            }
            OutlinedButton(
                onClick = { viewModel.testAiConnection() },
                enabled = connectionState !is AiConnectionUiState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (connectionState is AiConnectionUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在测试连接…")
                } else {
                    Text("测试 DeepSeek 连接")
                }
            }
            when (val state = connectionState) {
                AiConnectionUiState.Idle, AiConnectionUiState.Loading -> Unit
                is AiConnectionUiState.Success -> Text(
                    "连接成功：${state.modelIds.take(3).joinToString("、")}" +
                        if (state.modelIds.size > 3) " 等 ${state.modelIds.size} 个模型" else "",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                is AiConnectionUiState.Error -> Text(
                    state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Divider()

            // ── 口吻 ──
            SectionTitle("口吻")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReminderTone.entries.forEach { tone ->
                    FilterChip(
                        selected = s.toneKey == tone,
                        onClick = {
                            viewModel.updateReminderTone(tone)
                            onSettingChanged("口吻: ${toneLabel(tone)}")
                        },
                        label = { Text(toneLabel(tone)) }
                    )
                }
            }
            if (s.toneKey == ReminderTone.CUSTOM) {
                OutlinedTextField(
                    value = customToneDraft,
                    onValueChange = { customToneDraft = it },
                    label = { Text("自定义口吻要求") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        viewModel.updateCustomToneInstruction(customToneDraft)
                        onSettingChanged("自定义口吻")
                    },
                    enabled = customToneDraft.isNotBlank()
                ) { Text("保存口吻要求") }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("口吻预览", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        tonePreview.ifBlank { "设置口吻后这里会显示示例提醒" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Divider()

            /* Legacy return destination radio UI retained temporarily. The overlay now owns this choice.
            // ── 返回行为 ──
            SectionTitle("返回行为")
            ReturnDestination.entries.forEach { destination ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = s.returnDestination == destination,
                        onClick = {
                            viewModel.updateReturnDestination(destination)
                            onSettingChanged("返回行为: ${returnLabel(destination)}")
                        }
                    )
                    Text(returnLabel(destination))
                }
            }
            */
            run {
                val selectedAppName = remember(customReturnPackage) {
                    loadInstalledApps(context)
                        .firstOrNull { it.packageName == customReturnPackage }
                        ?.appName
                        ?: customReturnPackage.ifBlank { null }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navigateToCustomReturnPicker() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("返回指定应用", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            selectedAppName ?: "点击选择应用",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedAppName == null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )
                    }
                    Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
            Divider()

            // ── 检测方式 ──
            SectionTitle("检测方式")
            DetectionMode.entries.forEach { mode ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = s.detectionMode == mode,
                        onClick = {
                            viewModel.updateDetectionMode(mode)
                            onSettingChanged("检测方式: ${detectionLabel(mode)}")
                            if (mode == DetectionMode.REALTIME &&
                                !PermissionHelper.isAccessibilityServiceEnabled(context)
                            ) {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        }
                    )
                    Column {
                        Text(detectionLabel(mode))
                        Text(
                            detectionHint(mode),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用无障碍检测", modifier = Modifier.weight(1f))
                Switch(
                    checked = s.enableAccessibility,
                    onCheckedChange = { enabled ->
                        viewModel.toggleAccessibility()
                        onSettingChanged(if (enabled) "开启无障碍检测" else "关闭无障碍检测")
                        if (enabled && !PermissionHelper.isAccessibilityServiceEnabled(context)) {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("显示在其他应用上层（弹窗提醒）", modifier = Modifier.weight(1f))
                if (overlayGranted) {
                    Text("已授予", color = MaterialTheme.colorScheme.primary)
                } else {
                    TextButton(onClick = { PermissionHelper.openOverlaySettings(context) }) {
                        Text("去开启")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("常驻守护（防止后台被回收）", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "实时模式会显示一条低优先级常驻通知",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = keepAliveEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setKeepAliveEnabled(enabled)
                        onSettingChanged(if (enabled) "开启常驻守护" else "关闭常驻守护")
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            Divider()
            SectionTitle("\u53cd\u9988\u4e0e\u652f\u6301")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navigateToFeedbackAndSupport() }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "\u95ee\u5377\u661f\u3001\u516c\u4f17\u53f7\u4e0e\u8d5e\u52a9\u5165\u53e3",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "\u4e8c\u7ef4\u7801\u6216\u94fe\u63a5\u7531\u4f60\u540e\u7eed\u63d0\u4f9b",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text("\u2192", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
            }

        }
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("保存设置？") },
            text = { Text("你有未保存的修改。请先保存，或继续修改。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsEdited = false
                        showExitConfirmation = false
                        onBack()
                    }
                ) { Text("保存并退出") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text("继续修改")
                }
            }
        )
    }
}

private fun themeModeLabel(mode: AppThemeMode): String = when (mode) {
    AppThemeMode.SYSTEM -> "跟随系统"
    AppThemeMode.DAY -> "日间"
    AppThemeMode.NIGHT -> "夜间"
}

private fun themeColorLabel(color: AppThemeColor): String = when (color) {
    AppThemeColor.MINT -> "薄荷青"
    AppThemeColor.BLUE -> "宁静蓝"
    AppThemeColor.ORANGE -> "暖阳橙"
    AppThemeColor.GRAPHITE -> "石墨"
}

private val themeGradient: Map<AppThemeColor, List<Color>> = mapOf(
    AppThemeColor.MINT to listOf(Color(0xFF0B6B57), Color(0xFF2BB673)),
    AppThemeColor.BLUE to listOf(Color(0xFF16304F), Color(0xFF2E5EAA)),
    AppThemeColor.ORANGE to listOf(Color(0xFFC85A12), Color(0xFFF5A623)),
    AppThemeColor.GRAPHITE to listOf(Color(0xFF1F242B), Color(0xFF2F80ED))
)

@Composable
private fun ThemeColorSwatch(
    color: AppThemeColor,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(themeGradient[color] ?: emptyList()))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = CircleShape
                )
                .clickable(onClick = onClick)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(themeColorLabel(color), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
    )
}

private fun toneLabel(tone: ReminderTone): String = when (tone) {
    ReminderTone.GENTLE -> "温和"
    ReminderTone.DIRECT -> "直接"
    ReminderTone.SARCASTIC -> "毒舌"
    ReminderTone.CUSTOM -> "自定义"
}

private fun returnLabel(destination: ReturnDestination): String = when (destination) {
    ReturnDestination.FOCUS -> "不刷了，返回 Focus"
    ReturnDestination.HOME -> "不刷了，返回桌面"
    ReturnDestination.CUSTOM -> "不刷了，返回指定应用"
}

private fun detectionLabel(mode: DetectionMode): String = when (mode) {
    DetectionMode.REALTIME -> "实时模式（无障碍，推荐）"
    DetectionMode.COMPATIBILITY -> "兼容模式（使用情况访问）"
}

private fun detectionHint(mode: DetectionMode): String = when (mode) {
    DetectionMode.REALTIME -> "低耗电，实时响应；需要开启无障碍服务"
    DetectionMode.COMPATIBILITY -> "更省电的兼容方式；需要使用情况访问权限与通知权限"
}
