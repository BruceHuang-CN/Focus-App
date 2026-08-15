package com.example.focus_app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focus_app.data.local.migration.MIGRATION_5_6
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration5To6Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf()
    )

    @Test
    fun migration_converts_each_legacy_reminded_session_once() {
        helper.createDatabase("migration-reminder-display", 5).apply {
            execSQL(
                "INSERT INTO app_usage_sessions " +
                    "(id,packageName,appName,startedAt,endedAt,taskId,remindedAt,userAction,toneKey,snoozeUntil) " +
                    "VALUES (1,'target','Target',1000,NULL,NULL,2000,NULL,'gentle',NULL)"
            )
            execSQL(
                "INSERT INTO app_usage_sessions " +
                    "(id,packageName,appName,startedAt,endedAt,taskId,remindedAt,userAction,toneKey,snoozeUntil) " +
                    "VALUES (2,'other','Other',1000,NULL,NULL,NULL,NULL,'gentle',NULL)"
            )
            close()
        }

        helper.runMigrationsAndValidate(
            "migration-reminder-display",
            6,
            true,
            MIGRATION_5_6
        ).use { database ->
            database.query(
                "SELECT attemptId,sessionId,displayedAt,kind FROM reminder_display_events"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("legacy-1", cursor.getString(0))
                assertEquals(1L, cursor.getLong(1))
                assertEquals(2_000L, cursor.getLong(2))
                assertEquals("initial", cursor.getString(3))
                assertFalse(cursor.moveToNext())
            }
        }
    }
}
