package com.example.focus_app.service

import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.model.ReminderTone
import com.example.focus_app.domain.time.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSessionCoordinatorTest {
    @Test
    fun same_package_window_changes_create_one_session_and_leaving_closes_it() = runTest {
        val fixture = fixture()

        fixture.coordinator.onPackageChanged(TARGET_A)
        fixture.clock.epochMillis += 1_000L
        fixture.coordinator.onPackageChanged(TARGET_A)
        fixture.coordinator.onPackageChanged(LAUNCHER)

        assertEquals(1, fixture.repository.sessions.size)
        val session = fixture.repository.sessions.single()
        assertEquals(1_000L, session.endedAt!! - session.startedAt)
    }

    @Test
    fun switching_targets_closes_the_first_and_opens_the_second() = runTest {
        val fixture = fixture()

        fixture.coordinator.onPackageChanged(TARGET_A)
        fixture.clock.epochMillis += 2_000L
        fixture.coordinator.onPackageChanged(TARGET_B)

        assertEquals(2, fixture.repository.sessions.size)
        assertEquals(TARGET_A, fixture.repository.sessions[0].packageName)
        assertEquals(STARTED_AT + 2_000L, fixture.repository.sessions[0].endedAt)
        assertEquals(TARGET_B, fixture.repository.sessions[1].packageName)
        assertNull(fixture.repository.sessions[1].endedAt)

        fixture.clock.epochMillis += 500L
        fixture.coordinator.onPackageChanged(LAUNCHER)

        assertEquals(2, fixture.repository.sessions.size)
        assertEquals(STARTED_AT + 2_500L, fixture.repository.sessions[1].endedAt)
    }

    @Test
    fun non_target_packages_do_not_create_sessions() = runTest {
        val fixture = fixture()

        fixture.coordinator.onPackageChanged(LAUNCHER)
        fixture.coordinator.onPackageChanged("com.example.not.a.target")

        assertEquals(emptyList<AppUsageSession>(), fixture.repository.sessions)
    }

    @Test
    fun session_creation_captures_active_task_and_tone() = runTest {
        val fixture = fixture(activeTaskId = 42L, toneKey = ReminderTone.DIRECT.key)

        fixture.coordinator.onPackageChanged(TARGET_A)
        fixture.contextProvider.context = fixture.contextProvider.context.copy(
            activeTaskId = 99L,
            toneKey = ReminderTone.SARCASTIC.key
        )
        fixture.coordinator.onPackageChanged(LAUNCHER)

        val session = fixture.repository.sessions.single()
        assertEquals(42L, session.taskId)
        assertEquals(ReminderTone.DIRECT.key, session.toneKey)
    }

    @Test
    fun target_session_starts_one_reminder_job_and_leaving_cancels_it() = runTest {
        val fixture = fixture()

        fixture.coordinator.onPackageChanged(TARGET_A)
        val sessionId = fixture.repository.sessions.single().id
        fixture.coordinator.onPackageChanged(LAUNCHER)

        assertEquals(listOf(sessionId), fixture.reminderScheduler.startedSessionIds)
        assertEquals(listOf(sessionId), fixture.reminderScheduler.cancelledSessionIds)
    }

    @Test
    fun compatibility_verifier_is_forwarded_to_the_delayed_reminder_check() = runTest {
        val fixture = fixture()
        val verifiedPackages = mutableListOf<String>()

        fixture.coordinator.onPackageChanged(
            packageName = TARGET_A,
            foregroundVerifier = { expectedPackage ->
                verifiedPackages += expectedPackage
                false
            }
        )

        assertEquals(false, fixture.reminderScheduler.foregroundChecks.single().invoke())
        assertEquals(listOf(TARGET_A), verifiedPackages)
    }

    @Test
    fun startup_closes_a_stale_session_at_observation_time_capped_to_24_hours() = runTest {
        val recentlyStale = staleFixture(observedAfter = 2 * HOUR_MS)
        recentlyStale.coordinator.onPackageChanged(LAUNCHER)

        assertEquals(
            STARTED_AT + 2 * HOUR_MS,
            recentlyStale.repository.sessions.single().endedAt
        )

        val longStale = staleFixture(observedAfter = 30 * HOUR_MS)
        longStale.coordinator.onPackageChanged(LAUNCHER)

        assertEquals(
            STARTED_AT + 24 * HOUR_MS,
            longStale.repository.sessions.single().endedAt
        )
    }

    private fun fixture(
        activeTaskId: Long? = 7L,
        toneKey: String = ReminderTone.GENTLE.key,
        repository: FakeAppSessionRepository = FakeAppSessionRepository(),
        now: Long = STARTED_AT,
        reminderScheduler: RecordingSessionReminderScheduler = RecordingSessionReminderScheduler()
    ): Fixture {
        val clock = FakeClock(now)
        val contextProvider = FakeAppSessionContextProvider(
            AppSessionContext(
                targetApps = mapOf(
                    TARGET_A to "Douyin",
                    TARGET_B to "Xiaohongshu"
                ),
                activeTaskId = activeTaskId,
                toneKey = toneKey
            )
        )
        return Fixture(
            coordinator = AppSessionCoordinator(
                repository,
                contextProvider,
                clock,
                reminderScheduler
            ),
            repository = repository,
            contextProvider = contextProvider,
            clock = clock,
            reminderScheduler = reminderScheduler
        )
    }

    private fun staleFixture(observedAfter: Long): Fixture {
        val repository = FakeAppSessionRepository(
            initialSessions = listOf(
                AppUsageSession(
                    id = 10L,
                    packageName = TARGET_A,
                    appName = "Douyin",
                    startedAt = STARTED_AT,
                    endedAt = null,
                    taskId = 7L,
                    remindedAt = null,
                    userAction = null,
                    toneKey = ReminderTone.GENTLE.key
                )
            )
        )
        return fixture(repository = repository, now = STARTED_AT + observedAfter)
    }

    private data class Fixture(
        val coordinator: AppSessionCoordinator,
        val repository: FakeAppSessionRepository,
        val contextProvider: FakeAppSessionContextProvider,
        val clock: FakeClock,
        val reminderScheduler: RecordingSessionReminderScheduler
    )

    private companion object {
        const val TARGET_A = "com.ss.android.ugc.aweme"
        const val TARGET_B = "com.xingin.xhs"
        const val LAUNCHER = "com.android.launcher"
        const val STARTED_AT = 1_000_000L
        const val HOUR_MS = 60 * 60 * 1_000L
    }
}

