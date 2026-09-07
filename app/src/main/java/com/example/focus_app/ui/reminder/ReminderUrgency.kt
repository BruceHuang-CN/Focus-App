package com.example.focus_app.ui.reminder

internal data class ReminderUrgency(
    val widthFraction: Float,
    val heightFraction: Float,
    val isFinalReminder: Boolean
)

internal fun reminderUrgency(
    windowReminderCount: Int,
    windowLimit: Int
): ReminderUrgency {
    val safeCount = windowReminderCount.coerceAtLeast(1)
    return ReminderUrgency(
        widthFraction = 1f,
        heightFraction = 1f,
        isFinalReminder = windowLimit > 0 && safeCount >= windowLimit
    )
}
