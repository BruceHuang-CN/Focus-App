package com.example.focus_app.data.repository

import com.example.focus_app.data.security.ApiKeyStore
import com.example.focus_app.data.remote.OpenAiApi
import com.example.focus_app.data.remote.dto.ChatRequest
import com.example.focus_app.data.remote.dto.ChatResponse
import com.example.focus_app.data.remote.dto.Choice
import com.example.focus_app.data.remote.dto.MessageContent
import com.example.focus_app.data.remote.dto.ModelsResponse
import com.example.focus_app.domain.model.AiProvider
import com.example.focus_app.domain.model.ReminderContext
import com.example.focus_app.domain.model.ReminderTone
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class AiRepositoryTest {
    @Test
    fun invalid_custom_endpoint_still_returns_a_local_batch() = runTest {
        val repository = AiRepository(StaticApiKeyStore("secret"))
        val settings = AppSettings(
            aiProvider = AiProvider.CUSTOM,
            apiEndpoint = "not a url",
            aiModel = "custom-model"
        )

        val messages = repository.generateBatch(context(), settings).getOrThrow()

        assertEquals(3, messages.size)
        assertEquals(3, messages.distinct().size)
    }

    @Test
    fun endpoint_change_replaces_the_single_cached_api_reference() = runTest {
        val createdEndpoints = mutableListOf<String>()
        val repository = AiRepository(StaticApiKeyStore("secret")) { endpoint ->
            createdEndpoints += endpoint
            SuccessfulOpenAiApi()
        }
        val first = AppSettings(apiEndpoint = "https://first.example/v1")
        val second = AppSettings(apiEndpoint = "https://second.example/v1")

        repository.generateBatch(context(), first).getOrThrow()
        repository.generateBatch(context(), first).getOrThrow()
        repository.generateBatch(context(), second).getOrThrow()
        repository.generateBatch(context(), first).getOrThrow()

        assertEquals(
            listOf("https://first.example/v1", "https://second.example/v1", "https://first.example/v1"),
            createdEndpoints
        )
    }

    private fun context() = ReminderContext(
        taskTitle = "写方案",
        timeBlock = null,
        latestMood = null,
        appName = "抖音",
        openCountToday = 0,
        remindersInWindow = 0,
        activeExitsToday = 0,
        tone = ReminderTone.GENTLE
    )
}

private class SuccessfulOpenAiApi : OpenAiApi {
    override suspend fun chatCompletion(
        authorization: String,
        request: ChatRequest
    ): Response<ChatResponse> = Response.success(
        ChatResponse(
            choices = listOf(
                Choice(
                    MessageContent(
                        role = "assistant",
                        content = """{"messages":["一","二","三"]}"""
                    )
                )
            )
        )
    )

    override suspend fun listModels(authorization: String): Response<ModelsResponse> =
        Response.success(ModelsResponse())
}

private class StaticApiKeyStore(private val value: String) : ApiKeyStore {
    override suspend fun read(): String = value
    override suspend fun write(value: String) = Unit
    override suspend fun clear() = Unit
}
