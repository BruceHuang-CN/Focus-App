package com.example.focus_app.ui.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderUrgencyTest {
    @Test
    fun reminder_surface_grows_after_each_consumed_quota() {
        val first = reminderUrgency(windowReminderCount = 1, windowLimit = 5)
        val second = reminderUrgency(windowReminderCount = 2, windowLimit = 5)
        val fourth = reminderUrgency(windowReminderCount = 4, windowLimit = 5)

        assertTrue(second.widthFraction > first.widthFraction)
        assertTrue(second.heightFraction > first.heightFraction)
        assertTrue(fourth.widthFraction > second.widthFraction)
        assertTrue(fourth.heightFraction > second.heightFraction)
        assertFalse(fourth.isFinalReminder)
    }

    @Test
    fun final_available_reminder_fills_the_screen() {
        val urgency = reminderUrgency(windowReminderCount = 5, windowLimit = 5)

        assertEquals(1f, urgency.widthFraction, 0.0001f)
        assertEquals(1f, urgency.heightFraction, 0.0001f)
        assertTrue(urgency.isFinalReminder)
    }

    @Test
    fun invalid_or_missing_quota_uses_the_smallest_safe_surface() {
        val urgency = reminderUrgency(windowReminderCount = 0, windowLimit = 0)

        assertEquals(0.82f, urgency.widthFraction, 0.0001f)
        assertEquals(0.50f, urgency.heightFraction, 0.0001f)
        assertFalse(urgency.isFinalReminder)
    }
}
