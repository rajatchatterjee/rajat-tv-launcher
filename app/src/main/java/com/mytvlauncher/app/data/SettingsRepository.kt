package com.mytvlauncher.app.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mytvlauncher.app.model.LauncherApp
import com.mytvlauncher.app.model.LauncherTheme
import com.mytvlauncher.app.model.ThemePresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BackgroundStyle(val label: String) { BLUR("Blur"), DIM("Dim"), SOLID("Solid") }

enum class DateStyle(val pattern: String) {
    SHORT("EEE, d MMM yyyy"),
    LONG("EEEE, d MMMM"),
    NUMERIC("dd/MM/yyyy")
}

/** Everything the user can change, as one immutable snapshot the UI collects. */
data class LauncherSettings(
    val theme: LauncherTheme,
    val accentOverridden: Boolean,
    val backgroundStyle: BackgroundStyle,
    val showClock: Boolean,
    val use24Hour: Boolean,
    val dateStyle: DateStyle,
    val gridColumns: Int,
    val showAppNames: Boolean,
    val useBanners: Boolean,
    val focusZoom: Boolean,
    /** Drifting light, particles and live-weather effects behind everything. */
    val livingBackground: Boolean,
    /** Home hero cycles through items on its own when the remote is idle. */
    val heroRotation: Boolean,
    /** Package names, in the user's chosen order. */
    val favourites: List<String>,
    val favouritesSeeded: Boolean,
    val liveTvAdded: Set<String>,
    val liveTvRemoved: Set<String>
) {
    fun isLiveTv(app: LauncherApp): Boolean =
        app.packageName in liveTvAdded ||
            (app.isLiveTvCandidate && app.packageName !in liveTvRemoved)
}

/**
 * Persists launcher settings in SharedPreferences (deliberately not
 * DataStore — see CLAUDE.md). Keys from earlier builds (theme_id,
 * custom_accent_hex, custom_background_hex, show_clock, grid_columns)
 * are kept so an upgrade doesn't lose the user's choices.
 */
class SettingsRepository(context: Context) {

    private object Keys {
        const val THEME_ID = "theme_id"
        const val CUSTOM_BACKGROUND_HEX = "custom_background_hex"
        const val CUSTOM_ACCENT_HEX = "custom_accent_hex"
        const val BACKGROUND_STYLE = "background_style"
        const val SHOW_CLOCK = "show_clock"
        const val USE_24H = "use_24h"
        const val DATE_STYLE = "date_style"
        const val GRID_COLUMNS = "grid_columns"
        const val SHOW_APP_NAMES = "show_app_names"
        const val USE_BANNERS = "use_banners"
        const val FOCUS_ZOOM = "focus_zoom"
        const val LIVING_BACKGROUND = "living_background"
        const val HERO_ROTATION = "hero_rotation"
        const val FAVOURITES = "favourites"
        const val LIVE_TV_ADDED = "live_tv_added"
        const val LIVE_TV_REMOVED = "live_tv_removed"
    }

    private val prefs = context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<LauncherSettings> = _state.asStateFlow()

