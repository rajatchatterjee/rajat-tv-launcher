package com.mytvlauncher.app.model

import android.graphics.drawable.Drawable

data class LauncherApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable,
    val isSideloaded: Boolean
)
