package com.projectmimir.finr

import android.content.Context

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(AppText.PREFS_NAME, Context.MODE_PRIVATE)

    fun themeMode(): ThemeMode {
        val prefMode = prefs.getString(AppText.PREF_THEME_MODE, null)
        return if (prefMode != null) {
            ThemeMode.fromPref(prefMode)
        } else if (prefs.getBoolean(AppText.PREF_THEME_DARK, false)) {
            ThemeMode.DARK
        } else {
            ThemeMode.LIGHT
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit()
            .putString(AppText.PREF_THEME_MODE, mode.prefValue)
            .putBoolean(AppText.PREF_THEME_DARK, mode != ThemeMode.LIGHT)
            .apply()
    }

    fun secureEnabled(): Boolean = prefs.getBoolean(AppText.PREF_SECURE_ENABLED, false)

    fun setSecureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(AppText.PREF_SECURE_ENABLED, enabled).apply()
    }

    fun bankBackfillDone(): Boolean = prefs.getBoolean(AppText.PREF_BANK_BACKFILL_DONE, false)

    fun setBankBackfillDone(done: Boolean) {
        prefs.edit().putBoolean(AppText.PREF_BANK_BACKFILL_DONE, done).apply()
    }

    fun setCategoriesSeeded(seeded: Boolean) {
        prefs.edit().putBoolean(AppText.PREF_CATEGORIES_SEEDED, seeded).apply()
    }
}
