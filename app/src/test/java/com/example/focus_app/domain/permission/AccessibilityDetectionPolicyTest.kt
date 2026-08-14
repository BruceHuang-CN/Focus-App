package com.example.focus_app.domain.permission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityDetectionPolicyTest {
    @Test
    fun detection_is_ready_only_when_user_and_system_are_both_enabled() {
        assertTrue(isAccessibilityDetectionReady(userEnabled = true, systemEnabled = true))
        assertFalse(isAccessibilityDetectionReady(userEnabled = true, systemEnabled = false))
        assertFalse(isAccessibilityDetectionReady(userEnabled = false, systemEnabled = true))
        assertFalse(isAccessibilityDetectionReady(userEnabled = false, systemEnabled = false))
    }
}
