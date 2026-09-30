package com.example.focus_app.data.remote

import com.example.focus_app.domain.feedback.AppFeedbackRequest
import com.example.focus_app.domain.feedback.AppFeedbackResponse
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/** 独立的开发者反馈 endpoint，不复用用户配置的 AI 接口或凭据。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FeedbackEndpoint

interface FeedbackApi {
    @POST("api/app-feedback")
    suspend fun submit(@Body payload: AppFeedbackRequest): Response<AppFeedbackResponse>
}

@Module
@InstallIn(SingletonComponent::class)
object FeedbackApiModule {

    /** 固定开发者域名；不接受用户配置，也不跟随重定向到第三方域名重新发送正文。 */
    const val BASE_URL = "https://brucehere.com/"

    @Provides
    @Singleton
    @FeedbackEndpoint
    fun provideFeedbackRetrofit(): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideFeedbackApi(@FeedbackEndpoint retrofit: Retrofit): FeedbackApi =
        retrofit.create(FeedbackApi::class.java)
}
