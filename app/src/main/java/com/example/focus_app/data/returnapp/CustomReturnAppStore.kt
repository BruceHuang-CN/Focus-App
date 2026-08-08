package com.example.focus_app.data.returnapp

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

interface CustomReturnAppStore {
    fun read(): String
    fun write(packageName: String)
}

@Singleton
class SharedPrefsCustomReturnAppStore @Inject constructor(
    @ApplicationContext context: Context
) : CustomReturnAppStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): String =
        prefs.getString(KEY_CUSTOM_RETURN_PACKAGE, "").orEmpty()

    override fun write(packageName: String) {
        prefs.edit().putString(KEY_CUSTOM_RETURN_PACKAGE, packageName.trim()).apply()
    }

    private companion object {
        const val PREFS_NAME = "focus_prefs"
        const val KEY_CUSTOM_RETURN_PACKAGE = "custom_return_package"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CustomReturnAppStoreModule {
    @Binds
    @Singleton
    abstract fun bindCustomReturnAppStore(
        impl: SharedPrefsCustomReturnAppStore
    ): CustomReturnAppStore
}
