package com.example.focus_app.domain.feedback

/** 展示文案由界面资源提供；默认值供纯 Kotlin 使用者使用。 */
data class FeedbackContextLabels(
    val header: String = "Automatic diagnostic supplement",
    val note: String = "Collected now; this does not reconstruct the moment of the issue.",
    val accessibilityEnabled: String = "Accessibility switch enabled",
    val notificationsEnabled: String = "Notifications allowed",
    val overlayAllowed: String = "Display over other apps allowed",
    val usageAccessAllowed: String = "Usage access allowed",
    val batteryOptimizationsIgnored: String = "Battery optimization exemption",
    val targetAppCount: String = "Selected guardian app count",
    val remindersInWindow: String = "Reminders counted in current window",
    val screenWidthDp: String = "Window width (dp)",
    val screenHeightDp: String = "Window height (dp)",
    val fontScale: String = "Font scale",
    val systemDarkTheme: String = "System dark theme"
)