private class FakeAppSessionContextProvider(
    var context: AppSessionContext
) : AppSessionContextProvider {
    override suspend fun currentContext(): AppSessionContext = context
}

private class FakeAppSessionRepository(
    initialSessions: List<AppUsageSession> = emptyList()
) : AppSessionRepository {
    val sessions = initialSessions.toMutableList()
    private var nextId = (sessions.maxOfOrNull { it.id } ?: 0L) + 1L

    override suspend fun openSession(
        packageName: String,
        appName: String,
        startedAt: Long,
        taskId: Long?,
        toneKey: String
    ): AppUsageSession {
        val session = AppUsageSession(
            id = nextId++,
            packageName = packageName,
            appName = appName,
            startedAt = startedAt,
            endedAt = null,
            taskId = taskId,
            remindedAt = null,
            userAction = null,
            toneKey = toneKey
        )
        sessions += session
        return session
    }

    override suspend fun closeSession(sessionId: Long, endedAt: Long) {
        val index = sessions.indexOfFirst { it.id == sessionId }
        check(index >= 0) { "Unknown session id: $sessionId" }
        sessions[index] = sessions[index].copy(endedAt = endedAt)
    }

    override suspend fun currentOpenSession(): AppUsageSession? =
        sessions.lastOrNull { it.endedAt == null }

    override suspend fun reminderTimesSince(since: Long): List<Long> = emptyList()

    override suspend fun markRemindedIfNeeded(sessionId: Long, remindedAt: Long): Boolean = false

    override suspend fun markUserAction(sessionId: Long, action: String) = Unit
}

private class RecordingSessionReminderScheduler : SessionReminderScheduler {
    val startedSessionIds = mutableListOf<Long>()
    val cancelledSessionIds = mutableListOf<Long>()
    val foregroundChecks = mutableListOf<suspend () -> Boolean>()

    override fun onSessionStarted(
        session: AppUsageSession,
        appStillForeground: suspend () -> Boolean
    ) {
        startedSessionIds += session.id
        foregroundChecks += appStillForeground
    }

    override fun cancel(sessionId: Long) {
        cancelledSessionIds += sessionId
    }
}
