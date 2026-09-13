package com.example.focus_app.data.local.dao

import androidx.room.*
import com.example.focus_app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderAnalyticsDao {
    /** 同一次真实展示后的已落库决策即完成体验，包括返回、休息和有目的使用。 */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM reminder_action_events AS decision_event
            INNER JOIN reminder_display_events AS display
                ON display.attemptId = decision_event.attemptId AND display.sessionId = decision_event.sessionId
            INNER JOIN app_usage_sessions AS session ON session.id = decision_event.sessionId
            WHERE session.taskId = :taskId AND session.packageName IN (:packages)
                AND display.displayedAt >= :since AND decision_event.occurredAt >= display.displayedAt

        )
    """)
    suspend fun tutorialExperienceRecorded(taskId: Long, since: Long, packages: List<String>): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM reminder_action_events WHERE attemptId = :attemptId)")
    suspend fun hasAction(attemptId: String): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAction(event: ReminderActionEventEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensureHistory(state: ReminderHistoryStateEntity): Long
    @Query("SELECT * FROM reminder_history_state WHERE id = 1")
    suspend fun history(): ReminderHistoryStateEntity?
    @Query("UPDATE reminder_history_state SET quotaAfterId = COALESCE((SELECT MAX(id) FROM reminder_display_events),0) WHERE id = 1")
    suspend fun resetQuota()
    @Query("SELECT COUNT(*) FROM reminder_display_events WHERE displayedAt >= :since AND kind != 'forced_redisplay' AND id > COALESCE((SELECT quotaAfterId FROM reminder_history_state WHERE id = 1),0)")
    suspend fun quotaCount(since: Long): Int
    @Query("SELECT displayedAt FROM reminder_display_events WHERE displayedAt >= :since AND kind != 'forced_redisplay' AND id > COALESCE((SELECT quotaAfterId FROM reminder_history_state WHERE id = 1),0) ORDER BY displayedAt,id")
    suspend fun quotaTimes(since: Long): List<Long>
    @Query("SELECT * FROM reminder_display_events WHERE displayedAt >= :from AND displayedAt < :to ORDER BY displayedAt,id")
    fun displays(from: Long, to: Long): Flow<List<ReminderDisplayEventEntity>>
    @Query("SELECT * FROM reminder_action_events WHERE occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt,id")
    fun actions(from: Long, to: Long): Flow<List<ReminderActionEventEntity>>
    @Query("SELECT * FROM app_usage_sessions WHERE startedAt < :to AND (endedAt IS NULL OR endedAt > :from) ORDER BY startedAt,id")
    fun sessions(from: Long, to: Long): Flow<List<AppUsageSessionEntity>>
}
