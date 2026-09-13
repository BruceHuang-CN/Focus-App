package com.example.focus_app.data.local.dao

import androidx.room.*
import com.example.focus_app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskGroupDao {
    @Transaction @Query("SELECT * FROM task_groups ORDER BY sortOrder, id")
    fun observeGroupsWithTasks(): Flow<List<TaskGroupWithTasks>>
    @Query("SELECT * FROM task_groups ORDER BY sortOrder, id")
    suspend fun all(): List<TaskGroupEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(group: TaskGroupEntity): Long
    @Update suspend fun update(group: TaskGroupEntity)
    @Query("DELETE FROM task_groups WHERE id = :id AND id != 1")
    suspend fun delete(id: Long)
    @Query("SELECT * FROM focus_tasks WHERE id = :id")
    suspend fun task(id: Long): FocusTaskEntity?
    @Query("SELECT * FROM focus_tasks WHERE groupId = :id ORDER BY sortOrder, id")
    suspend fun tasks(id: Long): List<FocusTaskEntity>
    @Query("UPDATE focus_tasks SET isManualActive = 0, manualUntil = NULL, manualStartedAt = NULL WHERE isManualActive = 1")
    suspend fun clearManual()
    @Query("UPDATE focus_tasks SET isManualActive = 1, manualStartedAt = :start, manualUntil = :end WHERE id = :id AND isCompleted = 0")
    suspend fun activate(id: Long, start: Long, end: Long)
}
