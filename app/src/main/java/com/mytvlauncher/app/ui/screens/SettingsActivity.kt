package com.mytvlauncher.app.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mytvlauncher.app.data.ThemeRepository

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeRepository = ThemeRepository(applicationContext)
        setContent {
            SettingsScreen(themeRepository = themeRepository, onDone = { finish() })
        }
    }
}
