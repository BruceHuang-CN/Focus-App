package com.example.focus_app.di

import android.content.Context
import androidx.room.Room
import com.example.focus_app.data.appgroup.AppGroupStore
import com.example.focus_app.data.local.AppDatabase
import com.example.focus_app.data.local.dao.AiReminderCacheDao
import com.example.focus_app.data.local.dao.AppUsageEventDao
import com.example.focus_app.data.local.dao.AppUsageSessionDao
import com.example.focus_app.data.local.dao.FocusTaskDao
import com.example.focus_app.data.local.dao.MoodRecordDao
import com.example.focus_app.data.local.dao.ReminderDisplayEventDao
import com.example.focus_app.data.local.dao.SettingsDao
import com.example.focus_app.data.local.migration.MIGRATION_1_2
import com.example.focus_app.data.local.migration.MIGRATION_2_3
import com.example.focus_app.data.local.migration.MIGRATION_3_4
import com.example.focus_app.data.local.migration.MIGRATION_4_5
import com.example.focus_app.data.local.migration.MIGRATION_5_6
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.ReminderDisplayRepository
import com.example.focus_app.data.repository.RoomReminderDisplayRepository
import com.example.focus_app.data.repository.RoomAppSessionRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.data.security.ApiKeyStore
import com.example.focus_app.data.security.EncryptedApiKeyStore
import com.example.focus_app.data.security.MigratingApiKeyStore
import com.example.focus_app.data.security.RoomLegacyApiKeySource
import com.example.focus_app.domain.time.SystemClock
import com.example.focus_app.service.AppSessionCoordinator
import com.example.focus_app.service.ReturnToFocusGrace
import com.example.focus_app.service.AndroidReminderLauncher
import com.example.focus_app.service.ReminderLauncher
import com.example.focus_app.service.ReminderScheduler
import com.example.focus_app.service.RepositoryAppSessionContextProvider
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
    fun provideAppGroupStore(@ApplicationContext context: Context): AppGroupStore =
        AppGroupStore(context.getSharedPreferences(AppGroupStore.PREFERENCES_NAME, Context.MODE_PRIVATE))

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "focus_app_db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6
            )
            .build()
    }

    @Provides fun provideAppUsageEventDao(db: AppDatabase): AppUsageEventDao = db.appUsageEventDao()
    @Provides fun provideMoodRecordDao(db: AppDatabase): MoodRecordDao = db.moodRecordDao()
    @Provides fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
    @Provides fun provideFocusTaskDao(db: AppDatabase): FocusTaskDao = db.focusTaskDao()
    @Provides fun provideAppUsageSessionDao(db: AppDatabase): AppUsageSessionDao = db.appUsageSessionDao()
    @Provides fun provideAiReminderCacheDao(db: AppDatabase): AiReminderCacheDao = db.aiReminderCacheDao()
    @Provides
    fun provideReminderDisplayEventDao(db: AppDatabase): ReminderDisplayEventDao =
        db.reminderDisplayEventDao()

    @Provides
    @Singleton
    fun provideReminderDisplayRepository(
        db: AppDatabase,
        displayDao: ReminderDisplayEventDao,
        sessionDao: AppUsageSessionDao
    ): ReminderDisplayRepository = RoomReminderDisplayRepository(db, displayDao, sessionDao)

    @Provides
    @Singleton
    fun provideApiKeyStore(
        encryptedStore: EncryptedApiKeyStore,
        legacySource: RoomLegacyApiKeySource
    ): ApiKeyStore = MigratingApiKeyStore(encryptedStore, legacySource)

    @Provides
    @Singleton
    fun provideAppSessionRepository(dao: AppUsageSessionDao): AppSessionRepository =
        RoomAppSessionRepository(dao)

    @Provides
    @Singleton
    fun provideAppSessionCoordinator(
        repository: AppSessionRepository,
        settingsRepository: SettingsRepository,
        taskRepository: TaskRepository,
        reminderScheduler: ReminderScheduler,
        returnToFocusGrace: ReturnToFocusGrace
    ): AppSessionCoordinator = AppSessionCoordinator(
        repository = repository,
        contextProvider = RepositoryAppSessionContextProvider(settingsRepository, taskRepository),
        clock = SystemClock,
        reminderScheduler = reminderScheduler,
        returnToFocusGrace = returnToFocusGrace
    )

    @Provides
    @Singleton
    fun provideReminderLauncher(launcher: AndroidReminderLauncher): ReminderLauncher = launcher
}
