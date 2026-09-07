package com.example.focus_app.di

import com.example.focus_app.service.AndroidFollowUpCountdownNotifier
import com.example.focus_app.service.FollowUpCountdownNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FollowUpCountdownNotificationModule {
    @Binds
    @Singleton
    abstract fun bindFollowUpCountdownNotifier(
        impl: AndroidFollowUpCountdownNotifier
    ): FollowUpCountdownNotifier
}
