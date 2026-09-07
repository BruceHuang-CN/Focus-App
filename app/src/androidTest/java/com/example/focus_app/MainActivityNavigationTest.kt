package com.example.focus_app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
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

    private companion object {
        const val PREFERENCES_NAME = "focus_prefs"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
