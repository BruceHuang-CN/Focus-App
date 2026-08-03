package com.example.focus_app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.focus_app.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface ReminderLauncher {
    fun show(data: ReminderLaunchData)
    fun returnToFocus(taskId: Long?)
    fun returnHome()
}

internal fun presentReminder(
    canDrawOverlays: Boolean,
    startActivity: () -> Unit,
    postNotification: () -> Unit
) {
    if (canDrawOverlays) startActivity() else postNotification()
}

@Singleton
class AndroidReminderLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) : ReminderLauncher {
    override fun show(data: ReminderLaunchData) {
        presentReminder(
            canDrawOverlays = Settings.canDrawOverlays(context),
            startActivity = { context.startActivity(reminderIntent(data)) },
            postNotification = { postReminderNotification(data) }
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

    private fun reminderIntent(data: ReminderLaunchData) =
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

    private fun postReminderNotification(data: ReminderLaunchData) {
        ensureReminderChannel()
        val notificationId = data.sessionId.hashCode() and Int.MAX_VALUE
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            reminderIntent(data),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Focus：先停一下")
            .setContentText(data.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(data.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun ensureReminderChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL_ID,
                "Focus 任务召回",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "打开短视频应用时的任务召回提醒"
            }
        )
    }

    companion object {
        const val ACTIVE_TASK_ID = "active_task_id"
        private const val REMINDER_CHANNEL_ID = "focus_task_reminders"
    }
}
