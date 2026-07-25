package com.example.focus_app.domain.model

data class ReminderContext(val appName: String, val currentTime: String, val openCountToday: Int, val lastOpenTime: String?, val latestMood: String?, val exitedCountToday: Int, val personality: String = "gentle")
