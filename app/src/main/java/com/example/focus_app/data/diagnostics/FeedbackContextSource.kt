package com.example.focus_app.data.diagnostics

import android.content.Context
import android.content.res.Configuration
import android.os.PowerManager
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.ReminderDisplayRepository
import com.example.focus_app.domain.feedback.FeedbackContextDiagnostics
import com.example.focus_app.util.PermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/** 复用同次采集的设置与时间，避免诊断中出现两个不同的额度窗口。 */
fun interface FeedbackContextSource {
    suspend fun collect(settings: AppSettings?, collectedAtMillis: Long): FeedbackContextDiagnostics?
}

/** 系统读取边界；任何方法都只读，不申请权限、不打开设置、不改变服务状态。 */
interface FeedbackSystemStateSource {
    fun accessibilityEnabled(): Boolean?
    fun notificationsEnabled(): Boolean?
    fun overlayAllowed(): Boolean?
    fun usageAccessAllowed(): Boolean?
    fun batteryOptimizationsIgnored(): Boolean?
    fun screenWidthDp(): Int?
    fun screenHeightDp(): Int?
    fun fontScale(): Float?
    fun systemDarkTheme(): Boolean?
}

@Singleton
class AndroidFeedbackSystemStateSource @Inject constructor(
    @ApplicationContext private val context: Context
) : FeedbackSystemStateSource {
    override fun accessibilityEnabled(): Boolean = PermissionHelper.isAccessibilityServiceEnabled(context)
    override fun notificationsEnabled(): Boolean = PermissionHelper.notificationsEnabled(context)
    override fun overlayAllowed(): Boolean = PermissionHelper.hasOverlayPermission(context)
    override fun usageAccessAllowed(): Boolean = PermissionHelper.hasUsageStatsPermission(context)

    override fun batteryOptimizationsIgnored(): Boolean? {
        // PermissionHelper 为界面返回 false；诊断需区分无法取得系统服务与确实未获豁免。
        if (context.getSystemService(Context.POWER_SERVICE) !is PowerManager) return null
        return PermissionHelper.isIgnoringBatteryOptimizations(context)
    }

    override fun screenWidthDp(): Int = context.resources.configuration.screenWidthDp
    override fun screenHeightDp(): Int = context.resources.configuration.screenHeightDp
    override fun fontScale(): Float = context.resources.configuration.fontScale
    override fun systemDarkTheme(): Boolean? =
        when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            Configuration.UI_MODE_NIGHT_NO -> false
            else -> null
        }
}

/** 单项失败保留为 null，其他项继续采集；不吞掉协程取消。 */
@Singleton
class DefaultFeedbackContextSource @Inject constructor(
    private val system: FeedbackSystemStateSource,
    private val reminderDisplays: ReminderDisplayRepository
) : FeedbackContextSource {
    override suspend fun collect(settings: AppSettings?, collectedAtMillis: Long): FeedbackContextDiagnostics {
        val windowMinutes = settings?.reminderWindowMinutes?.takeIf { it >= 0 }
        val reminderCount = if (windowMinutes == null) {
            null
        } else {
            readOptional {
                reminderDisplays.countSince(collectedAtMillis - windowMinutes * 60_000L)
                    .takeIf { it >= 0 }
            }
        }
        return FeedbackContextDiagnostics(
            accessibilityEnabled = readOptional { system.accessibilityEnabled() },
            notificationsEnabled = readOptional { system.notificationsEnabled() },
            overlayAllowed = readOptional { system.overlayAllowed() },
            usageAccessAllowed = readOptional { system.usageAccessAllowed() },
            batteryOptimizationsIgnored = readOptional { system.batteryOptimizationsIgnored() },
            targetAppCount = settings?.targetApps?.size,
            remindersInWindow = reminderCount,
            reminderWindowMinutes = windowMinutes,
            reminderLimit = settings?.maxRemindersPerWindow?.takeIf { it >= 0 },
            screenWidthDp = readOptional { system.screenWidthDp()?.takeIf { it > 0 } },
            screenHeightDp = readOptional { system.screenHeightDp()?.takeIf { it > 0 } },
            fontScale = readOptional { system.fontScale()?.takeIf { it.isFinite() && it > 0f } },
            systemDarkTheme = readOptional { system.systemDarkTheme() }
        )
    }
}

private inline fun <T> readOptional(block: () -> T?): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}
