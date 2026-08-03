package com.example.focus_app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.focus_app.data.local.entity.AiReminderCacheEntity

@Dao
interface AiReminderCacheDao {
    @Query(
        """SELECT * FROM ai_reminder_cache
            WHERE taskId = :taskId
              AND appPackageName = :packageName
              AND toneKey = :toneKey
            ORDER BY lastUsedAt ASC, id ASC
            LIMIT 1"""
    )
    suspend fun next(
        taskId: Long,
        packageName: String,
        toneKey: String
    ): AiReminderCacheEntity?
}
