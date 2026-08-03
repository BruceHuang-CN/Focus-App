package com.example.focus_app.domain.reminder

import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.domain.time.FakeClock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPolicyTest {
    private val clock = FakeClock(3_600_000L)
    private val policy = ReminderPolicy(clock)
    private val settings = AppSettings(
        reminderWindowMinutes = 60,
        maxRemindersPerWindow = 3
    )

    @Test
    fun blocks_when_three_reminders_are_inside_window() {
        assertFalse(policy.canShow(listOf(1_000L, 2_000L, 3_000L), settings))
    }

    @Test
    fun allows_when_oldest_reminder_leaves_rolling_window() {
        assertTrue(policy.canShow(listOf(-1L, 2_000L, 3_000L), settings))
    }

    @Test
    fun counts_reminder_at_window_boundary() {
        assertFalse(policy.canShow(listOf(0L, 2_000L, 3_000L), settings))
    }
}
