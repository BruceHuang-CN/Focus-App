package com.example.focus_app.domain.task

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    @Test
    fun zero_completions_means_zero_streak() = runTest {
        val streak = StreakCalculator.streakDays(LocalDate.of(2026, 8, 4)) { 0 }
        assertEquals(0, streak)
    }

    @Test
    fun consecutive_completion_days_count() = runTest {
        val today = LocalDate.of(2026, 8, 4)
        val streak = StreakCalculator.streakDays(today) { day ->
            if (day >= LocalDate.of(2026, 8, 2)) 1 else 0
        }
        assertEquals(3, streak)
    }

    @Test
    fun gap_before_today_breaks_streak() = runTest {
        val today = LocalDate.of(2026, 8, 4)
        val streak = StreakCalculator.streakDays(today) { day ->
            if (day == today || day == today.minusDays(1)) 1 else 0
        }
        assertEquals(2, streak)
    }

    @Test
    fun today_without_completion_still_counts_yesterday() = runTest {
        val today = LocalDate.of(2026, 8, 4)
        val streak = StreakCalculator.streakDays(today) { day ->
            if (day == today.minusDays(1) || day == today.minusDays(2)) 1 else 0
        }
        assertEquals(0, streak)
    }
}
