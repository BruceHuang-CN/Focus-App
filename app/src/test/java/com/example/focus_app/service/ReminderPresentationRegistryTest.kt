package com.example.focus_app.service

import com.example.focus_app.data.repository.ReminderDisplayKind
import com.example.focus_app.domain.model.ReturnDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPresentationRegistryTest {
    @Test
    fun forced_reminder_becomes_pending_when_activity_stops_without_an_action() {
        val registry = ReminderPresentationRegistry { 0L }
        registry.show(FORCED_DATA)

        registry.onActivityStopped(FORCED_DATA.sessionId, FORCED_DATA.attemptId)

        assertFalse(registry.isVisible())
        assertTrue(registry.hasPendingDecision())
        assertTrue(registry.protectsSession())
    }

    @Test
    fun normal_reminder_is_cleared_when_activity_stops() {
        val registry = ReminderPresentationRegistry { 0L }
        registry.show(FORCED_DATA.copy(forceReminder = false))

        registry.onActivityStopped(FORCED_DATA.sessionId, FORCED_DATA.attemptId)

        assertFalse(registry.isVisible())
        assertFalse(registry.hasPendingDecision())
        assertFalse(registry.protectsSession())
    }

    @Test
    fun only_the_original_target_can_claim_one_redisplay_attempt() {
        val registry = ReminderPresentationRegistry { 0L }
        registry.show(FORCED_DATA)
        registry.onActivityStopped(FORCED_DATA.sessionId, FORCED_DATA.attemptId)

        assertNull(registry.prepareRedisplay("com.example.other", "attempt-2"))

        val redisplay = registry.prepareRedisplay(TARGET_PACKAGE, "attempt-2")

        assertEquals("attempt-2", redisplay?.attemptId)
        assertEquals(ReminderDisplayKind.FORCED_REDISPLAY, redisplay?.displayKind)
        assertFalse(registry.isVisible())
        assertTrue(registry.protectsSession())
        assertFalse(registry.hasPendingDecision())
        assertTrue(registry.confirmVisible(redisplay!!))
        assertTrue(registry.isVisible())
        assertNull(registry.prepareRedisplay(TARGET_PACKAGE, "attempt-3"))
    }

    @Test
    fun interrupted_launch_keeps_the_original_display_kind_until_first_real_draw() {
        val registry = ReminderPresentationRegistry { 0L }
        registry.markLaunchRequested(FORCED_DATA)
        registry.onActivityStopped(FORCED_DATA.sessionId, FORCED_DATA.attemptId)

        val redisplay = registry.prepareRedisplay(TARGET_PACKAGE, "attempt-2")

        assertEquals(ReminderDisplayKind.INITIAL, redisplay?.displayKind)
    }

    @Test
    fun explicit_action_clears_pending_redisplay() {
        val registry = ReminderPresentationRegistry { 0L }
        registry.show(FORCED_DATA)
        registry.onActivityStopped(FORCED_DATA.sessionId, FORCED_DATA.attemptId)

        registry.hide(FORCED_DATA.sessionId, FORCED_DATA.attemptId)

        assertFalse(registry.protectsSession())
        assertNull(registry.prepareRedisplay(TARGET_PACKAGE, "attempt-2"))
    }

    @Test
    fun snoozed_reminder_keeps_transition_protection_without_becoming_pending() {
        var elapsedRealtime = 1_000L
        val registry = ReminderPresentationRegistry { elapsedRealtime }
        registry.show(FORCED_DATA)

        registry.keepSnoozeTransition(FORCED_DATA.sessionId, FORCED_DATA.attemptId)
        registry.onActivityStopped(FORCED_DATA.sessionId, FORCED_DATA.attemptId)

        assertTrue(registry.protectsSession())
        assertFalse(registry.hasPendingDecision())

        elapsedRealtime += 8_001L

        assertFalse(registry.protectsSession())
    }

    @Test
    fun old_attempt_cannot_stop_or_hide_a_new_attempt() {
        val registry = ReminderPresentationRegistry { 0L }
        val newData = FORCED_DATA.copy(attemptId = "attempt-2")
        registry.show(newData)

        registry.onActivityStopped(FORCED_DATA.sessionId, FORCED_DATA.attemptId)
        registry.hide(FORCED_DATA.sessionId, FORCED_DATA.attemptId)

        assertTrue(registry.isVisible())
        assertTrue(registry.isCurrentAttempt(newData.sessionId, newData.attemptId))
        assertFalse(registry.confirmVisible(FORCED_DATA))
    }

    @Test
    fun abandoned_launch_returns_to_pending_after_the_grace_period() {
        var elapsedRealtime = 1_000L
        val registry = ReminderPresentationRegistry { elapsedRealtime }
        registry.show(FORCED_DATA)
        registry.onActivityStopped(FORCED_DATA.sessionId, FORCED_DATA.attemptId)

        val redisplay = registry.prepareRedisplay(TARGET_PACKAGE, "attempt-2")!!
        elapsedRealtime += 8_001L

        assertTrue(registry.hasPendingDecision())
        assertFalse(registry.isCurrentAttempt(redisplay.sessionId, "missing"))
    }

    private companion object {
        const val TARGET_PACKAGE = "com.example.target"
        val FORCED_DATA = ReminderLaunchData(
            sessionId = 9L,
            taskId = 42L,
            taskTitle = "Write proposal",
            appName = "Target",
            message = "Pause",
            showBreathing = false,
            returnDestination = ReturnDestination.FOCUS,
            targetPackageName = TARGET_PACKAGE,
            windowLimit = 3,
            windowMinutes = 30,
            forceReminder = true,
            attemptId = "attempt-1",
            displayKind = ReminderDisplayKind.INITIAL
        )
    }
}
