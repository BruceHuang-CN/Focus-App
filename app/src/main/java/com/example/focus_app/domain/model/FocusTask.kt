package com.example.focus_app.domain.model

data class FocusTask(
    val id: Long = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val isManualActive: Boolean = false,
    val scheduleStartMinute: Int? = null,
    val scheduleEndMinute: Int? = null,
    val repeatDaysMask: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
