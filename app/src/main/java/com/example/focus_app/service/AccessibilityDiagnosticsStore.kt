package com.example.focus_app.service

import android.content.Context
import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AccessibilityDiagnosticsStore {
    val state: StateFlow<AccessibilityDiagnosticsState>

    fun recordServiceConnected(nowMillis: Long = System.currentTimeMillis())
    fun recordServiceDestroyed(nowMillis: Long = System.currentTimeMillis())
    fun recordServiceInterrupted(nowMillis: Long = System.currentTimeMillis())
    fun recordAppLaunch(
        packageLastUpdateTimeMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    )
}

@Singleton
class SharedPrefsAccessibilityDiagnosticsStore @Inject constructor(
    @ApplicationContext context: Context
) : AccessibilityDiagnosticsStore {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(loadPersistedState())

    override val state: StateFlow<AccessibilityDiagnosticsState> = mutableState.asStateFlow()

    @Synchronized
    override fun recordServiceConnected(nowMillis: Long) {
        update(AccessibilityDiagnosticsEvent.ServiceConnected(nowMillis), "service_connected")
    }

    @Synchronized
    override fun recordServiceDestroyed(nowMillis: Long) {
        update(AccessibilityDiagnosticsEvent.ServiceDestroyed(nowMillis), "service_destroyed")
    }

    @Synchronized
    override fun recordServiceInterrupted(nowMillis: Long) {
        update(AccessibilityDiagnosticsEvent.ServiceInterrupted(nowMillis), "service_interrupted")
    }

    @Synchronized
    override fun recordAppLaunch(
        packageLastUpdateTimeMillis: Long,
        nowMillis: Long
    ) {
        val before = mutableState.value
        update(
            AccessibilityDiagnosticsEvent.AppLaunched(packageLastUpdateTimeMillis, nowMillis),
            "app_launched"
        )
        if (before.packageLastUpdateTimeMillis != packageLastUpdateTimeMillis) {
            Log.i(TAG, "first_launch_after_update packageUpdatedAt=$packageLastUpdateTimeMillis launchedAt=$nowMillis")
        }
    }

    private fun update(event: AccessibilityDiagnosticsEvent, logName: String) {
        val updated = reduceAccessibilityDiagnostics(mutableState.value, event)
        mutableState.value = updated
        persist(updated)
        Log.i(TAG, "$logName state=$updated")
    }

    private fun loadPersistedState(): AccessibilityDiagnosticsState =
        AccessibilityDiagnosticsState(
            serviceBound = false,
            lastConnectedAtMillis = preferences.optionalLong(KEY_LAST_CONNECTED),
            lastDestroyedAtMillis = preferences.optionalLong(KEY_LAST_DESTROYED),
            lastInterruptedAtMillis = preferences.optionalLong(KEY_LAST_INTERRUPTED),
            packageLastUpdateTimeMillis = preferences.optionalLong(KEY_PACKAGE_LAST_UPDATE),
            firstLaunchAfterUpdateAtMillis = preferences.optionalLong(KEY_FIRST_LAUNCH_AFTER_UPDATE)
        )

    private fun persist(state: AccessibilityDiagnosticsState) {
        preferences.edit().apply {
            putOptionalLong(KEY_LAST_CONNECTED, state.lastConnectedAtMillis)
            putOptionalLong(KEY_LAST_DESTROYED, state.lastDestroyedAtMillis)
            putOptionalLong(KEY_LAST_INTERRUPTED, state.lastInterruptedAtMillis)
            putOptionalLong(KEY_PACKAGE_LAST_UPDATE, state.packageLastUpdateTimeMillis)
            putOptionalLong(KEY_FIRST_LAUNCH_AFTER_UPDATE, state.firstLaunchAfterUpdateAtMillis)
        }.apply()
    }

    private fun android.content.SharedPreferences.optionalLong(key: String): Long? =
        if (contains(key)) getLong(key, 0L) else null

    private fun android.content.SharedPreferences.Editor.putOptionalLong(
        key: String,
        value: Long?
    ): android.content.SharedPreferences.Editor =
        if (value == null) remove(key) else putLong(key, value)

    private companion object {
        const val TAG = "FocusAccessibility"
        const val PREFS_NAME = "accessibility_diagnostics"
        const val KEY_LAST_CONNECTED = "last_connected_at"
        const val KEY_LAST_DESTROYED = "last_destroyed_at"
        const val KEY_LAST_INTERRUPTED = "last_interrupted_at"
        const val KEY_PACKAGE_LAST_UPDATE = "package_last_update_at"
        const val KEY_FIRST_LAUNCH_AFTER_UPDATE = "first_launch_after_update_at"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AccessibilityDiagnosticsStoreModule {
    @Binds
    @Singleton
    abstract fun bindAccessibilityDiagnosticsStore(
        impl: SharedPrefsAccessibilityDiagnosticsStore
    ): AccessibilityDiagnosticsStore
}
