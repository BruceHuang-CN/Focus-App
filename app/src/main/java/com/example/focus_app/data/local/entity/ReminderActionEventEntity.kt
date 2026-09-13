package com.example.focus_app.data.local.entity

import androidx.room.*

@Entity(tableName = "reminder_action_events", indices = [Index(value = ["attemptId"], unique = true), Index(value = ["occurredAt"])])
data class ReminderActionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val attemptId: String, val sessionId: Long, val occurredAt: Long, val actionKey: String
)
@Entity(tableName = "reminder_history_state")
data class ReminderHistoryStateEntity(
    @PrimaryKey val id: Int = 1,
    val quotaAfterId: Long = 0,
    val completeHistoryFrom: Long
)
