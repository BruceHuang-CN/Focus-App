package com.example.focus_app.domain.usecase

import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.model.StatsRange
import com.example.focus_app.domain.time.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class GetStatsUseCaseTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun today_range_queries_only_today_and_aggregates() = runTest {
        val now = epoch(2026, 8, 4, 15, 0)
        val clock = FakeClock(now)
        val repository = FakeSessionRepository(
            listOf(
                session(1, startedAt = epoch(2026, 8, 4, 9, 0), endedAt = epoch(2026, 8, 4, 9, 30)),
                session(2, startedAt = epoch(2026, 8, 3, 22, 0), endedAt = epoch(2026, 8, 3, 22, 30))
            )
        )
        val useCase = GetStatsUseCase(repository, clock)

        val stats = useCase(StatsRange.TODAY)

        assertEquals(epoch(2026, 8, 4, 0, 0), repository.lastFrom)
        assertEquals(epoch(2026, 8, 5, 0, 0), repository.lastTo)
        assertEquals(1, stats.openCount)
        assertEquals(30, stats.totalDurationMinutes)
        assertEquals(1, stats.daily.size)
    }

    @Test
    fun week_range_covers_seven_days() = runTest {
        val clock = FakeClock(epoch(2026, 8, 4, 15, 0))
        val repository = FakeSessionRepository(emptyList())
        val useCase = GetStatsUseCase(repository, clock)

        useCase(StatsRange.WEEK)

        assertEquals(epoch(2026, 7, 29, 0, 0), repository.lastFrom)
        assertEquals(epoch(2026, 8, 5, 0, 0), repository.lastTo)
        assertTrue(useCase(StatsRange.WEEK).daily.size in 1..7)
    }

    private fun session(
        id: Long,
        startedAt: Long,
        endedAt: Long
    ) = AppUsageSession(
        id = id,
        packageName = "com.example.app",
        appName = "测试App",
        startedAt = startedAt,
        endedAt = endedAt,
        toneKey = "gentle"
    )

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
}

private class FakeSessionRepository(
    private val sessions: List<AppUsageSession>
) : AppSessionRepository {
    var lastFrom: Long = -1
    var lastTo: Long = -1

    override suspend fun sessionsBetween(from: Long, to: Long): List<AppUsageSession> {
        lastFrom = from
        lastTo = to
        return sessions.filter { it.startedAt in from until to }
    }
}
