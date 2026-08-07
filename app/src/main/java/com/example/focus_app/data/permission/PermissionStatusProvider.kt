package com.example.focus_app.data.permission

import android.content.Context
import com.example.focus_app.util.PermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface PermissionStatusProvider {
    fun accessibilityEnabled(): Boolean
    fun usageStatsGranted(): Boolean
    fun notificationGranted(): Boolean
    fun overlayGranted(): Boolean
}

@Singleton
class AndroidPermissionStatusProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : PermissionStatusProvider {
    override fun accessibilityEnabled(): Boolean =
        PermissionHelper.isAccessibilityServiceEnabled(context)

    override fun usageStatsGranted(): Boolean =
        PermissionHelper.hasUsageStatsPermission(context)

    override fun notificationGranted(): Boolean =
        !PermissionHelper.needsNotificationPermission(context)

    override fun overlayGranted(): Boolean =
        PermissionHelper.hasOverlayPermission(context)
}
