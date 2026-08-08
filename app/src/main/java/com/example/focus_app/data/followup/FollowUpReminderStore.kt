package com.example.focus_app.data.followup

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

interface FollowUpReminderStore {
    fun readMinutes(): Int
    fun writeMinutes(minutes: Int)
}

@Singleton
class SharedPrefsFollowUpReminderStore @Inject constructor(
    @ApplicationContext context: Context
) : FollowUpReminderStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun readMinutes(): Int =
        prefs.getInt(KEY_FOLLOW_UP_MINUTES, DEFAULT_MINUTES).coerceIn(1, MAX_MINUTES)

    override fun writeMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_FOLLOW_UP_MINUTES, minutes.coerceIn(1, MAX_MINUTES)).apply()
    }

    private companion object {
        const val PREFS_NAME = "focus_prefs"
        const val KEY_FOLLOW_UP_MINUTES = "follow_up_reminder_minutes"
        const val DEFAULT_MINUTES = 10
        const val MAX_MINUTES = 120
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FollowUpReminderStoreModule {
    @Binds
    @Singleton
    abstract fun bindFollowUpReminderStore(
        impl: SharedPrefsFollowUpReminderStore
    ): FollowUpReminderStore
}
