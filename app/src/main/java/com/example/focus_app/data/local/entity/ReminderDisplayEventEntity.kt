package com.example.focus_app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminder_display_events",
    indices = [
        Index(value = ["attemptId"], unique = true),
        Index(value = ["displayedAt"])
    ]
)
data class ReminderDisplayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val attemptId: String,
    val sessionId: Long,
    val displayedAt: Long,
    val kind: String
)
