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
        val executor = RecordingExecutor { FollowUpDecision.SHOW }
        val scheduler = HybridFollowUpScheduler(executor, work, backgroundScope)

        scheduler.schedule(100L, 60_000L)
        advanceTimeBy(59_999L)
        runCurrent()
        assertEquals(0, executor.executedSessionIds.size)

        advanceTimeBy(1L)
        runCurrent()

        assertEquals(listOf(100L), executor.executedSessionIds)
        assertEquals(listOf(100L), work.cancelledSessionIds)
        assertEquals(listOf(100L to 60_000L), work.scheduled)
    }

    @Test
    fun schedule_still_enqueues_work_manager_fallback_for_process_death() = runTest {
        val work = RecordingWorkScheduler()
        val scheduler = HybridFollowUpScheduler(
            RecordingExecutor { FollowUpDecision.SHOW },
            work,
            backgroundScope
        )

        scheduler.schedule(100L, 60_000L)

        assertEquals(listOf(100L to 60_000L), work.scheduled)
    }

    @Test
    fun cancel_stops_pending_in_process_execution() = runTest {
        val executor = RecordingExecutor { FollowUpDecision.SHOW }
        val scheduler = HybridFollowUpScheduler(executor, RecordingWorkScheduler(), backgroundScope)

        scheduler.schedule(100L, 60_000L)
        scheduler.cancel(100L)
        advanceTimeBy(120_000L)
        runCurrent()

        assertEquals(0, executor.executedSessionIds.size)
    }

    @Test
    fun retry_waits_and_then_shows_reminder() = runTest {
        val decisions = ArrayDeque(listOf(FollowUpDecision.RETRY, FollowUpDecision.SHOW))
        val executor = RecordingExecutor { decisions.removeFirst() }
        val scheduler = HybridFollowUpScheduler(executor, RecordingWorkScheduler(), backgroundScope)

        scheduler.schedule(100L, 60_000L)
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(listOf(100L), executor.executedSessionIds)

        advanceTimeBy(HybridFollowUpScheduler.RETRY_INTERVAL_MS)
        runCurrent()

        assertEquals(listOf(100L, 100L), executor.executedSessionIds)
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
