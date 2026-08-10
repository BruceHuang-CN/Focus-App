package com.example.focus_app.data.appgroup

import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.SettingsRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppGroupRepository @Inject constructor(
    private val store: AppGroupStore,
    settingsRepository: SettingsRepository
) {
    private val initialState = store.read() ?: runBlocking {
        val apps = normalizeApps(settingsRepository.getSettings().targetApps)
        (apps.takeIf { it.isNotEmpty() }
            ?.let { AppGroup(UUID.randomUUID().toString(), CURRENT_GROUP_NAME, it) }
            ?.let { StoredAppGroups(listOf(it), it.id) }
            ?: StoredAppGroups(emptyList(), "")
        ).also(store::write)
    }
    private val mutableGroups = MutableStateFlow(initialState.groups)
    private val mutableActiveGroupId = MutableStateFlow(initialState.activeGroupId)

    val groups: StateFlow<List<AppGroup>> = mutableGroups.asStateFlow()
    val activeGroupId: StateFlow<String> = mutableActiveGroupId.asStateFlow()

    fun create(name: String, apps: List<AppInfo>) {
        val group = AppGroup(UUID.randomUUID().toString(), normalizeName(name), normalizeApps(apps))
        require(group.apps.isNotEmpty()) { "An app group must contain at least one app." }
        persist(mutableGroups.value + group, group.id)
    }

    fun update(id: String, name: String, apps: List<AppInfo>) {
        val normalizedApps = normalizeApps(apps)
        require(normalizedApps.isNotEmpty()) { "An app group must contain at least one app." }
        val existing = mutableGroups.value
        require(existing.any { it.id == id }) { "App group does not exist." }
        persist(existing.map { group ->
            if (group.id == id) group.copy(name = normalizeName(name), apps = normalizedApps) else group
        }, mutableActiveGroupId.value)
    }

    fun delete(id: String) {
        val existing = mutableGroups.value
        require(existing.any { it.id == id }) { "App group does not exist." }
        check(existing.size > 1) { "The final app group cannot be deleted." }
        val remaining = existing.filterNot { it.id == id }
        val activeId = if (mutableActiveGroupId.value == id) remaining.first().id else mutableActiveGroupId.value
        persist(remaining, activeId)
    }

    fun activate(id: String) {
        require(mutableGroups.value.any { it.id == id }) { "App group does not exist." }
        persist(mutableGroups.value, id)
    }

    private fun persist(groups: List<AppGroup>, activeGroupId: String) {
        store.write(StoredAppGroups(groups, activeGroupId))
        mutableGroups.value = groups
        mutableActiveGroupId.value = activeGroupId
    }

    private fun normalizeName(name: String): String = name.trim().take(MAX_NAME_LENGTH).also {
        require(it.isNotBlank()) { "An app group name is required." }
    }

    private fun normalizeApps(apps: List<AppInfo>): List<AppInfo> = apps.distinctBy { it.packageName }

    private companion object {
        const val MAX_NAME_LENGTH = 24
        const val CURRENT_GROUP_NAME = "当前应用组"
    }
}
