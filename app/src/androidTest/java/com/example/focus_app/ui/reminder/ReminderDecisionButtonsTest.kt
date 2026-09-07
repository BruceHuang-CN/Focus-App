package com.example.focus_app.ui.reminder

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ReminderDecisionButtonsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun intentional_use_waits_for_a_duration_choice_before_dispatching() {
        val selected = mutableListOf<Pair<ReminderDecisionAction, Int>>()
        composeRule.setContent {
            MaterialTheme {
                ReminderDecisionButtons(
                    actions = listOf(
                        ReminderDecisionAction.REST,
                        ReminderDecisionAction.RETURN,
                        ReminderDecisionAction.INTENTIONAL
                    ),
                    onReturnClick = {},
                    onTimedDecision = { action, minutes -> selected += action to minutes },
                    onCustomTimedAction = {}
                )
            }
        }

        composeRule.onNodeWithText("回到任务").assertIsDisplayed()
        composeRule.onNodeWithText("有目的使用").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("休息一下").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(emptyList<Pair<ReminderDecisionAction, Int>>(), selected)
        }
        composeRule.onNodeWithText("5 分钟后提醒").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(ReminderDecisionAction.INTENTIONAL to 5), selected)
        }
    }

    @Test
    fun rest_opens_its_own_custom_duration_entry() {
        var selected: ReminderDecisionAction? = null
        composeRule.setContent {
            MaterialTheme {
                ReminderDecisionButtons(
                    actions = listOf(
                        ReminderDecisionAction.INTENTIONAL,
                        ReminderDecisionAction.REST,
                        ReminderDecisionAction.RETURN
                    ),
                    onReturnClick = {},
                    onTimedDecision = { _, _ -> },
                    onCustomTimedAction = { selected = it }
                )
            }
        }

        composeRule.onNodeWithText("休息一下").performClick()
        composeRule.runOnIdle { assertNull(selected) }
        composeRule.onNodeWithText("自定义分钟数").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(ReminderDecisionAction.REST, selected)
        }
    }

    @Test
    fun actions_are_disabled_until_the_display_is_recorded() {
        var dispatched = false
        composeRule.setContent {
            MaterialTheme {
                ReminderDecisionButtons(
                    actions = ReminderDecisionAction.entries,
                    enabled = false,
                    onReturnClick = { dispatched = true },
                    onTimedDecision = { _, _ -> dispatched = true },
                    onCustomTimedAction = { dispatched = true }
                )
            }
        }

        composeRule.onNodeWithText("回到任务").assertIsNotEnabled().performClick()
        composeRule.onNodeWithText("有目的使用").assertIsNotEnabled().performClick()
        composeRule.onNodeWithText("休息一下").assertIsNotEnabled().performClick()

        composeRule.runOnIdle { assertEquals(false, dispatched) }
    }
}