    private fun read(): LauncherSettings {
        val base = ThemePresets.byId(prefs.getString(Keys.THEME_ID, null))
        val accentHex = prefs.getString(Keys.CUSTOM_ACCENT_HEX, null)
        val backgroundHex = prefs.getString(Keys.CUSTOM_BACKGROUND_HEX, null)
        val theme = base.copy(
            accent = accentHex.toColorOrNull() ?: base.accent,
            background = backgroundHex.toColorOrNull() ?: base.background
        )
        return LauncherSettings(
            theme = theme,
            accentOverridden = accentHex != null,
            backgroundStyle = enumOr(prefs.getString(Keys.BACKGROUND_STYLE, null), BackgroundStyle.BLUR),
            showClock = prefs.getBoolean(Keys.SHOW_CLOCK, true),
            use24Hour = prefs.getBoolean(Keys.USE_24H, false),
            dateStyle = enumOr(prefs.getString(Keys.DATE_STYLE, null), DateStyle.SHORT),
            gridColumns = prefs.getInt(Keys.GRID_COLUMNS, 6).coerceIn(4, 8),
            showAppNames = prefs.getBoolean(Keys.SHOW_APP_NAMES, true),
            useBanners = prefs.getBoolean(Keys.USE_BANNERS, true),
            focusZoom = prefs.getBoolean(Keys.FOCUS_ZOOM, true),
            livingBackground = prefs.getBoolean(Keys.LIVING_BACKGROUND, true),
            heroRotation = prefs.getBoolean(Keys.HERO_ROTATION, true),
            favourites = prefs.getString(Keys.FAVOURITES, null).toList(),
            favouritesSeeded = prefs.contains(Keys.FAVOURITES),
            liveTvAdded = prefs.getString(Keys.LIVE_TV_ADDED, null).toList().toSet(),
            liveTvRemoved = prefs.getString(Keys.LIVE_TV_REMOVED, null).toList().toSet()
        )
    }

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = read()
    }

    // ---- Theme & appearance ----

    /** Picking a theme clears colour overrides so the theme's own colours show. */
    fun selectTheme(id: String) = edit {
        putString(Keys.THEME_ID, id)
        remove(Keys.CUSTOM_ACCENT_HEX)
        remove(Keys.CUSTOM_BACKGROUND_HEX)
    }

    fun setAccent(color: Color) = edit { putString(Keys.CUSTOM_ACCENT_HEX, color.toHex()) }

    fun setBackgroundStyle(style: BackgroundStyle) = edit { putString(Keys.BACKGROUND_STYLE, style.name) }

    // ---- Clock ----

    fun setShowClock(show: Boolean) = edit { putBoolean(Keys.SHOW_CLOCK, show) }
    fun setUse24Hour(on: Boolean) = edit { putBoolean(Keys.USE_24H, on) }
    fun setDateStyle(style: DateStyle) = edit { putString(Keys.DATE_STYLE, style.name) }

    // ---- Layout ----

    fun setGridColumns(columns: Int) = edit { putInt(Keys.GRID_COLUMNS, columns.coerceIn(4, 8)) }
    fun setShowAppNames(on: Boolean) = edit { putBoolean(Keys.SHOW_APP_NAMES, on) }
    fun setUseBanners(on: Boolean) = edit { putBoolean(Keys.USE_BANNERS, on) }
    fun setFocusZoom(on: Boolean) = edit { putBoolean(Keys.FOCUS_ZOOM, on) }
    fun setLivingBackground(on: Boolean) = edit { putBoolean(Keys.LIVING_BACKGROUND, on) }
    fun setHeroRotation(on: Boolean) = edit { putBoolean(Keys.HERO_ROTATION, on) }

    // ---- Favourites ----

    fun setFavourites(packages: List<String>) = edit { putString(Keys.FAVOURITES, packages.joinToString(",")) }

    fun toggleFavourite(packageName: String) {
        val current = _state.value.favourites
        setFavourites(if (packageName in current) current - packageName else current + packageName)
    }

    fun addFavourite(packageName: String) {
        val current = _state.value.favourites
        if (packageName !in current) setFavourites(current + packageName)
    }

    /**
     * First run only: start Favourites with the big streaming apps (in the
     * mockup's order) if installed, topped up with other apps to six.
     */
    fun seedFavouritesIfNeeded(apps: List<LauncherApp>) {
        if (_state.value.favouritesSeeded || apps.isEmpty()) return
        val wanted = listOf("youtube", "netflix", "prime", "hotstar", "spotify", "jiocinema")
        val picks = mutableListOf<String>()
        for (word in wanted) {
            apps.firstOrNull { app ->
                app.packageName !in picks &&
                    "${app.packageName} ${app.label}".lowercase().contains(word) &&
                    !app.label.lowercase().contains("music")
            }?.let { picks += it.packageName }
        }
        apps.filter { it.isUserInstalled && it.packageName !in picks }
            .take((6 - picks.size).coerceAtLeast(0))
            .forEach { picks += it.packageName }
        setFavourites(picks)
    }

    // ---- Live TV membership ----

    fun setLiveTv(packageName: String, include: Boolean) {
        val s = _state.value
        val added = if (include) s.liveTvAdded + packageName else s.liveTvAdded - packageName
        val removed = if (include) s.liveTvRemoved - packageName else s.liveTvRemoved + packageName
        edit {
            putString(Keys.LIVE_TV_ADDED, added.joinToString(","))
            putString(Keys.LIVE_TV_REMOVED, removed.joinToString(","))
        }
    }

    // ---- helpers ----

    private fun String?.toList(): List<String> =
        this?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun String?.toColorOrNull(): Color? = try {
        this?.let { Color(android.graphics.Color.parseColor(it)) }
    } catch (e: IllegalArgumentException) {
        null
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    private fun Color.toHex(): String = String.format("#%06X", 0xFFFFFF and toArgb())
}
