package com.example.focus_app.domain.summary

import java.time.LocalDate
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class DailySummaryTest {
    @Test fun before_selected_time_runs_today() {
        assertEquals(ZonedDateTime.parse("2026-09-19T21:15:00+08:00[Asia/Shanghai]"),
            nextSummaryTime(ZonedDateTime.parse("2026-09-19T20:00:00+08:00[Asia/Shanghai]"), 21 * 60 + 15))
    }
    @Test fun at_or_after_selected_time_runs_tomorrow() {
        val now = ZonedDateTime.parse("2026-09-19T21:15:00+08:00[Asia/Shanghai]")
        assertEquals(now.plusDays(1), nextSummaryTime(now, 21 * 60 + 15))
    }
    @Test fun midnight_rolls_over_year_without_fixed_day_milliseconds() {
        assertEquals("2027-01-01T00:00+08:00[Asia/Shanghai]",
            nextSummaryTime(ZonedDateTime.parse("2026-12-31T23:59:00+08:00[Asia/Shanghai]"), 0).toString())
    }
    @Test fun spring_clock_change_resolves_nonexistent_local_time() {
        assertEquals("2026-03-08T03:30-04:00[America/New_York]",
            nextSummaryTime(ZonedDateTime.parse("2026-03-07T12:00:00-05:00[America/New_York]"), 150).toString())
    }
    @Test fun tomorrow_keeps_wall_clock_time_across_dst() {
        val next = nextSummaryTime(ZonedDateTime.parse("2026-10-31T22:00:00-04:00[America/New_York]"), 21 * 60)
        assertEquals("2026-11-01T21:00-05:00[America/New_York]", next.toString())
    }
    @Test fun cancelled_changed_and_old_day_work_cannot_generate_or_notify() {
        val today = LocalDate.parse("2026-09-19")
        val settings = DailySummarySettings(true, 1260, 8)
        assertTrue(summaryWorkIsCurrent(settings, 8, "2026-09-19", today))
        assertFalse(summaryWorkIsCurrent(settings.copy(enabled = false), 8, "2026-09-19", today))
        assertFalse(summaryWorkIsCurrent(settings, 7, "2026-09-19", today))
        assertFalse(summaryWorkIsCurrent(settings, 8, "2026-09-18", today))
    }
    @Test fun prompt_reports_real_metrics_without_claiming_focus_duration() {
        val message = summaryUserMessage(DailySummaryFacts("2026-09-19", 3, 12, 4, null))
        assertTrue(message.contains("Completed tasks: 3"))
        assertTrue(message.contains("NOT focus duration"))
        assertTrue(message.contains("not recorded"))
        assertTrue(summarySystemMessage("en-US").contains("English"))
        assertTrue(summarySystemMessage("zh-CN").contains("Simplified Chinese"))
    }
}
