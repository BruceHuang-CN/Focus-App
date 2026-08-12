package com.example.focus_app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focus_app.data.local.migration.MIGRATION_3_4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration3To4Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf()
    )

    @Test
    fun existing_settings_default_force_reminder_to_off_after_migration() {
        helper.createDatabase("migration-force-reminder", 3).apply {
            execSQL(
                """INSERT INTO settings (
                    id,targetApps,remindDelayMinutes,maxRemindsPerHour,aiProvider,
                    apiEndpoint,apiKey,aiModel,aiPersonality,enableAccessibility,
                    enableBreathingPause,reminderDelaySeconds,reminderWindowMinutes,
                    maxRemindersPerWindow,returnDestination,detectionMode,
                    dailyShortVideoLimitMinutes,toneKey,customToneInstruction,guardianEnabled
                ) VALUES (
                    1,'[]',2,3,'deepseek','https://api.deepseek.com','',
                    'deepseek-v4-flash','gentle',0,1,10,60,3,'focus','realtime',
                    30,'gentle','',1
                )""".trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            "migration-force-reminder",
            4,
            true,
            MIGRATION_3_4
        ).use { database ->
            database.query("SELECT forceReminder FROM settings WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }
}
