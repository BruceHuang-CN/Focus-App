package com.example.focus_app.domain.permission

import android.content.Context
import com.example.focus_app.R
import com.example.focus_app.data.language.localizedText

fun PermissionCheckItem.localizedLabel(context: Context): String = when (id) {
    "usage" -> context.localizedText(R.string.permission_usage_label)
    "accessibility" -> context.localizedText(R.string.permission_accessibility_label)
    "app_switch" -> context.localizedText(R.string.permission_switch_label)
    "notification" -> context.localizedText(R.string.permission_notification_label)
    "overlay" -> context.localizedText(R.string.permission_overlay_label)
    "targets" -> context.localizedText(R.string.permission_targets_label)
    else -> label
}

fun PermissionCheckItem.localizedDetail(context: Context): String = when (id) {
    "usage" -> context.localizedText(R.string.permission_usage_detail)
    "accessibility" -> context.localizedText(R.string.permission_accessibility_detail)
    "app_switch" -> context.localizedText(R.string.permission_switch_detail)
    "notification" -> context.localizedText(R.string.permission_notification_detail)
    "overlay" -> context.localizedText(R.string.permission_overlay_detail)
    "targets" -> context.localizedText(R.string.permission_targets_detail)
    else -> detail
}
