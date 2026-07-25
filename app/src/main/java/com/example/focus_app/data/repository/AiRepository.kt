package com.example.focus_app.data.repository

import com.example.focus_app.data.remote.OpenAiApi
import com.example.focus_app.data.remote.PromptBuilder
import com.example.focus_app.data.remote.dto.ChatRequest
import com.example.focus_app.data.remote.dto.Message
import com.example.focus_app.domain.model.ReminderContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor() {
    private val apiCache = ConcurrentHashMap<String, OpenAiApi>()

    suspend fun generateReminder(context: ReminderContext, settings: AppSettings): Result<String> {
        return try {
            val api = getOrCreateApi(settings.apiEndpoint)
            val request = ChatRequest(
                model = settings.aiModel,
                messages = listOf(
                    Message("system", PromptBuilder.buildSystemPrompt(settings.aiPersonality)),
                    Message("user", PromptBuilder.buildUserMessage(context))
                ),
                temperature = 0.7, max_tokens = 300
            )
            val response = api.chatCompletion("Bearer ${settings.apiKey}", request)
            if (response.isSuccessful) {
                val content = response.body()?.choices?.firstOrNull()?.message?.content ?: "(AI 暂时没有回应...)"
                Result.success(content.trim())
            } else Result.failure(Exception("API error: ${response.code()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun getOrCreateApi(endpoint: String): OpenAiApi = apiCache.getOrPut(endpoint) {
        val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
        Retrofit.Builder().baseUrl(endpoint.trimEnd('/') + "/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(OpenAiApi::class.java)
    }
}
