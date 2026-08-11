package com.example.focus_app.ui.settings

import com.example.focus_app.data.repository.AppInfo

data class AppGroupEditorValidation(
    val normalizedName: String,
    val canSave: Boolean,
    val errorMessage: String?
)

object AppGroupEditorPolicy {
    private const val MaxNameLength = 24

    fun validate(name: String, apps: List<AppInfo>): AppGroupEditorValidation {
        val normalizedName = name.trim().take(MaxNameLength)
        val errorMessage = when {
            normalizedName.isBlank() -> "Please enter a group name."
            apps.isEmpty() -> "Please select at least one app."
            else -> null
        }
        return AppGroupEditorValidation(normalizedName, errorMessage == null, errorMessage)
    }
}
