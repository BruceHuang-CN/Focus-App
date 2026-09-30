package com.example.focus_app.data.repository

import com.example.focus_app.data.remote.OpenAiApi
import com.example.focus_app.data.remote.dto.*
import com.example.focus_app.data.security.ApiKeyStore
import com.example.focus_app.domain.model.AiProvider
import com.example.focus_app.domain.summary.DailySummaryFacts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class AiDailySummaryTest {
    private val facts = DailySummaryFacts("2026-09-19", 2, 15, 3, "平静")
    private fun key(value: String) = object : ApiKeyStore {
        override suspend fun read() = value
        override suspend fun write(value: String) = Unit
        override suspend fun clear() = Unit
    }
    private class Api : OpenAiApi {
        var request: ChatRequest? = null
        var auth: String? = null
        var content = "完成了两件事，明天继续。"
        var cancel = false
        override suspend fun chatCompletion(authorization: String, request: ChatRequest): Response<ChatResponse> {
            if (cancel) throw CancellationException("cancelled")
            this.request = request; auth = authorization
            return Response.success(ChatResponse(choices = listOf(Choice(MessageContent("assistant", content)))))
        }
        override suspend fun listModels(authorization: String): Response<ModelsResponse> = Response.success(ModelsResponse())
    }
    @Test fun uses_user_endpoint_model_key_without_task_or_note_content() = runTest {
        val api = Api()
        var endpoint: String? = null
        val repository = AiRepository(key("test-key"), apiFactory = { endpoint = it; api })
        val result = repository.generateDailySummary(facts, AppSettings(aiProvider = AiProvider.CUSTOM,
            apiEndpoint = "https://configured.example/v1", aiModel = "user-model", customToneInstruction = "PRIVATE_NOTE"), "en")
        assertTrue(result.isSuccess)
        assertEquals("https://configured.example/v1", endpoint)
        assertEquals("user-model", api.request!!.model)
        assertEquals("Bearer test-key", api.auth)
        assertNull(api.request!!.thinking)
        assertNull(api.request!!.response_format)
        assertFalse(api.request!!.messages.joinToString().contains("PRIVATE_NOTE"))
        assertTrue(api.request!!.messages.first().content.contains("English"))
    }
    @Test fun missing_key_never_sends_a_request_and_never_fakes_ai_success() = runTest {
        var calls = 0
        val repository = AiRepository(key(""), apiFactory = { calls++; Api() })
        assertTrue(repository.generateDailySummary(facts, AppSettings()).isFailure)
        assertEquals(0, calls)
    }
    @Test fun empty_response_is_failure() = runTest {
        val api = Api().apply { content = " " }
        assertTrue(AiRepository(key("test"), apiFactory = { api }).generateDailySummary(facts, AppSettings()).isFailure)
    }
    @Test fun cancellation_is_propagated() = runTest {
        val api = Api().apply { cancel = true }
        try {
            AiRepository(key("test"), apiFactory = { api }).generateDailySummary(facts, AppSettings())
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }
}
