package com.example.focus_app.ui.settings

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.focus_app.domain.model.AiProvider
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.domain.model.ReminderTone
import com.example.focus_app.domain.model.ReturnDestination
import com.example.focus_app.ui.components.PresetSelector
import com.example.focus_app.util.PermissionHelper
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    navigateToTargetApps: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val s by viewModel.settings.collectAsState()
    val connectionState by viewModel.aiConnection.collectAsState()
    val tonePreview by viewModel.tonePreview.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayGranted by remember { mutableStateOf(PermissionHelper.hasOverlayPermission(context)) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var lastChange by remember { mutableStateOf("") }
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

    fun onSettingChanged(label: String) {
        lastChange = label
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar("已自动保存：$label", duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            // ── 目标应用 ──
            SectionTitle("目标应用")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navigateToTargetApps() }
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
            Divider()

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

            Spacer(modifier = Modifier.height(32.dp))
        }
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
}

private fun detectionLabel(mode: DetectionMode): String = when (mode) {
    DetectionMode.REALTIME -> "实时模式（无障碍，推荐）"
    DetectionMode.COMPATIBILITY -> "兼容模式（使用情况访问）"
}

private fun detectionHint(mode: DetectionMode): String = when (mode) {
    DetectionMode.REALTIME -> "低耗电，实时响应；需要开启无障碍服务"
    DetectionMode.COMPATIBILITY -> "更省电的兼容方式；需要使用情况访问权限与通知权限"
}
