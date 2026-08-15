package com.example.focus_app.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `reminder_display_events` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`attemptId` TEXT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`displayedAt` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_reminder_display_events_attemptId` " +
                "ON `reminder_display_events` (`attemptId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reminder_display_events_displayedAt` " +
                "ON `reminder_display_events` (`displayedAt`)"
        )
        database.execSQL(
            "INSERT INTO `reminder_display_events` " +
                "(`attemptId`, `sessionId`, `displayedAt`, `kind`) " +
                "SELECT 'legacy-' || `id`, `id`, `remindedAt`, 'initial' " +
                "FROM `app_usage_sessions` WHERE `remindedAt` IS NOT NULL"
        )
    }
}
