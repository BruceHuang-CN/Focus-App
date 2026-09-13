package com.example.focus_app.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_usage_sessions ADD COLUMN taskContextStartedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS task_groups (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, scheduleStartMinute INTEGER, scheduleEndMinute INTEGER, repeatDaysMask INTEGER NOT NULL, sortOrder INTEGER NOT NULL)")
        db.execSQL("ALTER TABLE focus_tasks ADD COLUMN groupId INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE focus_tasks ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE focus_tasks ADD COLUMN inheritsGroupSchedule INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE focus_tasks ADD COLUMN manualStartedAt INTEGER")
        db.execSQL("ALTER TABLE focus_tasks ADD COLUMN manualUntil INTEGER")
        db.execSQL("INSERT INTO task_groups(id,name,repeatDaysMask,sortOrder) VALUES(1,'未分组',0,0)")
        db.execSQL("""INSERT INTO task_groups(id,name,scheduleStartMinute,scheduleEndMinute,repeatDaysMask,sortOrder)
            SELECT MIN(id)+1, '原任务组 ' || (MIN(id)+1), scheduleStartMinute,scheduleEndMinute,repeatDaysMask,MIN(id)+1
            FROM focus_tasks WHERE scheduleStartMinute BETWEEN 0 AND 1439 AND scheduleEndMinute BETWEEN 1 AND 1440
            AND scheduleEndMinute > scheduleStartMinute AND repeatDaysMask > 0
            GROUP BY scheduleStartMinute,scheduleEndMinute,repeatDaysMask""")
        db.execSQL("""UPDATE focus_tasks SET groupId = COALESCE((SELECT id FROM task_groups g
            WHERE g.scheduleStartMinute = focus_tasks.scheduleStartMinute AND g.scheduleEndMinute = focus_tasks.scheduleEndMinute
            AND g.repeatDaysMask = focus_tasks.repeatDaysMask LIMIT 1),1), sortOrder = id,
            isManualActive = 0""")
        db.execSQL("UPDATE focus_tasks SET inheritsGroupSchedule = 1 WHERE groupId != 1")
        // Keep legacy times and task IDs intact. Old indefinite manual selections expire on upgrade.
    }
}
