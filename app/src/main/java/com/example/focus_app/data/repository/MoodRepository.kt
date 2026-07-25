package com.example.focus_app.data.repository

import com.example.focus_app.data.local.dao.MoodRecordDao
import com.example.focus_app.data.local.entity.MoodRecordEntity
import com.example.focus_app.domain.model.MoodRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoodRepository @Inject constructor(private val dao: MoodRecordDao) {
    fun getAllMoods(): Flow<List<MoodRecord>> = dao.getAllMoods().map { it.map { e -> e.toDomain() } }
    suspend fun getLatestMood(): MoodRecord? = dao.getLatestMood()?.toDomain()
    suspend fun recordMood(mood: String, note: String? = null) {
        dao.insert(MoodRecordEntity(timestamp = System.currentTimeMillis(), mood = mood, note = note))
    }
    private fun MoodRecordEntity.toDomain() = MoodRecord(id, timestamp, mood, note)
}
