package com.example.focus_app.data.repository

import com.example.focus_app.data.local.entity.SettingsEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class ForceReminderSettingsMappingTest {
    @Test
    fun force_reminder_round_trips_through_settings_mapping() {
        val existing = SettingsEntity(targetApps = "[]")

        val persisted = existing.toAppSettings()
            .copy(forceReminder = true)
            .toEntity(existing)

        assertTrue(persisted.forceReminder)
        assertTrue(persisted.toAppSettings().forceReminder)
    }
}
