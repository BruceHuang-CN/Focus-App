package com.example.focus_app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focus_app.data.local.migration.MIGRATION_4_5
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration4To5Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf()
    )

    @Test
    fun migration_preserves_existing_session_and_adds_empty_snooze_time() {
        helper.createDatabase("migration-snooze-until", 4).apply {
            execSQL(
                """INSERT INTO app_usage_sessions (
                    id,packageName,appName,startedAt,endedAt,taskId,remindedAt,userAction,toneKey
                ) VALUES (
                    1,'com.xingin.xhs','小红书',1000,NULL,7,2000,'snoozed_1m','direct'
                )""".trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            "migration-snooze-until",
            5,
            true,
            MIGRATION_4_5
        ).use { database ->
            database.query(
                "SELECT packageName, userAction, snoozeUntil FROM app_usage_sessions WHERE id = 1"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("com.xingin.xhs", cursor.getString(0))
                assertEquals("snoozed_1m", cursor.getString(1))
                assertTrue(cursor.isNull(2))
            }
        }
    }
}
