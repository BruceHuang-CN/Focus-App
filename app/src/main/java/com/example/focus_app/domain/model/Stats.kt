package com.example.focus_app.domain.model

import java.time.LocalDate

/** 统计时间范围。 */
enum class StatsRange(val key: String, val label: String) {
    TODAY("today", "今天"),
    WEEK("week", "最近 7 天"),
    MONTH("month", "最近 30 天")
}

data class HourBucket(
    val hour: Int,
    val durationMinutes: Int,
    val openCount: Int
)

data class DayBucket(
    val date: LocalDate,
    val durationMinutes: Int,
    val openCount: Int
)

data class AppShare(
    val packageName: String,
    val appName: String,
    val durationMinutes: Int
)

data class FocusStats(
    val totalDurationMinutes: Int,
    val openCount: Int,
    val remindedCount: Int,
    val activeExitCount: Int,
    val continuedCount: Int,
    val exitRate: Float,
    val hourly: List<HourBucket>,
    val daily: List<DayBucket>,
    val byApp: List<AppShare>
)
