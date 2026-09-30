package com.example.focus_app.data.repository

import com.example.focus_app.data.remote.FeedbackApi
import com.example.focus_app.domain.feedback.AppFeedbackRequest
import com.example.focus_app.domain.feedback.AppFeedbackResponse
import com.example.focus_app.domain.feedback.DiagnosticLevel
import com.example.focus_app.domain.feedback.buildAppFeedbackRequest
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FeedbackRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: FeedbackRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FeedbackApi::class.java)
        repository = FeedbackRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val payload = buildAppFeedbackRequest(
        id = "71e29975-138d-49ab-b7ef-fb094cce4601",
        problem = "打开目标应用后没有出现提醒。",
        steps = "",
        expected = "",
        level = DiagnosticLevel.NONE,
        snapshot = null
    )

    private fun enqueue(code: Int, body: String, vararg headers: Pair<String, String>) {
        val response = MockResponse().setResponseCode(code).setBody(body)
        headers.forEach { (name, value) -> response.setHeader(name, value) }
        response.setHeader("Content-Type", "application/json")
        server.enqueue(response)
    }

    private fun successBody(id: String) = """{"ok":true,"receipt":"$id"}"""

    @Test
    fun `201 saves and sends the protocol fields to the fixed path`() = runTest {
        enqueue(201, successBody(payload.id))

        assertEquals(FeedbackSubmitResult.Saved(payload.id), repository.submit(payload))

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/app-feedback", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"schemaVersion\":1"))
        assertTrue(body.contains("\"problem\""))
        // 没有诊断时不能出现 diagnostics。
        assertFalse(body.contains("diagnostics"))
        assertFalse(Gson().toJson(payload).contains("apiKey"))
    }

    @Test
    fun `200 with the matching receipt is treated as already saved`() = runTest {
        enqueue(200, successBody(payload.id))
        assertEquals(FeedbackSubmitResult.Saved(payload.id), repository.submit(payload))
    }

    @Test
    fun `conflict is reported without overwriting`() = runTest {
        enqueue(409, """{"ok":false,"code":"ID_CONFLICT"}""")
        assertEquals(FeedbackSubmitResult.Conflict, repository.submit(payload))
    }

    @Test
    fun `rate limit keeps the retry-after seconds`() = runTest {
        enqueue(429, """{"ok":false,"code":"RATE_LIMITED"}""", "Retry-After" to "42")
        assertEquals(FeedbackSubmitResult.RateLimited(42), repository.submit(payload))
    }

    @Test
    fun `rejected input is not marked as unavailable`() = runTest {
        enqueue(400, """{"ok":false,"code":"INVALID_PAYLOAD"}""")
        enqueue(413, """{"ok":false,"code":"PAYLOAD_TOO_LARGE"}""")
        enqueue(415, """{"ok":false,"code":"UNSUPPORTED_MEDIA_TYPE"}""")

        assertEquals(FeedbackSubmitResult.Rejected("INVALID_PAYLOAD"), repository.submit(payload))
        assertEquals(FeedbackSubmitResult.Rejected("PAYLOAD_TOO_LARGE"), repository.submit(payload))
        assertEquals(FeedbackSubmitResult.Rejected("UNSUPPORTED_MEDIA_TYPE"), repository.submit(payload))
    }

    @Test
    fun `503 and receipts that do not match are unconfirmed`() = runTest {
        enqueue(503, """{"ok":false,"code":"UNAVAILABLE"}""")
        assertEquals(FeedbackSubmitResult.Unavailable, repository.submit(payload))

        enqueue(200, successBody("some-other-id"))
        assertEquals(FeedbackSubmitResult.Unavailable, repository.submit(payload))

        enqueue(200, """{"ok":false,"receipt":"${payload.id}"}""")
        assertEquals(FeedbackSubmitResult.Unavailable, repository.submit(payload))
    }

    @Test
    fun `broken json and network failures are unconfirmed`() = runTest {
        enqueue(200, "<html>proxy challenge</html>")
        assertEquals(FeedbackSubmitResult.Unavailable, repository.submit(payload))

        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(FeedbackSubmitResult.Unavailable, repository.submit(payload))
    }
}
