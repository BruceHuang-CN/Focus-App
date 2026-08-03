package com.example.focus_app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_reminder_cache",
    indices = [Index(value = ["taskId", "appPackageName", "toneKey"])]
)
data class AiReminderCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val appPackageName: String,
    val toneKey: String,
    val text: String,
    val createdAt: Long,
    val lastUsedAt: Long? = null
)
