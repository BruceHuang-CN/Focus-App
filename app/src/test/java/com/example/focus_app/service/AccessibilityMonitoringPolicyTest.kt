package com.example.focus_app.service

import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.domain.model.DetectionMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityMonitoringPolicyTest {
    @Test
    fun system_overlay_windows_do_not_replace_the_last_real_application_package() {
        val foregroundState = AccessibilityForegroundState()

        foregroundState.onWindowStateChanged("com.ss.android.ugc.aweme", isApplicationTask = true)
        assertTrue(foregroundState.isForeground("com.ss.android.ugc.aweme"))

        foregroundState.onWindowStateChanged("com.coloros.colordirectservice", isApplicationTask = false)
        assertTrue(foregroundState.isForeground("com.ss.android.ugc.aweme"))
        foregroundState.onWindowStateChanged("com.coloros.smartsidebar", isApplicationTask = false)
        assertTrue(foregroundState.isForeground("com.ss.android.ugc.aweme"))

        foregroundState.onWindowStateChanged("com.oppo.launcher", isApplicationTask = true)
        assertFalse(foregroundState.isForeground("com.ss.android.ugc.aweme"))
    }

    @Test
    fun queued_accessibility_events_are_ignored_after_guardian_is_disabled() {
        assertFalse(
            shouldProcessQueuedAccessibilityEvent(
                AppSettings(
                    detectionMode = DetectionMode.REALTIME,
                    enableAccessibility = true,
                    guardianEnabled = false
                )
            )
        )
    }

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
