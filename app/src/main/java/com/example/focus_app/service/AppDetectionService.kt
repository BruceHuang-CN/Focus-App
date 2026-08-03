package com.example.focus_app.service

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.domain.usecase.TrackAppOpenUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

private const val POLL_INTERVAL_MS = 5000L
private const val USAGE_QUERY_WINDOW_MS = 10000L

@AndroidEntryPoint
class AppDetectionService : Service() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var trackAppOpenUseCase: TrackAppOpenUseCase

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pollingJob: Job? = null
    private var lastForegroundPackage: String? = null
    private var settings: AppSettings = AppSettings()

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        scope.launch { settingsRepository.getSettingsFlow().collect { settings = it } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                checkForegroundApp()
                delay(POLL_INTERVAL_MS)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun checkForegroundApp() {
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - USAGE_QUERY_WINDOW_MS, now)
            val pkg = stats.maxByOrNull { it.lastTimeUsed }?.packageName ?: return
            if (pkg == lastForegroundPackage) return
            lastForegroundPackage = pkg

            val app = settings.targetApps.find { it.packageName == pkg } ?: return
            Log.d("Focus", "Detected target app: ${app.appName} ($pkg)")
            val result = trackAppOpenUseCase(pkg, app.appName, settings.maxRemindsPerHour)
            Log.d("Focus", "Track result: eventId=${result.eventId}, shouldRemind=${result.shouldRemind}, reminded=${result.remindedCountThisHour}/${result.maxReminds}")
            if (result.shouldRemind) scheduleReminder(result.eventId, app.appName, pkg, settings.remindDelayMinutes)
        } catch (_: Exception) {}
    }

    private fun scheduleReminder(eventId: Long, appName: String, packageName: String, delayMinutes: Int) {
        val intent = Intent(this, ReminderActivity::class.java).apply {
            putExtra("event_id", eventId); putExtra("app_name", appName)
            putExtra("package_name", packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(this, (eventId % Int.MAX_VALUE).toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val triggerTime = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerTime, pi)
        }
        Log.d("Focus", "Reminder scheduled for $appName in ${delayMinutes}min at $triggerTime")
    }

    private fun buildNotification(): Notification {
        val channelId = "focus_detection"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "专注检测", NotificationManager.IMPORTANCE_LOW).apply { description = "Focus 正在守护你的专注力" })
        }
        return NotificationCompat.Builder(this, channelId).setContentTitle("Focus 专注助手").setContentText("正在为你守护专注力...")
            .setSmallIcon(android.R.drawable.ic_menu_view).setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true).build()
    }

    override fun onDestroy() { pollingJob?.cancel(); scope.cancel(); super.onDestroy() }
    companion object { const val NOTIFICATION_ID = 1001 }
}
