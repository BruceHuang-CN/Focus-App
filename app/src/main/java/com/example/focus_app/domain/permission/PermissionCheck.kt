package com.example.focus_app.domain.permission

import com.example.focus_app.domain.model.DetectionMode

enum class PermissionCheckStatus { OK, MISSING }

enum class PermissionCheckAction {
    NONE,
    OPEN_ACCESSIBILITY,
    OPEN_USAGE_STATS,
    OPEN_OVERLAY,
    REQUEST_NOTIFICATION,
    OPEN_TARGET_APPS,
    ENABLE_ACCESSIBILITY
}

data class PermissionCheckItem(
    val id: String,
    val label: String,
    val detail: String,
    val status: PermissionCheckStatus,
    val action: PermissionCheckAction = PermissionCheckAction.NONE
)

/**
 * 根据检测模式与各项权限状态，生成设置页「检测状态」窗口的检查清单。
 * 纯函数，便于单元测试。
 */
object PermissionCheckEvaluator {
    fun evaluate(
        mode: DetectionMode,
        accessibilityEnabled: Boolean,
        usageStatsGranted: Boolean,
        notificationGranted: Boolean,
        overlayGranted: Boolean,
        enableAccessibility: Boolean,
        hasTargetApps: Boolean
    ): List<PermissionCheckItem> = when (mode) {
        DetectionMode.REALTIME -> listOf(
            item(
                id = "accessibility",
                label = "系统无障碍服务",
                detail = "设置 → 无障碍 → Focus 专注助手",
                ok = accessibilityEnabled,
                action = PermissionCheckAction.OPEN_ACCESSIBILITY
            ),
            item(
                id = "app_switch",
                label = "启用无障碍检测",
                detail = "设置页中的应用内开关",
                ok = enableAccessibility,
                action = PermissionCheckAction.ENABLE_ACCESSIBILITY
            ),
            item(
                id = "notification",
                label = "通知权限",
                detail = "Android 13+ 需要通知权限",
                ok = notificationGranted,
                action = PermissionCheckAction.REQUEST_NOTIFICATION
            ),
            item(
                id = "overlay",
                label = "显示在其他应用上层",
                detail = "悬浮窗提醒需要此权限",
                ok = overlayGranted,
                action = PermissionCheckAction.OPEN_OVERLAY
            ),
            item(
                id = "targets",
                label = "目标 App",
                detail = "至少选择一个目标应用",
                ok = hasTargetApps,
                action = PermissionCheckAction.OPEN_TARGET_APPS
            )
        )

        DetectionMode.COMPATIBILITY -> listOf(
            item(
                id = "usage",
                label = "使用情况访问权限",
                detail = "设置 → 使用情况访问",
                ok = usageStatsGranted,
                action = PermissionCheckAction.OPEN_USAGE_STATS
            ),
            item(
                id = "notification",
                label = "通知权限",
                detail = "Android 13+ 需要通知权限",
                ok = notificationGranted,
                action = PermissionCheckAction.REQUEST_NOTIFICATION
            ),
            item(
                id = "overlay",
                label = "显示在其他应用上层",
                detail = "悬浮窗提醒需要此权限",
                ok = overlayGranted,
                action = PermissionCheckAction.OPEN_OVERLAY
            ),
            item(
                id = "targets",
                label = "目标 App",
                detail = "至少选择一个目标应用",
                ok = hasTargetApps,
                action = PermissionCheckAction.OPEN_TARGET_APPS
            )
        )
    }

    private fun item(
        id: String,
        label: String,
        detail: String,
        ok: Boolean,
        action: PermissionCheckAction
    ): PermissionCheckItem = PermissionCheckItem(
        id = id,
        label = label,
        detail = detail,
        status = if (ok) PermissionCheckStatus.OK else PermissionCheckStatus.MISSING,
        action = action
    )
}
