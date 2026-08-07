package com.example.focus_app.data.repository

import com.example.focus_app.data.local.dao.AiReminderCacheDao
import com.example.focus_app.data.local.entity.AiReminderCacheEntity
import com.example.focus_app.domain.time.Clock
import com.example.focus_app.domain.time.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderCacheRepository(
    private val dao: AiReminderCacheDao,
    private val clock: Clock
) {
    @Inject
    constructor(dao: AiReminderCacheDao) : this(dao, SystemClock)

    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    /** 请求后台重新生成当前任务的 AI 提醒缓存。 */
    fun requestRegeneration() {
        mutableRevision.update { it + 1 }
    }

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

    suspend fun isReady(taskId: Long, packageName: String, toneKey: String): Boolean =
        dao.count(taskId, packageName, toneKey) == 3
}
