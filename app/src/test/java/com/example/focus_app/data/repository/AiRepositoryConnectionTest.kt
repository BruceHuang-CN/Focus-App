package com.example.focus_app.data.repository

import com.example.focus_app.data.remote.OpenAiApi
import com.example.focus_app.data.security.ApiKeyStore
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.test.runTest

class AiRepositoryConnectionTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun success_returns_model_ids() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"object":"list","data":[{"id":"deepseek-chat"},{"id":"deepseek-v4-flash"}]}"""
            )
        )
        val result = repository("sk-test").testConnection(settings(server.url("/").toString()))
        assertEquals(
            ConnectionTestResult.Success(listOf("deepseek-chat", "deepseek-v4-flash")),
            result
        )
    }

    @Test
    fun blank_key_returns_friendly_error_without_request() = runTest {
        val result = repository("").testConnection(settings(server.url("/").toString()))
        assertEquals(ConnectionTestResult.Error("请先保存 API Key"), result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun unauthorized_maps_to_invalid_key_error() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = repository("sk-wrong").testConnection(settings(server.url("/").toString()))
        assertEquals(ConnectionTestResult.Error("API Key 无效或无权限"), result)
    }

    @Test
    fun unsupported_endpoint_maps_to_endpoint_error() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = repository("sk-test").testConnection(settings(server.url("/").toString()))
        assertEquals(ConnectionTestResult.Error("模型列表接口不可用，请检查 API 端点"), result)
    }

    @Test
    fun empty_model_list_is_reported_as_error() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"object":"list","data":[]}"""))
        val result = repository("sk-test").testConnection(settings(server.url("/").toString()))
        assertTrue(result is ConnectionTestResult.Error)
    }

    @Test
    fun unreachable_endpoint_maps_to_network_error() = runTest {
        val result = repository("sk-test").testConnection(settings("http://127.0.0.1:1/"))
        assertEquals(ConnectionTestResult.Error("网络连接失败，请检查网络或端点"), result)
    }

    private fun repository(key: String): AiRepository =
        AiRepository(FakeApiKeyStore(key)) { endpoint ->
            Retrofit.Builder()
                .baseUrl(endpoint)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenAiApi::class.java)
        }

    private fun settings(endpoint: String): AppSettings = AppSettings(apiEndpoint = endpoint)
}

private class FakeApiKeyStore(private val key: String) : ApiKeyStore {
    override suspend fun read(): String = key
    override suspend fun write(value: String) = Unit
    override suspend fun clear() = Unit
}
