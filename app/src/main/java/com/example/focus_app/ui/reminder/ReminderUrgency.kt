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
    if (windowLimit <= 0) {
        return ReminderUrgency(
            widthFraction = MIN_WIDTH_FRACTION,
            heightFraction = MIN_HEIGHT_FRACTION,
            isFinalReminder = false
        )
    }

    val safeCount = windowReminderCount.coerceIn(1, windowLimit)
    val progress = if (windowLimit == 1) {
        1f
    } else {
        (safeCount - 1).toFloat() / (windowLimit - 1).toFloat()
    }
    return ReminderUrgency(
        widthFraction = lerp(MIN_WIDTH_FRACTION, 1f, progress),
        heightFraction = lerp(MIN_HEIGHT_FRACTION, 1f, progress),
        isFinalReminder = safeCount >= windowLimit
    )
}

private fun lerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction.coerceIn(0f, 1f)

private const val MIN_WIDTH_FRACTION = 0.82f
private const val MIN_HEIGHT_FRACTION = 0.50f
