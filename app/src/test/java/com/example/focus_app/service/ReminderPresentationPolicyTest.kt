package com.example.focus_app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderPresentationPolicyTest {
    @Test
    fun missing_overlay_permission_posts_notification_without_starting_activity() {
        var activityStarts = 0
        var notifications = 0

        presentReminder(
            canDrawOverlays = false,
            startActivity = { activityStarts++ },
            postNotification = { notifications++ }
        )

        assertEquals(0, activityStarts)
        assertEquals(1, notifications)
    }

    @Test
    fun overlay_permission_allows_direct_reminder_activity() {
        var activityStarts = 0
        var notifications = 0

        presentReminder(
            canDrawOverlays = true,
            startActivity = { activityStarts++ },
            postNotification = { notifications++ }
        )

        assertEquals(1, activityStarts)
        assertEquals(0, notifications)
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
