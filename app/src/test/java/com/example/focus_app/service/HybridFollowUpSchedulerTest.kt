package com.example.focus_app.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HybridFollowUpSchedulerTest {

    @Test
    fun schedule_triggers_in_process_execution_after_delay_and_cancels_fallback() = runTest {
        val work = RecordingWorkScheduler()
        val alarm = RecordingAlarmScheduler()
        val executor = RecordingExecutor { FollowUpDecision.SHOW }
        val scheduler = HybridFollowUpScheduler(executor, work, alarm, backgroundScope)

        scheduler.schedule(100L, 60_000L)
        advanceTimeBy(59_999L)
        runCurrent()
        assertEquals(0, executor.executedSessionIds.size)

        advanceTimeBy(1L)
        runCurrent()

        assertEquals(listOf(100L), executor.executedSessionIds)
        assertEquals(listOf(100L), work.cancelledSessionIds)
        assertEquals(listOf(100L to 60_000L), work.scheduled)
        assertEquals(listOf(Triple(100L, 60_000L, 0)), alarm.scheduled)
        assertEquals(listOf(100L), alarm.cancelledSessionIds)
    }

    @Test
    fun schedule_still_enqueues_work_manager_fallback_for_process_death() = runTest {
        val work = RecordingWorkScheduler()
        val alarm = RecordingAlarmScheduler()
        val scheduler = HybridFollowUpScheduler(
            RecordingExecutor { FollowUpDecision.SHOW },
            work,
            alarm,
            backgroundScope
        )

        scheduler.schedule(100L, 60_000L)

        assertEquals(listOf(100L to 60_000L), work.scheduled)
        assertEquals(listOf(Triple(100L, 60_000L, 0)), alarm.scheduled)
    }

    @Test
    fun cancel_stops_pending_in_process_execution() = runTest {
        val executor = RecordingExecutor { FollowUpDecision.SHOW }
        val alarm = RecordingAlarmScheduler()
        val scheduler = HybridFollowUpScheduler(
            executor,
            RecordingWorkScheduler(),
            alarm,
            backgroundScope
        )

        scheduler.schedule(100L, 60_000L)
        scheduler.cancel(100L)
        advanceTimeBy(120_000L)
        runCurrent()

        assertEquals(0, executor.executedSessionIds.size)
        assertEquals(listOf(100L), alarm.cancelledSessionIds)
    }

    @Test
    fun retry_waits_and_then_shows_reminder() = runTest {
        val decisions = ArrayDeque(listOf(FollowUpDecision.RETRY, FollowUpDecision.SHOW))
        val executor = RecordingExecutor { decisions.removeFirst() }
        val alarm = RecordingAlarmScheduler()
        val scheduler = HybridFollowUpScheduler(
            executor,
            RecordingWorkScheduler(),
            alarm,
            backgroundScope
        )

        scheduler.schedule(100L, 60_000L)
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(listOf(100L), executor.executedSessionIds)

        advanceTimeBy(HybridFollowUpScheduler.RETRY_INTERVAL_MS)
        runCurrent()

        assertEquals(listOf(100L, 100L), executor.executedSessionIds)
        assertEquals(
            listOf(
                Triple(100L, 60_000L, 0),
                Triple(100L, HybridFollowUpScheduler.RETRY_INTERVAL_MS, 1)
            ),
            alarm.scheduled
        )
    }

    private class RecordingWorkScheduler : FollowUpReminderWorkScheduler {
        val scheduled = mutableListOf<Pair<Long, Long>>()
        val cancelledSessionIds = mutableListOf<Long>()

        override fun schedule(sessionId: Long, delayMillis: Long) {
            scheduled += sessionId to delayMillis
        }

        override fun cancel(sessionId: Long) {
            cancelledSessionIds += sessionId
        }
    }

    private class RecordingAlarmScheduler : FollowUpAlarmScheduler {
        val scheduled = mutableListOf<Triple<Long, Long, Int>>()
        val cancelledSessionIds = mutableListOf<Long>()

        override fun schedule(sessionId: Long, delayMillis: Long, retryAttempt: Int) {
            scheduled += Triple(sessionId, delayMillis, retryAttempt)
        }

        override fun cancel(sessionId: Long) {
            cancelledSessionIds += sessionId
        }
    }

    private class RecordingExecutor(
        private val decider: () -> FollowUpDecision
    ) : FollowUpExecutor {
        val executedSessionIds = mutableListOf<Long>()

        override suspend fun execute(sessionId: Long): FollowUpDecision {
            executedSessionIds += sessionId
            return decider()
        }
    }
}
