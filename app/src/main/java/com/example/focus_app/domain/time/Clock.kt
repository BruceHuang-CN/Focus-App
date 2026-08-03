package com.example.focus_app.domain.time

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

interface Clock {
    fun nowMillis(): Long

    fun now(zoneId: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
        Instant.ofEpochMilli(nowMillis()).atZone(zoneId)
}

object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
