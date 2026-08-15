package com.example.focus_app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.focus_app.data.local.entity.ReminderDisplayEventEntity

@Dao
interface ReminderDisplayEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: ReminderDisplayEventEntity): Long

    @Query("SELECT * FROM reminder_display_events WHERE attemptId = :attemptId LIMIT 1")
    suspend fun byAttemptId(attemptId: String): ReminderDisplayEventEntity?

    @Query("SELECT COUNT(*) FROM reminder_display_events WHERE displayedAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query(
        "SELECT displayedAt FROM reminder_display_events " +
            "WHERE displayedAt >= :since ORDER BY displayedAt ASC, id ASC"
    )
    suspend fun timesSince(since: Long): List<Long>

    @Query("DELETE FROM reminder_display_events WHERE displayedAt >= :since")
    suspend fun deleteSince(since: Long): Int
}
