package com.example.focus_app.data.local.dao

import androidx.room.*
import com.example.focus_app.data.local.entity.MoodRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodRecordDao {
    @Insert suspend fun insert(mood: MoodRecordEntity)
    @Query("SELECT * FROM mood_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMood(): MoodRecordEntity?
    @Query("SELECT * FROM mood_records ORDER BY timestamp DESC")
    fun getAllMoods(): Flow<List<MoodRecordEntity>>
}
