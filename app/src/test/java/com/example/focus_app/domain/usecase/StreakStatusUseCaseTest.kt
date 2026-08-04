package com.example.focus_app.domain.usecase

import com.example.focus_app.data.local.dao.FocusTaskDao
import com.example.focus_app.data.local.dao.SettingsDao
import com.example.focus_app.data.local.entity.FocusTaskEntity
import com.example.focus_app.data.local.entity.SettingsEntity
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.time.FakeClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class StreakStatusUseCaseTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun no_usage_and_no_completion_means_not_achieved() = runTest {
        val useCase = newUseCase(
            clock = FakeClock(epoch(2026, 8, 4, 15, 0)),
            sessions = emptyList(),
            completedCount = { _, _ -> 0 }
        )

        val status = useCase()

        assertEquals(0, status.streakDays)
        assertEquals(0, status.completedToday)
        assertTrue(status.withinDailyLimit)
        assertFalse(status.achievedToday)
    }

    @Test
    fun within_limit_and_task_done_means_achieved() = runTest {
        val useCase = newUseCase(
            clock = FakeClock(epoch(2026, 8, 4, 15, 0)),
            sessions = listOf(
                session(1, startedAt = epoch(2026, 8, 4, 9, 0), endedAt = epoch(2026, 8, 4, 9, 20))
            ),
            completedCount = { _, _ -> 1 }
        )

        val status = useCase()

        assertEquals(20, status.shortVideoTodayMinutes)
        assertEquals(30, status.shortVideoLimitMinutes)
        assertTrue(status.withinDailyLimit)
        assertTrue(status.taskDoneToday)
        assertTrue(status.achievedToday)
    }

    @Test
    fun over_limit_means_not_achieved_even_with_completed_task() = runTest {
        val useCase = newUseCase(
            clock = FakeClock(epoch(2026, 8, 4, 15, 0)),
            sessions = listOf(
                session(1, startedAt = epoch(2026, 8, 4, 9, 0), endedAt = epoch(2026, 8, 4, 10, 0))
            ),
            completedCount = { _, _ -> 1 }
        )

        val status = useCase()

        assertEquals(60, status.shortVideoTodayMinutes)
        assertFalse(status.withinDailyLimit)
        assertFalse(status.achievedToday)
    }

    @Test
    fun streak_counts_consecutive_completed_days() = runTest {
        var day = 0
        val useCase = newUseCase(
            clock = FakeClock(epoch(2026, 8, 4, 15, 0)),
            sessions = emptyList(),
            completedCount = { _, _ -> if (day++ < 3) 1 else 0 }
        )

        val status = useCase()

        assertEquals(3, status.streakDays)
    }

    private fun newUseCase(
        clock: FakeClock,
        sessions: List<AppUsageSession>,
        completedCount: (Long, Long) -> Int
    ) = StreakStatusUseCase(
        tasks = TaskRepository(FakeTaskDao(completedCount)),
        sessions = FakeSessionRepository(sessions),
        settings = SettingsRepository(
            FakeSettingsDao(SettingsEntity(targetApps = "[]", dailyShortVideoLimitMinutes = 30))
        ),
        clock = clock
    )

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

private class FakeTaskDao(
    private val completedCount: (Long, Long) -> Int
) : FocusTaskDao {
    override fun observeAll(): Flow<List<FocusTaskEntity>> = flowOf(emptyList())
    override suspend fun insert(task: FocusTaskEntity): Long = 0
    override suspend fun update(task: FocusTaskEntity) = Unit
    override suspend fun delete(task: FocusTaskEntity) = Unit
    override suspend fun setCompleted(id: Long, isCompleted: Boolean, updatedAt: Long) = Unit
    override suspend fun canSetManualActive(id: Long): Boolean = false
    override suspend fun clearManualActive() = Unit
    override suspend fun markManualActive(id: Long) = Unit
    override suspend fun completedCountBetween(startedAt: Long, endedAt: Long): Int =
        completedCount(startedAt, endedAt)
}

private class FakeSettingsDao(initial: SettingsEntity) : SettingsDao {
    private val state = MutableStateFlow<SettingsEntity?>(initial)
    override suspend fun insertOrUpdate(settings: SettingsEntity) {
        state.value = settings
    }
    override fun getSettings(): Flow<SettingsEntity?> = state
    override suspend fun getSettingsOnce(): SettingsEntity? = state.value
    override suspend fun clearLegacyApiKey() {
        state.value = state.value?.copy(legacyApiKey = "")
    }
}

private class FakeSessionRepository(
    private val sessions: List<AppUsageSession>
) : AppSessionRepository {
    override suspend fun sessionsBetween(from: Long, to: Long): List<AppUsageSession> =
        sessions.filter { it.startedAt in from until to }
}
