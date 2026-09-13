package com.example.focus_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.focus_app.domain.model.FocusTask

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
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "1") val groupId: Long = 1,
    @ColumnInfo(defaultValue = "0") val sortOrder: Long = 0,
    @ColumnInfo(defaultValue = "0") val inheritsGroupSchedule: Boolean = false,
    val manualStartedAt: Long? = null,
    val manualUntil: Long? = null
)

fun FocusTaskEntity.toDomain(): FocusTask = FocusTask(
    id = id,
    title = title,
    isCompleted = isCompleted,
    isManualActive = isManualActive,
    scheduleStartMinute = scheduleStartMinute,
    scheduleEndMinute = scheduleEndMinute,
    repeatDaysMask = repeatDaysMask,
    createdAt = createdAt,
    updatedAt = updatedAt,
    groupId = groupId, sortOrder = sortOrder, inheritsGroupSchedule = inheritsGroupSchedule,
    manualStartedAt = manualStartedAt, manualUntil = manualUntil
)

fun FocusTask.toEntity(): FocusTaskEntity = FocusTaskEntity(
    id = id,
    title = title,
    isCompleted = isCompleted,
    isManualActive = isManualActive,
    scheduleStartMinute = scheduleStartMinute,
    scheduleEndMinute = scheduleEndMinute,
    repeatDaysMask = repeatDaysMask,
    createdAt = createdAt,
    updatedAt = updatedAt,
    groupId = groupId, sortOrder = sortOrder, inheritsGroupSchedule = inheritsGroupSchedule,
    manualStartedAt = manualStartedAt, manualUntil = manualUntil
)
