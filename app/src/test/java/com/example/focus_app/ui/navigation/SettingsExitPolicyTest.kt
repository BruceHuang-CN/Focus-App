package com.example.focus_app.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsExitPolicyTest {
    @Test
    fun edited_settings_require_confirmation_before_leaving() {
        assertTrue(
            settingsExitNeedsConfirmation(
                currentRoute = Screen.Settings.route,
                hasUnsavedChanges = true
            )
        )
    }

    @Test
    fun saved_settings_can_leave_without_confirmation() {
        assertFalse(
            settingsExitNeedsConfirmation(
                currentRoute = Screen.Settings.route,
                hasUnsavedChanges = false
            )
        )
    }

    @Test
    fun other_tabs_are_not_blocked_by_the_settings_flag() {
        assertFalse(
            settingsExitNeedsConfirmation(
                currentRoute = Screen.Home.route,
                hasUnsavedChanges = true
            )
        )
    }
}
