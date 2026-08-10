package com.example.focus_app.data.appgroup

import android.content.SharedPreferences
import com.google.gson.Gson

class AppGroupStore(
    private val preferences: SharedPreferences,
    private val gson: Gson = Gson()
) {
    fun read(): StoredAppGroups? = preferences.getString(KEY_STATE, null)
        ?.let { gson.fromJson(it, StoredAppGroups::class.java) }

    fun write(state: StoredAppGroups) {
        preferences.edit().putString(KEY_STATE, gson.toJson(state)).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "focus_app_groups"
        private const val KEY_STATE = "state"
    }
}

data class StoredAppGroups(
    val groups: List<AppGroup>,
    val activeGroupId: String
)
