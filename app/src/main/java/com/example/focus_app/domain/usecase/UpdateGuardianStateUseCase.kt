package com.example.focus_app.domain.usecase

import com.example.focus_app.data.appgroup.AppGroupRepository
import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.ReminderCacheRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.domain.time.Clock
import com.example.focus_app.domain.time.SystemClock
import com.example.focus_app.service.AppSessionCoordinator
import javax.inject.Inject

class UpdateGuardianStateUseCase private constructor(
    private val groups: AppGroupRepository,
    private val settings: SettingsRepository,
    private val sessions: AppSessionRepository,
    private val reminderCache: ReminderCacheRepository,
    private val coordinator: AppSessionCoordinator,
    private val quotaClock: () -> Long
) {
    @Inject
    constructor(
        groups: AppGroupRepository,
        settings: SettingsRepository,
        sessions: AppSessionRepository,
        reminderCache: ReminderCacheRepository,
        coordinator: AppSessionCoordinator
    ) : this(groups, settings, sessions, reminderCache, coordinator, SystemClock::nowMillis)

    constructor(
        groups: AppGroupRepository,
        settings: SettingsRepository,
        sessions: AppSessionRepository,
        reminderCache: ReminderCacheRepository,
        coordinator: AppSessionCoordinator,
        clock: Clock
    ) : this(groups, settings, sessions, reminderCache, coordinator, clock::nowMillis)

    suspend fun activateGroup(groupId: String): Result<Unit> = runCatching {
        val group = groups.groups.value.firstOrNull { it.id == groupId }
            ?: error("App group does not exist.")
        require(group.apps.isNotEmpty()) { "An app group must contain at least one app." }

        coordinator.stopCurrentSession()
        settings.update { it.copy(targetApps = group.apps) }
        val current = settings.getSettings()
        sessions.resetReminderQuota(quotaClock() - current.reminderWindowMinutes * 60_000L)
        reminderCache.requestRegeneration()
        groups.activate(groupId)
    }

    suspend fun updateActiveGroup(
        groupId: String,
        name: String,
        apps: List<AppInfo>
    ): Result<Unit> = runCatching {
        require(groups.activeGroupId.value == groupId) { "Only the active app group can be updated here." }
        groups.update(groupId, name, apps)
        activateGroup(groupId).getOrThrow()
    }

    suspend fun setGuardianEnabled(enabled: Boolean) {
        if (!enabled) coordinator.stopCurrentSession()
        settings.setGuardianEnabled(enabled)
    }
}
