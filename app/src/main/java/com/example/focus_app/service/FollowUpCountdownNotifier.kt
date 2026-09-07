package com.example.focus_app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.focus_app.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 稍后提醒等待期间的倒计时展示边界；不承担真正的到期调度。 */
interface FollowUpCountdownNotifier {
    fun show(sessionId: Long, delayMillis: Long)
    fun cancel(sessionId: Long)
}

object NoOpFollowUpCountdownNotifier : FollowUpCountdownNotifier {
    override fun show(sessionId: Long, delayMillis: Long) = Unit
    override fun cancel(sessionId: Long) = Unit
}

internal data class FollowUpCountdownAlertPolicy(
    val channelId: String,
    val channelImportance: Int,
    val notificationPriority: Int,
    val useDefaultSound: Boolean
)

internal fun followUpCountdownAlertPolicy() = FollowUpCountdownAlertPolicy(
    channelId = "focus_follow_up_countdown_v3",
    channelImportance = NotificationManager.IMPORTANCE_HIGH,
    notificationPriority = NotificationCompat.PRIORITY_HIGH,
    useDefaultSound = true
)

internal fun followUpCountdownDueAt(nowMillis: Long, delayMillis: Long): Long =
    nowMillis + delayMillis.coerceAtLeast(0L)

@Singleton
class AndroidFollowUpCountdownNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) : FollowUpCountdownNotifier {
    private val notificationManager by lazy { NotificationManagerCompat.from(context) }

    @SuppressLint("MissingPermission")
    override fun show(sessionId: Long, delayMillis: Long) {
        if (sessionId <= 0L || !canPostNotifications()) return
        val alertPolicy = followUpCountdownAlertPolicy()
        ensureChannel(alertPolicy)
        val dueAt = followUpCountdownDueAt(System.currentTimeMillis(), delayMillis)
        val id = notificationId(sessionId)
        val contentIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_COUNTDOWN
                data = Uri.parse("focus://follow-up-countdown/$sessionId")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, alertPolicy.channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("稍后提醒倒计时")
            .setContentText("到时间后再次确认是否继续使用目标应用")
            .setWhen(dueAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(alertPolicy.notificationPriority)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(contentIntent)
            .build()
        try {
            notificationManager.notify(NOTIFICATION_TAG, id, notification)
        } catch (_: SecurityException) {
            // 通知权限属于显示增强，不能影响真正的稍后提醒调度。
        }
    }

    override fun cancel(sessionId: Long) {
        if (sessionId <= 0L) return
        notificationManager.cancel(NOTIFICATION_TAG, notificationId(sessionId))
    }

    private fun canPostNotifications(): Boolean {
        if (!notificationManager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(alertPolicy: FollowUpCountdownAlertPolicy) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                alertPolicy.channelId,
                "稍后提醒倒计时",
                alertPolicy.channelImportance
            ).apply {
                description = "显示距离下一次 Focus 提醒的剩余时间"
                if (!alertPolicy.useDefaultSound) setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun notificationId(sessionId: Long): Int = sessionId.hashCode() and Int.MAX_VALUE

    private companion object {
        const val NOTIFICATION_TAG = "focus_follow_up_countdown"
        const val ACTION_OPEN_COUNTDOWN = "com.example.focus_app.action.OPEN_FOLLOW_UP_COUNTDOWN"
    }
}
