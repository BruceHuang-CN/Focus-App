package com.example.focus_app.data.repository

import androidx.room.withTransaction
import com.example.focus_app.data.local.AppDatabase
import com.example.focus_app.data.local.dao.AppUsageSessionDao
import com.example.focus_app.data.local.dao.ReminderDisplayEventDao
import com.example.focus_app.data.local.entity.ReminderDisplayEventEntity

enum class ReminderDisplayKind(val key: String) {
    INITIAL("initial"),
    FOLLOW_UP("follow_up"),
    FORCED_REDISPLAY("forced_redisplay");

    companion object {
        fun fromKey(key: String?): ReminderDisplayKind =
            entries.firstOrNull { it.key == key } ?: INITIAL
    }
}

sealed interface ReminderDisplayResult {
    data class Displayed(val windowCount: Int) : ReminderDisplayResult
    data object QuotaExceeded : ReminderDisplayResult
}

interface ReminderDisplayRepository {
    suspend fun recordDisplay(
        attemptId: String,
        sessionId: Long,
        displayedAt: Long,
        kind: ReminderDisplayKind,
        windowStart: Long,
        limit: Int
    ): ReminderDisplayResult

    suspend fun countSince(since: Long): Int
    suspend fun timesSince(since: Long): List<Long>
    suspend fun resetSince(since: Long)
}

class RoomReminderDisplayRepository(
    private val database: AppDatabase,
    private val displayDao: ReminderDisplayEventDao,
    private val sessionDao: AppUsageSessionDao
) : ReminderDisplayRepository {
    override suspend fun recordDisplay(
        attemptId: String,
        sessionId: Long,
        displayedAt: Long,
        kind: ReminderDisplayKind,
        windowStart: Long,
        limit: Int
    ): ReminderDisplayResult = database.withTransaction {
        database.reminderAnalyticsDao().ensureHistory(com.example.focus_app.data.local.entity.ReminderHistoryStateEntity(
            completeHistoryFrom = displayedAt))
        if (displayDao.byAttemptId(attemptId) != null) {
            return@withTransaction ReminderDisplayResult.Displayed(
                database.reminderAnalyticsDao().quotaCount(windowStart)
            )
        }
        if (limit <= 0 || database.reminderAnalyticsDao().quotaCount(windowStart) >= limit) {
            return@withTransaction ReminderDisplayResult.QuotaExceeded
        }
        val inserted = displayDao.insert(
            ReminderDisplayEventEntity(
                attemptId = attemptId,
                sessionId = sessionId,
                displayedAt = displayedAt,
                kind = kind.key
            )
        )
        if (inserted == -1L) {
            return@withTransaction ReminderDisplayResult.Displayed(
                database.reminderAnalyticsDao().quotaCount(windowStart)
            )
        }
        sessionDao.updateRemindedAt(sessionId, displayedAt)
        ReminderDisplayResult.Displayed(database.reminderAnalyticsDao().quotaCount(windowStart))
    }

    override suspend fun countSince(since: Long): Int = database.reminderAnalyticsDao().quotaCount(since)

    override suspend fun timesSince(since: Long): List<Long> = database.reminderAnalyticsDao().quotaTimes(since)

    override suspend fun resetSince(since: Long) {
        database.withTransaction {
            database.reminderAnalyticsDao().ensureHistory(com.example.focus_app.data.local.entity.ReminderHistoryStateEntity(
                completeHistoryFrom = System.currentTimeMillis()))
            database.reminderAnalyticsDao().resetQuota()
        }
    }
}
