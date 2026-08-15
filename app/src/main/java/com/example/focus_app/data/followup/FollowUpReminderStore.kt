package com.example.focus_app.data.followup

import android.content.Context
import com.example.focus_app.domain.reminder.SnoozeDurationPolicy
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
        SnoozeDurationPolicy.normalizeStored(
            prefs.getInt(KEY_FOLLOW_UP_MINUTES, DEFAULT_MINUTES)
        )

    override fun writeMinutes(minutes: Int) {
        prefs.edit()
            .putInt(KEY_FOLLOW_UP_MINUTES, SnoozeDurationPolicy.normalizeStored(minutes))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "focus_prefs"
        const val KEY_FOLLOW_UP_MINUTES = "follow_up_reminder_minutes"
        const val DEFAULT_MINUTES = 10
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
