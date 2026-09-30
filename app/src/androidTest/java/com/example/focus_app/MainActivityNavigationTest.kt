package com.example.focus_app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityNavigationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences by lazy {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private var onboardingOriginallyDone = false

    @Before
    fun setUp() {
        onboardingOriginallyDone = preferences.getBoolean(KEY_ONBOARDING_DONE, false)
        preferences.edit().putBoolean(KEY_ONBOARDING_DONE, true).commit()
    }

    @After
    fun tearDown() {
        preferences.edit().putBoolean(KEY_ONBOARDING_DONE, onboardingOriginallyDone).commit()
    }

    @Test
    fun open_tasks_action_selects_the_tasks_tab() {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_TASKS
        }

        ActivityScenario.launch<MainActivity>(intent).use {
            composeRule.waitForIdle()
            composeRule.onNode(hasText("任务") and isSelectable()).assertIsSelected()
            composeRule.onNode(hasText("首页") and isSelectable()).assertIsNotSelected()
        }
    }

    @Test
    fun home_task_entry_then_home_tab_returns_home_without_back_button() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNode(hasText("首页") and isSelectable()).assertIsSelected()
            composeRule.onNode(hasText("管理任务") or hasText("选择任务")).performClick()
            composeRule.onNode(hasText("任务") and isSelectable()).assertIsSelected()
            composeRule.onNode(hasText("首页") and isSelectable()).performClick()
            composeRule.onNode(hasText("首页") and isSelectable()).assertIsSelected()
            composeRule.onNode(hasText("任务") and isSelectable()).performClick()
            composeRule.onNode(hasText("首页") and isSelectable()).performClick()
            composeRule.onNode(hasText("首页") and isSelectable()).assertIsSelected()
        }
    }

    @Test
    fun summary_notification_opens_statistics_and_home_remains_accessible() {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_SUMMARY
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            composeRule.onNode(hasText("统计") and isSelectable()).assertIsSelected()
            composeRule.onNodeWithText("每日总结").assertExists()
            composeRule.onNodeWithText("心情记录").assertExists()
            composeRule.onNode(hasText("首页") and isSelectable()).performClick()
            composeRule.onNode(hasText("首页") and isSelectable()).assertIsSelected()
            it.recreate()
            composeRule.onNode(hasText("首页") and isSelectable()).assertIsSelected()
        }
    }

    @Test
    fun return_feedback_is_consumed_once_and_does_not_replay_on_recreation() {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_TASKS
            putExtra(MainActivity.EXTRA_CELEBRATE_RETURN, true)
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            composeRule.onNode(hasText("任务") and isSelectable()).assertIsSelected()
            scenario.onActivity {
                org.junit.Assert.assertFalse(it.intent.hasExtra(MainActivity.EXTRA_CELEBRATE_RETURN))
                org.junit.Assert.assertEquals(MainActivity.ACTION_OPEN_TASKS, it.intent.action)
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("真棒，你已经找回一点自控力了！").assertDoesNotExist()
            scenario.recreate()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("真棒，你已经找回一点自控力了！").assertDoesNotExist()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "focus_prefs"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
