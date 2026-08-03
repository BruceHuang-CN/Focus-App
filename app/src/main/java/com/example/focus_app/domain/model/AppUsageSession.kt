package com.example.focus_app.domain.model

data class AppUsageSession(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val taskId: Long? = null,
    val remindedAt: Long? = null,
    val userAction: String? = null,
    val toneKey: String
)
