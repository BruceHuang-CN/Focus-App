package com.example.focus_app.data.repository

import com.example.focus_app.data.local.dao.AppUsageSessionDao
import com.example.focus_app.data.local.entity.AppUsageSessionEntity
import com.example.focus_app.data.local.entity.toDomain
import com.example.focus_app.domain.model.AppUsageSession
import javax.inject.Inject

interface AppSessionRepository {
    suspend fun openSession(
        packageName: String,
        appName: String,
        startedAt: Long,
        taskId: Long?,
        toneKey: String
    ): AppUsageSession

    suspend fun closeSession(sessionId: Long, endedAt: Long)

    suspend fun currentOpenSession(): AppUsageSession?

    suspend fun reminderTimesSince(since: Long): List<Long>

    suspend fun markRemindedIfNeeded(sessionId: Long, remindedAt: Long): Boolean

    suspend fun markUserAction(sessionId: Long, action: String)

    suspend fun countOpensSince(packageName: String, since: Long): Int = 0

    suspend fun countShownRemindersSince(since: Long): Int = 0

    suspend fun countActiveExitsSince(since: Long): Int = 0

    suspend fun sessionsBetween(from: Long, to: Long): List<AppUsageSession> = emptyList()
}

class RoomAppSessionRepository @Inject constructor(
    private val dao: AppUsageSessionDao
) : AppSessionRepository {
    override suspend fun openSession(
        packageName: String,
        appName: String,
        startedAt: Long,
        taskId: Long?,
        toneKey: String
    ): AppUsageSession {
        val session = AppUsageSessionEntity(
            packageName = packageName,
            appName = appName,
            startedAt = startedAt,
            taskId = taskId,
            toneKey = toneKey
        )
        return session.copy(id = dao.insert(session)).toDomain()
    }

    override suspend fun closeSession(sessionId: Long, endedAt: Long) {
        dao.close(sessionId, endedAt)
    }

    override suspend fun currentOpenSession(): AppUsageSession? =
        dao.currentOpen()?.toDomain()

    override suspend fun reminderTimesSince(since: Long): List<Long> =
        dao.reminderTimesSince(since).filterNotNull()

    override suspend fun markRemindedIfNeeded(sessionId: Long, remindedAt: Long): Boolean =
        dao.markRemindedIfNeeded(sessionId, remindedAt) == 1

    override suspend fun markUserAction(sessionId: Long, action: String) {
        dao.markUserAction(sessionId, action)
    }

    override suspend fun countOpensSince(packageName: String, since: Long): Int =
        dao.countOpensSince(packageName, since)

    override suspend fun countShownRemindersSince(since: Long): Int =
        dao.countShownRemindersSince(since)

    override suspend fun countActiveExitsSince(since: Long): Int =
        dao.countActiveExitsSince(since)

    override suspend fun sessionsBetween(from: Long, to: Long): List<AppUsageSession> =
        dao.sessionsBetween(from, to).map { it.toDomain() }
}
