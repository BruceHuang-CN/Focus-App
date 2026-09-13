package com.example.focus_app.domain.stats

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

enum class ActivityPeriod(val label: String) { WEEK("周"), MONTH("月"), YEAR("年") }
enum class DecisionKind(val label: String) {
    RETURN("回到任务"), INTENTIONAL("有目的使用"), REST("休息一下"), LEGACY("稍后提醒／旧操作")
}
data class DecisionCount(val kind: DecisionKind, val count: Int)
data class ActivityDay(val date: LocalDate, val count: Int, val future: Boolean, val historyIncomplete: Boolean)
object ReminderStatistics {
    fun classify(action: String): DecisionKind? = when {
        action in setOf("returned_to_focus", "returned_home", "returned_to_custom") -> DecisionKind.RETURN
        action.startsWith("intentional_") -> DecisionKind.INTENTIONAL
        action.startsWith("rest_") -> DecisionKind.REST
        action.startsWith("snoozed") || action == "continued" -> DecisionKind.LEGACY
        else -> null
    }
    fun bounds(anchor: LocalDate, period: ActivityPeriod): Pair<LocalDate, LocalDate> {
        val start = when (period) {
            ActivityPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            ActivityPeriod.MONTH -> anchor.withDayOfMonth(1)
            ActivityPeriod.YEAR -> anchor.withDayOfYear(1)
        }
        return start to when (period) {
            ActivityPeriod.WEEK -> start.plusWeeks(1)
            ActivityPeriod.MONTH -> start.plusMonths(1)
            ActivityPeriod.YEAR -> start.plusYears(1)
        }
    }
    fun shiftedAnchor(anchor: LocalDate, period: ActivityPeriod, direction: Int): LocalDate {
        val start = bounds(anchor, period).first
        return when (period) {
            ActivityPeriod.WEEK -> start.plusWeeks(direction.toLong())
            ActivityPeriod.MONTH -> start.plusMonths(direction.toLong())
            ActivityPeriod.YEAR -> start.plusYears(direction.toLong())
        }
    }
    fun weekColumn(firstMonday: LocalDate, date: LocalDate): Int =
        (java.time.temporal.ChronoUnit.DAYS.between(firstMonday, date) / 7).toInt()

    fun decisions(actions: List<String>): List<DecisionCount> = DecisionKind.entries.map { kind ->
        DecisionCount(kind, actions.count { classify(it) == kind })
    }
}
