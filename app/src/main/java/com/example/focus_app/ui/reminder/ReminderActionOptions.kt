package com.example.focus_app.ui.reminder

import com.example.focus_app.domain.model.ReturnDestination

internal val presetSnoozeMinutes = listOf(1, 5, 10)

internal fun exitDestinations(customPackageName: String): List<ReturnDestination> =
    buildList {
        add(ReturnDestination.HOME)
        add(ReturnDestination.FOCUS)
        if (customPackageName.isNotBlank()) add(ReturnDestination.CUSTOM)
    }

internal fun parseCustomSnoozeMinutes(raw: String): Int? =
    raw.trim().toIntOrNull()?.takeIf { it in 1..120 }
