package com.example.focus_app.service

import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.domain.model.AppUsageSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingFollowUpRestorerTest {
    @Test
    fun restore_reschedules_open_snooze_using_remaining_delay() = runTest {
        val scheduler = RestorerRecordingFollowUpScheduler()
        val restorer = PendingFollowUpRestorer(
            repository = PendingSessionRepository(
                listOf(session(id = 7L, snoozeUntil = 70_000L))
            ),
            scheduler = scheduler,
            nowMillis = { 40_000L }
        )

        restorer.restore()

        assertEquals(listOf(7L to 30_000L), scheduler.scheduled)
    }

    @Test
    fun overdue_snooze_is_restored_without_an_extra_delay() = runTest {
        val scheduler = RestorerRecordingFollowUpScheduler()
        val restorer = PendingFollowUpRestorer(
            repository = PendingSessionRepository(
                listOf(session(id = 8L, snoozeUntil = 30_000L))
            ),
            scheduler = scheduler,
            nowMillis = { 40_000L }
        )

        restorer.restore()

        assertEquals(listOf(8L to 0L), scheduler.scheduled)
    }

    private fun session(id: Long, snoozeUntil: Long) = AppUsageSession(
        id = id,
        packageName = "com.xingin.xhs",
        appName = "小红书",
        startedAt = 1_000L,
        toneKey = "gentle",
        snoozeUntil = snoozeUntil
    )
}

private class RestorerRecordingFollowUpScheduler : FollowUpScheduler {
    val scheduled = mutableListOf<Pair<Long, Long>>()

    override fun schedule(sessionId: Long, delayMillis: Long) {
        scheduled += sessionId to delayMillis
    }

    override fun cancel(sessionId: Long) = Unit
}

private class PendingSessionRepository(
    private val pending: List<AppUsageSession>
) : AppSessionRepository {
    override suspend fun openSession(
        packageName: String,
        appName: String,
        startedAt: Long,
        taskId: Long?,
        toneKey: String
    ): AppUsageSession = error("Not used")

    override suspend fun closeSession(sessionId: Long, endedAt: Long) = Unit
    override suspend fun currentOpenSession(): AppUsageSession? = pending.lastOrNull()
    override suspend fun reminderTimesSince(since: Long): List<Long> = emptyList()
    override suspend fun markRemindedIfNeeded(sessionId: Long, remindedAt: Long): Boolean = false
    override suspend fun markUserAction(sessionId: Long, action: String) = Unit
    override suspend fun pendingSnoozes(): List<AppUsageSession> = pending
}
