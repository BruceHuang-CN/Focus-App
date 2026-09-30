package com.example.focus_app.domain.feedback

import com.example.focus_app.domain.model.DetectionMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 正文中出现的全部固定文案。
 *
 * 由界面层按当前 App 语言从资源构造，格式化器本身保持纯 Kotlin，
 * 因此可以在 JVM 单元测试中直接验证输出。
 */
data class IssueReportTexts(
    val title: String,
    val sectionProblem: String,
    val sectionSteps: String,
    val sectionExpected: String,
    val sectionDiagnostics: String,
    val labelSuffix: String,
    val emptyValue: String,
    val noRecord: String,
    val unavailable: String,
    val yes: String,
    val no: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: String,
    val versionName: String,
    val versionCode: String,
    val systemLanguage: String,
    val collectedAt: String,
    val serviceBound: String,
    val lastConnectedAt: String,
    val lastDestroyedAt: String,
    val lastInterruptedAt: String,
    val diagnosticsNote: String,
    val settingsHeader: String,
    val guardianEnabled: String,
    val detectionMode: String,
    val detectionRealtime: String,
    val detectionCompatibility: String,
    val reminderDelaySeconds: String,
    val reminderWindowMinutes: String,
    val maxRemindersPerWindow: String,
    val contextLabels: FeedbackContextLabels = FeedbackContextLabels()
)

/**
 * 把表单内容和诊断快照渲染成固定分节的纯文本。
 *
 * 输出只包含白名单字段；[formatReport] 在 [includeDiagnostics] 为 false 或快照为 null 时
 * 完全不输出设备、版本、设置、服务记录和诊断采集时间。
 */
