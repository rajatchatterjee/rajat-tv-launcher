package com.mytvlauncher.app.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * One launchable app. Icon and banner are converted to bitmaps once, on a
 * background thread, when the app list loads — not on every recomposition.
 */
@Immutable
data class LauncherApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: ImageBitmap,
    /** The app's 16:9 Android TV banner, if it ships one (most TV apps do). */
    val banner: ImageBitmap?,
    /** Not a system app, so it can be offered for uninstall. */
    val isUserInstalled: Boolean,
    val category: AppCategory,
    /** Looks like a live-TV / channels app (JioTV, Tata Play, news apps...). */
    val isLiveTvCandidate: Boolean,
    val liveTvTag: LiveTvTag
)

/** Extra info shown in the app options menu; loaded on demand. */
data class AppDetails(
    val versionName: String?,
    val installedAt: Long,
    val apkSizeBytes: Long
)
