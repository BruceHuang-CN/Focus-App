package com.example.focus_app.service

import com.example.focus_app.data.repository.ReminderDisplayKind
import com.example.focus_app.data.repository.ReminderDisplayRepository
import com.example.focus_app.data.repository.ReminderDisplayResult
import com.example.focus_app.domain.model.ReturnDestination
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderDisplayCoordinatorTest {
    @Test
    fun confirmation_records_attempt_and_returns_actual_window_count() = runTest {
        val repository = FakeReminderDisplayRepository(ReminderDisplayResult.Displayed(2))
        val coordinator = ReminderDisplayCoordinator(repository, clock = { 10_000L })

        val confirmed = coordinator.confirm(LAUNCH_DATA)

        assertEquals(2, confirmed?.windowReminderCount)
        assertEquals(listOf("attempt-1"), repository.attemptIds)
        assertEquals(listOf(10_000L - 30 * 60_000L), repository.windowStarts)
    }

    @Test
    fun quota_rejection_returns_null() = runTest {
        val coordinator = ReminderDisplayCoordinator(
            FakeReminderDisplayRepository(ReminderDisplayResult.QuotaExceeded),
            clock = { 10_000L }
        )

        assertNull(coordinator.confirm(LAUNCH_DATA))
    }

    private class FakeReminderDisplayRepository(
        private val result: ReminderDisplayResult
    ) : ReminderDisplayRepository {
        val attemptIds = mutableListOf<String>()
        val windowStarts = mutableListOf<Long>()

        override suspend fun recordDisplay(
            attemptId: String,
            sessionId: Long,
            displayedAt: Long,
            kind: ReminderDisplayKind,
            windowStart: Long,
            limit: Int
        ): ReminderDisplayResult {
            attemptIds += attemptId
            windowStarts += windowStart
            return result
        }

        override suspend fun countSince(since: Long): Int = 0
        override suspend fun timesSince(since: Long): List<Long> = emptyList()
        override suspend fun resetSince(since: Long) = Unit
    }

    private companion object {
        val LAUNCH_DATA = ReminderLaunchData(
            sessionId = 7L,
            taskId = null,
            taskTitle = null,
            appName = "Target",
            message = "Pause",
            showBreathing = false,
            returnDestination = ReturnDestination.HOME,
            windowLimit = 3,
            windowMinutes = 30,
            attemptId = "attempt-1",
            displayKind = ReminderDisplayKind.FOLLOW_UP
        )
    }
}
