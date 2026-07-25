package com.example.focus_app.data.repository

import com.example.focus_app.data.local.dao.SettingsDao
import com.example.focus_app.data.local.entity.SettingsEntity
import com.example.focus_app.domain.model.AiProvider
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AppSettings(
    val targetApps: List<AppInfo> = emptyList(),
    val remindDelayMinutes: Int = 0,
    val maxRemindsPerHour: Int = 3,
    val aiProvider: AiProvider = AiProvider.DEEPSEEK,
    val apiEndpoint: String = "https://api.deepseek.com",
    val apiKey: String = "",
    val aiModel: String = "deepseek-chat",
    val aiPersonality: String = "gentle",
    val enableAccessibility: Boolean = false,
    val enableBreathingPause: Boolean = true
) {
    val isAiConfigured: Boolean get() = apiKey.isNotBlank()
}

data class AppInfo(val packageName: String, val appName: String)

@Singleton
class SettingsRepository @Inject constructor(private val settingsDao: SettingsDao) {
    private val gson = Gson()

    fun getSettingsFlow(): Flow<AppSettings> = settingsDao.getSettings().map { it?.toDomain() ?: AppSettings() }
    suspend fun getSettings(): AppSettings = settingsDao.getSettingsOnce()?.toDomain() ?: AppSettings()
    suspend fun updateSettings(settings: AppSettings) { settingsDao.insertOrUpdate(settings.toEntity()) }

    private fun SettingsEntity.toDomain(): AppSettings {
        val apps: List<AppInfo> = try { gson.fromJson(targetApps, Array<AppInfo>::class.java).toList() } catch (_: Exception) { emptyList() }
        return AppSettings(apps, remindDelayMinutes, maxRemindsPerHour, AiProvider.fromKey(aiProvider), apiEndpoint, apiKey, aiModel, aiPersonality, enableAccessibility, enableBreathingPause)
    }

    private fun AppSettings.toEntity() = SettingsEntity(
        targetApps = gson.toJson(targetApps), remindDelayMinutes = remindDelayMinutes, maxRemindsPerHour = maxRemindsPerHour,
        aiProvider = aiProvider.name.lowercase(), apiEndpoint = apiEndpoint, apiKey = apiKey, aiModel = aiModel,
        aiPersonality = aiPersonality, enableAccessibility = enableAccessibility, enableBreathingPause = enableBreathingPause
    )
}
