package com.example.focus_app.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focus_app.domain.model.*
import com.example.focus_app.ui.components.TaskCompletionCelebration
import com.example.focus_app.ui.theme.FocusAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Uses synthetic UI state only; never writes tasks, permissions, or device settings. */
@RunWith(Parameterized::class)
class HomeVisualAcceptanceTest(
    private val color: AppThemeColor,
    private val mode: AppThemeMode,
    private val narrow: Boolean,
    private val empty: Boolean
) {
    @get:Rule val compose = createComposeRule()
    private val name get() = "${color}_${mode}_${if (narrow) "320-large" else "393"}_${if (empty) "empty" else "task"}"

    @Test fun allPermissionsAndBottomActionsRemainReachable() {
        var permission = ""
        var resets = 0
        var appEntries = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (narrow) 1.5f else 1f)) {
                FocusAppTheme(mode = mode, color = color) {
                    Box(Modifier.requiredSize(if (narrow) 320.dp else 393.dp, if (narrow) 640.dp else 760.dp).testTag("capture")) {
                        Scaffold(bottomBar = {
                            NavigationBar {
                                listOf("首页", "任务", "统计", "设置").forEach {
                                    NavigationBarItem(selected = it == "首页", onClick = {},
                                        icon = { Text("•") }, label = { Text(it) })
                                }
                            }
                        }) { padding ->
                            Box(Modifier.padding(padding)) {
                                HomeContent(
                                    uiState = HomeUiState(
                                        activeTask = if (empty) null else FocusTask(title = if (narrow)
                                            "写下今天的想法，整理下一步计划，然后留一点时间给自己。" else "写下今天最重要的一件事"),
                                        targetAppCount = 3, activeGroupName = "测试应用组"
                                    ),
                                    onRecordMood = {}, onManageTasks = {}, onCompleteCurrentTask = {},
                                    onGuardianEnabledChange = {}, onResetReminderQuota = { resets++ },
                                    onRegenerateReminderMessages = {},
                                    permissions = listOf(
                                        HomePermissionItem("overlay", "悬浮窗权限", "已开启", true),
                                        HomePermissionItem("accessibility", "无障碍服务", "已连接", true),
                                        HomePermissionItem("usage", "使用情况访问", "已开启", true),
                                        HomePermissionItem("notification", "通知", "已开启", true),
                                        HomePermissionItem("battery", "电池优化", "未允许", false)
                                    ),
                                    onPermissionClick = { permission = it },
                                    onSaveMood = { _, _, done -> done() }, onManageApps = { appEntries++ }
                                )
                            }
                        }
                    }
                }
            }
        }
        shot("collapsed")
        compose.onNodeWithText("守护设置").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("权限与系统状态").performScrollTo().assertIsDisplayed().performClick()
        shot("expanded")
        for ((id, title) in listOf("overlay" to "悬浮窗权限", "accessibility" to "无障碍服务",
            "usage" to "使用情况访问", "notification" to "通知", "battery" to "电池优化")) {
            compose.onNodeWithText(title).performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(id, permission) }
        }
        compose.onNodeWithText("管理受守护应用（3）").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("重置与更新").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        shot("bottom")
        compose.onNodeWithText("重置与更新").performClick()
        compose.onNodeWithText("重置提醒额度").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, resets); assertEquals(1, appEntries) }
    }

    private fun shot(state: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("capture").captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "home-acceptance").apply { mkdirs() }
        File(dir, "$name-$state.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}-{1}-narrow={2}-empty={3}")
        fun cases(): List<Array<Any>> = AppThemeColor.entries.flatMap { color ->
            listOf(AppThemeMode.DAY, AppThemeMode.NIGHT).flatMap { mode ->
                listOf(arrayOf<Any>(color, mode, false, false), arrayOf<Any>(color, mode, true, false))
            }
        } + listOf(arrayOf<Any>(AppThemeColor.MINT, AppThemeMode.DAY, false, true),
            arrayOf<Any>(AppThemeColor.MINT, AppThemeMode.NIGHT, true, true))
    }
}
