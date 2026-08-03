package com.example.focus_app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focus_app.data.local.migration.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To2Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf()
    )

    @Test
    fun migrates_existing_settings_and_creates_v2_tables() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL(
                """INSERT INTO settings (
                    id,targetApps,remindDelayMinutes,maxRemindsPerHour,aiProvider,
                    apiEndpoint,apiKey,aiModel,aiPersonality,enableAccessibility,
                    enableBreathingPause
                ) VALUES (
                    1,'[]',2,3,'deepseek','https://api.deepseek.com','secret',
                    'deepseek-chat','gentle',0,1
                )""".trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate("migration-test", 2, true, MIGRATION_1_2).use { db ->
            db.query(
                "SELECT reminderDelaySeconds, reminderWindowMinutes, " +
                    "maxRemindersPerWindow FROM settings"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(120, it.getInt(0))
                assertEquals(60, it.getInt(1))
                assertEquals(3, it.getInt(2))
            }
        }
    }
}
