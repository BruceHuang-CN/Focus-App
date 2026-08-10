package com.example.focus_app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focus_app.data.local.migration.MIGRATION_2_3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration2To3Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf()
    )

    @Test
    fun existing_settings_enable_guardian_after_migration() {
        helper.createDatabase("migration-guardian-enabled", 2).apply {
            execSQL(
                """INSERT INTO settings (
                    id,targetApps,remindDelayMinutes,maxRemindsPerHour,aiProvider,
                    apiEndpoint,apiKey,aiModel,aiPersonality,enableAccessibility,
                    enableBreathingPause,reminderDelaySeconds,reminderWindowMinutes,
                    maxRemindersPerWindow,returnDestination,detectionMode,
                    dailyShortVideoLimitMinutes,toneKey,customToneInstruction
                ) VALUES (
                    1,'[]',2,3,'deepseek','https://api.deepseek.com','',
                    'deepseek-v4-flash','gentle',0,1,10,60,3,'focus','realtime',
                    30,'gentle',''
                )""".trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            "migration-guardian-enabled",
            3,
            true,
            MIGRATION_2_3
        ).use { database ->
            database.query("SELECT guardianEnabled FROM settings WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }
}
