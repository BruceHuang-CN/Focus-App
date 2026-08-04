package com.example.focus_app.domain.stats

import com.example.focus_app.domain.model.AppShare
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.model.DayBucket
import com.example.focus_app.domain.model.FocusStats
import com.example.focus_app.domain.model.HourBucket
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 将会话记录聚合为统计结果。
 * - 只统计 startedAt 落在所选范围内的会话；
 * - 跨午夜/跨小时的会话按小时和本地日期拆分时长；
 * - 小时与日期都补零，避免图表缺段；
 * - 打开次数按会话计入其开始的小时与日期。
 */
object StatsAggregator {

    fun aggregate(
        sessions: List<AppUsageSession>,
        rangeStart: Long,
        rangeEnd: Long,
        zone: ZoneId
    ): FocusStats {
        val inRange = sessions.filter { it.startedAt in rangeStart until rangeEnd }
        val openCount = inRange.size
        val remindedCount = inRange.count { it.remindedAt != null }
        val activeExitCount = inRange.count { it.userAction in ACTIVE_EXIT_ACTIONS }
        val continuedCount = inRange.count { it.userAction == "continued" }

        val hourly = MutableList(24) { HourBucket(it, 0, 0) }
        val daily = LinkedHashMap<LocalDate, DayBucket>()
        val byApp = LinkedHashMap<String, AppShare>()
        var totalMinutes = 0

        for (session in inRange) {
            val start = maxOf(session.startedAt, rangeStart)
            val end = minOf(session.endedAt ?: rangeEnd, rangeEnd)
            if (end <= start) continue

            val startInstant = Instant.ofEpochMilli(start).atZone(zone)
            val startHour = startInstant.hour
            val startDay = startInstant.toLocalDate()

            hourly[startHour] = hourly[startHour].copy(openCount = hourly[startHour].openCount + 1)
            val dayBucket = daily.getOrPut(startDay) { DayBucket(startDay, 0, 0) }
            daily[startDay] = dayBucket.copy(openCount = dayBucket.openCount + 1)

            var cursor = start
            while (cursor < end) {
                val nextHour = cursor - cursor % HOUR_MILLIS + HOUR_MILLIS
                val segmentEnd = minOf(end, nextHour)
                val minutes = ((segmentEnd - cursor) / MINUTE_MILLIS).toInt()
                val hour = Instant.ofEpochMilli(cursor).atZone(zone).hour
                hourly[hour] = hourly[hour].copy(durationMinutes = hourly[hour].durationMinutes + minutes)
                val day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
                val existing = daily.getOrPut(day) { DayBucket(day, 0, 0) }
                daily[day] = existing.copy(durationMinutes = existing.durationMinutes + minutes)
                cursor = segmentEnd
            }

            totalMinutes += ((end - start) / MINUTE_MILLIS).toInt()
            val app = byApp.getOrPut(session.packageName) {
                AppShare(session.packageName, session.appName, 0)
            }
            byApp[session.packageName] = app.copy(
                durationMinutes = app.durationMinutes + ((end - start) / MINUTE_MILLIS).toInt()
            )
        }

        val startDate = Instant.ofEpochMilli(rangeStart).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(rangeEnd).atZone(zone).toLocalDate()
        var date = startDate
        while (date.isBefore(endDate)) {
            daily.getOrPut(date) { DayBucket(date, 0, 0) }
            date = date.plusDays(1)
        }

        return FocusStats(
            totalDurationMinutes = totalMinutes,
            openCount = openCount,
            remindedCount = remindedCount,
            activeExitCount = activeExitCount,
            continuedCount = continuedCount,
            exitRate = if (openCount > 0) activeExitCount.toFloat() / openCount else 0f,
            hourly = hourly,
            daily = daily.values.sortedBy { it.date },
            byApp = byApp.values.sortedByDescending { it.durationMinutes }
        )
    }

    private const val HOUR_MILLIS = 3_600_000L
    private const val MINUTE_MILLIS = 60_000L

    private val ACTIVE_EXIT_ACTIONS = setOf("returned_to_focus", "returned_home")
}
