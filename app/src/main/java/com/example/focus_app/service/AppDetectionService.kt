package com.example.focus_app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.focus_app.MainActivity
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

private data class ForegroundObservation(
    val packageName: String,
    val timestamp: Long
)

private data class CompatibilityMonitoringState(
    val mode: DetectionMode,
    val guardianEnabled: Boolean
)

internal fun shouldRunCompatibilityMonitoring(
    mode: DetectionMode,
    guardianEnabled: Boolean
): Boolean = mode == DetectionMode.COMPATIBILITY && guardianEnabled

@AndroidEntryPoint
class AppDetectionService : Service() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var appSessionCoordinator: AppSessionCoordinator
    @Inject lateinit var reminderPresentationRegistry: ReminderPresentationRegistry
    @Inject lateinit var realtimeForegroundProvider: RealtimeForegroundProvider

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
                .map {
                    CompatibilityMonitoringState(
                        mode = it.detectionMode,
                        guardianEnabled = it.guardianEnabled
                    )
                }
                .distinctUntilChanged()
                .collectLatest { state ->
                    if (!shouldRunCompatibilityMonitoring(state.mode, state.guardianEnabled)) {
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

    private suspend fun publishLatestForegroundPackage(queryStartedAt: Long): Long {
        val now = System.currentTimeMillis()
        val observation = queryLatestForeground(queryStartedAt, now)
        observation?.let { foreground ->
            realtimeForegroundProvider.onRealApplicationForeground(foreground.packageName)
            appSessionCoordinator.onPackageChanged(
                packageName = foreground.packageName,
                foregroundVerifier = { expectedPackage ->
                    queryLatestForeground(
                        from = (foreground.timestamp - 1L).coerceAtLeast(0L),
                        to = System.currentTimeMillis()
                    )?.packageName == expectedPackage
                },
                isReminderPresentation = isReminderPresentationForForegroundChange(
                    reminderPresentationRegistry.isShowing()
                )
            )
        }
        return now
    }

    @Suppress("DEPRECATION")
    private fun queryLatestForeground(from: Long, to: Long): ForegroundObservation? = try {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val events = usm.queryEvents(from, to)
        val event = UsageEvents.Event()
        var latest: ForegroundObservation? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForegroundEvent =
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            if (isForegroundEvent && (latest == null || event.timeStamp >= latest.timestamp)) {
                latest = ForegroundObservation(event.packageName, event.timeStamp)
            }
        }
        latest
    } catch (error: SecurityException) {
        Log.w(TAG, "Usage access is not available", error)
        null
    } catch (error: RuntimeException) {
        Log.w(TAG, "Unable to query foreground app events", error)
        null
    }

    private fun buildNotification(): Notification {
        val channelId = "focus_detection"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "专注检测", NotificationManager.IMPORTANCE_LOW).apply { description = "Focus 正在守护你的专注力" })
        }
        val openFocus = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId).setContentTitle("Focus 专注助手").setContentText("正在为你守护专注力...")
            .setSmallIcon(android.R.drawable.ic_menu_view).setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true)
            .setContentIntent(openFocus).build()
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

