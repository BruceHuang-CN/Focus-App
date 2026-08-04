package com.example.focus_app.domain.usecase

import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.domain.task.StreakCalculator
import com.example.focus_app.domain.time.Clock
import com.example.focus_app.domain.time.SystemClock
import javax.inject.Inject

data class StreakStatus(
    val streakDays: Int,
    val completedToday: Int,
    val shortVideoTodayMinutes: Int,
    val shortVideoLimitMinutes: Int,
    val withinDailyLimit: Boolean,
    val taskDoneToday: Boolean,
    val achievedToday: Boolean
)

/**
 * 轻量养成状态：连续达标天数、今日短视频时长限制、今日任务完成情况。
 * 主动退出只作为正向反馈，不参与扣分。
 */
class StreakStatusUseCase(
    private val tasks: TaskRepository,
    private val sessions: AppSessionRepository,
    private val settings: SettingsRepository,
    private val clock: Clock
) {
    @Inject
    constructor(
        tasks: TaskRepository,
        sessions: AppSessionRepository,
        settings: SettingsRepository
    ) : this(tasks, sessions, settings, SystemClock)

    suspend operator fun invoke(): StreakStatus {
        val zone = clock.now().zone
        val now = clock.now(zone)
        val today = now.toLocalDate()
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val current = settings.getSettings()

        val shortVideoMinutes = (sessions.sessionsBetween(todayStart, tomorrowStart).sumOf { session ->
            val end = minOf(session.endedAt ?: now.toInstant().toEpochMilli(), tomorrowStart)
            (end - maxOf(session.startedAt, todayStart)).coerceAtLeast(0L)
        } / MINUTE_MILLIS).toInt()

        val completedToday = tasks.completedCountBetween(todayStart, tomorrowStart)
        val streakDays = StreakCalculator.streakDays(today) { day ->
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            tasks.completedCountBetween(start, start + DAY_MILLIS)
        }
        val withinLimit = shortVideoMinutes <= current.dailyShortVideoLimitMinutes
        return StreakStatus(
            streakDays = streakDays,
            completedToday = completedToday,
            shortVideoTodayMinutes = shortVideoMinutes,
            shortVideoLimitMinutes = current.dailyShortVideoLimitMinutes,
            withinDailyLimit = withinLimit,
            taskDoneToday = completedToday > 0,
            achievedToday = withinLimit && completedToday > 0
        )
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val MINUTE_MILLIS = 60_000L
    }
}
