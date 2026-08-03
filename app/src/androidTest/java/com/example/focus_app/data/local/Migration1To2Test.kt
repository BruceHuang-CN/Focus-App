package com.example.focus_app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focus_app.data.local.migration.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        helper.createDatabase("migration-existing-settings", 1).apply {
            insertLegacySettings(
                id = 1,
                delayMinutes = 2,
                maxReminders = 7,
                personality = "sarcastic",
                apiKey = "secret"
            )
            close()
        }

        helper.runMigrationsAndValidate(
            "migration-existing-settings",
            2,
            true,
            MIGRATION_1_2
        ).use { db ->
            db.query(
                "SELECT reminderDelaySeconds, reminderWindowMinutes, " +
                    "maxRemindersPerWindow, toneKey, apiKey FROM settings WHERE id = 1"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(120, it.getInt(0))
                assertEquals(60, it.getInt(1))
                assertEquals(7, it.getInt(2))
                assertEquals("sarcastic", it.getString(3))
                assertEquals("secret", it.getString(4))
            }
        }
    }

    @Test
    fun maps_all_legacy_delay_options_to_seconds() {
        helper.createDatabase("migration-delay-options", 1).apply {
            insertLegacySettings(id = 1, delayMinutes = 0)
            insertLegacySettings(id = 2, delayMinutes = 1)
            insertLegacySettings(id = 3, delayMinutes = 2)
            insertLegacySettings(id = 4, delayMinutes = 3)
            close()
        }

        helper.runMigrationsAndValidate(
            "migration-delay-options",
            2,
            true,
            MIGRATION_1_2
        ).use { db ->
            db.query(
                "SELECT id, reminderDelaySeconds FROM settings ORDER BY id"
            ).use { cursor ->
                listOf(3, 60, 120, 180).forEachIndexed { index, expectedSeconds ->
                    assertTrue(cursor.moveToNext())
                    assertEquals(index + 1, cursor.getInt(0))
                    assertEquals(expectedSeconds, cursor.getInt(1))
                }
                assertFalse(cursor.moveToNext())
            }
        }
    }

    private fun SupportSQLiteDatabase.insertLegacySettings(
        id: Int,
        delayMinutes: Int,
        maxReminders: Int = 3,
        personality: String = "gentle",
        apiKey: String = ""
    ) {
        execSQL(
            """INSERT INTO settings (
                id,targetApps,remindDelayMinutes,maxRemindsPerHour,aiProvider,
                apiEndpoint,apiKey,aiModel,aiPersonality,enableAccessibility,
                enableBreathingPause
            ) VALUES (
                ?, '[]', ?, ?, 'deepseek', 'https://api.deepseek.com', ?,
                'deepseek-chat', ?, 0, 1
            )""".trimIndent(),
            arrayOf(id, delayMinutes, maxReminders, apiKey, personality)
        )
    }
}
