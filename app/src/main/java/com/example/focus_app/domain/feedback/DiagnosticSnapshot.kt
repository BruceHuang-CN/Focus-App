package com.example.focus_app.domain.feedback

import com.example.focus_app.domain.model.DetectionMode

/**
 * 诊断时间的三态。
 *
 * 必须区分“从来没有记录”和“这次读取失败”，否则会把缺失时间误报成 1970 年。
 */
sealed interface DiagnosticTime {
    /** 已有记录，保留毫秒值便于核对。 */
    data class Recorded(val epochMillis: Long) : DiagnosticTime

    /** Store 里没有这条历史记录。 */
    data object NoRecord : DiagnosticTime

    /** 本次读取失败。 */
    data object Unavailable : DiagnosticTime
}

/** 设备与安装包信息；null 表示该项本次读取失败。 */
data class DeviceDiagnostics(
    val manufacturer: String? = null,
    val model: String? = null,
    val androidVersion: String? = null,
    val apiLevel: Int? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val systemLanguage: String? = null
)

/**
 * 来自既有无障碍诊断 Store 的四项记录。
 *
 * [serviceBound] 只是 App 自己记录的服务连接状态，不是系统授权开关，
 * 也不能证明提醒已经成功送达。
 */
data class AccessibilityDiagnosticsSnapshot(
    val serviceBound: Boolean? = null,
    val lastConnectedAt: DiagnosticTime = DiagnosticTime.NoRecord,
    val lastDestroyedAt: DiagnosticTime = DiagnosticTime.NoRecord,
    val lastInterruptedAt: DiagnosticTime = DiagnosticTime.NoRecord
)

/** 只读的设置摘要；整个对象为 null 表示本次设置读取失败。 */
data class SettingsDiagnostics(
    val guardianEnabled: Boolean? = null,
    val detectionMode: DetectionMode? = null,
    val reminderDelaySeconds: Int? = null,
    val reminderWindowMinutes: Int? = null,
    val maxRemindersPerWindow: Int? = null
)

/**
 * 采集当下的权限、提醒额度与显示配置，不能代表故障发生时的状态或推断故障原因。
 *
 * 每项只读；null 表示本次无法取得该项。目标应用仅保留数量，不保留名称或包名；
 * 提醒仅保留当前设置窗口内的已显示次数，不包含任务正文、使用明细或日志。
 */
data class FeedbackContextDiagnostics(
    val accessibilityEnabled: Boolean? = null,
    val notificationsEnabled: Boolean? = null,
    val overlayAllowed: Boolean? = null,
    val usageAccessAllowed: Boolean? = null,
    val batteryOptimizationsIgnored: Boolean? = null,
    val targetAppCount: Int? = null,
    val remindersInWindow: Int? = null,
    val reminderWindowMinutes: Int? = null,
    val reminderLimit: Int? = null,
    val screenWidthDp: Int? = null,
    val screenHeightDp: Int? = null,
    val fontScale: Float? = null,
    val systemDarkTheme: Boolean? = null
)

/**
 * 一次反馈可附带的全部诊断信息，字段来自固定白名单。
 *
 * 白名单之外的信息（API Key、接口地址、自定义口吻、任务正文、目标应用名单、
 * 使用记录、设备唯一标识、联系人、位置、完整日志或无障碍节点文本）永远不进入这里。
 */
data class DiagnosticSnapshot(
    val device: DeviceDiagnostics = DeviceDiagnostics(),
    val accessibility: AccessibilityDiagnosticsSnapshot = AccessibilityDiagnosticsSnapshot(),
    val settings: SettingsDiagnostics? = null,
    val collectedAtMillis: Long = 0L,
    val context: FeedbackContextDiagnostics? = null
) {
    companion object {
        /** 整体采集失败时的兜底快照：所有字段都按“无法读取”呈现。 */
        fun unavailable(collectedAtMillis: Long): DiagnosticSnapshot = DiagnosticSnapshot(
            device = DeviceDiagnostics(),
            accessibility = AccessibilityDiagnosticsSnapshot(
                serviceBound = null,
                lastConnectedAt = DiagnosticTime.Unavailable,
                lastDestroyedAt = DiagnosticTime.Unavailable,
                lastInterruptedAt = DiagnosticTime.Unavailable
            ),
            settings = null,
            collectedAtMillis = collectedAtMillis
        )
    }
}
