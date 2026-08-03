package com.example.focus_app.data.repository

import com.example.focus_app.data.local.dao.AiReminderCacheDao
import com.example.focus_app.data.local.entity.AiReminderCacheEntity
import com.example.focus_app.domain.time.Clock
import com.example.focus_app.domain.time.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderCacheRepository(
    private val dao: AiReminderCacheDao,
    private val clock: Clock
) {
    @Inject
    constructor(dao: AiReminderCacheDao) : this(dao, SystemClock)

    suspend fun replace(
        taskId: Long,
        packageName: String,
        toneKey: String,
        messages: List<String>
    ) {
        val normalized = messages.map { it.trim() }
        require(normalized.size == 3 && normalized.distinct().size == 3)
        require(normalized.all { it.isNotBlank() && it.length <= 80 })
        val now = clock.nowMillis()
        dao.replace(
            taskId,
            packageName,
            toneKey,
            normalized.map { message ->
                AiReminderCacheEntity(
                    taskId = taskId,
                    appPackageName = packageName,
                    toneKey = toneKey,
                    text = message,
                    createdAt = now
                )
            }
        )
    }

    suspend fun next(taskId: Long, packageName: String, toneKey: String): String? =
        dao.takeNext(taskId, packageName, toneKey, clock.nowMillis())?.text
}
