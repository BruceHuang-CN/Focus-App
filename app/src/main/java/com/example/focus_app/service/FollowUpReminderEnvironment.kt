package com.example.focus_app.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface FollowUpEnvironment {
    fun isDeviceInteractive(): Boolean
    fun foregroundSnapshot(): ForegroundSnapshot
}

enum class ForegroundSource {
    REALTIME,
    USAGE_EVENTS
}

sealed interface ForegroundSnapshot {
    data class Confirmed(
        val packageName: String,
        val source: ForegroundSource
    ) : ForegroundSnapshot

    data object Unknown : ForegroundSnapshot
}

data class TimedForegroundPackage(
    val packageName: String,
    val observedAtElapsedRealtime: Long
)

internal fun selectForegroundSnapshot(
    cached: TimedForegroundPackage?,
    nowElapsedRealtime: Long,
    maxCacheAgeMillis: Long,
    usagePackage: () -> String?
): ForegroundSnapshot {
    val cacheAge = cached?.let { nowElapsedRealtime - it.observedAtElapsedRealtime }
    if (cached != null && cacheAge != null && cacheAge in 0..maxCacheAgeMillis) {
        return ForegroundSnapshot.Confirmed(cached.packageName, ForegroundSource.REALTIME)
    }
    return usagePackage()?.let {
        ForegroundSnapshot.Confirmed(it, ForegroundSource.USAGE_EVENTS)
    } ?: ForegroundSnapshot.Unknown
}

@Singleton
class RealtimeForegroundProvider @Inject constructor() {
    @Volatile
    private var latest: TimedForegroundPackage? = null

    fun onRealApplicationForeground(
        packageName: String,
        observedAtElapsedRealtime: Long = SystemClock.elapsedRealtime()
    ) {
        latest = TimedForegroundPackage(packageName, observedAtElapsedRealtime)
    }

    fun current(): TimedForegroundPackage? = latest
}

@Singleton
class AndroidFollowUpEnvironment @Inject constructor(
    @ApplicationContext private val context: Context,
    private val realtimeForegroundProvider: RealtimeForegroundProvider
) : FollowUpEnvironment {

    override fun isDeviceInteractive(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isInteractive != false
    }

    override fun foregroundSnapshot(): ForegroundSnapshot = selectForegroundSnapshot(
        cached = realtimeForegroundProvider.current(),
        nowElapsedRealtime = SystemClock.elapsedRealtime(),
        maxCacheAgeMillis = REALTIME_CACHE_MAX_AGE_MS,
        usagePackage = ::queryUsageStatsForeground
    )

    @Suppress("DEPRECATION")
    private fun queryUsageStatsForeground(): String? = try {
        val usageStats = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(now - FOREGROUND_LOOKBACK_MS, now)
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTime = Long.MIN_VALUE
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val becameForeground =
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            if (becameForeground && event.timeStamp >= latestTime) {
                latestPackage = event.packageName
                latestTime = event.timeStamp
            }
        }
        latestPackage
    } catch (_: SecurityException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    private companion object {
        const val REALTIME_CACHE_MAX_AGE_MS = 3_000L
        const val FOREGROUND_LOOKBACK_MS = 24 * 60 * 60 * 1_000L
    }
}
