package com.example.focus_app.service

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderPresentationRegistry @Inject constructor() {
    private data class Presentation(
        val sessionId: Long,
        val forceReminder: Boolean
    )

    @Volatile
    private var activePresentation: Presentation? = null

    fun show(sessionId: Long, forceReminder: Boolean = false) {
        activePresentation = Presentation(sessionId, forceReminder)
    }

    fun hide(sessionId: Long) {
        if (activePresentation?.sessionId == sessionId) activePresentation = null
    }


    fun onActivityDestroyed(sessionId: Long) {
        val presentation = activePresentation ?: return
        if (presentation.sessionId != sessionId) return
        if (!shouldKeepReminderPending(presentation.forceReminder, explicitAction = false)) {
            activePresentation = null
        }
    }
    fun isShowing(): Boolean = activePresentation != null
}
