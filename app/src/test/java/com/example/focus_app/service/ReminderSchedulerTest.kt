package com.example.focus_app.service

import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.model.ReminderTone
import com.example.focus_app.domain.model.ReturnDestination
import com.example.focus_app.domain.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSchedulerTest {
    @Test
    fun leaving_before_delay_cancels_without_spending_quota() = runTest {
        val fixture = fixture()

        fixture.scheduler.onSessionStarted(fixture.session, appStillForeground = { false })
        advanceTimeBy(10_001L)
        runCurrent()

        assertEquals(0, fixture.launcher.shown.size)
        assertEquals(0, fixture.repository.remindedCount)
    }

    @Test
    fun showing_marks_quota_before_launch_and_uses_session_context() = runTest {
        val fixture = fixture()

        fixture.scheduler.onSessionStarted(fixture.session, appStillForeground = { true })
        advanceTimeBy(10_001L)
        runCurrent()

        assertEquals(1, fixture.launcher.shown.size)
        assertNotNull(fixture.repository.session(fixture.session.id)?.remindedAt)
        assertNotNull(fixture.launcher.remindedAtWhenShown)
        assertEquals(
            ReminderLaunchData(
                sessionId = fixture.session.id,
                taskId = 7L,
                taskTitle = "Write proposal",
                appName = "Douyin",
                message = "Return to Write proposal.",
                showBreathing = false,
                returnDestination = ReturnDestination.HOME
            ),
            fixture.launcher.shown.single()
        )
    }

    @Test
    fun rolling_quota_blocks_an_additional_reminder() = runTest {
        val fixture = fixture(previousReminderTimes = listOf(NOW - 1_000L))

        fixture.scheduler.onSessionStarted(fixture.session, appStillForeground = { true })
        advanceTimeBy(10_001L)
        runCurrent()

        assertEquals(0, fixture.launcher.shown.size)
        assertEquals(1, fixture.repository.remindedCount)
        assertEquals(null, fixture.repository.session(fixture.session.id)?.remindedAt)
    }

    @Test
    fun explicit_cancel_stops_the_pending_session_job() = runTest {
        val fixture = fixture()

        fixture.scheduler.onSessionStarted(fixture.session, appStillForeground = { true })
        fixture.scheduler.cancel(fixture.session.id)
        advanceTimeBy(10_001L)
        runCurrent()

        assertEquals(0, fixture.launcher.shown.size)
        assertEquals(0, fixture.repository.remindedCount)
    }

    private fun kotlinx.coroutines.test.TestScope.fixture(
        previousReminderTimes: List<Long> = emptyList()
    ): Fixture {
        val session = AppUsageSession(
            id = 12L,
            packageName = "com.ss.android.ugc.aweme",
            appName = "Douyin",
            startedAt = NOW,
            taskId = 7L,
            toneKey = ReminderTone.DIRECT.key
        )
        val repository = FakeReminderSessionRepository(session, previousReminderTimes)
        val launcher = RecordingReminderLauncher(repository)
        val settings = AppSettings(
            targetApps = listOf(AppInfo(session.packageName, session.appName)),
            reminderDelaySeconds = 10,
            reminderWindowMinutes = 60,
            maxRemindersPerWindow = 1,
            enableBreathingPause = false,
            returnDestination = ReturnDestination.HOME
        )
        val scheduler = ReminderScheduler(
            repository = repository,
            launcher = launcher,
            clock = FakeClock(NOW),
            scope = backgroundScope,
            settingsProvider = { settings },
            launchDataProvider = { usageSession, currentSettings ->
                ReminderLaunchData(
                    sessionId = usageSession.id,
                    taskId = usageSession.taskId,
                    taskTitle = "Write proposal",
                    appName = usageSession.appName,
                    message = "Return to Write proposal.",
                    showBreathing = currentSettings.enableBreathingPause,
                    returnDestination = currentSettings.returnDestination
                )
            }
        )
        return Fixture(scheduler, repository, launcher, session)
    }

    private data class Fixture(
        val scheduler: ReminderScheduler,
        val repository: FakeReminderSessionRepository,
        val launcher: RecordingReminderLauncher,
        val session: AppUsageSession
    )

    private companion object {
        const val NOW = 2_000_000L
    }
}

private class RecordingReminderLauncher(
    private val repository: FakeReminderSessionRepository
) : ReminderLauncher {
    val shown = mutableListOf<ReminderLaunchData>()
    var remindedAtWhenShown: Long? = null

    override fun show(data: ReminderLaunchData) {
        remindedAtWhenShown = repository.session(data.sessionId)?.remindedAt
        shown += data
    }

    override fun returnToFocus(taskId: Long?) = Unit
    override fun returnHome() = Unit
}

private class FakeReminderSessionRepository(
    session: AppUsageSession,
    previousReminderTimes: List<Long>
) : AppSessionRepository {
    private val sessions = mutableMapOf(session.id to session)
    private val previous = previousReminderTimes.toMutableList()

    val remindedCount: Int
        get() = previous.size + sessions.values.count { it.remindedAt != null }

    fun session(id: Long): AppUsageSession? = sessions[id]

    override suspend fun openSession(
        packageName: String,
        appName: String,
        startedAt: Long,
        taskId: Long?,
        toneKey: String
    ): AppUsageSession = error("Not used")

    override suspend fun closeSession(sessionId: Long, endedAt: Long) = Unit
    override suspend fun currentOpenSession(): AppUsageSession? = null

    override suspend fun reminderTimesSince(since: Long): List<Long> =
        previous.filter { it >= since } + sessions.values.mapNotNull { it.remindedAt }.filter { it >= since }

    override suspend fun markRemindedIfNeeded(sessionId: Long, remindedAt: Long): Boolean {
        val current = sessions[sessionId] ?: return false
        if (current.remindedAt != null) return false
        sessions[sessionId] = current.copy(remindedAt = remindedAt)
        return true
    }

    override suspend fun markUserAction(sessionId: Long, action: String) {
        val current = sessions[sessionId] ?: return
        sessions[sessionId] = current.copy(userAction = action)
    }
}
