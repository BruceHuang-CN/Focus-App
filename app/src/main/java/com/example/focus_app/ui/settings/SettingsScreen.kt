package com.example.focus_app.ui.settings

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.focus_app.domain.model.AiProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, navigateToTargetApps: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsState()
    val targetCount = s.targetApps.size
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var lastChange by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }

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
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { navigateToTargetApps() }.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("目标 App", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (targetCount == 0) "未选择任何 App（检测不会触发）" else "已选择 $targetCount 个 App",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (targetCount == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                }
                Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
            }
            Divider()
            Text("提醒延迟时间", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 1, 2, 3).forEach { m ->
                    FilterChip(
                        selected = s.remindDelayMinutes == m,
                        onClick = { viewModel.updateRemindDelay(m); onSettingChanged("延迟 ${m}分钟") },
                        label = { Text(if (m == 0) "即时" else "${m}分钟") }
                    )
                }
            }
            Divider()
            Text("每小时最多提醒次数", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3, 5).forEach { m ->
                    FilterChip(
                        selected = s.maxRemindsPerHour == m,
                        onClick = { viewModel.updateMaxReminds(m); onSettingChanged("最多 ${m}次/小时") },
                        label = { Text("${m}次") }
                    )
                }
            }
            Divider()
            Text("AI 服务", style = MaterialTheme.typography.titleMedium)
            AiProvider.entries.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = s.aiProvider == p,
                        onClick = { viewModel.updateAiProvider(p); onSettingChanged("AI: ${p.displayName}") }
                    )
                    Text(p.displayName)
                }
            }
            OutlinedTextField(value = s.apiEndpoint, onValueChange = { viewModel.updateApiEndpoint(it) }, label = { Text("API 端点") }, modifier = Modifier.fillMaxWidth())
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
            OutlinedTextField(value = s.aiModel, onValueChange = { viewModel.updateAiModel(it) }, label = { Text("模型名") }, modifier = Modifier.fillMaxWidth())
            Divider()
            Text("AI 提醒风格", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("gentle" to "温和", "humorous" to "幽默", "sarcastic" to "毒舌").forEach { (k, l) ->
                    FilterChip(
                        selected = s.aiPersonality == k,
                        onClick = { viewModel.updatePersonality(k); onSettingChanged("风格: $l") },
                        label = { Text(l) }
                    )
                }
            }
            Divider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用无障碍检测", modifier = Modifier.weight(1f))
                Switch(
                    checked = s.enableAccessibility,
                    onCheckedChange = { viewModel.toggleAccessibility(); onSettingChanged(if (!s.enableAccessibility) "开启无障碍检测" else "关闭无障碍检测") }
                )
            }
            Divider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("提醒前呼吸停顿", modifier = Modifier.weight(1f))
                Switch(
                    checked = s.enableBreathingPause,
                    onCheckedChange = { viewModel.toggleBreathingPause(); onSettingChanged(if (!s.enableBreathingPause) "开启呼吸停顿" else "关闭呼吸停顿") }
                )
            }
            // Spacer at bottom for snackbar visibility
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
