package com.example.focus_app.ui.tasks

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.focus_app.domain.model.FocusTask
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskEditorScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun editor_shows_validation_message_when_conflict_exists() {
        composeRule.setContent {
            MaterialTheme {
                TaskEditorScreen(
                    initial = null,
                    validationMessage = "与已有任务的时间段冲突，请调整",
                    onSave = { _, _, _, _ -> },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("与已有任务的时间段冲突，请调整").assertIsDisplayed()
    }

    @Test
    fun editor_accepts_title_input() {
        composeRule.setContent {
            MaterialTheme {
                TaskEditorScreen(
                    initial = null,
                    validationMessage = null,
                    onSave = { _, _, _, _ -> },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("写作业")
        composeRule.onNodeWithText("写作业").assertIsDisplayed()
    }

    @Test
    fun editor_prefills_existing_task_schedule() {
        composeRule.setContent {
            MaterialTheme {
                TaskEditorScreen(
                    initial = FocusTask(title = "背单词", scheduleStartMinute = 8 * 60, scheduleEndMinute = 9 * 60),
                    validationMessage = null,
                    onSave = { _, _, _, _ -> },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("背单词").assertIsDisplayed()
        composeRule.onNodeWithText("开始 08:00").assertIsDisplayed()
        composeRule.onNodeWithText("结束 09:00").assertIsDisplayed()
    }
}
