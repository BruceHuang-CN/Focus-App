package com.example.focus_app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.focus_app.MainActivity
import com.example.focus_app.R
import com.example.focus_app.data.language.localizedText

/**
 * 实时模式下的低打扰前台服务：仅用于保活进程，不做任何轮询。
 * 展示一条低优先级常驻通知，用户可在设置中关闭。
 */
class KeepAliveService : Service() {
    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    localizedText(R.string.service_keepalive_channel),
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = localizedText(R.string.service_keepalive_description)
                    setShowBadge(false)
                }
            )
        }
        val openFocus = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(localizedText(R.string.service_keepalive_title))
            .setContentText(localizedText(R.string.service_keepalive_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(openFocus)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "focus_keep_alive"
    }
}
