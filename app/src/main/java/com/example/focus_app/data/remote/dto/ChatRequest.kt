package com.example.focus_app.data.remote.dto

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 500
)
data class Message(val role: String, val content: String)
