package com.example.focus_app.service

import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.model.ReturnDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FollowUpReminderExecutorTest {

    private val session = AppUsageSession(
        id = 100L,
        packageName = "com.ss.android.ugc.aweme",
        appName = "抖音",
        startedAt = 1_000L,
        taskId = 7L,
        toneKey = "gentle"
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
    fun shows_reminder_when_foreground_cannot_be_verified() = kotlinx.coroutines.test.runTest {
        val env = FakeEnvironment(interactive = true, latestForeground = null)
        val fixture = fixture(environment = env)

        val decision = fixture.executor.execute(session.id)

        assertEquals(FollowUpDecision.SHOW, decision)
        assertEquals(1, fixture.launcher.shown.size)
        assertEquals(session.id, fixture.launcher.shown.single().sessionId)
        assertNotNull(fixture.repository.session(session.id)?.remindedAt)
    }

    @Test
    fun returns_retry_when_device_is_locked_without_showing() = kotlinx.coroutines.test.runTest {
        val env = FakeEnvironment(interactive = false, latestForeground = session.packageName)
        val fixture = fixture(environment = env)

        val decision = fixture.executor.execute(session.id)

        assertEquals(FollowUpDecision.RETRY, decision)
        assertEquals(0, fixture.launcher.shown.size)
        assertNull(fixture.repository.session(session.id)?.remindedAt)
    }

    @Test
    fun skips_when_user_confirmed_to_have_left_target_app() = kotlinx.coroutines.test.runTest {
        val env = FakeEnvironment(interactive = true, latestForeground = "com.tencent.mm")
        val fixture = fixture(environment = env)

        val decision = fixture.executor.execute(session.id)

        assertEquals(FollowUpDecision.SKIP, decision)
        assertEquals(0, fixture.launcher.shown.size)
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
        assertEquals("写方案", shown.taskTitle)
        assertEquals(7L, shown.taskId)
        assertEquals("Return to 写方案.", shown.message)
    }

    private fun fixture(
        repository: FakeSessionRepository = FakeSessionRepository(session),
        environment: FakeEnvironment = FakeEnvironment(
            interactive = true,
            latestForeground = session.packageName
        )
    ): Fixture {
        val launcher = RecordingLauncher()
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
            environment = environment
        )
        return Fixture(executor, repository, launcher)
    }

    private class FakeEnvironment(
        var interactive: Boolean,
        var latestForeground: String?
    ) : FollowUpEnvironment {
        override fun isDeviceInteractive(): Boolean = interactive
        override fun latestForegroundPackage(): String? = latestForeground
    }

    private data class Fixture(
        val executor: FollowUpReminderExecutor,
        val repository: FakeSessionRepository,
        val launcher: RecordingLauncher
    )

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
    }

    private class RecordingLauncher : ReminderLauncher {
        val shown = mutableListOf<ReminderLaunchData>()

        override fun show(data: ReminderLaunchData) {
            shown += data
        }

        override fun dismiss(sessionId: Long) = Unit
        override fun returnToFocus(taskId: Long?) = Unit
        override fun returnHome() = Unit
        override fun returnToCustom(packageName: String): CustomReturnResult =
            CustomReturnResult.SUCCESS
    }
}
