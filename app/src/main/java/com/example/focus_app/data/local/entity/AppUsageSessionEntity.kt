package com.example.focus_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.focus_app.domain.model.AppUsageSession

@Entity(
    tableName = "app_usage_sessions",
    indices = [Index("packageName"), Index("startedAt"), Index("remindedAt")]
)
data class AppUsageSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val taskId: Long? = null,
    val remindedAt: Long? = null,
    val userAction: String? = null,
    val toneKey: String,
    val snoozeUntil: Long? = null,
    @ColumnInfo(defaultValue = "0") val taskContextStartedAt: Long = 0
)

fun AppUsageSessionEntity.toDomain() = AppUsageSession(
    id = id,
    packageName = packageName,
    appName = appName,
    startedAt = startedAt,
    endedAt = endedAt,
    taskId = taskId,
    remindedAt = remindedAt,
    userAction = userAction,
    toneKey = toneKey,
    snoozeUntil = snoozeUntil,
    taskContextStartedAt = taskContextStartedAt
)

fun AppUsageSession.toEntity() = AppUsageSessionEntity(
    id = id,
    packageName = packageName,
    appName = appName,
    startedAt = startedAt,
    endedAt = endedAt,
    taskId = taskId,
    remindedAt = remindedAt,
    userAction = userAction,
    toneKey = toneKey,
    snoozeUntil = snoozeUntil,
    taskContextStartedAt = taskContextStartedAt
)
