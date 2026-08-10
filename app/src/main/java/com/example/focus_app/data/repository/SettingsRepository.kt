package com.example.focus_app.data.repository

import com.example.focus_app.data.local.dao.SettingsDao
import com.example.focus_app.data.local.entity.SettingsEntity
import com.example.focus_app.domain.model.AiProvider
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.domain.model.ReminderTone
import com.example.focus_app.domain.model.ReturnDestination
import com.example.focus_app.data.security.ApiKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class AppSettings(
    val targetApps: List<AppInfo> = emptyList(),
    val remindDelayMinutes: Int = 0,
    val maxRemindsPerHour: Int = 3,
    val aiProvider: AiProvider = AiProvider.DEEPSEEK,
    val apiEndpoint: String = "https://api.deepseek.com",
    val aiModel: String = "deepseek-v4-flash",
    val aiPersonality: String = "gentle",
    val enableAccessibility: Boolean = false,
    val guardianEnabled: Boolean = true,
    val enableBreathingPause: Boolean = true,
    val reminderDelaySeconds: Int = 10,
    val reminderWindowMinutes: Int = 60,
    val maxRemindersPerWindow: Int = 3,
    val returnDestination: ReturnDestination = ReturnDestination.FOCUS,
    val detectionMode: DetectionMode = DetectionMode.REALTIME,
    val toneKey: ReminderTone = ReminderTone.GENTLE,
    val customToneInstruction: String = "",
    val dailyShortVideoLimitMinutes: Int = 30
)

data class AppInfo(val packageName: String, val appName: String)

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao,
    private val apiKeyStore: ApiKeyStore
) {
    constructor(settingsDao: SettingsDao) : this(settingsDao, EmptyApiKeyStore)
    private val updateMutex = Mutex()
    private val mutableApiKeyRevision = MutableStateFlow(0L)
    val apiKeyRevision: StateFlow<Long> = mutableApiKeyRevision.asStateFlow()

    fun getSettingsFlow(): Flow<AppSettings> = settingsDao.getSettings().map {
        it?.toAppSettings() ?: AppSettings()
    }

    suspend fun getSettings(): AppSettings =
        settingsDao.getSettingsOnce()?.toAppSettings() ?: AppSettings()

    suspend fun updateSettings(settings: AppSettings) {
        update { settings }
    }

    suspend fun setGuardianEnabled(enabled: Boolean) {
        update { it.copy(guardianEnabled = enabled) }
    }

    suspend fun saveApiKey(value: String) {
        apiKeyStore.write(value.trim())
        mutableApiKeyRevision.update { it + 1 }
    }

    suspend fun clearApiKey() {
        apiKeyStore.clear()
        mutableApiKeyRevision.update { it + 1 }
    }

    suspend fun readApiKey(): String = apiKeyStore.read()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        updateMutex.withLock {
            val existing = settingsDao.getSettingsOnce() ?: SettingsEntity(
                targetApps = "[]",
                legacyApiKey = ""
            )
            val updated = transform(existing.toAppSettings())
            settingsDao.insertOrUpdate(updated.toEntity(existing))
        }
    }

    private object EmptyApiKeyStore : ApiKeyStore {
        override suspend fun read(): String = ""
        override suspend fun write(value: String) = Unit
        override suspend fun clear() = Unit
    }
}
