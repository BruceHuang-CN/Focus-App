package com.example.focus_app.domain.model

data class TaskGroup(
    val id: Long = 0,
    val name: String,
    val scheduleStartMinute: Int? = null,
    val scheduleEndMinute: Int? = null,
    val repeatDaysMask: Int = 0,
    val sortOrder: Long = 0
)
