package com.example.focus_app.ui.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderUrgencyTest {
    @Test
    fun every_available_reminder_fills_the_screen() {
        listOf(1, 2, 4).forEach { count ->
            val urgency = reminderUrgency(windowReminderCount = count, windowLimit = 5)

            assertEquals(1f, urgency.widthFraction, 0.0001f)
            assertEquals(1f, urgency.heightFraction, 0.0001f)
        }
    }

    @Test
    fun final_reminder_still_has_final_flag() {
        val urgency = reminderUrgency(windowReminderCount = 5, windowLimit = 5)

        assertTrue(urgency.isFinalReminder)
    }

    @Test
    fun missing_quota_is_still_full_screen_without_final_flag() {
        val urgency = reminderUrgency(windowReminderCount = 0, windowLimit = 0)

        assertEquals(1f, urgency.widthFraction, 0.0001f)
        assertEquals(1f, urgency.heightFraction, 0.0001f)
        assertFalse(urgency.isFinalReminder)
    }
}
