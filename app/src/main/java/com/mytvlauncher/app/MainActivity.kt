package com.mytvlauncher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mytvlauncher.app.data.AppRepository
import com.mytvlauncher.app.data.BulkUninstallController
import com.mytvlauncher.app.data.ThemeRepository
import com.mytvlauncher.app.ui.screens.HomeScreen

class MainActivity : ComponentActivity() {

    private lateinit var appRepository: AppRepository
    private lateinit var themeRepository: ThemeRepository
    private lateinit var bulkUninstallController: BulkUninstallController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appRepository = AppRepository(applicationContext)
        themeRepository = ThemeRepository(applicationContext)
        // Must be created in onCreate (before STARTED) — ActivityResultLauncher
        // registration requires it.
        bulkUninstallController = BulkUninstallController(this, appRepository)

        setContent {
            HomeScreen(appRepository, themeRepository, bulkUninstallController)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::themeRepository.isInitialized) themeRepository.refresh()
    }
}
