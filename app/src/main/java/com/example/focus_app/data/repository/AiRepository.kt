package com.example.focus_app.data.repository

import android.content.Context
import com.example.focus_app.data.language.AppLanguage
import com.example.focus_app.data.remote.AiFailure
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val currentLanguage: () -> String = { "zh-CN" },
    private val apiFactory: (String) -> OpenAiApi = RetrofitOpenAiApiFactory()
) {
    @Inject
    constructor(apiKeyStore: ApiKeyStore, @ApplicationContext context: Context) :
        this(apiKeyStore, { AppLanguage.tag(context) }, RetrofitOpenAiApiFactory())

    @Volatile
    private var cachedEndpoint: String? = null

    @Volatile
    private var cachedApi: OpenAiApi? = null

    suspend fun generateBatch(
        context: ReminderContext,
        settings: AppSettings,
        count: Int = 3,
        languageTag: String = currentLanguage()
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
            provider.generateBatch(context.copy(languageTag = languageTag), count)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            fallback.generateBatch(context.copy(languageTag = languageTag), count)
        }
    }

    /** 用户主动刷新文案时强制走远端，失败则保留现有缓存。 */
    suspend fun generateRemoteBatch(
        context: ReminderContext,
        settings: AppSettings,
        count: Int = 3,
        languageTag: String = currentLanguage()
    ): Result<List<String>> = try {
        val endpoint = settings.apiEndpoint.ifBlank { settings.aiProvider.defaultEndpoint }
        val model = settings.aiModel.ifBlank { settings.aiProvider.defaultModel }
        DeepSeekReminderProvider(
            api = getOrCreateApi(endpoint),
            apiKeyStore = apiKeyStore,
            model = model,
            includeDeepSeekOptions = settings.aiProvider == AiProvider.DEEPSEEK
        ).generateRemoteBatch(context.copy(languageTag = languageTag), count)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(IllegalStateException(AiFailure.fromException(error).message(languageTag)))
    }

    /**
     * 使用当前保存的 API Key 测试与配置端点的连通性。
     * 成功时返回端点可用的模型 ID 列表；失败时返回面向用户的简洁错误。
     */
    suspend fun testConnection(settings: AppSettings, languageTag: String = currentLanguage()): ConnectionTestResult {
        val key = apiKeyStore.read()
        if (key.isBlank()) return ConnectionTestResult.Error(AiFailure.MISSING_KEY.message(languageTag))

        val endpoint = settings.apiEndpoint.ifBlank { settings.aiProvider.defaultEndpoint }
        return try {
            val api = getOrCreateApi(endpoint)
            val response = api.listModels("Bearer $key")
            if (response.isSuccessful) {
                val ids = response.body()?.data?.map { it.id }.orEmpty()
                if (ids.isEmpty()) {
                    ConnectionTestResult.Error(AiFailure.INVALID_RESPONSE.message(languageTag))
                } else {
                    ConnectionTestResult.Success(ids)
                }
            } else {
                ConnectionTestResult.Error(AiFailure.fromHttp(response.code()).message(languageTag))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ConnectionTestResult.Error(AiFailure.fromException(e).message(languageTag))
        }
    }

    /** Daily reflection uses the same configured endpoint, model, key store and HTTP client. */
    suspend fun generateDailySummary(
        facts: com.example.focus_app.domain.summary.DailySummaryFacts,
        settings: AppSettings,
        languageTag: String = currentLanguage()
    ): Result<String> = try {
        val key = apiKeyStore.read()
        if (key.isBlank()) {
            Result.failure(IllegalStateException(AiFailure.MISSING_KEY.message(languageTag)))
        } else {
            val api = getOrCreateApi(settings.apiEndpoint.ifBlank { settings.aiProvider.defaultEndpoint })
            val response = api.chatCompletion("Bearer $key", com.example.focus_app.data.remote.dto.ChatRequest(
                model = settings.aiModel.ifBlank { settings.aiProvider.defaultModel },
                messages = listOf(
                    com.example.focus_app.data.remote.dto.Message("system", com.example.focus_app.domain.summary.summarySystemMessage(languageTag)),
                    com.example.focus_app.data.remote.dto.Message("user", com.example.focus_app.domain.summary.summaryUserMessage(facts))
                ), max_tokens = 500,
                thinking = if (settings.aiProvider == AiProvider.DEEPSEEK) com.example.focus_app.data.remote.dto.ThinkingConfig("disabled") else null
            ))
            val content = response.body()?.choices?.firstOrNull()?.message?.content?.trim()
            when {
                !response.isSuccessful -> Result.failure(IllegalStateException(AiFailure.fromHttp(response.code()).message(languageTag)))
                content.isNullOrBlank() || content.length > 4_000 -> Result.failure(IllegalStateException(AiFailure.INVALID_RESPONSE.message(languageTag)))
                else -> Result.success(content)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(IllegalStateException(AiFailure.fromException(error).message(languageTag)))
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
