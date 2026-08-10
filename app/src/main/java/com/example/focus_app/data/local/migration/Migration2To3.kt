package com.example.focus_app.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE `settings` ADD COLUMN `guardianEnabled` INTEGER NOT NULL DEFAULT 1"
        )
    }
}
