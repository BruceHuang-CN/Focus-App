package com.example.focus_app.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.focus_app.domain.model.FocusTask
import com.example.focus_app.ui.theme.FocusAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeContentTest {
    @get:Rule val compose = createComposeRule()

    private val permissions = listOf(
        HomePermissionItem("overlay", "悬浮窗权限", "未开启", false)
    )

    private fun setHome(
        uiState: HomeUiState = HomeUiState(targetAppCount = 3),
        permissionItems: List<HomePermissionItem> = permissions,
        onManageTasks: () -> Unit = {},
        onCompleteCurrentTask: () -> Unit = {},
        onGuardianEnabledChange: (Boolean) -> Unit = {},
        onResetReminderQuota: () -> Unit = {},
        onRegenerateReminderMessages: () -> Unit = {},
        onPermissionClick: (String) -> Unit = {}
    ) {
        compose.setContent {
            FocusAppTheme {
                HomeContent(
                    uiState = uiState,
                    onRecordMood = {},
                    onManageTasks = onManageTasks,
                    onCompleteCurrentTask = onCompleteCurrentTask,
                    onGuardianEnabledChange = onGuardianEnabledChange,
                    onResetReminderQuota = onResetReminderQuota,
                    onRegenerateReminderMessages = onRegenerateReminderMessages,
                    permissions = permissionItems,
                    onPermissionClick = onPermissionClick,
                    onSaveMood = { _, _, done -> done() }
                )
            }
        }
    }

    @Test
    fun currentTaskAndGuardianShareOneCardAndGuardianStartsCollapsed() {
        val title = "写下今天最重要的一件事"
        setHome(uiState = HomeUiState(activeTask = FocusTask(title = title), targetAppCount = 3))

        compose.onNodeWithText("回神").assertIsDisplayed()
        compose.onNodeWithText("当前任务").assertIsDisplayed()
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("进行中").assertIsDisplayed()
        compose.onNodeWithText("完成任务").assertIsDisplayed()
        compose.onNodeWithText("管理任务").assertIsDisplayed()
        compose.onNodeWithText("守护设置").assertIsDisplayed()
        compose.onNodeWithText("守护已开启").assertIsDisplayed()
        compose.onNodeWithText("悬浮窗权限").assertDoesNotExist()
        compose.onNode(isToggleable()).assertDoesNotExist()

        compose.onNodeWithText("守护设置").performScrollTo().performClick()
        compose.onNodeWithText("权限与系统状态").assertIsDisplayed()
        compose.onNode(isToggleable()).assertIsOn()
        compose.onNodeWithText("悬浮窗权限").assertDoesNotExist()
        compose.onNodeWithText("权限与系统状态").performScrollTo().performClick()
        compose.onNodeWithText("悬浮窗权限").assertIsDisplayed()
    }

    @Test
    fun expandingDoesNotTriggerBusinessCallbacksButControlsStillWork() {
        var guardianChanges = 0
        var enabledValue: Boolean? = null
        var selectedPermission: String? = null
        setHome(
            onGuardianEnabledChange = { guardianChanges++; enabledValue = it },
            onPermissionClick = { selectedPermission = it }
        )

        compose.onNodeWithText("守护设置").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(0, guardianChanges)
            assertNull(enabledValue)
            assertNull(selectedPermission)
        }

        compose.onNode(isToggleable()).performClick()
        compose.runOnIdle {
            assertEquals(1, guardianChanges)
            assertEquals(false, enabledValue)
        }

        compose.onNodeWithText("权限与系统状态").performScrollTo().performClick()
        compose.onNodeWithText("悬浮窗权限").performClick()
        compose.runOnIdle { assertEquals("overlay", selectedPermission) }

        compose.onNodeWithText("守护设置").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, guardianChanges) }
    }

    @Test
    fun resetQuotaAndRegenerateActionsRemainIndependent() {
        var quotaResets = 0
        var regenerations = 0
        setHome(
            onResetReminderQuota = { quotaResets++ },
            onRegenerateReminderMessages = { regenerations++ }
        )

        compose.onNodeWithText("守护设置").performScrollTo().performClick()
        compose.onNodeWithText("重置与更新").performScrollTo().performClick()
        compose.onNodeWithText("重置提醒额度").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, quotaResets)
            assertEquals(0, regenerations)
        }

        compose.onNodeWithText("重置与更新").performScrollTo().performClick()
        compose.onNodeWithText("重置 AI 文案").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, quotaResets)
            assertEquals(1, regenerations)
        }
    }

    @Test
    fun regenerationIsDisabledWhileBusyButQuotaResetRemainsAvailable() {
        setHome(uiState = HomeUiState(isRegeneratingMessages = true))

        compose.onNodeWithText("守护设置").performScrollTo().performClick()
        compose.onNodeWithText("AI 文案生成中…").performScrollTo().performClick()
        compose.onNodeWithText("生成中…").assertIsNotEnabled()
        compose.onNodeWithText("重置提醒额度").assertIsEnabled()
    }

    @Test
    fun activeTaskActionsWorkAndLongExpandedCardCanScrollToSettings() {
        var managed = 0
        var completed = 0
        var selectedPermission: String? = null
        var resets = 0
        val longTitle = "写下今天的想法并整理下一步计划。".repeat(8)
        val manyPermissions = (1..6).map { index ->
            HomePermissionItem("permission-$index", "系统权限 $index", "待检查", false)
        }
        setHome(
            uiState = HomeUiState(activeTask = FocusTask(title = longTitle)),
            permissionItems = manyPermissions,
            onManageTasks = { managed++ },
            onCompleteCurrentTask = { completed++ },
            onPermissionClick = { selectedPermission = it },
            onResetReminderQuota = { resets++ }
        )
        compose.onNodeWithText("管理任务").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("完成任务").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("守护设置").performScrollTo().performClick()
        compose.onNodeWithText("权限与系统状态").performScrollTo().performClick()
        compose.onNodeWithText("系统权限 6").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("重置与更新").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("重置提醒额度").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, managed)
            assertEquals(1, completed)
            assertEquals("permission-6", selectedPermission)
            assertEquals(1, resets)
        }
    }

    @Test
    fun emptyHomeOffersTaskSelectionWithoutCompletionAction() {
        var managed = 0
        setHome(onManageTasks = { managed++ })

        compose.onNodeWithText("选择任务").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, managed) }
        compose.onNodeWithText("完成任务").assertDoesNotExist()
    }
}
