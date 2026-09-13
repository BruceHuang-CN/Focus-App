package com.example.focus_app.data.repository
import com.example.focus_app.data.local.entity.SettingsEntity
import com.example.focus_app.domain.model.DetectionMode
import org.junit.Assert.*
import org.junit.Test
class RealtimeOnlyMappingTest {
 @Test fun legacyCompatibilityBecomesRealtimeWithoutLosingPreferences() {
   val old = SettingsEntity(targetApps="[]", legacyApiKey="", detectionMode="compatibility", enableAccessibility=false, guardianEnabled=false, forceReminder=true, reminderDelaySeconds=30)
   val current = old.toAppSettings()
   assertEquals(DetectionMode.REALTIME, current.detectionMode)
   assertTrue(current.enableAccessibility)
   assertFalse(current.guardianEnabled)
   assertTrue(current.forceReminder)
   assertEquals(30, current.reminderDelaySeconds)
 }
 @Test fun legacyModeCannotBeWrittenBack() {
   val old = SettingsEntity(targetApps="[]", legacyApiKey="")
   assertEquals(DetectionMode.REALTIME.key, AppSettings(detectionMode=DetectionMode.COMPATIBILITY).toEntity(old).detectionMode)
 }
}
