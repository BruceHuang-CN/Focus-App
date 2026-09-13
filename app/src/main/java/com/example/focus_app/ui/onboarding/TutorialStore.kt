package com.example.focus_app.ui.onboarding

import com.example.focus_app.R

import android.content.Context
import com.example.focus_app.util.PermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small progress record; reuses the project's preferences instead of adding a storage dependency. */
@Singleton
class TutorialStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("tutorial_v1", Context.MODE_PRIVATE)
    val completed get() = prefs.getBoolean("completed", false)
    val initialized get() = prefs.getBoolean("initialized", false)
    val step get() = prefs.getInt("step", 0).coerceIn(0, 6)
    val taskId get() = prefs.getLong("task_id", 0)
    val pendingTaskId get() = prefs.getLong("pending_task_id", 0)
    val groupId get() = prefs.getString("group_id", "").orEmpty()
    val experienceSince get() = prefs.getLong("experience_since", 0)
    val experienceDone get() = prefs.getBoolean("experience_done", false)
    val hasProgress get() = initialized || completed
    val oldUser get() = PermissionHelper.isOnboardingDone(context)

    suspend fun save(edit: android.content.SharedPreferences.Editor.() -> Unit) = withContext(Dispatchers.IO) {
        check(prefs.edit().apply(edit).commit()) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_113) }
    }
    suspend fun setStep(value: Int) = save { putInt("step", value.coerceIn(0, 6)) }
    suspend fun invalidateConfiguration(edit: android.content.SharedPreferences.Editor.() -> Unit = {}) = save {
        edit()
        putBoolean("completed", false)
        putBoolean("experience_done", false)
        remove("experience_since")
    }
    suspend fun defer() {
        save { putBoolean("deferred", true) }
        withContext(Dispatchers.IO) { PermissionHelper.markOnboardingDone(context) }
    }
    suspend fun complete() {
        save {
            putBoolean("completed", true); putBoolean("deferred", false); putInt("step", 6)
            if (experienceSince == 0L) putLong("experience_since", System.currentTimeMillis())
        }
        withContext(Dispatchers.IO) { PermissionHelper.markOnboardingDone(context) }
    }
}

internal fun tutorialTime(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return if (h in 0..23 && m in 0..59) h * 60 + m else null
}

internal fun tutorialFormatTime(minute: Int): String =
    java.lang.String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)
