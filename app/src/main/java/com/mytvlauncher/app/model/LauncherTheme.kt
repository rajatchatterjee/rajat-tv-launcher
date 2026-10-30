package com.mytvlauncher.app.model

import androidx.compose.ui.graphics.Color

/**
 * A single theme definition. Add new presets to ThemePresets below,
 * or let the user build a fully custom one in Settings (colors are
 * stored as hex strings so they persist easily via SharedPreferences).
 */
data class LauncherTheme(
    val id: String,
    val name: String,
    val background: Color,
    val backgroundImageUrl: String? = null, // optional custom wallpaper, local file path or content URI
    val surface: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val cardCornerRadiusDp: Int = 16,
    val showClock: Boolean = true,
    val gridColumns: Int = 6,
    val displayName: String = "",
    val wallpaperStyle: String = "none"
)

object ThemePresets {

    val Midnight = LauncherTheme(
        id = "midnight",
        name = "Midnight",
        background = Color(0xFF0B0E14),
        surface = Color(0xFF171B24),
        accent = Color(0xFF5B8CFF),
        textPrimary = Color(0xFFF5F6FA),
        textSecondary = Color(0xFF9AA3B2)
    )

    val Sunset = LauncherTheme(
        id = "sunset",
        name = "Sunset",
        background = Color(0xFF1A0F1A),
        surface = Color(0xFF2B1730),
        accent = Color(0xFFFF7A59),
        textPrimary = Color(0xFFFFF1E8),
        textSecondary = Color(0xFFD6A9A0)
    )

    val Forest = LauncherTheme(
        id = "forest",
        name = "Forest",
        background = Color(0xFF0E1A14),
        surface = Color(0xFF17271F),
        accent = Color(0xFF4CAF7D),
        textPrimary = Color(0xFFEFF6F1),
        textSecondary = Color(0xFFA3B8AC)
    )

    val Minimal = LauncherTheme(
        id = "minimal",
        name = "Minimal Light",
        background = Color(0xFFF4F4F6),
        surface = Color(0xFFFFFFFF),
        accent = Color(0xFF1E1E1E),
        textPrimary = Color(0xFF1A1A1A),
        textSecondary = Color(0xFF6B6B6B)
    )

    val all = listOf(Midnight, Sunset, Forest, Minimal)

    fun byId(id: String): LauncherTheme = all.find { it.id == id } ?: Midnight
}
