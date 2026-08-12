package com.example.focus_app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPresentationRegistryTest {
    @Test
    fun forced_reminder_survives_activity_destruction_without_an_explicit_action() {
        val registry = ReminderPresentationRegistry()

        registry.show(sessionId = 9L, forceReminder = true)
        registry.onActivityDestroyed(9L)

        assertTrue(registry.isShowing())
    }

    @Test
    fun normal_reminder_is_cleared_when_its_activity_is_destroyed() {
        val registry = ReminderPresentationRegistry()

        registry.show(sessionId = 9L, forceReminder = false)
        registry.onActivityDestroyed(9L)

        assertFalse(registry.isShowing())
    }

    @Test
    fun snoozed_reminder_keeps_session_protection_through_the_return_transition() {
        var elapsedRealtime = 1_000L
        val registry = ReminderPresentationRegistry { elapsedRealtime }

        registry.show(sessionId = 9L, forceReminder = false)
        registry.keepSnoozeTransition(sessionId = 9L)
        registry.onActivityDestroyed(9L)

        assertTrue(registry.isShowing())

        elapsedRealtime += 8_001L

        assertFalse(registry.isShowing())
    }
}
