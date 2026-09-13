package com.example.focus_app.data.repository

import com.example.focus_app.data.local.dao.ReminderAnalyticsDao
import com.example.focus_app.data.local.entity.*
import com.example.focus_app.domain.model.*
import com.example.focus_app.domain.stats.*
import java.time.*
import javax.inject.Inject
import kotlinx.coroutines.flow.*

data class StatisticsSnapshot(
    val usage: FocusStats,
    val coverage: Float?,
    val decisions: List<DecisionCount>,
    val days: List<ActivityDay>,
    val displays: List<ReminderDisplayEventEntity>,
    val actions: List<ReminderActionEventEntity>,
    val legacySessionCount: Int,
    val completeHistoryFrom: Long,
    val asOf: ZonedDateTime = ZonedDateTime.now()
)
class StatisticsRepository @Inject constructor(private val dao: ReminderAnalyticsDao) {
    fun observe(range: StatsRange, period: ActivityPeriod, anchor: LocalDate, now: ZonedDateTime): Flow<StatisticsSnapshot> = flow {
        dao.ensureHistory(ReminderHistoryStateEntity(completeHistoryFrom = now.toInstant().toEpochMilli()))
        val historyFrom = dao.history()!!.completeHistoryFrom
        val zone = now.zone
        val today = now.toLocalDate()
        val overviewStart = today.minusDays(when (range) { StatsRange.TODAY -> 0L; StatsRange.WEEK -> 6L; StatsRange.MONTH -> 29L })
        val from = overviewStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val (activityStart, activityEnd) = ReminderStatistics.bounds(anchor, period)
        val queryFrom = minOf(from, activityStart.atStartOfDay(zone).toInstant().toEpochMilli())
        val queryTo = maxOf(to, activityEnd.atStartOfDay(zone).toInstant().toEpochMilli())
        emitAll(combine(dao.sessions(from, to), dao.displays(queryFrom, queryTo), dao.actions(from, to)) { entities, displays, actions ->
            val nowMillis = now.toInstant().toEpochMilli()
            val sessions = entities.map { it.toDomain() }.map { it.copy(endedAt = minOf(it.endedAt ?: nowMillis, nowMillis)) }
            val opened = sessions.filter { it.startedAt in from until minOf(to, nowMillis) }
            val visibleDisplays = displays.filter { it.displayedAt <= nowMillis }
            val visibleActions = actions.filter { it.occurredAt <= nowMillis }
            val selectedDisplays = visibleDisplays.filter { it.displayedAt in from until to }
            val shownSessions = selectedDisplays.map { it.sessionId }.toSet()
            val usage = StatsAggregator.aggregate(sessions, from, to, zone, nowMillis)
            val counts = visibleDisplays.groupingBy { Instant.ofEpochMilli(it.displayedAt).atZone(zone).toLocalDate() }.eachCount()
            val days = generateSequence(activityStart) { it.plusDays(1) }.takeWhile { it < activityEnd }.map { date ->
                ActivityDay(date, counts[date] ?: 0, date > today,
                    date.atStartOfDay(zone).toInstant().toEpochMilli() < historyFrom)
            }.toList()
            StatisticsSnapshot(usage, if (opened.isEmpty()) null else opened.count { it.id in shownSessions }.toFloat() / opened.size,
                ReminderStatistics.decisions(visibleActions.map { it.actionKey }), days, visibleDisplays, visibleActions,
                opened.count { session -> session.startedAt < historyFrom && !session.userAction.isNullOrBlank() && actions.none { it.sessionId == session.id } }, historyFrom, now)
        })
    }
}
