package com.example.focus_app.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface FollowUpAlarmScheduler {
    fun schedule(sessionId: Long, delayMillis: Long, retryAttempt: Int = 0)
    fun cancel(sessionId: Long)
}

internal enum class FollowUpAlarmMode {
    EXACT_ALLOW_IDLE,
    ALLOW_IDLE
}

internal fun selectFollowUpAlarmMode(
    sdkInt: Int,
    canScheduleExact: Boolean
): FollowUpAlarmMode =
    if (sdkInt <= Build.VERSION_CODES.R || canScheduleExact) {
        FollowUpAlarmMode.EXACT_ALLOW_IDLE
    } else {
        FollowUpAlarmMode.ALLOW_IDLE
    }

internal fun followUpAlarmData(sessionId: Long): String =
    "focus://follow-up/$sessionId"

@Singleton
class AndroidFollowUpAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : FollowUpAlarmScheduler {
    private val alarmManager: AlarmManager by lazy {
        requireNotNull(context.getSystemService(AlarmManager::class.java))
    }

    @SuppressLint("ScheduleExactAlarm")
    override fun schedule(sessionId: Long, delayMillis: Long, retryAttempt: Int) {
        if (sessionId <= 0L) return

        val triggerAtMillis = SystemClock.elapsedRealtime() + delayMillis.coerceAtLeast(0L)
        val pendingIntent = pendingIntent(sessionId, retryAttempt)
        val canScheduleExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

        when (selectFollowUpAlarmMode(Build.VERSION.SDK_INT, canScheduleExact)) {
            FollowUpAlarmMode.EXACT_ALLOW_IDLE -> {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } catch (securityException: SecurityException) {
                    Log.w(TAG, "Exact follow-up alarm unavailable; using allow-while-idle")
                    scheduleAllowWhileIdle(triggerAtMillis, pendingIntent)
                }
            }

            FollowUpAlarmMode.ALLOW_IDLE ->
                scheduleAllowWhileIdle(triggerAtMillis, pendingIntent)
        }
    }

    override fun cancel(sessionId: Long) {
        if (sessionId <= 0L) return
        alarmManager.cancel(pendingIntent(sessionId, retryAttempt = 0))
    }

    private fun scheduleAllowWhileIdle(
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    private fun pendingIntent(sessionId: Long, retryAttempt: Int): PendingIntent {
        val intent = Intent(context, FollowUpAlarmReceiver::class.java).apply {
            action = ACTION_FOLLOW_UP_ALARM
            data = Uri.parse(followUpAlarmData(sessionId))
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_RETRY_ATTEMPT, retryAttempt.coerceAtLeast(0))
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        internal const val ACTION_FOLLOW_UP_ALARM =
            "com.example.focus_app.action.FOLLOW_UP_ALARM"
        internal const val EXTRA_SESSION_ID = "follow_up_session_id"
        internal const val EXTRA_RETRY_ATTEMPT = "follow_up_retry_attempt"
        private const val REQUEST_CODE = 0
        private const val TAG = "FollowUpAlarm"
    }
}
