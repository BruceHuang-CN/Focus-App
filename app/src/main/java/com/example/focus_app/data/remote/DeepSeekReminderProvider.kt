package com.example.focus_app.data.remote

import com.example.focus_app.data.remote.dto.ChatRequest
import com.example.focus_app.data.remote.dto.Message
import com.example.focus_app.data.remote.dto.ReminderBatchResponse
import com.example.focus_app.data.remote.dto.ResponseFormat
import com.example.focus_app.data.remote.dto.ThinkingConfig
import com.example.focus_app.data.security.ApiKeyStore
import com.example.focus_app.domain.model.ReminderContext
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException

class DeepSeekReminderProvider(
    private val api: OpenAiApi,
    private val apiKeyStore: ApiKeyStore,
    private val model: String = DEFAULT_MODEL,
    private val fallback: AiReminderProvider = LocalReminderProvider(),
    private val includeDeepSeekOptions: Boolean = true
) : AiReminderProvider {
    private val gson = Gson()

    override suspend fun generateBatch(
        context: ReminderContext,
        count: Int
    ): Result<List<String>> {
        val remote = generateRemoteBatch(context, count)
        return if (remote.isSuccess) remote else fallback.generateBatch(context, count)
    }

    /** 强制请求远端；用于用户主动刷新，失败时不得伪装成本地生成成功。 */
    suspend fun generateRemoteBatch(
        context: ReminderContext,
        count: Int
    ): Result<List<String>> {
        return try {
            val key = apiKeyStore.read()
            if (key.isBlank()) return Result.failure(IllegalStateException("API Key 未设置"))
            val response = api.chatCompletion(
                authorization = "Bearer $key",
                request = ChatRequest(
                    model = model.ifBlank { DEFAULT_MODEL },
                    messages = listOf(
                        Message("system", PromptBuilder.buildSystemPrompt(context.tone, context.customToneInstruction)),
                        Message("user", PromptBuilder.buildUserMessage(context, count))
                    ),
                    max_tokens = 500,
                    thinking = if (includeDeepSeekOptions) ThinkingConfig("disabled") else null,
                    response_format = ResponseFormat("json_object")
                )
            )
            if (!response.isSuccessful) {
                return Result.failure(IllegalStateException("AI 服务返回 ${response.code()}"))
            }
            val content = response.body()?.choices?.firstOrNull()?.message?.content
            val messages = parseMessages(content, count)
            if (messages == null) {
                Result.failure(IllegalStateException("AI 返回的文案格式无效"))
            } else {
                Result.success(messages)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun parseMessages(content: String?, count: Int): List<String>? {
        if (content.isNullOrBlank() || count <= 0) return null
        val parsed = try {
            gson.fromJson(content, ReminderBatchResponse::class.java).messages
        } catch (_: Exception) {
            return null
        }
        if (parsed.size < count) return null
        val selected = parsed.take(count).map { it.trim() }
        if (selected.any { it.isBlank() || it.length > 80 }) return null
        if (selected.distinct().size != count) return null
        return selected
    }

    companion object {
        const val DEFAULT_MODEL = "deepseek-v4-flash"
    }
}
