package com.example.focus_app.domain.task

import com.example.focus_app.domain.model.FocusTask
import java.time.ZonedDateTime

class ActiveTaskResolver {
    fun resolve(tasks: List<FocusTask>, now: ZonedDateTime): FocusTask? {
        val pending = tasks.filterNot { it.isCompleted }
        return pending.filter { TaskActivation.manualValid(it, now) }
            .maxByOrNull { it.manualStartedAt ?: 0 }
            ?: pending.filter { TaskActivation.window(it, now) != null }
                .minWithOrNull(compareBy<FocusTask> { it.sortOrder }.thenBy { it.id })
    }
}
