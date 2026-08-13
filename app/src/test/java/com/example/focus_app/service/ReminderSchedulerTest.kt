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
    fun trigger_time_verifier_false_does_not_mark_or_show() = runTest {
        val fixture = fixture()
        var verificationCount = 0

        fixture.scheduler.onSessionStarted(
            fixture.session,
            appStillForeground = {
                verificationCount++
                false
            }
        )
        advanceTimeBy(10_001L)
        runCurrent()

        assertEquals(1, verificationCount)
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
        assertEquals(0, fixture.launchDataBuildCount())
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

    @Test
    fun cancelling_session_dismisses_a_presented_reminder() = runTest {
        val fixture = fixture()

        fixture.scheduler.onSessionStarted(fixture.session, appStillForeground = { true })
        advanceTimeBy(10_001L)
        runCurrent()
        fixture.scheduler.cancel(fixture.session.id)

        assertEquals(listOf(fixture.session.id), fixture.launcher.dismissedSessionIds)
    }

    @Test
    fun same_session_started_twice_shows_only_one_reminder() = runTest {
        val fixture = fixture()

        // 同一次会话重复触发（例如无障碍事件去重前），只允许展示一次提醒
        fixture.scheduler.onSessionStarted(fixture.session, appStillForeground = { true })
        fixture.scheduler.onSessionStarted(fixture.session, appStillForeground = { true })
        advanceTimeBy(10_001L)
        runCurrent()

        assertEquals(1, fixture.launcher.shown.size)
        assertEquals(1, fixture.repository.remindedCount)
    }

    @Test
    fun follow_up_reminder_delegates_to_persistent_work_scheduler() = runTest {
        val fixture = fixture()

        fixture.scheduler.scheduleFollowUp(fixture.session.id, 60_000L)

        assertEquals(
            listOf(fixture.session.id to 60_000L),
            fixture.followUpWorkScheduler.scheduled
        )
        assertEquals(0, fixture.launcher.shown.size)
    }

    @Test
    fun cancelling_session_cancels_persistent_follow_up_work() = runTest {
        val fixture = fixture()

        fixture.scheduler.scheduleFollowUp(fixture.session.id, 60_000L)
        fixture.scheduler.cancel(fixture.session.id)

        assertEquals(
            listOf(fixture.session.id),
            fixture.followUpWorkScheduler.cancelledSessionIds
        )
    }

    @Test
    fun initial_reminder_does_not_fire_when_session_closed_before_delay() = runTest {
        val fixture = fixture()

        fixture.scheduler.onSessionStarted(fixture.session, appStillForeground = { true })
        fixture.repository.closeSession(fixture.session.id, NOW + 3_000L)
        advanceTimeBy(10_001L)
        runCurrent()

        assertEquals(0, fixture.launcher.shown.size)
        assertEquals(0, fixture.repository.remindedCount)
    }

    @Test
    fun closed_session_cannot_reserve_a_reminder_quota() = runTest {
        val fixture = fixture(closeImmediatelyBeforeReminderMark = true)

        fixture.scheduler.onSessionStarted(fixture.session, appStillForeground = { true })
        advanceTimeBy(10_001L)
        runCurrent()

        assertEquals(0, fixture.launcher.shown.size)
        assertEquals(0, fixture.repository.remindedCount)
    }

    private fun kotlinx.coroutines.test.TestScope.fixture(
        previousReminderTimes: List<Long> = emptyList(),
        closeImmediatelyBeforeReminderMark: Boolean = false
    ): Fixture {
        val session = AppUsageSession(
            id = 12L,
            packageName = "com.ss.android.ugc.aweme",
            appName = "Douyin",
            startedAt = NOW,
            taskId = 7L,
            toneKey = ReminderTone.DIRECT.key
        )
        val repository = FakeReminderSessionRepository(
            session = session,
            previousReminderTimes = previousReminderTimes,
            closeImmediatelyBeforeReminderMark = closeImmediatelyBeforeReminderMark
        )
        val launcher = RecordingReminderLauncher(repository)
        val settings = AppSettings(
            targetApps = listOf(AppInfo(session.packageName, session.appName)),
            reminderDelaySeconds = 10,
            reminderWindowMinutes = 60,
            maxRemindersPerWindow = 1,
            enableBreathingPause = false,
            returnDestination = ReturnDestination.HOME
        )
        var launchDataBuildCount = 0
        val followUpWorkScheduler = RecordingFollowUpWorkScheduler()
        val scheduler = ReminderScheduler(
            repository = repository,
            launcher = launcher,
            clock = FakeClock(NOW),
            scope = backgroundScope,
            settingsProvider = { settings },
            launchDataProvider = { usageSession, currentSettings ->
                launchDataBuildCount++
                ReminderLaunchData(
                    sessionId = usageSession.id,
                    taskId = usageSession.taskId,
                    taskTitle = "Write proposal",
                    appName = usageSession.appName,
                    message = "Return to Write proposal.",
                    showBreathing = currentSettings.enableBreathingPause,
                    returnDestination = currentSettings.returnDestination
                )
            },
            followUpWorkScheduler = followUpWorkScheduler
        )
        return Fixture(
            scheduler,
            repository,
            launcher,
            session,
            followUpWorkScheduler,
            { launchDataBuildCount }
        )
    }

    private data class Fixture(
        val scheduler: ReminderScheduler,
        val repository: FakeReminderSessionRepository,
        val launcher: RecordingReminderLauncher,
        val session: AppUsageSession,
        val followUpWorkScheduler: RecordingFollowUpWorkScheduler,
        val launchDataBuildCount: () -> Int
    )

    private companion object {
        const val NOW = 2_000_000L
    }
}

