package com.example.focus_app.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsEntityCompatibilityTest {
    @Test
    fun legacy_constructor_maps_existing_settings_to_v2_fields() {
        val settings = SettingsEntity(
            targetApps = "[]",
            remindDelayMinutes = 2,
            maxRemindsPerHour = 7,
            aiProvider = "deepseek",
            apiEndpoint = "https://api.deepseek.com",
            apiKey = "secret",
            aiModel = "deepseek-chat",
            aiPersonality = "sarcastic",
            enableAccessibility = false,
            enableBreathingPause = true
        )

        assertEquals(120, settings.reminderDelaySeconds)
        assertEquals(7, settings.maxRemindersPerWindow)
        assertEquals("sarcastic", settings.toneKey)
        assertEquals("secret", settings.legacyApiKey)
    }
}
