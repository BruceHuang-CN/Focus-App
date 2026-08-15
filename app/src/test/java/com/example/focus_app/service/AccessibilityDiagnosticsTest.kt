package com.example.focus_app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityDiagnosticsTest {
    @Test
    fun lifecycle_events_distinguish_connected_destroyed_and_interrupted_states() {
        val connected = reduceAccessibilityDiagnostics(
            AccessibilityDiagnosticsState(),
            AccessibilityDiagnosticsEvent.ServiceConnected(1_000L)
        )
        val interrupted = reduceAccessibilityDiagnostics(
            connected,
            AccessibilityDiagnosticsEvent.ServiceInterrupted(2_000L)
        )
        val destroyed = reduceAccessibilityDiagnostics(
            interrupted,
            AccessibilityDiagnosticsEvent.ServiceDestroyed(3_000L)
        )

        assertTrue(connected.serviceBound)
        assertEquals(1_000L, connected.lastConnectedAtMillis)
        assertTrue(interrupted.serviceBound)
        assertEquals(2_000L, interrupted.lastInterruptedAtMillis)
        assertFalse(destroyed.serviceBound)
        assertEquals(3_000L, destroyed.lastDestroyedAtMillis)
    }

    @Test
    fun package_update_records_only_the_first_launch_for_each_installed_build() {
        val firstLaunch = reduceAccessibilityDiagnostics(
            AccessibilityDiagnosticsState(),
            AccessibilityDiagnosticsEvent.AppLaunched(
                packageLastUpdateTimeMillis = 10_000L,
                launchedAtMillis = 11_000L
            )
        )
        val sameBuildLaunch = reduceAccessibilityDiagnostics(
            firstLaunch,
            AccessibilityDiagnosticsEvent.AppLaunched(
                packageLastUpdateTimeMillis = 10_000L,
                launchedAtMillis = 12_000L
            )
        )
        val updatedBuildLaunch = reduceAccessibilityDiagnostics(
            sameBuildLaunch,
            AccessibilityDiagnosticsEvent.AppLaunched(
                packageLastUpdateTimeMillis = 20_000L,
                launchedAtMillis = 21_000L
            )
        )

        assertEquals(11_000L, firstLaunch.firstLaunchAfterUpdateAtMillis)
        assertEquals(firstLaunch, sameBuildLaunch)
        assertEquals(20_000L, updatedBuildLaunch.packageLastUpdateTimeMillis)
        assertEquals(21_000L, updatedBuildLaunch.firstLaunchAfterUpdateAtMillis)
    }
}
