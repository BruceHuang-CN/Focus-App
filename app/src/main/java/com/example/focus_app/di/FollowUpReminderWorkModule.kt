package com.example.focus_app.di

import android.content.Context
import androidx.work.WorkManager
import com.example.focus_app.service.AndroidFollowUpReminderWorkScheduler
import com.example.focus_app.service.FollowUpReminderWorkScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FollowUpReminderWorkModule {
    @Binds
    @Singleton
    abstract fun bindFollowUpReminderWorkScheduler(
        impl: AndroidFollowUpReminderWorkScheduler
    ): FollowUpReminderWorkScheduler

    companion object {
        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
            WorkManager.getInstance(context)
    }
}
