package com.example.focus_app.data.repository

import com.example.focus_app.data.remote.DeepSeekReminderProvider
import com.example.focus_app.data.remote.LocalReminderProvider
import com.example.focus_app.data.remote.OpenAiApi
import com.example.focus_app.data.security.ApiKeyStore
import com.example.focus_app.domain.model.AiProvider
import com.example.focus_app.domain.model.ReminderContext
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** AI 连接测试结果，用于设置页展示。 */
sealed interface ConnectionTestResult {
    data object Loading : ConnectionTestResult
    data class Success(val modelIds: List<String>) : ConnectionTestResult
    data class Error(val message: String) : ConnectionTestResult
}

@Singleton
class AiRepository internal constructor(
    private val apiKeyStore: ApiKeyStore,
    private val apiFactory: (String) -> OpenAiApi
) {
    @Inject
    constructor(apiKeyStore: ApiKeyStore) : this(apiKeyStore, RetrofitOpenAiApiFactory())

    @Volatile
    private var cachedEndpoint: String? = null

    @Volatile
    private var cachedApi: OpenAiApi? = null

    suspend fun generateBatch(
        context: ReminderContext,
        settings: AppSettings,
        count: Int = 3
    ): Result<List<String>> {
        val fallback = LocalReminderProvider()
        return try {
            val endpoint = settings.apiEndpoint.ifBlank { settings.aiProvider.defaultEndpoint }
            val model = settings.aiModel.ifBlank { settings.aiProvider.defaultModel }
            val provider = DeepSeekReminderProvider(
                api = getOrCreateApi(endpoint),
                apiKeyStore = apiKeyStore,
                model = model,
                fallback = fallback,
                includeDeepSeekOptions = settings.aiProvider == AiProvider.DEEPSEEK
            )
            provider.generateBatch(context, count)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            fallback.generateBatch(context, count)
        }
    }

    /**
     * 使用当前保存的 API Key 测试与配置端点的连通性。
     * 成功时返回端点可用的模型 ID 列表；失败时返回面向用户的简洁错误。
     */
    suspend fun testConnection(settings: AppSettings): ConnectionTestResult {
        val key = apiKeyStore.read()
        if (key.isBlank()) return ConnectionTestResult.Error("请先保存 API Key")

        val endpoint = settings.apiEndpoint.ifBlank { settings.aiProvider.defaultEndpoint }
        val api = getOrCreateApi(endpoint)
        return try {
            val response = api.listModels("Bearer $key")
            if (response.isSuccessful) {
                val ids = response.body()?.data?.map { it.id }.orEmpty()
                if (ids.isEmpty()) {
                    ConnectionTestResult.Error("接口未返回模型列表，请检查端点")
                } else {
                    ConnectionTestResult.Success(ids)
                }
            } else {
                ConnectionTestResult.Error(mapHttpError(response.code()))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            ConnectionTestResult.Error("网络连接失败，请检查网络或端点")
        } catch (e: Exception) {
            ConnectionTestResult.Error("请求失败：${e.message ?: "未知错误"}")
        }
    }

    private fun getOrCreateApi(endpoint: String): OpenAiApi {
        val normalizedEndpoint = endpoint.trimEnd('/')
        val currentApi = cachedApi
        if (cachedEndpoint == normalizedEndpoint && currentApi != null) return currentApi

        return synchronized(this) {
            val synchronizedApi = cachedApi
            if (cachedEndpoint == normalizedEndpoint && synchronizedApi != null) {
                synchronizedApi
            } else {
                apiFactory(normalizedEndpoint).also {
                    cachedEndpoint = normalizedEndpoint
                    cachedApi = it
                }
            }
        }
    }

    private fun mapHttpError(code: Int): String = when (code) {
        401, 403 -> "API Key 无效或无权限"
        404 -> "模型列表接口不可用，请检查 API 端点"
        429 -> "请求过于频繁，请稍后再试"
        in 500..599 -> "服务端错误（$code）"
        else -> "请求失败（$code）"
    }
}

private class RetrofitOpenAiApiFactory : (String) -> OpenAiApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun invoke(endpoint: String): OpenAiApi =
        Retrofit.Builder()
            .baseUrl("$endpoint/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiApi::class.java)
}
