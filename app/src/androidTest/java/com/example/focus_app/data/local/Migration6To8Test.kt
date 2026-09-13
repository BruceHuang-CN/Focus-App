package com.example.focus_app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focus_app.data.local.migration.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Migration6To8Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java, listOf())
    @Test fun upgrades_real_v6_schema_without_rebuilding_or_losing_tasks_and_history() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-groups-" + UUID.randomUUID()
        helper.createDatabase(name, 6).apply {
            execSQL("INSERT INTO focus_tasks(id,title,isCompleted,isManualActive,scheduleStartMinute,scheduleEndMinute,repeatDaysMask,createdAt,updatedAt) VALUES(41,'原任务',1,0,540,600,31,123,456)")
            execSQL("INSERT INTO focus_tasks(id,title,isCompleted,isManualActive,repeatDaysMask,createdAt,updatedAt) VALUES(42,'无时段任务',0,1,0,789,790)")
            execSQL("INSERT INTO reminder_display_events(id,attemptId,sessionId,displayedAt,kind) VALUES(9,'old',8,2000,'initial')")
            close()
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(MIGRATION_6_7, MIGRATION_7_8).build()
        try {
            // Opening through current generated Room code validates the actual version 8 schema.
            val tasks = db.focusTaskDao().observeAll().first()
            assertEquals(2, tasks.size)
            val old = tasks.first { it.id == 41L }
            assertTrue(old.isCompleted); assertEquals(123L, old.createdAt); assertEquals(456L, old.updatedAt)
            assertEquals(540, old.scheduleStartMinute); assertTrue(old.inheritsGroupSchedule)
            assertEquals(1L, tasks.first { it.id == 42L }.groupId)
            assertFalse(tasks.first { it.id == 42L }.isManualActive)
            assertEquals(1, db.reminderAnalyticsDao().displays(0, Long.MAX_VALUE).first().size)
            assertTrue(db.reminderAnalyticsDao().actions(0, Long.MAX_VALUE).first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
