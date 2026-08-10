package com.example.focus_app.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetAppsExitPolicyTest {
    @Test
    fun exit_requires_confirmation_when_a_target_app_is_added() {
        assertTrue(
            TargetAppsExitPolicy.requiresConfirmation(
                savedPackages = setOf("com.example.saved"),
                selectedPackages = setOf("com.example.saved", "com.example.new")
            )
        )
    }

    @Test
    fun exit_requires_confirmation_when_a_target_app_is_removed() {
        assertTrue(
            TargetAppsExitPolicy.requiresConfirmation(
                savedPackages = setOf("com.example.saved", "com.example.removed"),
                selectedPackages = setOf("com.example.saved")
            )
        )
    }

    @Test
    fun exit_does_not_require_confirmation_when_selection_is_unchanged() {
        assertFalse(
            TargetAppsExitPolicy.requiresConfirmation(
                savedPackages = setOf("com.example.saved"),
                selectedPackages = setOf("com.example.saved")
            )
        )
    }
}
