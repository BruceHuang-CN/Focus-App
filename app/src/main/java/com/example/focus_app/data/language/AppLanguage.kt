package com.example.focus_app.data.language

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One language source for activities, long-lived services and AI requests. */
object AppLanguage {
    private const val PREFS = "app_language"
    private const val KEY = "selection"
    private const val MIGRATED = "system_locale_migrated"
    const val SYSTEM = "system"
    const val CHINESE = "zh-CN"
    const val ENGLISH = "en"
    private val mutableCurrent = MutableStateFlow(CHINESE)
    val current: StateFlow<String> = mutableCurrent.asStateFlow()

    fun refresh(context: Context) { mutableCurrent.value = tag(context) }

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY, SYSTEM) ?: SYSTEM
        if (Build.VERSION.SDK_INT >= 33) {
            // Transfer an Android 12 choice once after an OS upgrade; then the OS owns it.
            if (!prefs.getBoolean(MIGRATED, false)) {
                val manager = context.getSystemService(LocaleManager::class.java)
                if (manager.applicationLocales.isEmpty && saved != SYSTEM) {
                    manager.applicationLocales = android.os.LocaleList.forLanguageTags(saved)
                }
                prefs.edit().putBoolean(MIGRATED, true).apply()
            }
        } else {
            AppCompatDelegate.setApplicationLocales(
                if (saved == SYSTEM) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(saved)
            )
        }
        refresh(context)
    }

    fun selection(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            return if (locales.isEmpty) SYSTEM else normalize(locales[0])
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM
    }

    fun set(context: Context, selection: String) {
        require(selection in listOf(SYSTEM, CHINESE, ENGLISH))
        if (this.selection(context) == selection) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, selection).apply()
        AppCompatDelegate.setApplicationLocales(
            if (selection == SYSTEM) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(selection)
        )
        refresh(context)
    }

    fun tag(context: Context): String = when (val selected = selection(context)) {
        // Let Android resolve its ordered language list, including resource fallback.
        SYSTEM -> context.applicationContext.resources.getString(com.example.focus_app.R.string.language_tag)
        else -> selected
    }

    fun context(context: Context): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag(context)))
        return context.createConfigurationContext(config)
    }

    private fun normalize(locale: Locale): String = if (locale.language == "en") ENGLISH else CHINESE
}
