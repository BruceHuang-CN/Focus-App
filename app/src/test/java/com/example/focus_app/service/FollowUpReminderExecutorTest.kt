package com.example.focus_app.service

import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.ReminderDisplayKind
import com.example.focus_app.data.repository.ReminderDisplayRepository
import com.example.focus_app.data.repository.ReminderDisplayResult
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.model.ReturnDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FollowUpReminderExecutorTest {

    private val session = AppUsageSession(
        id = 100L,
        packageName = "com.ss.android.ugc.aweme",
        appName = "抖音",
        startedAt = 1_000L,
        taskId = 7L,
        toneKey = "gentle",
        snoozeUntil = 20_000L
    )
    private val settings = AppSettings(
        guardianEnabled = true,
        targetApps = listOf(AppInfo("com.ss.android.ugc.aweme", "抖音")),
        reminderWindowMinutes = 60,
        maxRemindersPerWindow = 3,
        enableBreathingPause = true,
        returnDestination = ReturnDestination.HOME,
        forceReminder = false
    )

    @Test
    fun retries_when_foreground_cannot_be_verified() = kotlinx.coroutines.test.runTest {
        val env = FakeEnvironment(interactive = true, snapshot = ForegroundSnapshot.Unknown)
        val fixture = fixture(environment = env)

        val decision = fixture.executor.execute(session.id)

        assertEquals(FollowUpDecision.RETRY, decision)
        assertEquals(0, fixture.launcher.shown.size)
        assertNull(fixture.repository.session(session.id)?.remindedAt)
        assertEquals(20_000L, fixture.repository.session(session.id)?.snoozeUntil)
    }

    @Test
    fun cancels_expired_countdown_before_retry_decision() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(
            environment = FakeEnvironment(
                interactive = true,
                snapshot = ForegroundSnapshot.Unknown
            )
        )

        val decision = fixture.executor.execute(session.id)

        assertEquals(FollowUpDecision.RETRY, decision)
        assertEquals(listOf(session.id), fixture.countdownNotifier.cancelledSessionIds)
    }

    @Test
    fun returns_retry_when_device_is_locked_without_showing() = kotlinx.coroutines.test.runTest {
        val env = FakeEnvironment(
            interactive = false,
            snapshot = ForegroundSnapshot.Confirmed(session.packageName, ForegroundSource.REALTIME)
        )
        val fixture = fixture(environment = env)

        val decision = fixture.executor.execute(session.id)

        assertEquals(FollowUpDecision.RETRY, decision)
        assertEquals(0, fixture.launcher.shown.size)
        assertNull(fixture.repository.session(session.id)?.remindedAt)
        assertEquals(20_000L, fixture.repository.session(session.id)?.snoozeUntil)
    }

    @Test
    fun skips_when_user_confirmed_to_have_left_target_app() = kotlinx.coroutines.test.runTest {
        val env = FakeEnvironment(
            interactive = true,
            snapshot = ForegroundSnapshot.Confirmed("com.tencent.mm", ForegroundSource.USAGE_EVENTS)
        )
        val fixture = fixture(environment = env)

        val decision = fixture.executor.execute(session.id)

        assertEquals(FollowUpDecision.SKIP, decision)
        assertEquals(0, fixture.launcher.shown.size)
        assertNull(fixture.repository.session(session.id)?.snoozeUntil)
    }

    @Test
    fun skips_when_session_was_closed() = kotlinx.coroutines.test.runTest {
        val repo = FakeSessionRepository(session.copy(endedAt = 9_000L))
        val fixture = fixture(repository = repo)

        val decision = fixture.executor.execute(session.id)

        assertEquals(FollowUpDecision.SKIP, decision)
        assertEquals(0, fixture.launcher.shown.size)
    }

    @Test
    fun shows_with_task_context_when_everything_is_ok() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        val decision = fixture.executor.execute(session.id)

        assertEquals(FollowUpDecision.SHOW, decision)
        val shown = fixture.launcher.shown.single()
        assertEquals("attempt-1", shown.attemptId)
        assertEquals(ReminderDisplayKind.FOLLOW_UP, shown.displayKind)
        assertEquals("写方案", shown.taskTitle)
        assertEquals(7L, shown.taskId)
        assertEquals("Return to 写方案.", shown.message)
    }

    @Test
    fun same_persisted_snooze_can_only_be_shown_once() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()

        val first = fixture.executor.execute(session.id)
        val second = fixture.executor.execute(session.id)

        assertEquals(FollowUpDecision.SHOW, first)
        assertEquals(FollowUpDecision.SKIP, second)
        assertEquals(1, fixture.launcher.shown.size)
    }

    private fun fixture(
        repository: FakeSessionRepository = FakeSessionRepository(session),
        environment: FakeEnvironment = FakeEnvironment(
            interactive = true,
            snapshot = ForegroundSnapshot.Confirmed(session.packageName, ForegroundSource.REALTIME)
        )
    ): Fixture {
        val launcher = RecordingLauncher()
        val countdownNotifier = RecordingCountdownNotifier()
        val executor = FollowUpReminderExecutor(
            sessionRepository = repository,
            settingsProvider = { settings },
            taskTitleProvider = { taskId ->
                if (taskId == 7L) "写方案" else null
            },
            messageProvider = { usageSession ->
                if (usageSession.taskId == 7L) "Return to 写方案." else null
            },
            returnPackageProvider = { "" },
            launcher = launcher,
            gate = FollowUpReminderGate(),
            environment = environment,
            displayRepository = FakeDisplayRepository(),
            attemptIdProvider = { "attempt-1" },
            countdownNotifier = countdownNotifier
        )
        return Fixture(executor, repository, launcher, countdownNotifier)
    }

    private class FakeEnvironment(
        var interactive: Boolean,
        var snapshot: ForegroundSnapshot
    ) : FollowUpEnvironment {
        override fun isDeviceInteractive(): Boolean = interactive
        override fun foregroundSnapshot(): ForegroundSnapshot = snapshot
    }

    private class FakeDisplayRepository : ReminderDisplayRepository {
        override suspend fun recordDisplay(
            attemptId: String,
            sessionId: Long,
            displayedAt: Long,
            kind: ReminderDisplayKind,
            windowStart: Long,
            limit: Int
        ): ReminderDisplayResult = error("Not used")

        override suspend fun countSince(since: Long): Int = 0
        override suspend fun timesSince(since: Long): List<Long> = emptyList()
        override suspend fun resetSince(since: Long) = Unit
    }

    private data class Fixture(
        val executor: FollowUpReminderExecutor,
        val repository: FakeSessionRepository,
        val launcher: RecordingLauncher,
        val countdownNotifier: RecordingCountdownNotifier
    )

    private class RecordingCountdownNotifier : FollowUpCountdownNotifier {
        val cancelledSessionIds = mutableListOf<Long>()

        override fun show(sessionId: Long, delayMillis: Long) = Unit

        override fun cancel(sessionId: Long) {
            cancelledSessionIds += sessionId
        }
    }

    private class FakeSessionRepository(initial: AppUsageSession) : AppSessionRepository {
        private val sessions = mutableMapOf(initial.id to initial)

        fun session(id: Long) = sessions[id]

        override suspend fun openSession(
            packageName: String,
            appName: String,
            startedAt: Long,
            taskId: Long?,
            toneKey: String
        ): AppUsageSession = error("Not used")

        override suspend fun closeSession(sessionId: Long, endedAt: Long) {
            val current = sessions[sessionId] ?: return
            sessions[sessionId] = current.copy(endedAt = endedAt)
        }

        override suspend fun currentOpenSession(): AppUsageSession? =
            sessions.values.firstOrNull { it.endedAt == null }

        override suspend fun reminderTimesSince(since: Long): List<Long> =
            sessions.values.mapNotNull { it.remindedAt }.filter { it >= since }

        override suspend fun markRemindedIfNeeded(sessionId: Long, remindedAt: Long): Boolean {
            val current = sessions[sessionId] ?: return false
            if (current.remindedAt != null || current.endedAt != null) return false
            sessions[sessionId] = current.copy(remindedAt = remindedAt)
            return true
        }

        override suspend fun markUserAction(sessionId: Long, action: String) = Unit

        override suspend fun countShownRemindersSince(since: Long): Int =
            sessions.values.count { it.remindedAt != null && it.remindedAt >= since }

        override suspend fun sessionById(id: Long): AppUsageSession? = sessions[id]

        override suspend fun updateRemindedAt(sessionId: Long, remindedAt: Long) {
            val current = sessions[sessionId] ?: return
            if (current.endedAt != null) return
            sessions[sessionId] = current.copy(remindedAt = remindedAt)
        }

        override suspend fun setSnoozeUntil(sessionId: Long, snoozeUntil: Long?) {
            val current = sessions[sessionId] ?: return
            sessions[sessionId] = current.copy(snoozeUntil = snoozeUntil)
        }

        override suspend fun claimSnooze(sessionId: Long): Boolean {
            val current = sessions[sessionId] ?: return false
            if (current.endedAt != null || current.snoozeUntil == null) return false
            sessions[sessionId] = current.copy(snoozeUntil = null)
            return true
        }
    }

    private class RecordingLauncher : ReminderLauncher {
        val shown = mutableListOf<ReminderLaunchData>()

        override fun show(data: ReminderLaunchData): Boolean {
            shown += data
            return true
        }

        override fun dismiss(sessionId: Long) = Unit
        override fun returnToFocus(taskId: Long?) = Unit
        override fun returnHome() = Unit
        override fun returnToCustom(packageName: String): CustomReturnResult =
            CustomReturnResult.SUCCESS
    }
}
