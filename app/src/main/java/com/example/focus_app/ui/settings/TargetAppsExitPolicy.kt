package com.example.focus_app.ui.settings

internal object TargetAppsExitPolicy {
    fun requiresConfirmation(
        savedPackages: Set<String>,
        selectedPackages: Set<String>
    ): Boolean = savedPackages != selectedPackages
}
