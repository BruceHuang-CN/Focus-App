package com.example.focus_app.service

internal fun presentReminder(
    canDrawOverlays: Boolean,
    startActivity: () -> Unit,
    postNotification: () -> Unit
) {
    if (canDrawOverlays) startActivity() else postNotification()
}

internal fun isReminderSessionCurrent(sessionId: Long, currentSessionId: Long?): Boolean =
    sessionId > 0L && sessionId == currentSessionId
