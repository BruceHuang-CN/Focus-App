package com.example.focus_app.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 稍后提醒到期时需要的设备状态。 */
interface FollowUpEnvironment {
    fun isDeviceInteractive(): Boolean
    /** 当前真实前台包名；null 表示无法确认（例如缺少使用情况访问权限）。 */
    fun latestForegroundPackage(): String?
}

/** 记录无障碍/兼容检测最后确认的真实前台应用包名，供稍后提醒复用，避免重复查询系统服务。 */
@Singleton
class RealtimeForegroundProvider @Inject constructor() {
    @Volatile private var latestPackageName: String? = null

    fun onRealApplicationForeground(packageName: String) {
        latestPackageName = packageName
    }

    fun current(): String? = latestPackageName
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

    override fun latestForegroundPackage(): String? =
        realtimeForegroundProvider.current() ?: queryUsageStatsForeground()

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
        const val FOREGROUND_LOOKBACK_MS = 24 * 60 * 60 * 1_000L
    }
}
