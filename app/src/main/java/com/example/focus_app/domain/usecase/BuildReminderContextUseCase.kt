package com.example.focus_app.domain.usecase

import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.MoodRepository
import com.example.focus_app.domain.model.FocusTask
import com.example.focus_app.domain.model.ReminderContext
import com.example.focus_app.domain.time.Clock
import com.example.focus_app.domain.time.SystemClock
import java.util.Locale
import javax.inject.Inject

class BuildReminderContextUseCase(
    private val sessions: AppSessionRepository,
    private val moods: MoodRepository,
    private val clock: Clock
) {
    @Inject
    constructor(sessions: AppSessionRepository, moods: MoodRepository) : this(sessions, moods, SystemClock)

    suspend operator fun invoke(
        task: FocusTask,
        app: AppInfo,
        settings: AppSettings
    ): ReminderContext {
        val now = clock.now()
        val startOfToday = now.toLocalDate().atStartOfDay(now.zone).toInstant().toEpochMilli()
        val windowStart = clock.nowMillis() - settings.reminderWindowMinutes * 60_000L
        return ReminderContext(
            taskTitle = task.title,
            timeBlock = task.timeBlockLabel(),
            latestMood = moods.getLatestMood()?.mood,
            appName = app.appName,
            openCountToday = sessions.countOpensSince(app.packageName, startOfToday),
            remindersInWindow = sessions.countShownRemindersSince(windowStart),
            activeExitsToday = sessions.countActiveExitsSince(startOfToday),
            tone = settings.toneKey,
            customToneInstruction = settings.customToneInstruction
        )
    }

    private fun FocusTask.timeBlockLabel(): String? {
        val start = scheduleStartMinute ?: return null
        val end = scheduleEndMinute ?: return null
        return "${formatMinute(start)}-${formatMinute(end)}"
    }

    private fun formatMinute(minute: Int): String = String.format(
        Locale.ROOT,
        "%02d:%02d",
        (minute / 60) % 24,
        minute % 60
    )
}
