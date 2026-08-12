package com.example.focus_app.data.repository

import com.example.focus_app.data.local.entity.SettingsEntity
import com.example.focus_app.domain.model.AiProvider
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.domain.model.ReminderTone
import com.example.focus_app.domain.model.ReturnDestination
import com.google.gson.Gson

private val gson = Gson()

internal fun SettingsEntity.toAppSettings(): AppSettings {
    val apps = try {
        gson.fromJson(targetApps, Array<AppInfo>::class.java).toList()
    } catch (_: Exception) {
        emptyList()
    }
    val provider = AiProvider.fromKey(aiProvider)
    val model = if (provider == AiProvider.DEEPSEEK && aiModel == "deepseek-chat") {
        AiProvider.DEEPSEEK.defaultModel
    } else {
        aiModel
    }

    return AppSettings(
        targetApps = apps,
        remindDelayMinutes = remindDelayMinutes,
        maxRemindsPerHour = maxRemindsPerHour,
        aiProvider = provider,
        apiEndpoint = apiEndpoint,
        aiModel = model,
        aiPersonality = aiPersonality,
        enableAccessibility = enableAccessibility,
        guardianEnabled = guardianEnabled,
        forceReminder = forceReminder,
        enableBreathingPause = enableBreathingPause,
        reminderDelaySeconds = reminderDelaySeconds.coerceIn(1, 300),
        reminderWindowMinutes = reminderWindowMinutes.coerceIn(5, 1_440),
        maxRemindersPerWindow = maxRemindersPerWindow.coerceIn(1, 20),
        returnDestination = ReturnDestination.fromKey(returnDestination),
        detectionMode = DetectionMode.fromKey(detectionMode),
        toneKey = ReminderTone.fromKey(toneKey),
        customToneInstruction = customToneInstruction,
        dailyShortVideoLimitMinutes = dailyShortVideoLimitMinutes.coerceIn(1, 1_440)
    )
}

internal fun AppSettings.toEntity(existing: SettingsEntity): SettingsEntity = existing.copy(
    targetApps = gson.toJson(targetApps),
    remindDelayMinutes = remindDelayMinutes,
    maxRemindsPerHour = maxRemindsPerHour,
    aiProvider = aiProvider.name.lowercase(),
    apiEndpoint = apiEndpoint,
    aiModel = aiModel,
    aiPersonality = aiPersonality,
    enableAccessibility = enableAccessibility,
    guardianEnabled = guardianEnabled,
    forceReminder = forceReminder,
    enableBreathingPause = enableBreathingPause,
    reminderDelaySeconds = reminderDelaySeconds.coerceIn(1, 300),
    reminderWindowMinutes = reminderWindowMinutes.coerceIn(5, 1_440),
    maxRemindersPerWindow = maxRemindersPerWindow.coerceIn(1, 20),
    returnDestination = returnDestination.key,
    detectionMode = detectionMode.key,
    toneKey = toneKey.key,
    customToneInstruction = customToneInstruction,
    dailyShortVideoLimitMinutes = dailyShortVideoLimitMinutes.coerceIn(1, 1_440)
)
