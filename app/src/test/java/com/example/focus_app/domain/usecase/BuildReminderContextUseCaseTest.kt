package com.example.focus_app.domain.usecase

import com.example.focus_app.data.local.dao.MoodRecordDao
import com.example.focus_app.data.local.entity.MoodRecordEntity
import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.MoodRepository
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.model.FocusTask
import com.example.focus_app.domain.time.FakeClock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildReminderContextUseCaseTest {
    @Test
    fun active_exits_use_today_boundary_and_are_included_in_context() = runTest {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.of(2026, 8, 3).atTime(12, 0).atZone(zone)
        val sessions = ContextSessionRepository(activeExits = 4)
        val useCase = BuildReminderContextUseCase(
            sessions,
            MoodRepository(EmptyMoodDao()),
            FakeClock(now.toInstant().toEpochMilli())
        )

        val context = useCase(
            FocusTask(id = 7L, title = "写方案"),
            AppInfo("douyin", "抖音"),
            AppSettings()
        )

        assertEquals(4, context.activeExitsToday)
        assertEquals(
            now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli(),
            sessions.activeExitSince
        )
    }
}

private class ContextSessionRepository(
    private val activeExits: Int
) : AppSessionRepository {
    var activeExitSince: Long? = null

    override suspend fun countActiveExitsSince(since: Long): Int {
        activeExitSince = since
        return activeExits
    }

    override suspend fun openSession(
        packageName: String,
        appName: String,
        startedAt: Long,
        taskId: Long?,
        toneKey: String
    ): AppUsageSession = error("not used")
    override suspend fun closeSession(sessionId: Long, endedAt: Long) = Unit
    override suspend fun currentOpenSession(): AppUsageSession? = null
    override suspend fun reminderTimesSince(since: Long): List<Long> = emptyList()
    override suspend fun markRemindedIfNeeded(sessionId: Long, remindedAt: Long): Boolean = false
    override suspend fun markUserAction(sessionId: Long, action: String) = Unit
}

private class EmptyMoodDao : MoodRecordDao {
    override suspend fun insert(mood: MoodRecordEntity) = Unit
    override suspend fun getLatestMood(): MoodRecordEntity? = null
    override fun getAllMoods(): Flow<List<MoodRecordEntity>> = flowOf(emptyList())
    override fun observeLatestMood(): Flow<MoodRecordEntity?> = flowOf(null)
}
