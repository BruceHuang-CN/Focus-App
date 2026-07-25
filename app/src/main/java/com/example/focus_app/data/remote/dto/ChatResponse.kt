package com.example.focus_app.data.remote.dto

data class ChatResponse(val id: String? = null, val choices: List<Choice> = emptyList())
data class Choice(val message: MessageContent? = null)
data class MessageContent(val role: String? = null, val content: String? = null)
