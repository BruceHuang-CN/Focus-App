package com.example.focus_app.service

import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.domain.model.DetectionMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityMonitoringPolicyTest {
    @Test
    fun accessibility_events_require_realtime_mode_accessibility_toggle_and_guardian_toggle() {
        assertTrue(
            shouldProcessAccessibilityEvents(
                AppSettings(
                    detectionMode = DetectionMode.REALTIME,
                    enableAccessibility = true
                )
            )
        )
        assertFalse(
            shouldProcessAccessibilityEvents(
                AppSettings(
                    detectionMode = DetectionMode.REALTIME,
                    enableAccessibility = false
                )
            )
        )
        assertFalse(
            shouldProcessAccessibilityEvents(
                AppSettings(
                    detectionMode = DetectionMode.COMPATIBILITY,
                    enableAccessibility = true
                )
            )
        )
        assertFalse(
            shouldProcessAccessibilityEvents(
                AppSettings(
                    detectionMode = DetectionMode.REALTIME,
                    enableAccessibility = true,
                    guardianEnabled = false
                )
            )
        )
    }
}
