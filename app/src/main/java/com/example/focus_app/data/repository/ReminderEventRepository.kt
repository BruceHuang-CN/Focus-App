package com.example.focus_app.data.repository

import androidx.room.withTransaction
import com.example.focus_app.data.local.AppDatabase
import com.example.focus_app.data.local.entity.ReminderActionEventEntity
import javax.inject.Inject

class ReminderEventRepository @Inject constructor(private val database: AppDatabase) {
    suspend fun canDecide(attemptId: String, sessionId: Long): Boolean = database.withTransaction {
        database.reminderDisplayEventDao().byAttemptId(attemptId)?.sessionId == sessionId &&
            !database.reminderAnalyticsDao().hasAction(attemptId)
    }

    /** One confirmed display can produce at most one decision, even on double taps/recreation. */
    suspend fun record(attemptId: String, sessionId: Long, action: String): Boolean = database.withTransaction {
        val display = database.reminderDisplayEventDao().byAttemptId(attemptId)
        if (display?.sessionId != sessionId) return@withTransaction false
        val inserted = database.reminderAnalyticsDao().insertAction(ReminderActionEventEntity(attemptId = attemptId,
            sessionId = sessionId, occurredAt = System.currentTimeMillis(), actionKey = action)) != -1L
        if (inserted) database.appUsageSessionDao().markUserAction(sessionId, action)
        inserted
    }
}
