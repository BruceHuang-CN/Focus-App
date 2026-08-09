package com.example.focus_app.service

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderPresentationRegistry @Inject constructor() {
    @Volatile
    private var activeSessionId: Long? = null

    fun show(sessionId: Long) {
        activeSessionId = sessionId
    }

    fun hide(sessionId: Long) {
        if (activeSessionId == sessionId) activeSessionId = null
    }

    fun isShowing(): Boolean = activeSessionId != null
}
