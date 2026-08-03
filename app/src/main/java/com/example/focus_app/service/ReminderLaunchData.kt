package com.example.focus_app.service

import com.example.focus_app.domain.model.ReturnDestination

data class ReminderLaunchData(
    val sessionId: Long,
    val taskId: Long?,
    val taskTitle: String?,
    val appName: String,
    val message: String,
    val showBreathing: Boolean,
    val returnDestination: ReturnDestination
) {
    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_SHOW_BREATHING = "show_breathing"
        const val EXTRA_RETURN_DESTINATION = "return_destination"
    }
}
