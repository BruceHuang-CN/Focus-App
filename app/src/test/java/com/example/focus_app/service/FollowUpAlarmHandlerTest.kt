package com.example.focus_app.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpAlarmHandlerTest {
    @Test
    fun show_cancels_remaining_paths() = runTest {
        val scheduler = RecordingAlarmFollowUpScheduler()
        val handler = FollowUpAlarmHandler(
            executor = FixedAlarmExecutor(FollowUpDecision.SHOW),
            scheduler = scheduler,
            alarmScheduler = RecordingAlarmWakeScheduler()
        )

        handler.handle(7L)

        assertEquals(listOf(7L), scheduler.cancelled)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun skip_cancels_remaining_paths() = runTest {
        val scheduler = RecordingAlarmFollowUpScheduler()
        val handler = FollowUpAlarmHandler(
            executor = FixedAlarmExecutor(FollowUpDecision.SKIP),
            scheduler = scheduler,
            alarmScheduler = RecordingAlarmWakeScheduler()
        )

        handler.handle(7L)

        assertEquals(listOf(7L), scheduler.cancelled)
    }

    @Test
    fun retry_rearms_system_alarm_with_incremented_attempt() = runTest {
        val alarm = RecordingAlarmWakeScheduler()
        val handler = FollowUpAlarmHandler(
            executor = FixedAlarmExecutor(FollowUpDecision.RETRY),
            scheduler = RecordingAlarmFollowUpScheduler(),
            alarmScheduler = alarm
        )

        handler.handle(sessionId = 7L, retryAttempt = 3)

        assertEquals(
            listOf(Triple(7L, HybridFollowUpScheduler.RETRY_INTERVAL_MS, 4)),
            alarm.scheduled
        )
    }

    @Test
    fun retry_at_limit_does_not_create_unbounded_alarm_loop() = runTest {
        val alarm = RecordingAlarmWakeScheduler()
        val handler = FollowUpAlarmHandler(
            executor = FixedAlarmExecutor(FollowUpDecision.RETRY),
            scheduler = RecordingAlarmFollowUpScheduler(),
            alarmScheduler = alarm
        )

        handler.handle(
            sessionId = 7L,
            retryAttempt = HybridFollowUpScheduler.MAX_RETRY_ATTEMPTS
        )

        assertTrue(alarm.scheduled.isEmpty())
    }

    @Test
    fun invalid_session_id_does_not_execute_or_schedule() = runTest {
        val executor = FixedAlarmExecutor(FollowUpDecision.SHOW)
        val scheduler = RecordingAlarmFollowUpScheduler()
        val alarm = RecordingAlarmWakeScheduler()

        FollowUpAlarmHandler(executor, scheduler, alarm).handle(0L)

        assertTrue(executor.executed.isEmpty())
        assertTrue(scheduler.cancelled.isEmpty())
        assertTrue(alarm.scheduled.isEmpty())
    }
}

private class RecordingAlarmWakeScheduler : FollowUpAlarmScheduler {
    val scheduled = mutableListOf<Triple<Long, Long, Int>>()
    val cancelled = mutableListOf<Long>()

    override fun schedule(sessionId: Long, delayMillis: Long, retryAttempt: Int) {
        scheduled += Triple(sessionId, delayMillis, retryAttempt)
    }

    override fun cancel(sessionId: Long) {
        cancelled += sessionId
    }
}

private class RecordingAlarmFollowUpScheduler : FollowUpScheduler {
    val scheduled = mutableListOf<Pair<Long, Long>>()
    val cancelled = mutableListOf<Long>()

    override fun schedule(sessionId: Long, delayMillis: Long) {
        scheduled += sessionId to delayMillis
    }

    override fun cancel(sessionId: Long) {
        cancelled += sessionId
    }
}

private class FixedAlarmExecutor(
    private val decision: FollowUpDecision
) : FollowUpExecutor {
    val executed = mutableListOf<Long>()

    override suspend fun execute(sessionId: Long): FollowUpDecision {
        executed += sessionId
        return decision
    }
}
