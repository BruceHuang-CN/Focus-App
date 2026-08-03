package com.example.focus_app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.domain.model.DetectionMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val POLL_INTERVAL_MS = 10_000L
private const val INITIAL_QUERY_WINDOW_MS = 20_000L

@AndroidEntryPoint
class AppDetectionService : Service() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var appSessionCoordinator: AppSessionCoordinator

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pollingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            settingsRepository.getSettingsFlow()
                .map { it.detectionMode }
                .distinctUntilChanged()
                .collectLatest { mode ->
                    if (mode != DetectionMode.COMPATIBILITY) {
                        appSessionCoordinator.onPackageChanged(null)
                        stopSelf(startId)
                        return@collectLatest
                    }

                    var queryStartedAt = System.currentTimeMillis() - INITIAL_QUERY_WINDOW_MS
                    while (isActive) {
                        queryStartedAt = publishLatestForegroundPackage(queryStartedAt)
                        delay(POLL_INTERVAL_MS)
                    }
                }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    private suspend fun publishLatestForegroundPackage(queryStartedAt: Long): Long {
        val now = System.currentTimeMillis()
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return now
            val events = usm.queryEvents(queryStartedAt, now)
            val event = UsageEvents.Event()
            var latestPackage: String? = null
            var latestTimestamp = Long.MIN_VALUE

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isForegroundEvent =
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
                if (isForegroundEvent && event.timeStamp >= latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    latestPackage = event.packageName
                }
            }

            latestPackage?.let { appSessionCoordinator.onPackageChanged(it) }
        } catch (error: SecurityException) {
            Log.w(TAG, "Usage access is not available", error)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to query foreground app events", error)
        }
        return now
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

    override fun onDestroy() {
        pollingJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        private const val TAG = "AppDetectionService"
    }
}
