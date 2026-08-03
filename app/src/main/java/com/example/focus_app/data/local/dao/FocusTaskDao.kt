package com.example.focus_app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.focus_app.data.local.entity.FocusTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusTaskDao {
    @Query("SELECT * FROM focus_tasks ORDER BY isCompleted ASC, updatedAt DESC")
    fun observeAll(): Flow<List<FocusTaskEntity>>
}
