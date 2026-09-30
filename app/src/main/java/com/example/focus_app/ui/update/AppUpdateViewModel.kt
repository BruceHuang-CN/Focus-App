package com.example.focus_app.ui.update

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.update.AppRelease
import com.example.focus_app.data.update.AppUpdateClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal interface UpdateCheckStore {
    var nextAutomaticCheckAt: Long
}

internal enum class UpdateNotice { CURRENT, UNAVAILABLE, UNSUPPORTED, BROWSER_UNAVAILABLE }

internal data class AppUpdateUiState(
    val checking: Boolean = false,
    val release: AppRelease? = null,
    val notice: UpdateNotice? = null
)

@HiltViewModel
internal class AppUpdateViewModel internal constructor(
    private val fetch: suspend () -> AppRelease,
    private val applicationId: String,
    private val installedCode: Long,
    private val sdkInt: Int,
    private val store: UpdateCheckStore,
    private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {
    @Inject constructor(@ApplicationContext context: Context) : this(
        fetch = AppUpdateClient()::fetch,
        applicationId = context.packageName,
        installedCode = installedVersion(context),
        sdkInt = Build.VERSION.SDK_INT,
        store = PreferenceUpdateCheckStore(context)
    )

    private val _state = MutableStateFlow(AppUpdateUiState())
    val state = _state.asStateFlow()

    /** Called by the foreground Activity or an explicit Settings action; coalesces repeated requests. */
    fun check(manual: Boolean = false) {
        if (_state.value.checking || _state.value.release != null || _state.value.notice != null) return
        val wait = store.nextAutomaticCheckAt - now()
        if (!manual && wait in 1..AUTOMATIC_INTERVAL_MS) return
        _state.value = AppUpdateUiState(checking = true)
        viewModelScope.launch {
            try {
                val release = fetch()
                require(release.applicationId == applicationId) { "Update belongs to another app" }
                store.nextAutomaticCheckAt = now() + AUTOMATIC_INTERVAL_MS
                _state.value = when {
                    release.versionCode <= installedCode -> AppUpdateUiState(
                        notice = if (manual) UpdateNotice.CURRENT else null)
                    release.minSdk > sdkInt -> AppUpdateUiState(
                        notice = if (manual) UpdateNotice.UNSUPPORTED else null)
                    else -> AppUpdateUiState(release = release)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                store.nextAutomaticCheckAt = now() + FAILURE_RETRY_MS
                _state.value = AppUpdateUiState(notice = if (manual) UpdateNotice.UNAVAILABLE else null)
            } finally {
                if (_state.value.checking) _state.value = _state.value.copy(checking = false)
            }
        }
    }

    fun dismiss() {
        if (_state.value.release != null) store.nextAutomaticCheckAt = now() + AUTOMATIC_INTERVAL_MS
        _state.value = _state.value.copy(release = null, notice = null)
    }

    fun browserUnavailable() {
        _state.value = AppUpdateUiState(notice = UpdateNotice.BROWSER_UNAVAILABLE)
    }

    companion object {
        const val AUTOMATIC_INTERVAL_MS = 6 * 60 * 60 * 1_000L
        const val FAILURE_RETRY_MS = 5 * 60 * 1_000L
    }
}

private fun installedVersion(context: Context): Long =
    PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

private class PreferenceUpdateCheckStore(context: Context) : UpdateCheckStore {
    private val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val key = "next_check_${installedVersion(context)}"
    override var nextAutomaticCheckAt: Long
        get() = prefs.getLong(key, 0L)
        set(value) { prefs.edit().putLong(key, value).apply() }
}
