package com.example.focus_app.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE `settings` ADD COLUMN `reminderDelaySeconds` " +
                "INTEGER NOT NULL DEFAULT 10"
        )
        database.execSQL(
            "ALTER TABLE `settings` ADD COLUMN `reminderWindowMinutes` " +
                "INTEGER NOT NULL DEFAULT 60"
        )
        database.execSQL(
            "ALTER TABLE `settings` ADD COLUMN `maxRemindersPerWindow` " +
                "INTEGER NOT NULL DEFAULT 3"
        )
        database.execSQL(
            "ALTER TABLE `settings` ADD COLUMN `returnDestination` " +
                "TEXT NOT NULL DEFAULT 'focus'"
        )
        database.execSQL(
            "ALTER TABLE `settings` ADD COLUMN `detectionMode` " +
                "TEXT NOT NULL DEFAULT 'realtime'"
        )
        database.execSQL(
            "ALTER TABLE `settings` ADD COLUMN `dailyShortVideoLimitMinutes` " +
                "INTEGER NOT NULL DEFAULT 30"
        )
        database.execSQL(
            "ALTER TABLE `settings` ADD COLUMN `toneKey` " +
                "TEXT NOT NULL DEFAULT 'gentle'"
        )
        database.execSQL(
            "ALTER TABLE `settings` ADD COLUMN `customToneInstruction` " +
                "TEXT NOT NULL DEFAULT ''"
        )
        database.execSQL(
            """UPDATE `settings`
               SET `reminderDelaySeconds` = CASE `remindDelayMinutes`
                   WHEN 0 THEN 3
                   ELSE `remindDelayMinutes` * 60
               END,
                   `maxRemindersPerWindow` = `maxRemindsPerHour`,
                   `toneKey` = `aiPersonality`""".trimIndent()
        )

        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `focus_tasks` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `isCompleted` INTEGER NOT NULL,
                `isManualActive` INTEGER NOT NULL,
                `scheduleStartMinute` INTEGER,
                `scheduleEndMinute` INTEGER,
                `repeatDaysMask` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )""".trimIndent()
        )
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `app_usage_sessions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `packageName` TEXT NOT NULL,
                `appName` TEXT NOT NULL,
                `startedAt` INTEGER NOT NULL,
                `endedAt` INTEGER,
                `taskId` INTEGER,
                `remindedAt` INTEGER,
                `userAction` TEXT,
                `toneKey` TEXT NOT NULL
            )""".trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_app_usage_sessions_packageName` " +
                "ON `app_usage_sessions` (`packageName`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_app_usage_sessions_startedAt` " +
                "ON `app_usage_sessions` (`startedAt`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_app_usage_sessions_remindedAt` " +
                "ON `app_usage_sessions` (`remindedAt`)"
        )

        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `ai_reminder_cache` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `taskId` INTEGER NOT NULL,
                `appPackageName` TEXT NOT NULL,
                `toneKey` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `lastUsedAt` INTEGER
            )""".trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_ai_reminder_cache_taskId_appPackageName_toneKey` " +
                "ON `ai_reminder_cache` (`taskId`, `appPackageName`, `toneKey`)"
        )
    }
}
