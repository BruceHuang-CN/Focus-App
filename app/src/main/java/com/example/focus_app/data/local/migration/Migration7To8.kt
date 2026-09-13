package com.example.focus_app.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS reminder_action_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, attemptId TEXT NOT NULL, sessionId INTEGER NOT NULL, occurredAt INTEGER NOT NULL, actionKey TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_reminder_action_events_attemptId ON reminder_action_events(attemptId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reminder_action_events_occurredAt ON reminder_action_events(occurredAt)")
        db.execSQL("CREATE TABLE IF NOT EXISTS reminder_history_state (id INTEGER NOT NULL PRIMARY KEY, quotaAfterId INTEGER NOT NULL, completeHistoryFrom INTEGER NOT NULL)")
        db.execSQL("INSERT OR IGNORE INTO reminder_history_state(id,quotaAfterId,completeHistoryFrom) VALUES(1,0,?)", arrayOf(System.currentTimeMillis()))
        // Legacy last actions lack action timestamps. Keep them in sessions; do not fabricate events.
    }
}
