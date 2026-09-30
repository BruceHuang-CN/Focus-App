package com.example.focus_app.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focus_app.ui.components.TaskCompletionCelebration
import com.example.focus_app.ui.theme.FocusAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class HomeCelebrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun feedbackAppearsAfterSuccessAndExpires() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FocusAppTheme {
                var event by remember { mutableIntStateOf(0) }
                Box(Modifier.fillMaxSize()) {
                    Button(onClick = { event++ }) { Text("模拟保存成功") }
                    TaskCompletionCelebration(event)
                }
            }
        }
        compose.onNodeWithTag("completion-celebration").assertDoesNotExist()
        compose.onNodeWithText("模拟保存成功").performClick()
        compose.mainClock.advanceTimeBy(1_120)
        compose.onNodeWithText("真棒！", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("你已经找回\n一点自控力了！", useUnmergedTree = true).assertIsDisplayed()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "home-acceptance").apply { mkdirs() }
        File(dir, "celebration.png").outputStream().use {
            compose.onNodeWithTag("completion-celebration").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.mainClock.advanceTimeBy(3_200)
        compose.onNodeWithTag("completion-celebration").assertDoesNotExist()
    }

    @Test fun tappingCelebrationDismissesWithoutTouchingUnderlyingPage() {
        var backgroundClicks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FocusAppTheme {
                Box(Modifier.fillMaxSize().clickable { backgroundClicks++ }) {
                    TaskCompletionCelebration(eventId = 1)
                }
            }
        }
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithTag("completion-celebration").performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("completion-celebration").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, backgroundClicks) }
    }

    @Test fun anotherSuccessfulActionStartsANewCelebration() {
        val event = mutableIntStateOf(1)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FocusAppTheme { TaskCompletionCelebration(event.intValue) }
        }
        compose.mainClock.advanceTimeBy(3_000)
        compose.runOnIdle { event.intValue++ }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithText("真棒！", useUnmergedTree = true).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2_700)
        compose.onNodeWithTag("completion-celebration").assertDoesNotExist()
    }
}
