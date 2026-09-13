package com.example.focus_app.ui.settings

import android.content.Context
import androidx.annotation.StringRes

/** Resolve when displayed, so retained ViewModels do not retain the old UI language. */
data class SetupMessage(@StringRes val resourceId: Int, val arguments: List<Any?> = emptyList()) {
    constructor(@StringRes resourceId: Int, vararg arguments: Any?) : this(resourceId, arguments.toList())

    fun resolve(context: Context): String = context.getString(resourceId,
        *arguments.map { if (it is SetupMessage) it.resolve(context) else it }.toTypedArray())
}
