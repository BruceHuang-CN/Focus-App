package com.example.focus_app.service

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AccessibilityDiagnosticsStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(loadPersistedState())

    val state: StateFlow<AccessibilityDiagnosticsState> = mutableState.asStateFlow()

    @Synchronized
    fun recordServiceConnected(nowMillis: Long = System.currentTimeMillis()) {
        update(AccessibilityDiagnosticsEvent.ServiceConnected(nowMillis), "service_connected")
    }

    @Synchronized
    fun recordServiceDestroyed(nowMillis: Long = System.currentTimeMillis()) {
        update(AccessibilityDiagnosticsEvent.ServiceDestroyed(nowMillis), "service_destroyed")
    }

    @Synchronized
    fun recordServiceInterrupted(nowMillis: Long = System.currentTimeMillis()) {
        update(AccessibilityDiagnosticsEvent.ServiceInterrupted(nowMillis), "service_interrupted")
    }

    @Synchronized
    fun recordAppLaunch(
        packageLastUpdateTimeMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
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
