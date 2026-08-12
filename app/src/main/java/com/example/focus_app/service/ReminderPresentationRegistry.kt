package com.example.focus_app.service

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderPresentationRegistry @Inject constructor() {
    private data class Presentation(
        val sessionId: Long,
        val forceReminder: Boolean,
        val snoozeTransitionDeadlineMillis: Long? = null
    )

    internal constructor(elapsedRealtime: () -> Long) : this() {
        elapsedRealtimeProvider = elapsedRealtime
    }

    private var elapsedRealtimeProvider: () -> Long = SystemClock::elapsedRealtime

    @Volatile
    private var activePresentation: Presentation? = null

    fun show(sessionId: Long, forceReminder: Boolean = false) {
        activePresentation = Presentation(sessionId, forceReminder)
    }

    fun keepSnoozeTransition(sessionId: Long) {
        val presentation = activePresentation ?: return
        if (presentation.sessionId != sessionId) return
        activePresentation = presentation.copy(
            snoozeTransitionDeadlineMillis = elapsedRealtimeProvider() + SNOOZE_TRANSITION_GRACE_MILLIS
        )
    }

    fun hide(sessionId: Long) {
        if (activePresentation?.sessionId == sessionId) activePresentation = null
    }

    fun onActivityDestroyed(sessionId: Long) {
        val presentation = activePresentation ?: return
        if (presentation.sessionId != sessionId) return
        if (isSnoozeTransitionPending(presentation)) return
        if (!shouldKeepReminderPending(presentation.forceReminder, explicitAction = false)) {
            activePresentation = null
        }
    }

    fun isShowing(): Boolean {
        val presentation = activePresentation ?: return false
        if (presentation.snoozeTransitionDeadlineMillis != null &&
            !isSnoozeTransitionPending(presentation)
        ) {
            activePresentation = null
            return false
        }
        return true
    }

    private fun isSnoozeTransitionPending(presentation: Presentation): Boolean =
        presentation.snoozeTransitionDeadlineMillis?.let { it > elapsedRealtimeProvider() } == true

    private companion object {
        const val SNOOZE_TRANSITION_GRACE_MILLIS = 8_000L
    }
}
