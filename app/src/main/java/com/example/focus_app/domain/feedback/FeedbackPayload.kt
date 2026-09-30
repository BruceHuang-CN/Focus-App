package com.example.focus_app.domain.feedback

import com.example.focus_app.domain.model.DetectionMode

/**
 * 反馈诊断级别。分级决定实际会上传到服务器、以及用户能看到的诊断字段。
 *
 * - [NONE]：不附带任何诊断。
 * - [BASIC]：设备与版本、服务连接状态、守护开关、检测模式、采集时间。
 * - [DETAILED]：基础字段外加系统语言、三项历史时间和提醒频率摘要。
 */
enum class DiagnosticLevel(val wireValue: String) {
    NONE("none"),
    BASIC("basic"),
    DETAILED("detailed");

    companion object {
        /** 协议值解析；未知值按最保守的 [NONE] 处理。 */
        fun fromWire(value: String?): DiagnosticLevel =
            DiagnosticLevel.entries.firstOrNull { it.wireValue == value } ?: NONE
    }
}

/**
 * `POST /api/app-feedback` 的请求体，字段与 `docs/app-feedback-api-v1.md` 一一对应。
 *
 * 只包含协议白名单字段；Gson 默认省略 null，因此关闭或未采集的字段不会出现在线上 JSON 中。
 * 不要把 AppSettings、无障碍运行时对象或任何用户配置直接序列化进这里。
 */
