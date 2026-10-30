package com.mytvlauncher.app.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.mytvlauncher.app.model.LauncherTheme
import com.mytvlauncher.app.model.ThemePresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the active theme + any user overrides (custom colors,
 * custom wallpaper, grid column count) so they survive restarts.
 *
 * Uses plain Android SharedPreferences rather than Jetpack DataStore:
 * DataStore's preferences artifact bundles a native (.so) library for
 * its cross-process file lock, and that library isn't guaranteed to
 * ship for every CPU architecture a budget Android TV might run on.
 * SharedPreferences has no native code at all, so it installs
 * everywhere — worth the minor API inconvenience for a launcher that
 * needs to run on whatever silicon is in the TV.
 */
class ThemeRepository(private val context: Context) {

    private object Keys {
        const val THEME_ID = "theme_id"
        const val CUSTOM_BACKGROUND_HEX = "custom_background_hex"
        const val CUSTOM_ACCENT_HEX = "custom_accent_hex"
        const val CUSTOM_WALLPAPER_URI = "custom_wallpaper_uri"
        const val SHOW_CLOCK = "show_clock"
        const val GRID_COLUMNS = "grid_columns"
    }

    private val prefs = context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)

    private val _currentTheme = MutableStateFlow(readTheme())
    val currentTheme: StateFlow<LauncherTheme> = _currentTheme.asStateFlow()

    private fun readTheme(): LauncherTheme {
        val base = ThemePresets.byId(prefs.getString(Keys.THEME_ID, null) ?: ThemePresets.Crimson.id)
        val customBackgroundHex = prefs.getString(Keys.CUSTOM_BACKGROUND_HEX, null)
        val customAccentHex = prefs.getString(Keys.CUSTOM_ACCENT_HEX, null)
        return base.copy(
            background = customBackgroundHex?.let { Color(android.graphics.Color.parseColor(it)) }
                ?: base.background,
            accent = customAccentHex?.let { Color(android.graphics.Color.parseColor(it)) }
                ?: base.accent,
            backgroundImageUrl = prefs.getString(Keys.CUSTOM_WALLPAPER_URI, null) ?: base.backgroundImageUrl,
            showClock = prefs.getBoolean(Keys.SHOW_CLOCK, base.showClock),
            gridColumns = prefs.getInt(Keys.GRID_COLUMNS, base.gridColumns)
        )
    }

    private fun refresh() {
        _currentTheme.value = readTheme()
    }

    fun selectPreset(themeId: String) {
        prefs.edit()
            .putString(Keys.THEME_ID, themeId)
            // Selecting a preset clears prior custom overrides so the
            // preset's own colors show through.
            .remove(Keys.CUSTOM_BACKGROUND_HEX)
            .remove(Keys.CUSTOM_ACCENT_HEX)
            .apply()
        refresh()
    }

    fun setCustomBackground(hex: String) {
        prefs.edit().putString(Keys.CUSTOM_BACKGROUND_HEX, hex).apply()
        refresh()
    }

    fun setCustomAccent(hex: String) {
        prefs.edit().putString(Keys.CUSTOM_ACCENT_HEX, hex).apply()
        refresh()
    }

    fun setWallpaper(uriOrPath: String) {
        prefs.edit().putString(Keys.CUSTOM_WALLPAPER_URI, uriOrPath).apply()
        refresh()
    }

    fun clearWallpaper() {
        prefs.edit().remove(Keys.CUSTOM_WALLPAPER_URI).apply()
        refresh()
    }

    fun setShowClock(show: Boolean) {
        prefs.edit().putBoolean(Keys.SHOW_CLOCK, show).apply()
        refresh()
    }

    fun setGridColumns(columns: Int) {
        prefs.edit().putInt(Keys.GRID_COLUMNS, columns).apply()
        refresh()
    }
}
