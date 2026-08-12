package com.example.focus_app.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE `settings` ADD COLUMN `forceReminder` INTEGER NOT NULL DEFAULT 0"
        )
    }
}
