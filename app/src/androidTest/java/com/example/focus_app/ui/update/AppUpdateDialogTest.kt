package com.example.focus_app.ui.update

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.focus_app.data.update.AppRelease
import com.example.focus_app.ui.theme.FocusAppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppUpdateDialogTest {
    @get:Rule val compose = createComposeRule()
    private val release = AppRelease("com.example.focus_app", 3, "1.0.2",
        "https://brucehere.com/downloads/huishen/", "改善呼吸页面的反馈", 26)

    @Test fun later_keeps_the_browser_closed() {
        var opened = false
        var dismissed = false
        compose.setContent {
            FocusAppTheme { AppUpdateDialog(AppUpdateUiState(release = release),
                onOpenWebsite = { opened = true }, onDismiss = { dismissed = true }) }
        }
        compose.onNodeWithText("发现新版本 1.0.2").assertIsDisplayed()
        compose.onNodeWithText("稍后再说").performClick()
        compose.runOnIdle { assertTrue(dismissed); assertFalse(opened) }
    }

    @Test fun opening_the_download_page_requires_the_update_button() {
        var opened: String? = null
        compose.setContent {
            FocusAppTheme { AppUpdateDialog(AppUpdateUiState(release = release),
                onOpenWebsite = { opened = it }, onDismiss = {}) }
        }
        compose.runOnIdle { assertNull(opened) }
        compose.onNodeWithText("前往更新").performClick()
        compose.runOnIdle { assertEquals(release.downloadPageUrl, opened) }
    }
}
