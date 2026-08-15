package com.example.focus_app.domain.usecase

import com.example.focus_app.data.repository.ReminderDisplayRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.domain.time.Clock
import com.example.focus_app.domain.time.SystemClock
import com.example.focus_app.service.AppSessionCoordinator
import javax.inject.Inject

class ResetReminderQuotaUseCase private constructor(
    private val settings: SettingsRepository,
    private val displays: ReminderDisplayRepository,
    private val coordinator: AppSessionCoordinator,
    private val clock: () -> Long
) {
    @Inject
    constructor(
        settings: SettingsRepository,
        displays: ReminderDisplayRepository,
        coordinator: AppSessionCoordinator
    ) : this(settings, displays, coordinator, SystemClock::nowMillis)

    constructor(
        settings: SettingsRepository,
        displays: ReminderDisplayRepository,
        coordinator: AppSessionCoordinator,
        clock: Clock
    ) : this(settings, displays, coordinator, clock::nowMillis)

    suspend operator fun invoke() {
        coordinator.stopCurrentSession()
        val current = settings.getSettings()
        displays.resetSince(clock() - current.reminderWindowMinutes * 60_000L)
    }
}