data class AppFeedbackRequest(
    val schemaVersion: Int = SCHEMA_VERSION,
    val id: String,
    val problem: String,
    val steps: String,
    val expected: String,
    val diagnosticLevel: String,
    val diagnostics: AppFeedbackDiagnostics?
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

data class AppFeedbackDiagnostics(
    val collectedAtMillis: Long,
    val device: AppFeedbackDevice,
    val accessibility: AppFeedbackAccessibility,
    val settings: AppFeedbackSettings?
)

data class AppFeedbackDevice(
    val manufacturer: String? = null,
    val model: String? = null,
    val androidVersion: String? = null,
    val apiLevel: Int? = null,
    val versionName: String? = null,
    val versionCode: String? = null,
    val systemLanguage: String? = null
)

data class AppFeedbackAccessibility(
    val serviceBound: Boolean? = null,
    val lastConnectedAt: AppFeedbackTime? = null,
    val lastDestroyedAt: AppFeedbackTime? = null,
    val lastInterruptedAt: AppFeedbackTime? = null
)

data class AppFeedbackSettings(
    val guardianEnabled: Boolean? = null,
    val detectionMode: String? = null,
    val reminderDelaySeconds: Int? = null,
    val reminderWindowMinutes: Int? = null,
    val maxRemindersPerWindow: Int? = null
)

/** 历史时间三态；[state] 取 recorded / no_record / unavailable。 */
data class AppFeedbackTime(
    val state: String,
    val epochMillis: Long? = null
) {
    companion object {
        const val RECORDED = "recorded"
        const val NO_RECORD = "no_record"
        const val UNAVAILABLE = "unavailable"
    }
}

data class AppFeedbackResponse(
    val ok: Boolean? = null,
    val receipt: String? = null,
    val code: String? = null
)

/**
 * 把已冻结的诊断快照按级别投影为线上请求体。
 *
 * [level] 为 [DiagnosticLevel.NONE] 时 `diagnostics` 必须为 null；
 * basic / detailed 时必须提供快照。basic 不会携带 detailed 字段（值为 null，Gson 序列化时省略）。
 */
fun buildAppFeedbackRequest(
    id: String,
    problem: String,
    steps: String,
    expected: String,
    level: DiagnosticLevel,
    snapshot: DiagnosticSnapshot?
): AppFeedbackRequest {
    val diagnostics = when (level) {
        DiagnosticLevel.NONE -> null
        else -> projectDiagnostics(requireNotNull(snapshot) { "basic/detailed requires a snapshot" }, level)
    }
    return AppFeedbackRequest(
        id = id,
        problem = problem.trim(),
        steps = steps.trim(),
        expected = expected.trim(),
        diagnosticLevel = level.wireValue,
        diagnostics = diagnostics
    )
}

private fun projectDiagnostics(snapshot: DiagnosticSnapshot, level: DiagnosticLevel): AppFeedbackDiagnostics {
    val detailed = level == DiagnosticLevel.DETAILED
    return AppFeedbackDiagnostics(
        collectedAtMillis = snapshot.collectedAtMillis,
        device = AppFeedbackDevice(
            manufacturer = snapshot.device.manufacturer,
            model = snapshot.device.model,
            androidVersion = snapshot.device.androidVersion,
            apiLevel = snapshot.device.apiLevel,
            versionName = snapshot.device.versionName,
            versionCode = snapshot.device.versionCode?.toString(),
            systemLanguage = if (detailed) snapshot.device.systemLanguage else null
        ),
        accessibility = AppFeedbackAccessibility(
            serviceBound = snapshot.accessibility.serviceBound,
            lastConnectedAt = if (detailed) snapshot.accessibility.lastConnectedAt.toWireTime() else null,
            lastDestroyedAt = if (detailed) snapshot.accessibility.lastDestroyedAt.toWireTime() else null,
            lastInterruptedAt = if (detailed) snapshot.accessibility.lastInterruptedAt.toWireTime() else null
        ),
        settings = snapshot.settings?.let { settings ->
            AppFeedbackSettings(
                guardianEnabled = settings.guardianEnabled,
                detectionMode = settings.detectionMode?.wireValue,
                reminderDelaySeconds = if (detailed) settings.reminderDelaySeconds else null,
                reminderWindowMinutes = if (detailed) settings.reminderWindowMinutes else null,
                maxRemindersPerWindow = if (detailed) settings.maxRemindersPerWindow else null
            )
        }
    )
}

/**
 * 从已保存的待发送请求重建诊断快照，供进程恢复后本地展示。
 *
 * 只还原协议白名单字段，不读取当前设备或设置的实时值。
 */
fun AppFeedbackRequest.toDiagnosticSnapshot(): DiagnosticSnapshot? {
    val payload = diagnostics ?: return null
    return DiagnosticSnapshot(
        device = DeviceDiagnostics(
            manufacturer = payload.device.manufacturer,
            model = payload.device.model,
            androidVersion = payload.device.androidVersion,
            apiLevel = payload.device.apiLevel,
            versionName = payload.device.versionName,
            versionCode = payload.device.versionCode?.toLongOrNull(),
            systemLanguage = payload.device.systemLanguage
        ),
        accessibility = AccessibilityDiagnosticsSnapshot(
            serviceBound = payload.accessibility.serviceBound,
            lastConnectedAt = payload.accessibility.lastConnectedAt.toDiagnosticTime(),
            lastDestroyedAt = payload.accessibility.lastDestroyedAt.toDiagnosticTime(),
            lastInterruptedAt = payload.accessibility.lastInterruptedAt.toDiagnosticTime()
        ),
        settings = payload.settings?.let {
            SettingsDiagnostics(
                guardianEnabled = it.guardianEnabled,
                detectionMode = DetectionMode.entries.firstOrNull { mode -> mode.wireValue == it.detectionMode },
                reminderDelaySeconds = it.reminderDelaySeconds,
                reminderWindowMinutes = it.reminderWindowMinutes,
                maxRemindersPerWindow = it.maxRemindersPerWindow
            )
        },
        collectedAtMillis = payload.collectedAtMillis
    )
}

val DetectionMode.wireValue: String
    get() = when (this) {
        DetectionMode.REALTIME -> "realtime"
        DetectionMode.COMPATIBILITY -> "compatibility"
    }

private fun DiagnosticTime.toWireTime(): AppFeedbackTime = when (this) {
    is DiagnosticTime.Recorded -> AppFeedbackTime(AppFeedbackTime.RECORDED, epochMillis)
    DiagnosticTime.NoRecord -> AppFeedbackTime(AppFeedbackTime.NO_RECORD)
    DiagnosticTime.Unavailable -> AppFeedbackTime(AppFeedbackTime.UNAVAILABLE)
}

private fun AppFeedbackTime?.toDiagnosticTime(): DiagnosticTime = when (this?.state) {
    AppFeedbackTime.RECORDED -> DiagnosticTime.Recorded(epochMillis ?: 0L)
    AppFeedbackTime.UNAVAILABLE -> DiagnosticTime.Unavailable
    else -> DiagnosticTime.NoRecord
}

/** 已冻结、可预览、可复制、可重试的一次投稿。预览与实际上传必须来自同一份数据。 */
data class PreparedSubmission(
    val payload: AppFeedbackRequest,
    val displayBody: String,
    val level: DiagnosticLevel,
    /** 仅本地保留原始输入，恢复时不把自动诊断当作用户正文再次拼接。 */
    val formSteps: String? = null,
    val context: FeedbackContextDiagnostics? = null
) {
    val id: String get() = payload.id
}
