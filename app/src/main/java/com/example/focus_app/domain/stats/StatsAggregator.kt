package com.example.focus_app.domain.stats

import com.example.focus_app.domain.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Accumulate milliseconds first; truncate once at each displayed bucket, never per session. */
object StatsAggregator {
    fun aggregate(sessions: List<AppUsageSession>, rangeStart: Long, rangeEnd: Long,
        zone: ZoneId, nowMillis: Long = System.currentTimeMillis()): FocusStats {
        val endLimit = minOf(rangeEnd, nowMillis)
        val opened = sessions.filter { it.startedAt >= rangeStart && it.startedAt < endLimit }
        val inRange = sessions.filter { it.startedAt < endLimit && (it.endedAt ?: endLimit) > rangeStart }
        val hours = LongArray(24)
        val hourOpens = IntArray(24)
        val hourApps = List(24) { linkedMapOf<String, Long>() }
        val days = linkedMapOf<LocalDate, Long>()
        val dayOpens = linkedMapOf<LocalDate, Int>()
        val appMillis = linkedMapOf<String, Long>()
        val appNames = linkedMapOf<String, String>()
        var total = 0L
        opened.forEach { session ->
            val dateTime = Instant.ofEpochMilli(session.startedAt).atZone(zone)
            hourOpens[dateTime.hour]++
            dayOpens[dateTime.toLocalDate()] = dayOpens.getOrDefault(dateTime.toLocalDate(), 0) + 1
        }
        for (session in inRange) {
            val start = maxOf(session.startedAt, rangeStart)
            val end = minOf(session.endedAt ?: endLimit, endLimit)
            if (end <= start) continue
            total += end - start
            appMillis[session.packageName] = appMillis.getOrDefault(session.packageName, 0L) + end - start
            appNames[session.packageName] = session.appName
            var cursor = start
            while (cursor < end) {
                val local = Instant.ofEpochMilli(cursor).atZone(zone)
                // Use local hour boundaries; UTC-hour modulo is wrong for e.g. Asia/Kolkata.
                val nextHour = local.toLocalDateTime().truncatedTo(ChronoUnit.HOURS).plusHours(1).atZone(zone).toInstant().toEpochMilli()
                val nextDay = local.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                // Split at offset transitions too, including 30-minute DST changes.
                val transition = zone.rules.nextTransition(local.toInstant())?.instant?.toEpochMilli() ?: Long.MAX_VALUE
                val segmentEnd = minOf(end, nextHour, nextDay, transition)
                val duration = segmentEnd - cursor
                hours[local.hour] += duration
                hourApps[local.hour][session.appName] = hourApps[local.hour].getOrDefault(session.appName, 0L) + duration
                days[local.toLocalDate()] = days.getOrDefault(local.toLocalDate(), 0L) + duration
                cursor = segmentEnd
            }
        }
        var date = Instant.ofEpochMilli(rangeStart).atZone(zone).toLocalDate()
        val lastDate = Instant.ofEpochMilli(maxOf(rangeStart, rangeEnd - 1)).atZone(zone).toLocalDate()
        while (date <= lastDate) { days.putIfAbsent(date, 0L); date = date.plusDays(1) }
        val exits = opened.count { it.userAction in setOf("returned_to_focus", "returned_home") }
        return FocusStats(
            totalDurationMinutes = minutes(total), openCount = opened.size,
            remindedCount = opened.count { it.remindedAt != null && it.remindedAt <= endLimit },
            activeExitCount = exits, continuedCount = opened.count { it.userAction == "continued" },
            exitRate = if (opened.isEmpty()) 0f else exits.toFloat() / opened.size,
            hourly = (0..23).map { hour -> HourBucket(hour, minutes(hours[hour]), hourOpens[hour],
                hourApps[hour].map { (name, duration) -> HourAppUsage(name, minutes(duration)) }.sortedByDescending { it.durationMinutes }) },
            daily = days.map { (day, duration) -> DayBucket(day, minutes(duration), dayOpens[day] ?: 0) }.sortedBy { it.date },
            byApp = appMillis.map { (pkg, duration) -> AppShare(pkg, appNames.getValue(pkg), minutes(duration)) }.sortedByDescending { it.durationMinutes }
        )
    }
    private fun minutes(millis: Long) = (millis.coerceAtLeast(0L) / 60_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
