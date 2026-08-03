package com.example.focus_app.data.local.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface AppUsageSessionDao {
    @Query("SELECT COUNT(*) FROM app_usage_sessions WHERE remindedAt >= :since")
    suspend fun countRemindersSince(since: Long): Int
}
