package com.example.focus_app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_tasks")
data class FocusTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val isManualActive: Boolean = false,
    val scheduleStartMinute: Int? = null,
    val scheduleEndMinute: Int? = null,
    val repeatDaysMask: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