class IssueReportFormatter(
    private val texts: IssueReportTexts,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(TIME_PATTERN).withZone(zoneId)

    fun formatReport(
        problem: String,
        steps: String,
        expected: String,
        snapshot: DiagnosticSnapshot?,
        level: DiagnosticLevel
    ): String {
        val lines = mutableListOf<String>()
        lines += texts.title
        lines += ""
        lines += header(texts.sectionProblem)
        lines += userText(problem)
        lines += ""
        lines += header(texts.sectionSteps)
        lines += userText(steps)
        lines += ""
        lines += header(texts.sectionExpected)
        lines += userText(expected)
        if (level != DiagnosticLevel.NONE && snapshot != null) {
            lines += ""
            lines += header(texts.sectionDiagnostics)
            lines += formatDiagnostics(snapshot, level)
        }
        return lines.joinToString("\n")
    }

    fun formatDiagnostics(snapshot: DiagnosticSnapshot, level: DiagnosticLevel = DiagnosticLevel.DETAILED): String {
        val detailed = level == DiagnosticLevel.DETAILED
        val lines = mutableListOf<String>()
        lines += field(texts.manufacturer, snapshot.device.manufacturer)
        lines += field(texts.model, snapshot.device.model)
        lines += field(texts.androidVersion, snapshot.device.androidVersion)
        lines += field(texts.apiLevel, snapshot.device.apiLevel?.toString())
        lines += field(texts.versionName, snapshot.device.versionName)
        lines += field(texts.versionCode, snapshot.device.versionCode?.toString())
        if (detailed) {
            lines += field(texts.systemLanguage, snapshot.device.systemLanguage)
        }
        lines += field(texts.collectedAt, formatTime(snapshot.collectedAtMillis))
        lines += ""
        lines += field(texts.serviceBound, snapshot.accessibility.serviceBound?.let(::yesNo))
        if (detailed) {
            lines += field(
                texts.lastConnectedAt,
                formatDiagnosticTime(snapshot.accessibility.lastConnectedAt)
            )
            lines += field(
                texts.lastDestroyedAt,
                formatDiagnosticTime(snapshot.accessibility.lastDestroyedAt)
            )
            lines += field(
                texts.lastInterruptedAt,
                formatDiagnosticTime(snapshot.accessibility.lastInterruptedAt)
            )
        }
        lines += ""
        lines += texts.settingsHeader
        val settings = snapshot.settings
        lines += field(texts.guardianEnabled, settings?.guardianEnabled?.let(::yesNo))
        lines += field(texts.detectionMode, settings?.detectionMode?.let(::detectionModeLabel))
        if (detailed) {
            lines += field(texts.reminderDelaySeconds, settings?.reminderDelaySeconds?.toString())
            lines += field(texts.reminderWindowMinutes, settings?.reminderWindowMinutes?.toString())
            lines += field(texts.maxRemindersPerWindow, settings?.maxRemindersPerWindow?.toString())
        }
        lines += ""
        lines += texts.diagnosticsNote
        formatContext(snapshot, level).takeIf { it.isNotBlank() }?.let {
            lines += ""
            lines += it
        }
        return lines.joinToString("\n")
    }

    /** 用户可预览的附加诊断。协议 v1 用既有 steps 文本承载，不扩充远端 JSON 白名单。 */
    fun formatContext(snapshot: DiagnosticSnapshot?, level: DiagnosticLevel): String {
        if (level == DiagnosticLevel.NONE) return ""
        val value = snapshot?.context ?: return ""
        val labels = texts.contextLabels
        return listOf(
            "【${labels.header}】",
            field(texts.collectedAt, formatTime(snapshot.collectedAtMillis)),
            labels.note,
            field(labels.accessibilityEnabled, value.accessibilityEnabled?.let(::yesNo)),
            field(labels.notificationsEnabled, value.notificationsEnabled?.let(::yesNo)),
            field(labels.overlayAllowed, value.overlayAllowed?.let(::yesNo)),
            field(labels.usageAccessAllowed, value.usageAccessAllowed?.let(::yesNo)),
            field(labels.batteryOptimizationsIgnored, value.batteryOptimizationsIgnored?.let(::yesNo)),
            field(labels.targetAppCount, value.targetAppCount?.toString()),
            field(labels.remindersInWindow, value.remindersInWindow?.toString()),
            field(texts.reminderWindowMinutes, value.reminderWindowMinutes?.toString()),
            field(texts.maxRemindersPerWindow, value.reminderLimit?.toString()),
            field(labels.screenWidthDp, value.screenWidthDp?.toString()),
            field(labels.screenHeightDp, value.screenHeightDp?.toString()),
            field(labels.fontScale, value.fontScale?.toString()),
            field(labels.systemDarkTheme, value.systemDarkTheme?.let(::yesNo))
        ).joinToString("\n")
    }

    private fun header(label: String): String = label + texts.labelSuffix

    private fun field(label: String, value: String?): String =
        label + texts.labelSuffix + (value?.takeIf { it.isNotBlank() } ?: texts.unavailable)

    /** 用户自己输入的内容保留原意和换行，只去掉首尾空白；空内容统一显示“未填写”。 */
    private fun userText(value: String): String =
        if (value.isBlank()) texts.emptyValue else value.trim()

    private fun yesNo(value: Boolean): String = if (value) texts.yes else texts.no

    private fun detectionModeLabel(mode: DetectionMode): String = when (mode) {
        DetectionMode.REALTIME -> texts.detectionRealtime
        DetectionMode.COMPATIBILITY -> texts.detectionCompatibility
    }

    private fun formatDiagnosticTime(value: DiagnosticTime): String = when (value) {
        is DiagnosticTime.Recorded -> formatTime(value.epochMillis)
        DiagnosticTime.NoRecord -> texts.noRecord
        DiagnosticTime.Unavailable -> texts.unavailable
    }

    /** 显示时注明时区，并保留毫秒值便于核对。 */
    private fun formatTime(epochMillis: Long): String =
        timeFormatter.format(Instant.ofEpochMilli(epochMillis))

    private companion object {
        const val TIME_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS O"
    }
}
