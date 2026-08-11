package com.example.focus_app.domain.usecase

import com.example.focus_app.data.local.dao.SettingsDao
import com.example.focus_app.data.local.entity.SettingsEntity
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.time.FakeClock
import com.example.focus_app.service.AppSessionContext
import com.example.focus_app.service.AppSessionContextProvider
import com.example.focus_app.service.AppSessionCoordinator
import com.example.focus_app.service.SessionReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ResetReminderQuotaUseCaseTest {
    @Test
    fun reset_stops_session_before_resetting_a_60_minute_window_quota() = runTest {
        val fixture = fixture(reminderWindowMinutes = 60)

        fixture.useCase()

        assertEquals(listOf("cancel", "close", "settings", "quota"), fixture.events)
        assertEquals(listOf(4_400_000L), fixture.sessions.resetSince)
    }

    @Test
    fun reset_honors_a_configured_30_minute_window() = runTest {
        val fixture = fixture(reminderWindowMinutes = 30)

        fixture.useCase()

        assertEquals(listOf(6_200_000L), fixture.sessions.resetSince)
    }

    @Test
    fun reset_use_case_has_no_reminder_cache_dependency() {
        assertFalse(
            ResetReminderQuotaUseCase::class.java.constructors
                .flatMap { it.parameterTypes.asIterable() }
                .any { it.simpleName == "ReminderCacheRepository" }
        )
    }

    private fun fixture(reminderWindowMinutes: Int): Fixture {
        val events = mutableListOf<String>()
        val sessions = ResetRecordingSessions(events)
        val coordinator = AppSessionCoordinator(
            repository = sessions,
            contextProvider = object : AppSessionContextProvider {
                override suspend fun currentContext() = AppSessionContext(emptyMap(), null, "gentle")
            },
            clock = FakeClock(8_000_000L),
            reminderScheduler = ResetRecordingScheduler(events)
        )
        return Fixture(
            useCase = ResetReminderQuotaUseCase(
                SettingsRepository(ResetSettingsDao(reminderWindowMinutes, events)),
                sessions,
                coordinator,
                FakeClock(8_000_000L)
            ),
            sessions = sessions,
            events = events
        )
    }

    private data class Fixture(
        val useCase: ResetReminderQuotaUseCase,
        val sessions: ResetRecordingSessions,
        val events: MutableList<String>
    )
}

private class ResetSettingsDao(
    reminderWindowMinutes: Int,
    private val events: MutableList<String>
) : SettingsDao {
    private val state = MutableStateFlow<SettingsEntity?>(
        SettingsEntity(targetApps = "[]", reminderWindowMinutes = reminderWindowMinutes)
    )

    override suspend fun insertOrUpdate(settings: SettingsEntity) {
        state.value = settings
    }

    override fun getSettings(): Flow<SettingsEntity?> = state
    override suspend fun getSettingsOnce(): SettingsEntity? = state.value.also { events += "settings" }
    override suspend fun clearLegacyApiKey() = Unit
}

private class ResetRecordingSessions(private val events: MutableList<String>) : AppSessionRepository {
    val resetSince = mutableListOf<Long>()
    private var open = AppUsageSession(1L, "video.app", "Video", 8_000_000L, null, null, null, null, "gentle")

    override suspend fun openSession(
        packageName: String,
        appName: String,
        startedAt: Long,
        taskId: Long?,
        toneKey: String
    ): AppUsageSession = open

    override suspend fun closeSession(sessionId: Long, endedAt: Long) {
        events += "close"
        open = open.copy(endedAt = endedAt)
    }

    override suspend fun currentOpenSession(): AppUsageSession? = open.takeIf { it.endedAt == null }
    override suspend fun reminderTimesSince(since: Long): List<Long> = emptyList()
    override suspend fun markRemindedIfNeeded(sessionId: Long, remindedAt: Long) = false
    override suspend fun markUserAction(sessionId: Long, action: String) = Unit

    override suspend fun resetReminderQuota(since: Long) {
        events += "quota"
        resetSince += since
    }
}

private class ResetRecordingScheduler(private val events: MutableList<String>) : SessionReminderScheduler {
    override fun onSessionStarted(session: AppUsageSession, appStillForeground: suspend () -> Boolean) = Unit
    override fun cancel(sessionId: Long) {
        events += "cancel"
    }
}
