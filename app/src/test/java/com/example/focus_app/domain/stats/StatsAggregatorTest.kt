package com.example.focus_app.domain.stats

import com.example.focus_app.domain.model.AppUsageSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class StatsAggregatorTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun empty_sessions_produce_zero_filled_buckets() {
        val today = epoch(2026, 8, 4, 0, 0)
        val tomorrow = today + DAY_MILLIS

        val stats = StatsAggregator.aggregate(emptyList(), today, tomorrow, zone)

        assertEquals(0, stats.openCount)
        assertEquals(0, stats.totalDurationMinutes)
        assertEquals(0f, stats.exitRate, 0.001f)
        assertEquals(24, stats.hourly.size)
        assertTrue(stats.hourly.all { it.durationMinutes == 0 && it.openCount == 0 })
        assertEquals(1, stats.daily.size)
        assertEquals(0, stats.daily.first().durationMinutes)
        assertTrue(stats.byApp.isEmpty())
    }

    @Test
    fun session_within_range_counts_all_metrics() {
        val today = epoch(2026, 8, 4, 0, 0)
        val tomorrow = today + DAY_MILLIS
        val session = session(
            id = 1,
            startedAt = epoch(2026, 8, 4, 10, 15),
            endedAt = epoch(2026, 8, 4, 10, 45),
            remindedAt = epoch(2026, 8, 4, 10, 20),
            userAction = "returned_to_focus"
        )

        val stats = StatsAggregator.aggregate(listOf(session), today, tomorrow, zone)

        assertEquals(1, stats.openCount)
        assertEquals(1, stats.remindedCount)
        assertEquals(1, stats.activeExitCount)
        assertEquals(0, stats.continuedCount)
        assertEquals(30, stats.totalDurationMinutes)
        assertEquals(1f, stats.exitRate, 0.001f)
        assertEquals(30, stats.hourly[10].durationMinutes)
        assertEquals(1, stats.hourly[10].openCount)
        assertEquals(30, stats.daily.single { it.date.dayOfMonth == 4 }.durationMinutes)
        assertEquals(30, stats.byApp.single().durationMinutes)
    }

    @Test
    fun midnight_session_splits_duration_across_days_and_hours() {
        val today = epoch(2026, 8, 4, 0, 0)
        val rangeEnd = epoch(2026, 8, 5, 1, 0)
        val session = session(
            id = 2,
            startedAt = epoch(2026, 8, 4, 23, 30),
            endedAt = epoch(2026, 8, 5, 0, 30)
        )

        val stats = StatsAggregator.aggregate(listOf(session), today, rangeEnd, zone)

        assertEquals(60, stats.totalDurationMinutes)
        assertEquals(30, stats.hourly[23].durationMinutes)
        assertEquals(30, stats.hourly[0].durationMinutes)
        val day4 = stats.daily.single { it.date.dayOfMonth == 4 }
        val day5 = stats.daily.single { it.date.dayOfMonth == 5 }
        assertEquals(30, day4.durationMinutes)
        assertEquals(30, day5.durationMinutes)
    }

    @Test
    fun sessions_started_outside_range_are_ignored() {
        val today = epoch(2026, 8, 4, 0, 0)
        val tomorrow = today + DAY_MILLIS
        val outside = session(id = 3, startedAt = epoch(2026, 8, 3, 23, 0), endedAt = epoch(2026, 8, 3, 23, 30))

        val stats = StatsAggregator.aggregate(listOf(outside), today, tomorrow, zone)

        assertEquals(0, stats.openCount)
        assertEquals(0, stats.totalDurationMinutes)
    }

    @Test
    fun by_app_is_sorted_by_duration_descending() {
        val today = epoch(2026, 8, 4, 0, 0)
        val tomorrow = today + DAY_MILLIS
        val short = session(id = 4, packageName = "com.b", appName = "B站", startedAt = epoch(2026, 8, 4, 9, 0), endedAt = epoch(2026, 8, 4, 9, 10))
        val long = session(id = 5, packageName = "com.a", appName = "抖音", startedAt = epoch(2026, 8, 4, 10, 0), endedAt = epoch(2026, 8, 4, 11, 0))

        val stats = StatsAggregator.aggregate(listOf(short, long), today, tomorrow, zone)

        assertEquals(listOf("抖音", "B站"), stats.byApp.map { it.appName })
        assertEquals(60, stats.byApp[0].durationMinutes)
        assertEquals(10, stats.byApp[1].durationMinutes)
    }

    @Test
    fun hourly_buckets_break_down_apps_and_durations() {
        val today = epoch(2026, 8, 4, 0, 0)
        val tomorrow = today + DAY_MILLIS
        val douyin = session(
            id = 6,
            packageName = "com.a",
            appName = "抖音",
            startedAt = epoch(2026, 8, 4, 10, 0),
            endedAt = epoch(2026, 8, 4, 10, 20)
        )
        val bilibili = session(
            id = 7,
            packageName = "com.b",
            appName = "B站",
            startedAt = epoch(2026, 8, 4, 10, 10),
            endedAt = epoch(2026, 8, 4, 10, 15)
        )

        val stats = StatsAggregator.aggregate(listOf(douyin, bilibili), today, tomorrow, zone)
        val hour10 = stats.hourly[10]

        assertEquals(25, hour10.durationMinutes)
        assertEquals(2, hour10.openCount)
        assertEquals(listOf("抖音", "B站"), hour10.apps.map { it.appName })
        assertEquals(20, hour10.apps.first().durationMinutes)
    }

    private fun session(
        id: Long,
        packageName: String = "com.example.app",
        appName: String = "测试App",
        startedAt: Long,
        endedAt: Long,
        remindedAt: Long? = null,
        userAction: String? = null
    ) = AppUsageSession(
        id = id,
        packageName = packageName,
        appName = appName,
        startedAt = startedAt,
        endedAt = endedAt,
        remindedAt = remindedAt,
        userAction = userAction,
        toneKey = "gentle"
    )

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
