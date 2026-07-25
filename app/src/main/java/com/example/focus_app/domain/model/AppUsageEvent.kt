package com.example.focus_app.domain.model

data class AppUsageEvent(val id: Long = 0, val packageName: String, val appName: String, val openTime: Long, val reminded: Boolean = false, val userAction: String? = null, val hourBucket: String)
