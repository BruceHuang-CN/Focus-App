package com.example.focus_app.service

import com.example.focus_app.domain.model.AppUsageSession
import javax.inject.Inject

/** 稍后提醒到期时的决策结果。 */
enum class FollowUpDecision {
    /** 展示提醒。 */
    SHOW,

    /** 条件不满足，放弃本次提醒。 */
    SKIP,

    /** 暂时不能展示（例如锁屏），稍后重试。 */
    RETRY
}

data class FollowUpGateInput(
    val guardianEnabled: Boolean,
    val session: AppUsageSession?,
    val currentOpenSessionId: Long?,
    val targetPackages: List<String>,
    val deviceInteractive: Boolean,
    /** 当前真实前台包名；null 表示无法确认（例如缺少使用情况访问权限）。 */
    val latestForegroundPackage: String?,
    val remindedCountSinceWindow: Int,
    val maxRemindersPerWindow: Int
)

/**
 * 稍后提醒的展示决策。
 *
 * 前台状态无法确认时不视为“已离开”：宁可多提醒一次，也不能让用户主动选择的
 * 稍后提醒静默丢失（这是本类与旧实现的关键差异）。
 */
class FollowUpReminderGate @Inject constructor() {
    fun decide(input: FollowUpGateInput): FollowUpDecision {
        val session = input.session ?: return FollowUpDecision.SKIP
        if (!input.guardianEnabled) return FollowUpDecision.SKIP
        if (session.endedAt != null) return FollowUpDecision.SKIP
        if (input.currentOpenSessionId != session.id) return FollowUpDecision.SKIP
        if (session.packageName !in input.targetPackages) return FollowUpDecision.SKIP

        if (!input.deviceInteractive) return FollowUpDecision.RETRY

        val foreground = input.latestForegroundPackage
        if (foreground != null && foreground != session.packageName) {
            return FollowUpDecision.SKIP
        }

        if (input.remindedCountSinceWindow >= input.maxRemindersPerWindow) {
            return FollowUpDecision.SKIP
        }
        return FollowUpDecision.SHOW
    }
}

