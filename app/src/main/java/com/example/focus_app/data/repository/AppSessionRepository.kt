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
}
