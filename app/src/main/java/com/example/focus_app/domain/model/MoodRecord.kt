package com.example.focus_app.domain.model

data class MoodRecord(val id: Long = 0, val timestamp: Long, val mood: String, val note: String? = null)
