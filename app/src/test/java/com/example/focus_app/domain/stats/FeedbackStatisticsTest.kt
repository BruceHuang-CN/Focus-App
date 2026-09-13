package com.example.focus_app.domain.stats
import com.example.focus_app.domain.model.AppUsageSession
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.time.temporal.TemporalAdjusters

class FeedbackStatisticsTest {
    private fun session(id: Long, start: Long, end: Long?) = AppUsageSession(id = id,
        packageName = "target", appName = "目标", startedAt = start, endedAt = end, taskId = null, toneKey = "gentle")
    @Test fun short_opens_accumulate_before_rounding() {
        val start = Instant.parse("2026-09-10T00:00:00Z").toEpochMilli()
        val sessions = (0L..9L).map { session(it, start + it * 6000, start + (it + 1) * 6000) }
        val stats = StatsAggregator.aggregate(sessions, start, start + 3600000, ZoneOffset.UTC, start + 60000)
        assertEquals(10, stats.openCount)
        assertEquals(1, stats.totalDurationMinutes)
        assertEquals(1, stats.hourly[0].durationMinutes)
        assertEquals(1, stats.byApp.single().durationMinutes)
    }
    @Test fun ongoing_session_stops_at_as_of_not_tomorrow() {
        val start = Instant.parse("2026-09-10T00:00:00Z").toEpochMilli()
        val stats = StatsAggregator.aggregate(listOf(session(1, start, null)), start, start + 86400000, ZoneOffset.UTC, start + 90000)
        assertEquals(1, stats.totalDurationMinutes)
    }
    @Test fun fractional_timezone_splits_at_local_midnight() {
        val zone = ZoneId.of("Asia/Kolkata")
        val midnight = LocalDate.of(2026, 9, 10).atStartOfDay(zone).toInstant().toEpochMilli()
        val stats = StatsAggregator.aggregate(listOf(session(1, midnight - 600000, midnight + 600000)),
            midnight - 3600000, midnight + 3600000, zone, midnight + 3600000)
        assertEquals(listOf(10, 10), stats.daily.map { it.durationMinutes })
        assertEquals(10, stats.hourly[23].durationMinutes)
        assertEquals(10, stats.hourly[0].durationMinutes)
    }
    @Test fun dst_repeated_hour_counts_elapsed_time_once() {
        val zone = ZoneId.of("America/New_York")
        val start = Instant.parse("2026-11-01T05:30:00Z").toEpochMilli()
        val end = Instant.parse("2026-11-01T07:30:00Z").toEpochMilli()
        val stats = StatsAggregator.aggregate(listOf(session(1,start,end)),start,end,zone,end)
        assertEquals(120,stats.totalDurationMinutes)
        assertEquals(90,stats.hourly[1].durationMinutes)
        assertEquals(30,stats.hourly[2].durationMinutes)
    }
    @Test fun half_hour_dst_fallback_advances_and_splits_the_hour() {
        val zone = ZoneId.of("Australia/Lord_Howe")
        val start = Instant.parse("2026-04-04T14:45:00Z").toEpochMilli()
        val end = Instant.parse("2026-04-04T15:45:00Z").toEpochMilli()
        val stats = StatsAggregator.aggregate(listOf(session(1,start,end)),start,end,zone,end)
        assertEquals(60,stats.totalDurationMinutes)
        assertEquals(45,stats.hourly[1].durationMinutes)
        assertEquals(15,stats.hourly[2].durationMinutes)
    }
    @Test fun month_navigation_does_not_drift_from_january_31() {
        val feb = ReminderStatistics.shiftedAnchor(LocalDate.of(2026,1,31),ActivityPeriod.MONTH,1)
        assertEquals(LocalDate.of(2026,2,1),feb)
        assertEquals(LocalDate.of(2026,1,1),ReminderStatistics.shiftedAnchor(feb,ActivityPeriod.MONTH,-1))
    }
    @Test fun october_and_december_labels_use_the_same_columns_as_their_days() {
        for (year in 2024..2028) {
            val first = LocalDate.of(year,1,1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            for (month in listOf(10,12)) {
                val date=LocalDate.of(year,month,1)
                val column=ReminderStatistics.weekColumn(first,date)
                assertEquals(date,first.plusDays(column * 7L + date.dayOfWeek.value - 1))
            }
        }
    }
}
