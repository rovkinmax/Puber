package com.kino.puber.data.repository

import android.content.Context

internal class AppUpdatePreferencesRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoUpdateCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE_CHECK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_UPDATE_CHECK_ENABLED, value).apply()

    private companion object {
        const val PREFS_NAME = "app_update_preferences"
        const val KEY_AUTO_UPDATE_CHECK_ENABLED = "auto_update_check_enabled"
    }
}
