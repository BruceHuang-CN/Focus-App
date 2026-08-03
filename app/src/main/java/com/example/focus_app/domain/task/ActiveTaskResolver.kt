package com.example.focus_app.domain.task

import com.example.focus_app.domain.model.FocusTask
import java.time.ZonedDateTime

class ActiveTaskResolver {
    fun resolve(tasks: List<FocusTask>, now: ZonedDateTime): FocusTask? {
        val minuteOfDay = now.hour * 60 + now.minute
        val dayMask = 1 shl (now.dayOfWeek.value - 1)
        val scheduled = tasks.filter { task ->
            !task.isCompleted &&
                task.scheduleStartMinute != null &&
                task.scheduleEndMinute != null &&
                (task.repeatDaysMask and dayMask) != 0 &&
                minuteOfDay >= task.scheduleStartMinute &&
                minuteOfDay < task.scheduleEndMinute
        }.sortedWith(compareBy<FocusTask> { it.id }.thenBy { it.createdAt }).firstOrNull()

        if (scheduled != null) return scheduled

        return tasks.filter { !it.isCompleted && it.isManualActive }
            .sortedWith(compareBy<FocusTask> { it.id }.thenBy { it.createdAt })
            .firstOrNull()
    }
}
