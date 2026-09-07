package com.example.focus_app.data.reminder

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ReminderActionOrderStore {
    val randomizeEnabled: StateFlow<Boolean>
    fun setRandomizeEnabled(enabled: Boolean)
}
object DefaultReminderActionOrderStore : ReminderActionOrderStore {
    override val randomizeEnabled: StateFlow<Boolean> = MutableStateFlow(true)
    override fun setRandomizeEnabled(enabled: Boolean) = Unit
}

@Singleton
class SharedPrefsReminderActionOrderStore @Inject constructor(
    @ApplicationContext context: Context
) : ReminderActionOrderStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutableRandomizeEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_RANDOMIZE_ACTIONS, true)
    )

    override val randomizeEnabled: StateFlow<Boolean> = mutableRandomizeEnabled.asStateFlow()

    override fun setRandomizeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RANDOMIZE_ACTIONS, enabled).apply()
        mutableRandomizeEnabled.value = enabled
    }

    private companion object {
        const val PREFS_NAME = "focus_prefs"
        const val KEY_RANDOMIZE_ACTIONS = "randomize_reminder_actions"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderActionOrderStoreModule {
    @Binds
    @Singleton
    abstract fun bindReminderActionOrderStore(
        impl: SharedPrefsReminderActionOrderStore
    ): ReminderActionOrderStore
}
