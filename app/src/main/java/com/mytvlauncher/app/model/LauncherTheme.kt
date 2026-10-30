package com.mytvlauncher.app.model

import androidx.compose.ui.graphics.Color

/**
 * What gets painted behind the launcher. The five scenic ones are drawn
 * procedurally in code (see ui/components/Wallpaper.kt) — no image files,
 * so they're crisp at any TV resolution and cost nothing in APK size.
 * GRADIENT is the older look: the theme's background colour with a soft
 * time-of-day tint.
 */
enum class Scene { CINEMATIC_SUNSET, AURORA, MINIMAL_DARK, OCEAN_BLUE, FOREST, GRADIENT }

/**
 * A theme: a wallpaper scene plus the colours the UI is drawn in.
 * The accent can be overridden by the user in Theme & Appearance.
 */
data class LauncherTheme(
    val id: String,
    val name: String,
    val scene: Scene,
    val background: Color,
    val surface: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val isLight: Boolean = false
)

object ThemePresets {

    /** The blue used throughout the mockups. */
    val DefaultAccent = Color(0xFF3D7BFF)

    /** Accent choices offered in Theme & Appearance (matches the mockup row). */
    val accentChoices = listOf(
        Color(0xFF3D7BFF), // blue
        Color(0xFF5B5BF0), // indigo
        Color(0xFFA64BF4), // purple
        Color(0xFFFF8A3D), // orange
        Color(0xFF2FBF71), // green
        Color(0xFF9AA0A6)  // grey
    )

    private fun scenic(id: String, name: String, scene: Scene) = LauncherTheme(
        id = id,
        name = name,
        scene = scene,
        background = Color(0xFF0B0D14),
        surface = Color(0xFF161A24),
        accent = DefaultAccent,
        textPrimary = Color(0xFFF5F6FA),
        textSecondary = Color(0xFFB4BAC6)
    )

    val CinematicSunset = scenic("cinematic_sunset", "Cinematic Sunset", Scene.CINEMATIC_SUNSET)
    val Aurora = scenic("aurora", "Aurora", Scene.AURORA)
    val MinimalDark = scenic("minimal_dark", "Minimal Dark", Scene.MINIMAL_DARK)
    val OceanBlue = scenic("ocean_blue", "Ocean Blue", Scene.OCEAN_BLUE)
    val ForestScene = scenic("forest_scene", "Forest", Scene.FOREST)

    // ---- Earlier colour themes, kept so a saved choice still works ----

    val Midnight = LauncherTheme(
        id = "midnight", name = "Midnight", scene = Scene.GRADIENT,
        background = Color(0xFF0B0E14), surface = Color(0xFF171B24), accent = Color(0xFF5B8CFF),
        textPrimary = Color(0xFFF5F6FA), textSecondary = Color(0xFF9AA3B2)
    )
    val Sunset = LauncherTheme(
        id = "sunset", name = "Plum Sunset", scene = Scene.GRADIENT,
        background = Color(0xFF1A0F1A), surface = Color(0xFF2B1730), accent = Color(0xFFFF7A59),
        textPrimary = Color(0xFFFFF1E8), textSecondary = Color(0xFFD6A9A0)
    )
    val Forest = LauncherTheme(
        id = "forest", name = "Pine", scene = Scene.GRADIENT,
        background = Color(0xFF0E1A14), surface = Color(0xFF17271F), accent = Color(0xFF4CAF7D),
        textPrimary = Color(0xFFEFF6F1), textSecondary = Color(0xFFA3B8AC)
    )
    val Minimal = LauncherTheme(
        id = "minimal", name = "Minimal Light", scene = Scene.GRADIENT,
        background = Color(0xFFF4F4F6), surface = Color(0xFFFFFFFF), accent = Color(0xFF1E1E1E),
        textPrimary = Color(0xFF1A1A1A), textSecondary = Color(0xFF6B6B6B), isLight = true
    )
    val Crimson = LauncherTheme(
        id = "crimson", name = "Crimson", scene = Scene.GRADIENT,
        background = Color(0xFF0A0A0A), surface = Color(0xFF1A1414), accent = Color(0xFFE11F3C),
        textPrimary = Color(0xFFF5F0F0), textSecondary = Color(0xFFB08C8F)
    )
    val Obsidian = LauncherTheme(
        id = "obsidian", name = "Obsidian", scene = Scene.GRADIENT,
        background = Color(0xFF000000), surface = Color(0xFF0C0C0C), accent = Color(0xFF00D4FF),
        textPrimary = Color(0xFFF2F2F2), textSecondary = Color(0xFF7A7A7A)
    )
    val Neon = LauncherTheme(
        id = "neon", name = "Neon", scene = Scene.GRADIENT,
        background = Color(0xFF07060C), surface = Color(0xFF140F1F), accent = Color(0xFFFF2BD6),
        textPrimary = Color(0xFFF4EEFF), textSecondary = Color(0xFFB79CE0)
    )
    val PureWhite = LauncherTheme(
        id = "pure_white", name = "Pure White", scene = Scene.GRADIENT,
        background = Color(0xFFFFFFFF), surface = Color(0xFFF6F6F7), accent = Color(0xFF111111),
        textPrimary = Color(0xFF111111), textSecondary = Color(0xFF7D7D7D), isLight = true
    )

    /** Scenic themes first (as in the mockup), then the colour themes. */
    val all = listOf(
        CinematicSunset, Aurora, MinimalDark, OceanBlue, ForestScene,
        Crimson, Midnight, Sunset, Forest, Obsidian, Neon, Minimal, PureWhite
    )

    val Default = CinematicSunset

    fun byId(id: String?): LauncherTheme = all.find { it.id == id } ?: Default
}
