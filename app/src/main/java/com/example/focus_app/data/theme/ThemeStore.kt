package com.example.focus_app.data.theme

import android.content.Context
import com.example.focus_app.domain.model.AppThemeColor
import com.example.focus_app.domain.model.AppThemeMode
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

data class ThemeSettings(
    val mode: AppThemeMode = AppThemeMode.SYSTEM,
    val color: AppThemeColor = AppThemeColor.MINT
)

interface ThemeStore {
    val settings: StateFlow<ThemeSettings>
    fun setMode(mode: AppThemeMode)
    fun setColor(color: AppThemeColor)
}

@Singleton
class SharedPrefsThemeStore @Inject constructor(
    @ApplicationContext context: Context
) : ThemeStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(
        ThemeSettings(
            mode = AppThemeMode.fromKey(prefs.getString(KEY_MODE, null)),
            color = AppThemeColor.fromKey(prefs.getString(KEY_COLOR, null))
        )
    )

    override val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()

    override fun setMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.key).apply()
        _settings.value = _settings.value.copy(mode = mode)
    }

    override fun setColor(color: AppThemeColor) {
        prefs.edit().putString(KEY_COLOR, color.key).apply()
        _settings.value = _settings.value.copy(color = color)
    }

    private companion object {
        const val PREFS_NAME = "focus_prefs"
        const val KEY_MODE = "theme_mode"
        const val KEY_COLOR = "theme_color"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ThemeStoreModule {
    @Binds
    @Singleton
    abstract fun bindThemeStore(impl: SharedPrefsThemeStore): ThemeStore
}
