package com.example.focus_app.service

import com.example.focus_app.domain.model.DetectionMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDetectionServicePolicyTest {
    @Test
    fun compatibility_monitoring_stops_when_guardian_is_disabled() {
        assertFalse(
            shouldRunCompatibilityMonitoring(
                mode = DetectionMode.COMPATIBILITY,
                guardianEnabled = false
            )
        )
    }

    @Test
    fun compatibility_monitoring_runs_when_guardian_is_enabled() {
        assertTrue(
            shouldRunCompatibilityMonitoring(
                mode = DetectionMode.COMPATIBILITY,
                guardianEnabled = true
            )
        )
    }
}
