package com.example.focus_app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.focus_app.ui.theme.FocusAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeContentTest {
    @get:Rule val compose = createComposeRule()

    private val permissions = listOf(HomePermissionItem("overlay", "悬浮窗权限", "未开启", false))

    @Test fun permissionRowsAreCollapsedAndDispatchTheSelectedAction() {
        var selected: String? = null
        compose.setContent {
            FocusAppTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HomeGuardianCard(HomeUiState(), permissions, {}, {}, {}, { selected = it })
                }
            }
        }
        compose.onNodeWithText("悬浮窗权限").assertDoesNotExist()
        compose.onNodeWithText("权限与系统状态").performScrollTo().performClick()
        compose.onNodeWithText("悬浮窗权限").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("overlay", selected) }
        compose.onNodeWithText("权限与系统状态").performScrollTo().performClick()
        compose.onNodeWithText("悬浮窗权限").assertDoesNotExist()
    }

    @Test fun resetWindowKeepsQuotaAndAiActionsIndependent() {
        var quota = 0
        var ai = 0
        compose.setContent {
            FocusAppTheme {
                HomeGuardianCard(HomeUiState(), permissions, {}, { quota++ }, { ai++ }, {})
            }
        }
        compose.onNodeWithText("重置提醒额度").assertDoesNotExist()
        compose.onNodeWithText("重置与更新").performClick()
        compose.onNodeWithText("重置提醒额度").assertIsDisplayed()
        compose.onNodeWithText("重置 AI 文案").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(0, quota); assertEquals(1, ai) }
        compose.onNodeWithText("重置与更新").performClick()
        compose.onNodeWithText("重置提醒额度").performClick()
        compose.runOnIdle { assertEquals(1, quota); assertEquals(1, ai) }
    }

    @Test fun regenerationCannotBeTriggeredAgainWhileBusy() {
        compose.setContent {
            FocusAppTheme {
                HomeGuardianCard(HomeUiState(isRegeneratingMessages = true), permissions, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("AI 文案生成中…").performClick()
        compose.onNodeWithText("生成中…").assertIsNotEnabled()
        compose.onNodeWithText("重置提醒额度").assertIsEnabled()
    }

    @Test fun homeUsesNewBrandAndSavesSelectedMoodWithNote() {
        var savedMood: String? = null
        var savedNote: String? = null
        compose.setContent {
            FocusAppTheme {
                HomeContent(HomeUiState(), {}, {}, {}, {}, {}, {}, permissions, {},
                    { mood, note, done -> savedMood = mood; savedNote = note; done() })
            }
        }
        compose.onNodeWithText("回神").assertIsDisplayed()
        compose.onNodeWithText("Focus 专注助手").assertDoesNotExist()
        compose.onNodeWithText("今日状态", substring = true).assertDoesNotExist()
        compose.onNodeWithText("平静").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("完成了一段阅读")
        compose.onNodeWithText("记录心情").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("平静", savedMood)
            assertEquals("完成了一段阅读", savedNote)
        }
        compose.onNodeWithText("记录心情").assertDoesNotExist()
    }
}
