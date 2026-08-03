package com.example.focus_app.data.repository

import com.example.focus_app.data.remote.DeepSeekReminderProvider
import com.example.focus_app.data.remote.LocalReminderProvider
import com.example.focus_app.data.remote.OpenAiApi
import com.example.focus_app.data.security.ApiKeyStore
import com.example.focus_app.domain.model.AiProvider
import com.example.focus_app.domain.model.ReminderContext
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

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
