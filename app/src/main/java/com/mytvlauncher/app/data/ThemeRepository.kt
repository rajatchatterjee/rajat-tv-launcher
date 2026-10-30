package com.mytvlauncher.app.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mytvlauncher.app.model.LauncherTheme
import com.mytvlauncher.app.model.ThemePresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "launcher_settings")

/**
 * Persists the active theme + any user overrides (custom colors,
 * custom wallpaper, grid column count) so they survive restarts.
 */
class ThemeRepository(private val context: Context) {

    private object Keys {
        val THEME_ID = stringPreferencesKey("theme_id")
        val CUSTOM_BACKGROUND_HEX = stringPreferencesKey("custom_background_hex")
        val CUSTOM_ACCENT_HEX = stringPreferencesKey("custom_accent_hex")
        val CUSTOM_WALLPAPER_URI = stringPreferencesKey("custom_wallpaper_uri")
        val SHOW_CLOCK = booleanPreferencesKey("show_clock")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
    }

    val currentTheme: Flow<LauncherTheme> = context.dataStore.data.map { prefs ->
        val base = ThemePresets.byId(prefs[Keys.THEME_ID] ?: ThemePresets.Midnight.id)
        base.copy(
            background = prefs[Keys.CUSTOM_BACKGROUND_HEX]?.let { Color(android.graphics.Color.parseColor(it)) }
                ?: base.background,
            accent = prefs[Keys.CUSTOM_ACCENT_HEX]?.let { Color(android.graphics.Color.parseColor(it)) }
                ?: base.accent,
            backgroundImageUrl = prefs[Keys.CUSTOM_WALLPAPER_URI] ?: base.backgroundImageUrl,
            showClock = prefs[Keys.SHOW_CLOCK] ?: base.showClock,
            gridColumns = prefs[Keys.GRID_COLUMNS] ?: base.gridColumns
        )
    }

    suspend fun selectPreset(themeId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_ID] = themeId
            // Selecting a preset clears prior custom overrides so the
            // preset's own colors show through.
            prefs.remove(Keys.CUSTOM_BACKGROUND_HEX)
            prefs.remove(Keys.CUSTOM_ACCENT_HEX)
        }
    }

    suspend fun setCustomBackground(hex: String) {
        context.dataStore.edit { it[Keys.CUSTOM_BACKGROUND_HEX] = hex }
    }

    suspend fun setCustomAccent(hex: String) {
        context.dataStore.edit { it[Keys.CUSTOM_ACCENT_HEX] = hex }
    }

    suspend fun setWallpaper(uriOrPath: String) {
        context.dataStore.edit { it[Keys.CUSTOM_WALLPAPER_URI] = uriOrPath }
    }

    suspend fun clearWallpaper() {
        context.dataStore.edit { it.remove(Keys.CUSTOM_WALLPAPER_URI) }
    }

    suspend fun setShowClock(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_CLOCK] = show }
    }

    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit { it[Keys.GRID_COLUMNS] = columns }
    }
}
