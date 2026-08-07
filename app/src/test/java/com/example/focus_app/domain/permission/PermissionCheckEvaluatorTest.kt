package com.example.focus_app.domain.permission

import com.example.focus_app.domain.model.DetectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionCheckEvaluatorTest {

    @Test
    fun realtime_mode_checks_accessibility_switch_notification_overlay_and_targets() {
        val items = PermissionCheckEvaluator.evaluate(
            mode = DetectionMode.REALTIME,
            accessibilityEnabled = false,
            usageStatsGranted = false,
            notificationGranted = false,
            overlayGranted = false,
            enableAccessibility = false,
            hasTargetApps = false
        )

        assertEquals(listOf("accessibility", "app_switch", "notification", "overlay", "targets"), items.map { it.id })
        assertTrue(items.all { it.status == PermissionCheckStatus.MISSING })
    }

    @Test
    fun realtime_mode_all_ok_when_everything_is_granted() {
        val items = PermissionCheckEvaluator.evaluate(
            mode = DetectionMode.REALTIME,
            accessibilityEnabled = true,
            usageStatsGranted = false,
            notificationGranted = true,
            overlayGranted = true,
            enableAccessibility = true,
            hasTargetApps = true
        )

        assertTrue(items.all { it.status == PermissionCheckStatus.OK })
    }

    @Test
    fun compatibility_mode_checks_usage_stats_instead_of_accessibility() {
        val items = PermissionCheckEvaluator.evaluate(
            mode = DetectionMode.COMPATIBILITY,
            accessibilityEnabled = true,
            usageStatsGranted = false,
            notificationGranted = false,
            overlayGranted = false,
            enableAccessibility = true,
            hasTargetApps = true
        )

        assertEquals(listOf("usage", "notification", "overlay", "targets"), items.map { it.id })
        assertEquals(PermissionCheckStatus.MISSING, items.first { it.id == "usage" }.status)
        assertTrue(items.none { it.id == "accessibility" || it.id == "app_switch" })
    }

    @Test
    fun missing_target_apps_is_flagged_in_both_modes() {
        val realtime = PermissionCheckEvaluator.evaluate(
            mode = DetectionMode.REALTIME,
            accessibilityEnabled = true,
            usageStatsGranted = true,
            notificationGranted = true,
            overlayGranted = true,
            enableAccessibility = true,
            hasTargetApps = false
        )
        val compatibility = PermissionCheckEvaluator.evaluate(
            mode = DetectionMode.COMPATIBILITY,
            accessibilityEnabled = true,
            usageStatsGranted = true,
            notificationGranted = true,
            overlayGranted = true,
            enableAccessibility = true,
            hasTargetApps = false
        )

        assertEquals(PermissionCheckStatus.MISSING, realtime.first { it.id == "targets" }.status)
        assertEquals(PermissionCheckStatus.MISSING, compatibility.first { it.id == "targets" }.status)
    }
}
