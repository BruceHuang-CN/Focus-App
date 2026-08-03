package com.example.focus_app.service

import android.content.Context
import android.content.Intent
import com.example.focus_app.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface ReminderLauncher {
    fun show(data: ReminderLaunchData)
    fun returnToFocus(taskId: Long?)
    fun returnHome()
}

@Singleton
class AndroidReminderLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) : ReminderLauncher {
    override fun show(data: ReminderLaunchData) {
        context.startActivity(
            Intent(context, ReminderActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(ReminderLaunchData.EXTRA_SESSION_ID, data.sessionId)
                data.taskId?.let { putExtra(ReminderLaunchData.EXTRA_TASK_ID, it) }
                putExtra(ReminderLaunchData.EXTRA_TASK_TITLE, data.taskTitle)
                putExtra(ReminderLaunchData.EXTRA_APP_NAME, data.appName)
                putExtra(ReminderLaunchData.EXTRA_MESSAGE, data.message)
                putExtra(ReminderLaunchData.EXTRA_SHOW_BREATHING, data.showBreathing)
                putExtra(ReminderLaunchData.EXTRA_RETURN_DESTINATION, data.returnDestination.key)
            }
        )
    }

    override fun returnToFocus(taskId: Long?) {
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                taskId?.let { putExtra(ACTIVE_TASK_ID, it) }
            }
        )
    }

    override fun returnHome() {
        if (FocusAccessibilityService.performHomeAction()) return
        context.startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    companion object {
        const val ACTIVE_TASK_ID = "active_task_id"
    }
}
