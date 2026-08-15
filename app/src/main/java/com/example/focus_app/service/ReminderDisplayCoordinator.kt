package com.example.focus_app.service

import com.example.focus_app.data.repository.ReminderDisplayRepository
import com.example.focus_app.data.repository.ReminderDisplayResult
import com.example.focus_app.domain.time.SystemClock
import javax.inject.Inject

class ReminderDisplayCoordinator(
    private val displays: ReminderDisplayRepository,
    private val clock: () -> Long
) {
    @Inject
    constructor(displays: ReminderDisplayRepository) : this(displays, SystemClock::nowMillis)

    suspend fun confirm(data: ReminderLaunchData): ReminderLaunchData? {
        if (data.attemptId.isBlank() || data.windowLimit <= 0 || data.windowMinutes <= 0) {
            return null
        }
        val now = clock()
        val since = now - data.windowMinutes * 60_000L
        return when (
            val result = displays.recordDisplay(
                attemptId = data.attemptId,
                sessionId = data.sessionId,
                displayedAt = now,
                kind = data.displayKind,
                windowStart = since,
                limit = data.windowLimit
            )
        ) {
            is ReminderDisplayResult.Displayed ->
                data.copy(windowReminderCount = result.windowCount)
            ReminderDisplayResult.QuotaExceeded -> null
        }
    }
}
