package com.example.focus_app.domain.usecase

import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.domain.model.FocusStats
import com.example.focus_app.domain.model.StatsRange
import com.example.focus_app.domain.stats.StatsAggregator
import com.example.focus_app.domain.time.Clock
import com.example.focus_app.domain.time.SystemClock
import javax.inject.Inject

class GetStatsUseCase(
    private val sessions: AppSessionRepository,
    private val clock: Clock
) {
    @Inject
    constructor(sessions: AppSessionRepository) : this(sessions, SystemClock)

    suspend operator fun invoke(range: StatsRange): FocusStats {
        val zone = clock.now().zone
        val today = clock.now(zone).toLocalDate()
        val rangeStart = when (range) {
            StatsRange.TODAY -> today.atStartOfDay(zone).toInstant().toEpochMilli()
            StatsRange.WEEK -> today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
            StatsRange.MONTH -> today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val rangeEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return StatsAggregator.aggregate(
            sessions.sessionsBetween(rangeStart, rangeEnd),
            rangeStart,
            rangeEnd,
            zone
        )
    }
}
