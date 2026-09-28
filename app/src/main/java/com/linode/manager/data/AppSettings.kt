package com.linode.manager.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Small, non-secret UI preferences, observable from Compose. */
class AppSettings(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var themeMode by mutableStateOf(
        runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
    )
        private set

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString("theme", mode.name).apply()
    }
}
