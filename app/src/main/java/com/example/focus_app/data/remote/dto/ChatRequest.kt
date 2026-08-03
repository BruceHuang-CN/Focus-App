package com.example.focus_app.data.remote.dto

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 500,
    val thinking: ThinkingConfig? = null,
    val response_format: ResponseFormat? = null
)
data class Message(val role: String, val content: String)
data class ThinkingConfig(val type: String)
data class ResponseFormat(val type: String)
