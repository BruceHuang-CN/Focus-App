package com.example.focus_app.data.appgroup

import android.content.SharedPreferences
import com.example.focus_app.data.local.dao.SettingsDao
import com.example.focus_app.data.local.entity.SettingsEntity
import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.SettingsRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppGroupRepositoryTest {
    @Test
    fun first_read_migrates_current_target_apps_into_one_active_group() = runTest {
        val repository = repositoryWith(
            targetApps = listOf(AppInfo("video.app", "Video"))
        )

        assertEquals("当前应用组", repository.groups.value.single().name)
        assertEquals(listOf(AppInfo("video.app", "Video")), repository.groups.value.single().apps)
        assertEquals(repository.groups.value.single().id, repository.activeGroupId.value)
    }

    @Test
    fun create_rejects_an_empty_app_list() = runTest {
        val repository = repositoryWith(targetApps = listOf(AppInfo("video.app", "Video")))

        assertThrows(IllegalArgumentException::class.java) {
            repository.create("视频", emptyList())
        }
    }

    @Test
    fun delete_rejects_the_last_group() = runTest {
        val repository = repositoryWith(targetApps = listOf(AppInfo("video.app", "Video")))

        assertThrows(IllegalStateException::class.java) {
            repository.delete(repository.activeGroupId.value)
        }
    }

    @Test
    fun create_keeps_the_existing_active_group_selected() = runTest {
        val repository = repositoryWith(targetApps = listOf(AppInfo("video.app", "Video")))
        val activeId = repository.activeGroupId.value

        repository.create("Social", listOf(AppInfo("social.app", "Social")))

        assertEquals(activeId, repository.activeGroupId.value)
    }

    @Test
    fun delete_rejects_the_active_group_before_selecting_a_replacement() = runTest {
        val repository = repositoryWith(targetApps = listOf(AppInfo("video.app", "Video")))
        repository.create("Social", listOf(AppInfo("social.app", "Social")))
        val activeId = repository.activeGroupId.value

        assertThrows(IllegalStateException::class.java) {
            repository.delete(activeId)
        }
        assertEquals(activeId, repository.activeGroupId.value)
    }

    @Test
    fun update_removes_duplicate_packages_and_preserves_the_first_app_name() = runTest {
        val repository = repositoryWith(targetApps = listOf(AppInfo("video.app", "Video")))
        val id = repository.activeGroupId.value

        repository.update(
            id,
            "视频",
            listOf(AppInfo("video.app", "首个名称"), AppInfo("video.app", "重复名称"))
        )

        assertEquals(listOf(AppInfo("video.app", "首个名称")), repository.groups.value.single().apps)
    }

    @Test
    fun empty_legacy_target_apps_do_not_mark_migration_complete() = runTest {
        val store = AppGroupStore(InMemorySharedPreferences())
        val dao = FakeSettingsDao(emptyList())

        AppGroupRepository(store, SettingsRepository(dao))

        assertEquals(null, store.read())
        dao.setTargetApps(listOf(AppInfo("video.app", "Video")))
        val migrated = AppGroupRepository(store, SettingsRepository(dao))

        assertEquals(listOf(AppInfo("video.app", "Video")), migrated.groups.value.single().apps)
        assertEquals(migrated.groups.value.single().id, migrated.activeGroupId.value)
    }

    @Test
    fun activate_normalizes_apps_in_every_persisted_group() = runTest {
        val store = AppGroupStore(InMemorySharedPreferences())
        store.write(groupsWithDuplicateApps())
        val repository = AppGroupRepository(store, SettingsRepository(FakeSettingsDao(emptyList())))

        repository.activate("second")

        assertEquals(listOf(AppInfo("video.app", "First")), repository.groups.value.first().apps)
        assertEquals(listOf(AppInfo("social.app", "Social")), repository.groups.value.last().apps)
    }

    @Test
    fun delete_normalizes_apps_in_remaining_groups() = runTest {
        val store = AppGroupStore(InMemorySharedPreferences())
        store.write(groupsWithDuplicateApps())
        val repository = AppGroupRepository(store, SettingsRepository(FakeSettingsDao(emptyList())))

        repository.activate("second")
        repository.delete("first")

        assertEquals(listOf(AppInfo("social.app", "Social")), repository.groups.value.single().apps)
    }

    private fun repositoryWith(targetApps: List<AppInfo>): AppGroupRepository = AppGroupRepository(
        AppGroupStore(InMemorySharedPreferences()),
        SettingsRepository(FakeSettingsDao(targetApps))
    )

    private fun groupsWithDuplicateApps() = StoredAppGroups(
        groups = listOf(
            AppGroup("first", "First", listOf(AppInfo("video.app", "First"), AppInfo("video.app", "Duplicate"))),
            AppGroup("second", "Second", listOf(AppInfo("social.app", "Social"), AppInfo("social.app", "Duplicate")))
        ),
        activeGroupId = "first"
    )
}

private class FakeSettingsDao(targetApps: List<AppInfo>) : SettingsDao {
    private val settings = MutableStateFlow<SettingsEntity?>(SettingsEntity(targetApps = Gson().toJson(targetApps)))

    fun setTargetApps(targetApps: List<AppInfo>) {
        settings.value = SettingsEntity(targetApps = Gson().toJson(targetApps))
    }

    override suspend fun insertOrUpdate(settings: SettingsEntity) { this.settings.value = settings }
    override fun getSettings(): Flow<SettingsEntity?> = settings
    override suspend fun getSettingsOnce(): SettingsEntity? = settings.value
    override suspend fun clearLegacyApiKey() = Unit
}

private class InMemorySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clear = false
        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply { pending[key] = values }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { pending[key] = value }
        override fun remove(key: String): SharedPreferences.Editor = apply { pending[key] = null }
        override fun clear(): SharedPreferences.Editor = apply { clear = true }
        override fun commit(): Boolean = applyChanges()
        override fun apply() { applyChanges() }
        private fun applyChanges(): Boolean {
            if (clear) values.clear()
            pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            return true
        }
    }
}
