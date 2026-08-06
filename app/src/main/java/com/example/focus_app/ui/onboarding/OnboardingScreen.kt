package com.example.focus_app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.ui.settings.SettingsViewModel
import com.example.focus_app.util.PermissionHelper

/**
 * 首次引导：先选择检测方式（实时/无障碍为推荐项，UsageStats 为可选兼容项），
 * 再按选择引导对应权限，最后配置 AI。
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mode by remember { mutableStateOf(DetectionMode.REALTIME) }
    var hasUsageAccess by remember { mutableStateOf(PermissionHelper.hasUsageStatsPermission(context)) }
    var hasAccessibility by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var step by remember { mutableIntStateOf(0) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 结果由权限页面展示兜底 */ }

    // 从系统设置返回时自动刷新权限状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = PermissionHelper.hasUsageStatsPermission(context)
                hasAccessibility = PermissionHelper.isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 兼容模式第二步时，若未授予通知权限则自动请求
    LaunchedEffect(step, mode) {
        if (step == 2 && mode == DetectionMode.COMPATIBILITY &&
            PermissionHelper.needsNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // 进入 AI 配置步骤时（Android 13+）请求通知权限，实时/兼容模式都覆盖
    LaunchedEffect(step) {
        if (step == 3 && PermissionHelper.needsNotificationPermission(context)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    when (step) {
        0 -> Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("欢迎使用 Focus", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "选择一种检测方式，Focus 会在你打开目标 App 时提醒你回到任务。",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedButton(
                onClick = {
                    viewModel.applyOnboardingDetectionMode(DetectionMode.COMPATIBILITY)
                    mode = DetectionMode.COMPATIBILITY
                    step = 2
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("兼容模式（使用情况访问，更省电）")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.applyOnboardingDetectionMode(DetectionMode.REALTIME)
                    mode = DetectionMode.REALTIME
                    step = 1
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("实时模式（无障碍服务，推荐）")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "实时模式响应更快，兼容模式更省电，稍后都可在设置中更改。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }

        1 -> Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("开启无障碍服务", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Focus 通过「无障碍服务」实时检测你打开目标 App 的行为，无需持续轮询、更省电。\n\n" +
                    "请点击下方按钮，在列表中找到「Focus 专注助手」并开启。",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            ) { Text("前往开启") }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = {
                    if (PermissionHelper.isAccessibilityServiceEnabled(context)) step = 3
                }
            ) {
                Text(
                    if (hasAccessibility) "✓ 无障碍已开启，下一步 →" else "已开启，下一步 →"
                )
            }
        }

        2 -> Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("开启使用情况访问", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "兼容模式需要「使用情况访问权限」来检测目标 App，\n并需要通知权限来展示提醒。\n\n" +
                    "请点击下方按钮开启使用情况访问。",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { PermissionHelper.openUsageStatsSettings(context) }
            ) { Text("前往开启") }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = {
                    if (PermissionHelper.hasUsageStatsPermission(context)) step = 3
                }
            ) {
                Text(
                    if (hasUsageAccess) "✓ 权限已开启，下一步 →" else "已开启，下一步 →"
                )
            }
        }

        else -> Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("配置 AI", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Focus 使用你自己的 AI API Key 来生成个性化提醒。\n\n你可以在设置中随时修改。",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    PermissionHelper.markOnboardingDone(context)
                    onComplete()
                }
            ) { Text("进入 App") }
        }
    }
}
