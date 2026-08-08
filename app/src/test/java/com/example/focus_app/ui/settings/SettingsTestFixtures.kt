package com.example.focus_app.ui.settings

import com.example.focus_app.data.local.dao.AiReminderCacheDao
import com.example.focus_app.data.local.entity.AiReminderCacheEntity
import com.example.focus_app.data.remote.OpenAiApi
import com.example.focus_app.data.remote.dto.ChatRequest
import com.example.focus_app.data.remote.dto.ChatResponse
import com.example.focus_app.data.remote.dto.ModelInfo
import com.example.focus_app.data.remote.dto.ModelsResponse
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.followup.FollowUpReminderStore
import com.example.focus_app.data.returnapp.CustomReturnAppStore
import com.example.focus_app.data.security.ApiKeyStore
import com.example.focus_app.data.permission.PermissionStatusProvider
import com.example.focus_app.domain.model.AppUsageSession
import retrofit2.Response

class TestSessionRepository : AppSessionRepository {
    val resetCalls = mutableListOf<Long>()

    override suspend fun openSession(
        packageName: String,
        appName: String,
        startedAt: Long,
        taskId: Long?,
        toneKey: String
    ): AppUsageSession = error("unused")

    override suspend fun closeSession(sessionId: Long, endedAt: Long) = Unit
    override suspend fun currentOpenSession(): AppUsageSession? = null
    override suspend fun reminderTimesSince(since: Long): List<Long> = emptyList()
    override suspend fun markRemindedIfNeeded(sessionId: Long, remindedAt: Long): Boolean = true
    override suspend fun markUserAction(sessionId: Long, action: String) = Unit
    override suspend fun resetReminderQuota(since: Long) {
        resetCalls += since
    }
}

class TestCacheDao : AiReminderCacheDao {
    override suspend fun delete(taskId: Long, packageName: String, toneKey: String) = Unit
    override suspend fun insertAll(entries: List<AiReminderCacheEntity>) = Unit
    override suspend fun selectNext(
        taskId: Long,
        packageName: String,
        toneKey: String
    ): AiReminderCacheEntity? = null

    override suspend fun maxLastUsedAt(taskId: Long, packageName: String, toneKey: String): Long? = null
    override suspend fun count(taskId: Long, packageName: String, toneKey: String): Int = 0
    override suspend fun updateLastUsedAt(id: Long, usedAt: Long) = Unit
}

class TestApiKeyStore(private val key: String = "sk-test") : ApiKeyStore {
    override suspend fun read(): String = key
    override suspend fun write(value: String) = Unit
    override suspend fun clear() = Unit
}

class TestOpenAiApi : OpenAiApi {
    override suspend fun chatCompletion(
        authorization: String,
        request: ChatRequest
    ): Response<ChatResponse> = Response.success(ChatResponse())

    override suspend fun listModels(authorization: String): Response<ModelsResponse> =
        Response.success(ModelsResponse(data = listOf(ModelInfo("deepseek-v4-flash"))))
}

class TestPermissionProvider : PermissionStatusProvider {
    override fun accessibilityEnabled(): Boolean = true
    override fun usageStatsGranted(): Boolean = true
    override fun notificationGranted(): Boolean = true
    override fun overlayGranted(): Boolean = true
}

class TestCustomReturnAppStore : CustomReturnAppStore {
    var value: String = ""
    override fun read(): String = value
    override fun write(packageName: String) {
        value = packageName.trim()
    }
}

class TestFollowUpReminderStore : FollowUpReminderStore {
    var minutes: Int = 10
    override fun readMinutes(): Int = minutes
    override fun writeMinutes(minutes: Int) {
        this.minutes = minutes.coerceIn(1, 120)
    }
}
