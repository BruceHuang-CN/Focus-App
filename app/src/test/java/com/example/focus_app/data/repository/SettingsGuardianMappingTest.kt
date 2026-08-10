package com.example.focus_app.data.repository

import com.example.focus_app.data.local.entity.SettingsEntity
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsGuardianMappingTest {
    @Test
    fun disabled_guardian_survives_settings_entity_mapping() {
        val mapped = AppSettings(guardianEnabled = false)
            .toEntity(SettingsEntity(targetApps = "[]"))
            .toAppSettings()

        assertFalse(mapped.guardianEnabled)
    }
}
