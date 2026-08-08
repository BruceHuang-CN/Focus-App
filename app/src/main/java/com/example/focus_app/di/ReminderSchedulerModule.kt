package com.example.focus_app.di

import com.example.focus_app.service.ReminderScheduler
import com.example.focus_app.service.SessionReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderSchedulerModule {
    @Binds
    @Singleton
    abstract fun bindSessionReminderScheduler(
        impl: ReminderScheduler
    ): SessionReminderScheduler
}
