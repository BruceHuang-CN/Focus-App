package com.example.focus_app.service

internal data class ForegroundObservation(
    val packageName: String,
    val timestamp: Long
)

/**
 * Compatibility polling deliberately re-reads an overlapping UsageEvents window.
 * This cursor prevents a delayed event from being lost while publishing each event once.
 */
internal class ForegroundObservationCursor(
    private val overlapMillis: Long
) {
    private var lastPublished: ForegroundObservation? = null

    fun queryStart(now: Long): Long = (now - overlapMillis).coerceAtLeast(0L)

    fun takeIfNew(observation: ForegroundObservation?): ForegroundObservation? {
        observation ?: return null
        val previous = lastPublished
        if (previous != null && observation.timestamp < previous.timestamp) return null
        if (observation == previous) return null
        lastPublished = observation
        return observation
    }
}
