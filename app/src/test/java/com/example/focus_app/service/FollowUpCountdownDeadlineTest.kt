package com.example.focus_app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class FollowUpCountdownDeadlineTest {

    @Test
    fun five_minute_delay_maps_to_exact_wall_clock_deadline() {
        assertEquals(301_000L, followUpCountdownDueAt(nowMillis = 1_000L, delayMillis = 300_000L))
    }

    @Test
    fun negative_delay_maps_to_current_wall_clock_time() {
        assertEquals(1_000L, followUpCountdownDueAt(nowMillis = 1_000L, delayMillis = -1L))
    }
}
