package com.example.focus_app.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.focus_app.util.PermissionHelper

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasUsageAccess by remember { mutableStateOf(PermissionHelper.hasUsageStatsPermission(context)) }
    var hasAccessibility by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var step by remember { mutableIntStateOf(0) }

    // Auto-check permissions when returning from system settings
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

    when (step) {
        0 -> Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("欢迎使用 Focus", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Focus 需要「使用情况访问权限」来检测你何时打开目标 App。\n\n你的数据只存在本地，不会上传。", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { PermissionHelper.openUsageStatsSettings(context) }) { Text("前往开启权限") }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = {
                if (PermissionHelper.hasUsageStatsPermission(context)) step = 1
            }) {
                Text(if (hasUsageAccess) "✓ 权限已开启，下一步 →" else "已开启，下一步 →")
            }
        }
        1 -> Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("开启无障碍服务", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Focus 需要通过「无障碍服务」实时检测\n你打开目标 App 的行为。\n\n请点击下方按钮，在列表中找到「Focus 专注助手」\n并开启。", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("前往开启") }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = {
                if (PermissionHelper.isAccessibilityServiceEnabled(context)) step = 2
            }) {
                Text(if (hasAccessibility) "✓ 无障碍已开启，下一步 →" else "已开启，下一步 →")
            }
        }
        2 -> Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("配置 AI", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Focus 使用你自己的 AI API Key 来生成个性化提醒。\n\n你可以在设置中随时修改。", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = {
                PermissionHelper.markOnboardingDone(context)
                onComplete()
            }) { Text("进入 App") }
        }
    }
}
