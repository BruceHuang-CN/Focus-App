package com.example.focus_app.domain.usecase

import com.example.focus_app.data.local.dao.SettingsDao
import com.example.focus_app.data.local.entity.SettingsEntity
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.ReminderDisplayKind
import com.example.focus_app.data.repository.ReminderDisplayRepository
import com.example.focus_app.data.repository.ReminderDisplayResult
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ResetReminderQuotaUseCaseTest {
    @Test
    fun reset_stops_session_before_resetting_a_60_minute_window_quota() = runTest {
        val fixture = fixture(reminderWindowMinutes = 60)

        fixture.useCase()

        assertEquals(listOf("cancel", "close", "settings", "quota"), fixture.events)
        assertEquals(listOf(4_400_000L), fixture.displays.resetSince)
    }

    @Test
    fun reset_honors_a_configured_30_minute_window() = runTest {
        val fixture = fixture(reminderWindowMinutes = 30)

        fixture.useCase()

        assertEquals(listOf(6_200_000L), fixture.displays.resetSince)
    }

    @Test
    fun reset_use_case_requires_only_settings_displays_and_session_coordinator() {
        val injectedConstructor = ResetReminderQuotaUseCase::class.java.constructors
            .single { it.parameterTypes.size == 3 }

        assertArrayEquals(
            arrayOf(
                SettingsRepository::class.java,
                ReminderDisplayRepository::class.java,
                AppSessionCoordinator::class.java
            ),
            injectedConstructor.parameterTypes
        )
    }

    private fun fixture(reminderWindowMinutes: Int): Fixture {
        val events = mutableListOf<String>()
        val sessions = ResetRecordingSessions(events)
        val displays = ResetRecordingDisplays(events)
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
                displays,
                coordinator,
                FakeClock(8_000_000L)
            ),
            sessions = sessions,
            displays = displays,
            events = events
        )
    }

    private data class Fixture(
        val useCase: ResetReminderQuotaUseCase,
        val sessions: ResetRecordingSessions,
        val displays: ResetRecordingDisplays,
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

}

private class ResetRecordingDisplays(
    private val events: MutableList<String>
) : ReminderDisplayRepository {
    val resetSince = mutableListOf<Long>()

    override suspend fun recordDisplay(
        attemptId: String,
        sessionId: Long,
        displayedAt: Long,
        kind: ReminderDisplayKind,
        windowStart: Long,
        limit: Int
    ): ReminderDisplayResult = error("not used")

    override suspend fun countSince(since: Long): Int = 0
    override suspend fun timesSince(since: Long): List<Long> = emptyList()
    override suspend fun resetSince(since: Long) {
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
