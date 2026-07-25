package com.example.focus_app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_usage_events")
data class AppUsageEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val openTime: Long,
    val reminded: Boolean = false,
    val userAction: String? = null,
    val hourBucket: String
)
