package com.example.focus_app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnNavigationGuardTest {
    @Test fun navigation_and_main_page_block_external_events_but_real_reentry_is_allowed() {
        var now = 100L
        val guard = ReturnNavigationGuard { now }
        val activity = Any()
        guard.beginReturn()
        assertFalse(guard.allowsExternalEvent(101L))
        now = 200L
        guard.onMainResumed(activity)
        now = 10000L
        assertFalse(guard.allowsExternalEvent(now))
        guard.onMainPaused(activity)
        assertFalse(guard.allowsExternalEvent(150L))
        now++
        assertTrue(guard.allowsExternalEvent(now))
    }

    @Test fun old_activity_pause_cannot_unprotect_new_main_activity() {
        var now = 100L
        val guard = ReturnNavigationGuard { now }
        val oldActivity = Any()
        val newActivity = Any()
        guard.onMainResumed(oldActivity)
        guard.onMainResumed(newActivity)
        guard.onMainPaused(oldActivity)
        now++
        assertFalse(guard.allowsExternalEvent(now))
    }

    @Test fun failed_navigation_does_not_disable_guardian_indefinitely() {
        var now = 100L
        val guard = ReturnNavigationGuard { now }
        guard.beginReturn()
        now = 3101L
        assertTrue(guard.allowsExternalEvent(now))
        assertFalse(guard.allowsExternalEvent(99L))
    }
}
