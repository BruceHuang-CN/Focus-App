package com.example.focus_app.domain.reminder

object SnoozeDurationPolicy {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 60

    fun isValid(minutes: Int): Boolean = minutes in MIN_MINUTES..MAX_MINUTES

    fun normalizeStored(minutes: Int): Int =
        minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
}
