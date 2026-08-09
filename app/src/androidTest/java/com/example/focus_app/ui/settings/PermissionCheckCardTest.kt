package com.example.focus_app.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.domain.permission.PermissionCheckAction
import com.example.focus_app.domain.permission.PermissionCheckItem
import com.example.focus_app.domain.permission.PermissionCheckStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionCheckCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun summary_shows_missing_count_and_expands_to_rows() {
        var acted: PermissionCheckAction? = null
        val items = listOf(
            PermissionCheckItem(
                id = "accessibility",
                label = "系统无障碍服务",
                detail = "设置 → 无障碍 → Focus 专注助手",
                status = PermissionCheckStatus.MISSING,
                action = PermissionCheckAction.OPEN_ACCESSIBILITY
            ),
            PermissionCheckItem(
                id = "notification",
                label = "通知权限",
                detail = "Android 13+ 需要通知权限",
                status = PermissionCheckStatus.OK
            )
        )
        composeRule.setContent {
            MaterialTheme {
                PermissionCheckCard(
                    mode = DetectionMode.REALTIME,
                    items = items,
                    onAction = { acted = it }
                )
            }
        }

        composeRule.onNodeWithText("实时模式 · 1 项未就绪").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("系统无障碍服务").fetchSemanticsNodes().size)

        composeRule.onNodeWithText("实时模式 · 1 项未就绪").performClick()
        composeRule.onNodeWithText("系统无障碍服务").assertIsDisplayed()
        composeRule.onNodeWithText("去开启").performClick()
        composeRule.runOnIdle {
            assertEquals(PermissionCheckAction.OPEN_ACCESSIBILITY, acted)
        }
    }
}
