package com.example.focus_app.service

import com.example.focus_app.data.repository.AppSessionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingFollowUpRestorer internal constructor(
    private val repository: AppSessionRepository,
    private val scheduler: FollowUpScheduler,
    private val nowMillis: () -> Long
) {
    @Inject
    constructor(
        repository: AppSessionRepository,
        scheduler: FollowUpScheduler
    ) : this(repository, scheduler, System::currentTimeMillis)

    suspend fun restore() {
        val now = nowMillis()
        repository.pendingSnoozes().forEach { session ->
            val dueAt = session.snoozeUntil ?: return@forEach
            scheduler.schedule(session.id, (dueAt - now).coerceAtLeast(0L))
        }
    }
}
