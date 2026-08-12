package com.example.focus_app.util

import android.Manifest
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.focus_app.service.FocusAccessibilityService

object PermissionHelper {
    private const val PREFS_NAME = "focus_prefs"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val expected = ComponentName(context, FocusAccessibilityService::class.java).flattenToString()
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun needsNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

    fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun openOverlaySettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        )
    }

    fun notificationsEnabled(context: Context): Boolean =
        !needsNotificationPermission(context) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun openNotificationSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    }

    fun openUsageStatsSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            isIgnoringBatteryOptimizations(context)
        ) {
            return
        }
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
            )
        } catch (_: ActivityNotFoundException) {
            openBatteryOptimizationSettings(context)
        } catch (_: SecurityException) {
            openBatteryOptimizationSettings(context)
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }

    fun backgroundProtectionHint(): String = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi" -> "\u82e5\u4ecd\u6709\u5ef6\u8fdf\uff0c\u53ef\u5728\u5b89\u5168\u4e2d\u5fc3\u5141\u8bb8\u81ea\u542f\u52a8\u548c\u540e\u53f0\u6d3b\u52a8\u3002"
        "huawei", "honor" -> "\u82e5\u4ecd\u6709\u5ef6\u8fdf\uff0c\u53ef\u5728\u5e94\u7528\u542f\u52a8\u7ba1\u7406\u5141\u8bb8\u81ea\u542f\u52a8\u548c\u540e\u53f0\u6d3b\u52a8\u3002"
        "oppo", "realme", "vivo", "iqoo" -> "\u82e5\u4ecd\u6709\u5ef6\u8fdf\uff0c\u53ef\u5728\u7cfb\u7edf\u7ba1\u5bb6\u4e2d\u5141\u8bb8\u81ea\u542f\u52a8\u548c\u540e\u53f0\u6d3b\u52a8\u3002"
        else -> "\u4e0d\u540c\u54c1\u724c\u540d\u79f0\u4e0d\u540c\uff1b\u8bf7\u5141\u8bb8 Focus \u81ea\u542f\u52a8\u548c\u540e\u53f0\u6d3b\u52a8\u3002"
    }

    fun isOnboardingDone(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun markOnboardingDone(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }
}
