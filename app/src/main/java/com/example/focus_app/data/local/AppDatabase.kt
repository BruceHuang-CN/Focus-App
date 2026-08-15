package com.example.focus_app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.focus_app.data.local.dao.AiReminderCacheDao
import com.example.focus_app.data.local.dao.AppUsageEventDao
import com.example.focus_app.data.local.dao.AppUsageSessionDao
import com.example.focus_app.data.local.dao.FocusTaskDao
import com.example.focus_app.data.local.dao.MoodRecordDao
import com.example.focus_app.data.local.dao.ReminderDisplayEventDao
import com.example.focus_app.data.local.dao.SettingsDao
import com.example.focus_app.data.local.entity.AiReminderCacheEntity
import com.example.focus_app.data.local.entity.AppUsageEventEntity
import com.example.focus_app.data.local.entity.AppUsageSessionEntity
import com.example.focus_app.data.local.entity.FocusTaskEntity
import com.example.focus_app.data.local.entity.MoodRecordEntity
import com.example.focus_app.data.local.entity.ReminderDisplayEventEntity
import com.example.focus_app.data.local.entity.SettingsEntity

@Database(
    entities = [
        AppUsageEventEntity::class,
        MoodRecordEntity::class,
        SettingsEntity::class,
        FocusTaskEntity::class,
        AppUsageSessionEntity::class,
        ReminderDisplayEventEntity::class,
        AiReminderCacheEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appUsageEventDao(): AppUsageEventDao
    abstract fun moodRecordDao(): MoodRecordDao
    abstract fun settingsDao(): SettingsDao
    abstract fun focusTaskDao(): FocusTaskDao
    abstract fun appUsageSessionDao(): AppUsageSessionDao
    abstract fun aiReminderCacheDao(): AiReminderCacheDao
    abstract fun reminderDisplayEventDao(): ReminderDisplayEventDao
}
