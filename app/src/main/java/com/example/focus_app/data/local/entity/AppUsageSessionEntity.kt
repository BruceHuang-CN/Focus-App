package com.example.focus_app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_usage_sessions",
    indices = [Index("packageName"), Index("startedAt"), Index("remindedAt")]
)
data class AppUsageSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val taskId: Long? = null,
    val remindedAt: Long? = null,
    val userAction: String? = null,
    val toneKey: String
)
