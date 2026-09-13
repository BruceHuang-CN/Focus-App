package com.example.focus_app.domain.task

import com.example.focus_app.domain.model.FocusTask
import java.time.ZonedDateTime

object TaskActivation {
    /** Half-open local calendar window, including 24:00 and DST transitions. */
    fun window(task: FocusTask, now: ZonedDateTime): Pair<Long, Long>? {
        val start = task.scheduleStartMinute ?: return null
        val end = task.scheduleEndMinute ?: return null
        if (start !in 0..1439 || end !in 1..1440 || end <= start ||
            (task.repeatDaysMask and (1 shl (now.dayOfWeek.value - 1))) == 0) return null
        val date = now.toLocalDate()
        val from = date.atStartOfDay().plusMinutes(start.toLong()).atZone(now.zone).toInstant().toEpochMilli()
        val until = date.atStartOfDay().plusMinutes(end.toLong()).atZone(now.zone).toInstant().toEpochMilli()
        return (from to until).takeIf { now.toInstant().toEpochMilli() in from until until }
    }
    fun manualValid(task: FocusTask, now: ZonedDateTime): Boolean = task.isManualActive &&
        task.manualStartedAt != null && task.manualUntil != null &&
        now.toInstant().toEpochMilli() in task.manualStartedAt until task.manualUntil
    fun accepts(task: FocusTask, contextStartedAt: Long, now: ZonedDateTime): Boolean {
        if (task.isCompleted) return false
        val period = if (manualValid(task, now)) task.manualStartedAt!! to task.manualUntil!! else window(task, now)
        return period != null && contextStartedAt in period.first until period.second
    }
}
