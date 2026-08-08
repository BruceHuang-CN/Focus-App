package com.example.focus_app.data.keepalive

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface KeepAliveStore {
    val enabled: StateFlow<Boolean>
    fun setEnabled(enabled: Boolean)
}

@Singleton
class SharedPrefsKeepAliveStore @Inject constructor(
    @ApplicationContext context: Context
) : KeepAliveStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_KEEP_ALIVE, true))

    override val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_ALIVE, enabled).apply()
        _enabled.value = enabled
    }

    private companion object {
        const val PREFS_NAME = "focus_prefs"
        const val KEY_KEEP_ALIVE = "keep_alive_enabled"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class KeepAliveStoreModule {
    @Binds
    @Singleton
    abstract fun bindKeepAliveStore(impl: SharedPrefsKeepAliveStore): KeepAliveStore
}
