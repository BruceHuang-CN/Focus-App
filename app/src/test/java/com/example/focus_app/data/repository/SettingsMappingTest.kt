package com.example.focus_app.data.repository

import com.example.focus_app.data.local.entity.SettingsEntity
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.domain.model.ReminderTone
import com.example.focus_app.domain.model.ReturnDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsMappingTest {
    @Test
    fun retired_deepseek_model_is_upgraded_for_existing_installations() {
        val settings = SettingsEntity(
            targetApps = "[]",
            aiProvider = "deepseek",
            aiModel = "deepseek-chat"
        ).toAppSettings()

        assertEquals("deepseek-v4-flash", settings.aiModel)
    }

    @Test
    fun corrupt_persisted_v2_values_are_clamped_or_defaulted() {
        val settings = SettingsEntity(
            targetApps = "[]",
            legacyApiKey = "secret",
            reminderDelaySeconds = 999,
            reminderWindowMinutes = 1,
            maxRemindersPerWindow = 0,
            returnDestination = "unknown",
            detectionMode = "unknown",
            dailyShortVideoLimitMinutes = 2_000,
            toneKey = "unknown"
        ).toAppSettings()

        assertEquals(300, settings.reminderDelaySeconds)
        assertEquals(5, settings.reminderWindowMinutes)
        assertEquals(1, settings.maxRemindersPerWindow)
        assertEquals(1_440, settings.dailyShortVideoLimitMinutes)
        assertEquals(ReturnDestination.FOCUS, settings.returnDestination)
        assertEquals(DetectionMode.REALTIME, settings.detectionMode)
        assertEquals(ReminderTone.GENTLE, settings.toneKey)
    }

    @Test
    fun valid_v2_values_round_trip_without_overwriting_existing_legacy_settings() {
        val existing = SettingsEntity(
            id = 7,
            targetApps = "[{\"packageName\":\"video.app\",\"appName\":\"Video\"}]",
            remindDelayMinutes = 2,
            maxRemindsPerHour = 8,
            aiProvider = "custom",
            apiEndpoint = "https://example.test",
            legacyApiKey = "legacy-secret",
            aiModel = "custom-model",
            aiPersonality = "direct",
            enableAccessibility = true,
            enableBreathingPause = false
        )
        val settings = existing.toAppSettings().copy(
            reminderDelaySeconds = 45,
            reminderWindowMinutes = 120,
            maxRemindersPerWindow = 5,
            returnDestination = ReturnDestination.HOME,
            detectionMode = DetectionMode.COMPATIBILITY,
            toneKey = ReminderTone.SARCASTIC,
            customToneInstruction = "Call me captain",
            dailyShortVideoLimitMinutes = 90
        )

        val persisted = settings.toEntity(existing)

        assertEquals(45, persisted.reminderDelaySeconds)
        assertEquals(120, persisted.reminderWindowMinutes)
        assertEquals(5, persisted.maxRemindersPerWindow)
        assertEquals("home", persisted.returnDestination)
        assertEquals("compatibility", persisted.detectionMode)
        assertEquals("sarcastic", persisted.toneKey)
        assertEquals("Call me captain", persisted.customToneInstruction)
        assertEquals(90, persisted.dailyShortVideoLimitMinutes)
        assertEquals(7, persisted.id)
        assertEquals(existing.targetApps, persisted.targetApps)
        assertEquals(existing.remindDelayMinutes, persisted.remindDelayMinutes)
        assertEquals(existing.maxRemindsPerHour, persisted.maxRemindsPerHour)
        assertEquals(existing.aiProvider, persisted.aiProvider)
        assertEquals(existing.apiEndpoint, persisted.apiEndpoint)
        assertEquals("legacy-secret", persisted.legacyApiKey)
        assertEquals(existing.aiModel, persisted.aiModel)
        assertEquals(existing.aiPersonality, persisted.aiPersonality)
        assertEquals(existing.enableAccessibility, persisted.enableAccessibility)
        assertEquals(existing.enableBreathingPause, persisted.enableBreathingPause)
    }
}
