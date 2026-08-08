package com.example.focus_app.data.remote

import com.example.focus_app.data.remote.dto.ChatRequest
import com.example.focus_app.data.remote.dto.ChatResponse
import com.example.focus_app.data.remote.dto.Choice
import com.example.focus_app.data.remote.dto.MessageContent
import com.example.focus_app.data.remote.dto.ModelsResponse
import com.example.focus_app.data.security.ApiKeyStore
import com.example.focus_app.domain.model.ReminderContext
import com.example.focus_app.domain.model.ReminderTone
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import retrofit2.http.POST
import okhttp3.ResponseBody.Companion.toResponseBody

class DeepSeekReminderProviderTest {
    @Test
    fun requests_three_json_messages_with_thinking_disabled() = runTest {
        val api = FakeOpenAiApi("""{"messages":["先关掉抖音，继续写方案。","任务在等你，先完成最小一步。","别让滑动替你决定时间，回去继续。"]}""")
        val provider = DeepSeekReminderProvider(api, FakeApiKeyStore("secret"))

        val messages = provider.generateBatch(context(), 3).getOrThrow()

        assertEquals(3, messages.size)
        assertTrue(messages.all { it.length <= 80 })
        assertEquals("deepseek-v4-flash", api.lastRequest?.model)
        assertEquals("disabled", api.lastRequest?.thinking?.type)
        assertEquals("json_object", api.lastRequest?.response_format?.type)
        assertEquals(listOf("system", "user"), api.lastRequest?.messages?.map { it.role })
    }

    @Test
    fun relative_chat_path_preserves_a_custom_base_path() {
        val post = OpenAiApi::class.java.methods
            .single { it.name == "chatCompletion" }
            .getAnnotation(POST::class.java)

        assertEquals("chat/completions", post?.value)
    }

    @Test
    fun malformed_or_failed_responses_use_three_unique_local_messages() = runTest {
        val invalidBodies = listOf(
            "",
            """{"messages":["只有一条"]}""",
            """{"messages":["重复","重复","第三条"]}""",
            """{"messages":["${"太".repeat(81)}","第二条","第三条"]}"""
        )

        invalidBodies.forEach { body ->
            val messages = DeepSeekReminderProvider(
                FakeOpenAiApi(body),
                FakeApiKeyStore("secret")
            ).generateBatch(context(), 3).getOrThrow()

            assertEquals(3, messages.size)
            assertEquals(3, messages.distinct().size)
            assertTrue(messages.all { it.isNotBlank() && it.length <= 80 })
        }

        val failed = DeepSeekReminderProvider(
            FakeOpenAiApi(responseCode = 429),
            FakeApiKeyStore("secret")
        ).generateBatch(context(), 3).getOrThrow()
        assertEquals(3, failed.distinct().size)
    }

    @Test(expected = CancellationException::class)
    fun cancellation_is_not_converted_into_a_fallback_batch() = runTest {
        DeepSeekReminderProvider(
            CancellingOpenAiApi(),
            FakeApiKeyStore("secret")
        ).generateBatch(context(), 3)
    }

    private fun context() = ReminderContext(
        taskTitle = "写完产品方案",
        timeBlock = "09:00-10:00",
        latestMood = "有点分心",
        appName = "抖音",
        openCountToday = 4,
        remindersInWindow = 1,
        activeExitsToday = 2,
        tone = ReminderTone.DIRECT,
        customToneInstruction = ""
    )
}

private class CancellingOpenAiApi : OpenAiApi {
    override suspend fun chatCompletion(
        authorization: String,
        request: ChatRequest
    ): Response<ChatResponse> = throw CancellationException("activation changed")

    override suspend fun listModels(authorization: String): Response<ModelsResponse> =
        throw CancellationException("activation changed")
}

private class FakeOpenAiApi(
    private val content: String = "",
    private val responseCode: Int = 200
) : OpenAiApi {
    var lastRequest: ChatRequest? = null

    override suspend fun chatCompletion(
        authorization: String,
        request: ChatRequest
    ): Response<ChatResponse> {
        lastRequest = request
        return if (responseCode == 200) {
            Response.success(
                ChatResponse(
                    choices = listOf(Choice(MessageContent(role = "assistant", content = content)))
                )
            )
        } else {
            Response.error(
                responseCode,
                "error".toResponseBody()
            )
        }
    }

    override suspend fun listModels(authorization: String): Response<ModelsResponse> =
        Response.success(ModelsResponse())
}

private class FakeApiKeyStore(initial: String = "") : ApiKeyStore {
    private var value = initial

    override suspend fun read(): String = value
    override suspend fun write(value: String) {
        this.value = value
    }

    override suspend fun clear() {
        value = ""
    }
}
