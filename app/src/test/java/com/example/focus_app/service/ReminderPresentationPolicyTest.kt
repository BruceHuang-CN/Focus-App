package com.example.focus_app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPresentationPolicyTest {
    @Test
    fun single_instance_activity_restarts_display_only_for_a_new_attempt() {
        assertTrue(isNewReminderAttempt("attempt-1", "attempt-2"))
        assertFalse(isNewReminderAttempt("attempt-1", "attempt-1"))
        assertFalse(isNewReminderAttempt("attempt-1", ""))
    }

    @Test
    fun confirmation_result_only_applies_to_the_current_resumed_attempt() {
        assertTrue(
            shouldApplyReminderConfirmation(
                expectedAttemptId = "attempt-2",
                currentAttemptId = "attempt-2",
                registryAttemptIsCurrent = true,
                isActivityResumed = true
            )
        )
        assertFalse(
            shouldApplyReminderConfirmation(
                expectedAttemptId = "attempt-1",
                currentAttemptId = "attempt-2",
                registryAttemptIsCurrent = true,
                isActivityResumed = true
            )
        )
        assertFalse(
            shouldApplyReminderConfirmation(
                expectedAttemptId = "attempt-2",
                currentAttemptId = "attempt-2",
                registryAttemptIsCurrent = true,
                isActivityResumed = false
            )
        )
        assertFalse(
            shouldApplyReminderConfirmation(
                expectedAttemptId = "attempt-2",
                currentAttemptId = "attempt-2",
                registryAttemptIsCurrent = false,
                isActivityResumed = true
            )
        )
    }

    @Test
    fun missing_overlay_permission_posts_notification_without_starting_activity() {
        var activityStarts = 0
        var notifications = 0

        val activityRequested = presentReminder(
            canDrawOverlays = false,
            startActivity = { activityStarts++ },
            postNotification = { notifications++ }
        )

        assertFalse(activityRequested)
        assertEquals(0, activityStarts)
        assertEquals(1, notifications)
    }

    @Test
    fun overlay_permission_shows_direct_reminder_and_posts_notification() {
        var activityStarts = 0
        var notifications = 0

        val activityRequested = presentReminder(
            canDrawOverlays = true,
            startActivity = { activityStarts++ },
            postNotification = { notifications++ }
        )

        assertTrue(activityRequested)
        assertEquals(1, activityStarts)
        assertEquals(1, notifications)
    }

    @Test
    fun failed_activity_start_falls_back_to_notification() {
        var notifications = 0

        val activityRequested = presentReminder(
            canDrawOverlays = true,
            startActivity = { error("start failed") },
            postNotification = { notifications++ }
        )

        assertFalse(activityRequested)
        assertEquals(1, notifications)
    }

    @Test
    fun overlay_permission_marks_presentation_before_starting_reminder_activity() {
        val events = mutableListOf<String>()

        presentReminder(
            canDrawOverlays = true,
            onBeforeStartActivity = { events += "mark" },
            startActivity = { events += "activity" },
            postNotification = { events += "notification" }
        )

        assertEquals(listOf("mark", "activity", "notification"), events)
    }

    @Test
    fun reminder_is_only_valid_for_its_current_open_session() {
        assertEquals(true, isReminderSessionCurrent(7L, 7L))
        assertEquals(false, isReminderSessionCurrent(7L, null))
        assertEquals(false, isReminderSessionCurrent(7L, 8L))
        assertEquals(false, isReminderSessionCurrent(0L, 0L))
    }

    @Test
    fun pending_reminder_protects_any_temporary_foreground_window() {
        assertEquals(true, isReminderPresentationForForegroundChange(true))
        assertEquals(false, isReminderPresentationForForegroundChange(false))
    }
}
