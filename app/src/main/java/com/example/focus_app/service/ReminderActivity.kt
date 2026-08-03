package com.example.focus_app.service

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.focus_app.domain.model.ReturnDestination
import com.example.focus_app.ui.reminder.ReminderOverlay
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val taskId = if (intent.hasExtra(ReminderLaunchData.EXTRA_TASK_ID)) {
            intent.getLongExtra(ReminderLaunchData.EXTRA_TASK_ID, 0L)
        } else {
            null
        }
        val data = ReminderLaunchData(
            sessionId = intent.getLongExtra(ReminderLaunchData.EXTRA_SESSION_ID, 0L),
            taskId = taskId,
            taskTitle = intent.getStringExtra(ReminderLaunchData.EXTRA_TASK_TITLE),
            appName = intent.getStringExtra(ReminderLaunchData.EXTRA_APP_NAME) ?: "目标应用",
            message = intent.getStringExtra(ReminderLaunchData.EXTRA_MESSAGE)
                ?: "停一下，想想你原本准备完成什么。",
            showBreathing = intent.getBooleanExtra(ReminderLaunchData.EXTRA_SHOW_BREATHING, false),
            returnDestination = ReturnDestination.fromKey(
                intent.getStringExtra(ReminderLaunchData.EXTRA_RETURN_DESTINATION).orEmpty()
            )
        )

        setContent {
            ReminderOverlay(
                data = data,
                onDismiss = { finishAndRemoveTask() }
            )
        }
    }
}
