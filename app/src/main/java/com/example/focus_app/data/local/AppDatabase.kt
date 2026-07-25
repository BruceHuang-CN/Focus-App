package com.example.focus_app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.focus_app.data.local.entity.AppUsageEventEntity
import com.example.focus_app.data.local.entity.MoodRecordEntity
import com.example.focus_app.data.local.entity.SettingsEntity

@Database(entities = [AppUsageEventEntity::class, MoodRecordEntity::class, SettingsEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appUsageEventDao(): com.example.focus_app.data.local.dao.AppUsageEventDao
    abstract fun moodRecordDao(): com.example.focus_app.data.local.dao.MoodRecordDao
    abstract fun settingsDao(): com.example.focus_app.data.local.dao.SettingsDao
}
