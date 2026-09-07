package com.example.focus_app.service

internal fun presentReminder(
    canDrawOverlays: Boolean,
    onBeforeStartActivity: () -> Unit = {},
    startActivity: () -> Unit,
    postNotification: () -> Unit
): Boolean {
    val activityRequested = if (canDrawOverlays) {
        try {
            onBeforeStartActivity()
            startActivity()
            true
        } catch (_: RuntimeException) {
            false
        }
    } else {
        false
    }
    postNotification()
    return activityRequested
}

internal fun isReminderSessionCurrent(sessionId: Long, currentSessionId: Long?): Boolean =
    sessionId > 0L && sessionId == currentSessionId

internal fun isReminderPresentationForForegroundChange(hasPendingReminder: Boolean): Boolean =
    hasPendingReminder

internal fun isNewReminderAttempt(currentAttemptId: String?, incomingAttemptId: String): Boolean =
    incomingAttemptId.isNotBlank() && incomingAttemptId != currentAttemptId

internal fun shouldApplyReminderConfirmation(
    expectedAttemptId: String,
    currentAttemptId: String?,
    registryAttemptIsCurrent: Boolean,
    isActivityResumed: Boolean
): Boolean =
    expectedAttemptId.isNotBlank() &&
        expectedAttemptId == currentAttemptId &&
        registryAttemptIsCurrent &&
        isActivityResumed
