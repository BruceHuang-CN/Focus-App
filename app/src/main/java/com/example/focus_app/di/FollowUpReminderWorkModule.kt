package com.example.focus_app.di

import android.content.Context
import androidx.work.WorkManager
import com.example.focus_app.service.AndroidFollowUpEnvironment
import com.example.focus_app.service.AndroidFollowUpReminderWorkScheduler
import com.example.focus_app.service.FollowUpEnvironment
import com.example.focus_app.service.FollowUpExecutor
import com.example.focus_app.service.FollowUpReminderExecutor
import com.example.focus_app.service.FollowUpReminderWorkScheduler
import com.example.focus_app.service.FollowUpScheduler
import com.example.focus_app.service.HybridFollowUpScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FollowUpReminderWorkModule {
    @Binds
    @Singleton
    abstract fun bindFollowUpReminderWorkScheduler(
        impl: AndroidFollowUpReminderWorkScheduler
    ): FollowUpReminderWorkScheduler

    @Binds
    @Singleton
    abstract fun bindFollowUpExecutor(
        impl: FollowUpReminderExecutor
    ): FollowUpExecutor

    @Binds
    @Singleton
    abstract fun bindFollowUpScheduler(
        impl: HybridFollowUpScheduler
    ): FollowUpScheduler

    @Binds
    @Singleton
    abstract fun bindFollowUpEnvironment(
        impl: AndroidFollowUpEnvironment
    ): FollowUpEnvironment

    companion object {
        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
            WorkManager.getInstance(context)

        @Provides
        @Singleton
        fun provideFollowUpCoroutineScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
