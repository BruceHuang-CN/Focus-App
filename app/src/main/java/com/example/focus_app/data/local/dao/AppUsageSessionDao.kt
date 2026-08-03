package com.example.focus_app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.focus_app.data.local.entity.AppUsageSessionEntity

@Dao
interface AppUsageSessionDao {
    @Insert
    suspend fun insert(session: AppUsageSessionEntity): Long

    @Query(
        "SELECT * FROM app_usage_sessions " +
            "WHERE endedAt IS NULL ORDER BY startedAt DESC, id DESC LIMIT 1"
    )
    suspend fun currentOpen(): AppUsageSessionEntity?

    @Query(
        "UPDATE app_usage_sessions SET endedAt = :endedAt " +
            "WHERE id = :sessionId AND endedAt IS NULL"
    )
    suspend fun close(sessionId: Long, endedAt: Long): Int

    @Update
    suspend fun update(session: AppUsageSessionEntity)

    @Query("SELECT COUNT(*) FROM app_usage_sessions WHERE remindedAt >= :since")
    suspend fun countRemindersSince(since: Long): Int
}
