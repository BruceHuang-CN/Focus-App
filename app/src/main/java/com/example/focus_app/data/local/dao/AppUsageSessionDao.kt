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
        "UPDATE app_usage_sessions SET endedAt = :endedAt, snoozeUntil = NULL " +
            "WHERE id = :sessionId AND endedAt IS NULL"
    )
    suspend fun close(sessionId: Long, endedAt: Long): Int

    @Update
    suspend fun update(session: AppUsageSessionEntity)

    @Query("SELECT COUNT(*) FROM app_usage_sessions WHERE remindedAt >= :since")
    suspend fun countRemindersSince(since: Long): Int

    @Query(
        "SELECT remindedAt FROM app_usage_sessions " +
            "WHERE remindedAt IS NOT NULL AND remindedAt >= :since"
    )
    suspend fun reminderTimesSince(since: Long): List<Long?>

    @Query(
        "UPDATE app_usage_sessions SET remindedAt = :remindedAt " +
            "WHERE id = :sessionId AND remindedAt IS NULL AND endedAt IS NULL"
    )
    suspend fun markRemindedIfNeeded(sessionId: Long, remindedAt: Long): Int

    @Query("UPDATE app_usage_sessions SET userAction = :action WHERE id = :sessionId")
    suspend fun markUserAction(sessionId: Long, action: String): Int

    @Query(
        "SELECT COUNT(*) FROM app_usage_sessions " +
            "WHERE packageName = :packageName AND startedAt >= :since"
    )
    suspend fun countOpensSince(packageName: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM app_usage_sessions WHERE remindedAt >= :since")
    suspend fun countShownRemindersSince(since: Long): Int

    @Query(
        "SELECT * FROM app_usage_sessions " +
            "WHERE startedAt >= :from AND startedAt < :to ORDER BY startedAt ASC"
    )
    suspend fun sessionsBetween(from: Long, to: Long): List<AppUsageSessionEntity>

    @Query(
        "UPDATE app_usage_sessions SET remindedAt = NULL " +
            "WHERE remindedAt >= :since"
    )
    suspend fun clearRemindedSince(since: Long): Int

    @Query("SELECT * FROM app_usage_sessions WHERE id = :id")
    suspend fun byId(id: Long): AppUsageSessionEntity?

    @Query(
        "UPDATE app_usage_sessions SET remindedAt = :remindedAt " +
            "WHERE id = :sessionId AND endedAt IS NULL"
    )
    suspend fun updateRemindedAt(sessionId: Long, remindedAt: Long): Int

    @Query(
        "UPDATE app_usage_sessions SET snoozeUntil = :value " +
            "WHERE id = :sessionId AND endedAt IS NULL"
    )
    suspend fun setSnoozeUntil(sessionId: Long, value: Long?): Int

    @Query(
        "UPDATE app_usage_sessions SET snoozeUntil = NULL " +
            "WHERE id = :sessionId AND endedAt IS NULL AND snoozeUntil IS NOT NULL"
    )
    suspend fun claimSnooze(sessionId: Long): Int

    @Query(
        "SELECT * FROM app_usage_sessions " +
            "WHERE endedAt IS NULL AND snoozeUntil IS NOT NULL " +
            "ORDER BY snoozeUntil ASC"
    )
    suspend fun pendingSnoozes(): List<AppUsageSessionEntity>

    @Query(
        "SELECT COUNT(*) FROM app_usage_sessions WHERE remindedAt >= :since " +
            "AND userAction IN ('returned_to_focus', 'returned_home')"
    )
    suspend fun countActiveExitsSince(since: Long): Int
}
