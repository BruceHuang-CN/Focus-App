package com.example.focus_app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.focus_app.data.local.entity.AiReminderCacheEntity

@Dao
interface AiReminderCacheDao {
    @Query(
        "DELETE FROM ai_reminder_cache WHERE taskId = :taskId " +
            "AND appPackageName = :packageName AND toneKey = :toneKey"
    )
    suspend fun delete(taskId: Long, packageName: String, toneKey: String)

    @Insert
    suspend fun insertAll(entries: List<AiReminderCacheEntity>)

    @Query(
        """SELECT * FROM ai_reminder_cache
            WHERE taskId = :taskId
              AND appPackageName = :packageName
              AND toneKey = :toneKey
            ORDER BY lastUsedAt ASC, id ASC
            LIMIT 1"""
    )
    suspend fun selectNext(
        taskId: Long,
        packageName: String,
        toneKey: String
    ): AiReminderCacheEntity?

    @Query(
        "SELECT MAX(lastUsedAt) FROM ai_reminder_cache WHERE taskId = :taskId " +
            "AND appPackageName = :packageName AND toneKey = :toneKey"
    )
    suspend fun maxLastUsedAt(taskId: Long, packageName: String, toneKey: String): Long?

    @Query("UPDATE ai_reminder_cache SET lastUsedAt = :usedAt WHERE id = :id")
    suspend fun updateLastUsedAt(id: Long, usedAt: Long)

    @Transaction
    suspend fun replace(
        taskId: Long,
        packageName: String,
        toneKey: String,
        entries: List<AiReminderCacheEntity>
    ) {
        delete(taskId, packageName, toneKey)
        insertAll(entries)
    }

    @Transaction
    suspend fun takeNext(
        taskId: Long,
        packageName: String,
        toneKey: String,
        now: Long
    ): AiReminderCacheEntity? {
        val entry = selectNext(taskId, packageName, toneKey) ?: return null
        val lastUsed = maxLastUsedAt(taskId, packageName, toneKey)
        val usedAt = if (lastUsed == null) now else maxOf(now, lastUsed + 1)
        updateLastUsedAt(entry.id, usedAt)
        return entry
    }
}
