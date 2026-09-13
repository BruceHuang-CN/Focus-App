package com.example.focus_app.domain.stats

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReminderStatisticsTest {
    @Test fun weekly_view_can_cross_year_boundary() {
        assertEquals(LocalDate.of(2025, 12, 29) to LocalDate.of(2026, 1, 5),
            ReminderStatistics.bounds(LocalDate.of(2026, 1, 1), ActivityPeriod.WEEK))
    }
    @Test fun leap_february_and_year_include_february_29() {
        val month = ReminderStatistics.bounds(LocalDate.of(2024, 2, 15), ActivityPeriod.MONTH)
        assertEquals(29, java.time.temporal.ChronoUnit.DAYS.between(month.first, month.second).toInt())
        val year = ReminderStatistics.bounds(LocalDate.of(2024, 8, 1), ActivityPeriod.YEAR)
        assertEquals(366, java.time.temporal.ChronoUnit.DAYS.between(year.first, year.second).toInt())
    }
    @Test fun maps_real_decisions_without_treating_missing_responses_as_continued_use() {
        val counts = ReminderStatistics.decisions(listOf("returned_home", "returned_to_custom", "returned_to_focus",
            "intentional_10m", "rest_5m", "snoozed_10m", "unknown", ""))
        assertEquals(3, counts.first { it.kind == DecisionKind.RETURN }.count)
        assertEquals(1, counts.first { it.kind == DecisionKind.INTENTIONAL }.count)
        assertEquals(1, counts.first { it.kind == DecisionKind.REST }.count)
        assertEquals(1, counts.first { it.kind == DecisionKind.LEGACY }.count)
        assertEquals(0, ReminderStatistics.decisions(emptyList()).sumOf { it.count })
    }
}