private class RecordingFollowUpWorkScheduler : FollowUpReminderWorkScheduler {
    val scheduled = mutableListOf<Pair<Long, Long>>()
    val cancelledSessionIds = mutableListOf<Long>()

    override fun schedule(sessionId: Long, delayMillis: Long) {
        scheduled += sessionId to delayMillis
    }

    override fun cancel(sessionId: Long) {
        cancelledSessionIds += sessionId
    }
}
private class RecordingReminderLauncher(
    private val repository: FakeReminderSessionRepository
) : ReminderLauncher {
    val shown = mutableListOf<ReminderLaunchData>()
    val dismissedSessionIds = mutableListOf<Long>()
    var remindedAtWhenShown: Long? = null

    override fun show(data: ReminderLaunchData) {
        remindedAtWhenShown = repository.session(data.sessionId)?.remindedAt
        shown += data
    }

    override fun returnToFocus(taskId: Long?) = Unit
    override fun returnHome() = Unit
    override fun returnToCustom(packageName: String): CustomReturnResult =
        CustomReturnResult.SUCCESS

    override fun dismiss(sessionId: Long) {
        dismissedSessionIds += sessionId
    }
}

private class FakeReminderSessionRepository(
    session: AppUsageSession,
    previousReminderTimes: List<Long>,
    private val closeImmediatelyBeforeReminderMark: Boolean = false
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

    override suspend fun closeSession(sessionId: Long, endedAt: Long) {
        val current = sessions[sessionId] ?: return
        sessions[sessionId] = current.copy(endedAt = endedAt)
    }

    override suspend fun currentOpenSession(): AppUsageSession? =
        sessions.values.firstOrNull { it.endedAt == null }

    override suspend fun reminderTimesSince(since: Long): List<Long> =
        previous.filter { it >= since } + sessions.values.mapNotNull { it.remindedAt }.filter { it >= since }

    override suspend fun markRemindedIfNeeded(sessionId: Long, remindedAt: Long): Boolean {
        if (closeImmediatelyBeforeReminderMark) {
            closeSession(sessionId, remindedAt)
        }
        val current = sessions[sessionId] ?: return false
        if (current.remindedAt != null || current.endedAt != null) return false
        sessions[sessionId] = current.copy(remindedAt = remindedAt)
        return true
    }

    override suspend fun markUserAction(sessionId: Long, action: String) {
        val current = sessions[sessionId] ?: return
        sessions[sessionId] = current.copy(userAction = action)
    }

    override suspend fun sessionById(id: Long): AppUsageSession? = sessions[id]

    override suspend fun updateRemindedAt(sessionId: Long, remindedAt: Long) {
        val current = sessions[sessionId] ?: return
        if (current.endedAt != null) return
        sessions[sessionId] = current.copy(remindedAt = remindedAt)
    }
}
