package com.example.focus_app.data.remote

import com.example.focus_app.data.remote.dto.ChatRequest
import com.example.focus_app.data.remote.dto.ChatResponse
import com.example.focus_app.data.remote.dto.ModelsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenAiApi {
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): Response<ChatResponse>

    @GET("models")
    suspend fun listModels(
        @Header("Authorization") authorization: String
    ): Response<ModelsResponse>
}
