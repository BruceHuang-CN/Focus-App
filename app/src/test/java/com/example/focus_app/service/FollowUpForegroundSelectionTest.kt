package com.example.focus_app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class FollowUpForegroundSelectionTest {
    @Test
    fun fresh_realtime_snapshot_wins() {
        assertEquals(
            ForegroundSnapshot.Confirmed("target", ForegroundSource.REALTIME),
            selectForegroundSnapshot(
                cached = TimedForegroundPackage("target", 9_000L),
                nowElapsedRealtime = 10_000L,
                maxCacheAgeMillis = 3_000L,
                usagePackage = { "settings" }
            )
        )
    }

    @Test
    fun stale_realtime_snapshot_queries_usage_events() {
        assertEquals(
            ForegroundSnapshot.Confirmed("settings", ForegroundSource.USAGE_EVENTS),
            selectForegroundSnapshot(
                cached = TimedForegroundPackage("target", 1_000L),
                nowElapsedRealtime = 10_000L,
                maxCacheAgeMillis = 3_000L,
                usagePackage = { "settings" }
            )
        )
    }

    @Test
    fun stale_cache_and_missing_usage_event_is_unknown() {
        assertEquals(
            ForegroundSnapshot.Unknown,
            selectForegroundSnapshot(
                cached = TimedForegroundPackage("target", 1_000L),
                nowElapsedRealtime = 10_000L,
                maxCacheAgeMillis = 3_000L,
                usagePackage = { null }
            )
        )
    }
}
