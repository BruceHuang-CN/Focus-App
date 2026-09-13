package com.example.focus_app.data.repository

import android.content.Context
import com.example.focus_app.data.language.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val clock: Clock,
    private val currentLanguage: () -> String = { "zh-CN" }
) {
    @Inject
    constructor(dao: AiReminderCacheDao, @ApplicationContext context: Context) :
        this(dao, SystemClock, { AppLanguage.tag(context) })

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
        messages: List<String>,
        languageTag: String = currentLanguage()
    ) {
        val normalized = messages.map { it.trim() }
        require(normalized.size == 3 && normalized.distinct().size == 3)
        require(normalized.all { it.isNotBlank() && it.length <= 80 })
        val now = clock.nowMillis()
        dao.replace(
            taskId,
            packageName,
            languageToneKey(toneKey, languageTag),
            normalized.map { message ->
                AiReminderCacheEntity(
                    taskId = taskId,
                    appPackageName = packageName,
                    toneKey = languageToneKey(toneKey, languageTag),
                    text = message,
                    createdAt = now
                )
            }
        )
    }

    suspend fun next(taskId: Long, packageName: String, toneKey: String, languageTag: String = currentLanguage()): String? =
        dao.takeNext(taskId, packageName, languageToneKey(toneKey, languageTag), clock.nowMillis())?.text

    suspend fun isReady(taskId: Long, packageName: String, toneKey: String, languageTag: String = currentLanguage()): Boolean =
        dao.count(taskId, packageName, languageToneKey(toneKey, languageTag)) == 3

    companion object {
        /** Keep existing Chinese rows readable; isolate English without a database migration. */
        fun languageToneKey(toneKey: String, languageTag: String): String =
            if (languageTag.startsWith("en", ignoreCase = true)) "en:$toneKey" else toneKey
    }
}
