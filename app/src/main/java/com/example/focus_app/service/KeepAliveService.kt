package com.example.focus_app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 实时模式下的低打扰前台服务：仅用于保活进程，不做任何轮询。
 * 展示一条低优先级常驻通知，用户可在设置中关闭。
 */
class KeepAliveService : Service() {
    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Focus 常驻守护",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "保持 Focus 进程存活以持续提醒，可在设置中关闭"
                    setShowBadge(false)
                }
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Focus 正在守护专注")
            .setContentText("打开目标应用时提醒你回到任务")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "focus_keep_alive"
    }
}
