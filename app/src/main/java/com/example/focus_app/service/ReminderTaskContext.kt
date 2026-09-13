package com.example.focus_app.service

import com.example.focus_app.domain.model.AppUsageSession

fun AppUsageSession.reminderContextStart(): Long = taskContextStartedAt.takeIf { it > 0 } ?: startedAt
internal fun matchesReminderContext(data: ReminderLaunchData, session: AppUsageSession): Boolean =
    data.sessionId == session.id && data.taskId == session.taskId &&
        data.taskContextStartedAt == session.reminderContextStart()
