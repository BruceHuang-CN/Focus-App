package com.example.focus_app.di

import android.content.Context
import androidx.room.Room
import com.example.focus_app.data.local.AppDatabase
import com.example.focus_app.data.local.dao.AppUsageEventDao
import com.example.focus_app.data.local.dao.MoodRecordDao
import com.example.focus_app.data.local.dao.SettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "focus_app_db").build()
    }

    @Provides fun provideAppUsageEventDao(db: AppDatabase): AppUsageEventDao = db.appUsageEventDao()
    @Provides fun provideMoodRecordDao(db: AppDatabase): MoodRecordDao = db.moodRecordDao()
    @Provides fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
}
