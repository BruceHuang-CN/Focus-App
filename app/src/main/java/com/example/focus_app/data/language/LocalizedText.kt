package com.example.focus_app.data.language

import android.content.Context
import androidx.annotation.StringRes

fun Context.localizedText(@StringRes id: Int, vararg args: Any): String =
    AppLanguage.context(this).getString(id, *args)
