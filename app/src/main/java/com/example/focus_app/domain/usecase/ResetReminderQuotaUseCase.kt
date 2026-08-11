package com.example.focus_app.domain.usecase

import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.domain.time.Clock
import com.example.focus_app.domain.time.SystemClock
import com.example.focus_app.service.AppSessionCoordinator
import javax.inject.Inject

class ResetReminderQuotaUseCase private constructor(
    private val settings: SettingsRepository,
    private val sessions: AppSessionRepository,
    private val coordinator: AppSessionCoordinator,
    private val clock: () -> Long
) {
    @Inject
    constructor(
        settings: SettingsRepository,
        sessions: AppSessionRepository,
        coordinator: AppSessionCoordinator
    ) : this(settings, sessions, coordinator, SystemClock::nowMillis)

    constructor(
        settings: SettingsRepository,
        sessions: AppSessionRepository,
        coordinator: AppSessionCoordinator,
        clock: Clock
    ) : this(settings, sessions, coordinator, clock::nowMillis)

    suspend operator fun invoke() {
        coordinator.stopCurrentSession()
        val current = settings.getSettings()
        sessions.resetReminderQuota(clock() - current.reminderWindowMinutes * 60_000L)
    }
}
