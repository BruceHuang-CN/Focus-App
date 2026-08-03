package com.example.focus_app.domain.model

data class ReminderContext(
    val taskTitle: String,
    val timeBlock: String?,
    val latestMood: String?,
    val appName: String,
    val openCountToday: Int,
    val remindersInWindow: Int,
    val activeExitsToday: Int,
    val tone: ReminderTone,
    val customToneInstruction: String = ""
)
