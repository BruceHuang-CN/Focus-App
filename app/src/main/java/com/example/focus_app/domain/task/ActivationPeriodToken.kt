package com.example.focus_app.domain.task

import com.example.focus_app.domain.model.FocusTask
import java.time.ZonedDateTime

internal const val MANUAL_ACTIVATION_PERIOD_TOKEN = Long.MIN_VALUE

internal fun activationPeriodToken(task: FocusTask, now: ZonedDateTime): Long {
    val start = task.scheduleStartMinute ?: return MANUAL_ACTIVATION_PERIOD_TOKEN
    val end = task.scheduleEndMinute ?: return MANUAL_ACTIVATION_PERIOD_TOKEN
    if (start !in 0 until MINUTES_PER_DAY || end !in 1..MINUTES_PER_DAY || end <= start) {
        return MANUAL_ACTIVATION_PERIOD_TOKEN
    }

    val dayMask = 1 shl (now.dayOfWeek.value - 1)
    val minuteOfDay = now.hour * 60 + now.minute
    if ((task.repeatDaysMask and dayMask) == 0 || minuteOfDay !in start until end) {
        return MANUAL_ACTIVATION_PERIOD_TOKEN
    }

    return now.toLocalDate().toEpochDay() * MINUTES_PER_DAY + start
}

private const val MINUTES_PER_DAY = 1_440
