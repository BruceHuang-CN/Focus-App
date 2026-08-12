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

internal fun isReminderPresentationForForegroundChange(hasPendingReminder: Boolean): Boolean =
    hasPendingReminder

internal fun shouldKeepReminderPending(forceReminder: Boolean, explicitAction: Boolean): Boolean =
    forceReminder && !explicitAction
