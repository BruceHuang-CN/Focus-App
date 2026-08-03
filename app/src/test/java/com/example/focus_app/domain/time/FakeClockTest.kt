package com.example.focus_app.domain.time

import org.junit.Assert.assertEquals
import org.junit.Test

class FakeClockTest {
    @Test
    fun advances_without_waiting() {
        val clock = FakeClock(1_000L)

        clock.epochMillis += 2_500L

        assertEquals(3_500L, clock.nowMillis())
    }
}

class FakeClock(var epochMillis: Long) : Clock {
    override fun nowMillis(): Long = epochMillis
}
