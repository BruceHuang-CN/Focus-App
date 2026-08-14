package com.example.focus_app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundObservationCursorTest {
    @Test
    fun delayed_event_inside_overlap_is_published_once() {
        val cursor = ForegroundObservationCursor(overlapMillis = 30_000L)
        val event = ForegroundObservation("com.xingin.xhs", 95_000L)

        assertEquals(70_000L, cursor.queryStart(100_000L))
        assertEquals(event, cursor.takeIfNew(event))
        assertNull(cursor.takeIfNew(event))
    }

    @Test
    fun newer_package_switch_is_published() {
        val cursor = ForegroundObservationCursor(overlapMillis = 30_000L)
        cursor.takeIfNew(ForegroundObservation("com.xingin.xhs", 95_000L))

        assertEquals(
            "com.ss.android.ugc.aweme",
            cursor.takeIfNew(
                ForegroundObservation("com.ss.android.ugc.aweme", 101_000L)
            )?.packageName
        )
    }

    @Test
    fun older_delayed_event_does_not_move_foreground_backwards() {
        val cursor = ForegroundObservationCursor(overlapMillis = 30_000L)
        cursor.takeIfNew(ForegroundObservation("com.ss.android.ugc.aweme", 101_000L))

        assertNull(cursor.takeIfNew(ForegroundObservation("com.xingin.xhs", 95_000L)))
    }
}
