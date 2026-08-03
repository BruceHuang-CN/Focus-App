package com.example.focus_app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.focus_app.data.local.entity.FocusTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusTaskDao {
    @Query("SELECT * FROM focus_tasks ORDER BY isCompleted ASC, updatedAt DESC")
    fun observeAll(): Flow<List<FocusTaskEntity>>

    @Insert
    suspend fun insert(task: FocusTaskEntity): Long

    @Update
    suspend fun update(task: FocusTaskEntity)

    @Delete
    suspend fun delete(task: FocusTaskEntity)

    @Query("UPDATE focus_tasks SET isCompleted = :isCompleted, isManualActive = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, isCompleted: Boolean, updatedAt: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM focus_tasks WHERE id = :id AND isCompleted = 0)")
    suspend fun canSetManualActive(id: Long): Boolean

    @Query("UPDATE focus_tasks SET isManualActive = 0 WHERE isManualActive = 1")
    suspend fun clearManualActive()

    @Query("UPDATE focus_tasks SET isManualActive = 1 WHERE id = :id AND isCompleted = 0")
    suspend fun markManualActive(id: Long)

    @Transaction
    suspend fun setManualActive(id: Long) {
        if (!canSetManualActive(id)) return
        clearManualActive()
        markManualActive(id)
    }

    @Query("SELECT COUNT(*) FROM focus_tasks WHERE isCompleted = 1 AND updatedAt BETWEEN :startedAt AND :endedAt")
    suspend fun completedCountBetween(startedAt: Long, endedAt: Long): Int
}
