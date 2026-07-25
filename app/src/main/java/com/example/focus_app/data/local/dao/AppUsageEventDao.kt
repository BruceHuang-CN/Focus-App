package com.example.focus_app.data.local.dao

import androidx.room.*
import com.example.focus_app.data.local.entity.AppUsageEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUsageEventDao {
    @Insert suspend fun insert(event: AppUsageEventEntity): Long
    @Update suspend fun update(event: AppUsageEventEntity)

    @Query("SELECT * FROM app_usage_events ORDER BY openTime DESC")
    fun getAllEvents(): Flow<List<AppUsageEventEntity>>

    @Query("SELECT * FROM app_usage_events WHERE hourBucket = :bucket")
    suspend fun getEventsByHourBucket(bucket: String): List<AppUsageEventEntity>

    @Query("SELECT COUNT(*) FROM app_usage_events WHERE hourBucket = :bucket AND reminded = 1")
    suspend fun getRemindedCountByHourBucket(bucket: String): Int

    @Query("SELECT * FROM app_usage_events WHERE openTime >= :since ORDER BY openTime DESC")
    fun getEventsSince(since: Long): Flow<List<AppUsageEventEntity>>

    @Query("SELECT COUNT(*) FROM app_usage_events WHERE openTime >= :since")
    suspend fun getEventCountSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM app_usage_events WHERE openTime >= :since AND userAction = 'exited'")
    suspend fun getExitedCountSince(since: Long): Int

    @Query("SELECT * FROM app_usage_events WHERE openTime >= :since AND reminded = 1 ORDER BY openTime DESC")
    fun getRemindedEventsSince(since: Long): Flow<List<AppUsageEventEntity>>
}
