package com.mytvlauncher.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.mytvlauncher.app.data.AppRepository
import com.mytvlauncher.app.data.BulkUninstallController
import com.mytvlauncher.app.data.SettingsRepository
import com.mytvlauncher.app.data.Updater
import com.mytvlauncher.app.ui.LauncherRoot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var appRepository: AppRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var uninstaller: BulkUninstallController

    /** Bumped when the remote's Home button is pressed while we're already showing. */
    private val homeTicks = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appRepository = AppRepository(applicationContext)
        settingsRepository = SettingsRepository(applicationContext)
        // Must be created in onCreate (before STARTED) — ActivityResultLauncher
        // registration requires it.
        uninstaller = BulkUninstallController(this, appRepository)

        setContent {
            LauncherRoot(
                appRepository = appRepository,
                settingsRepository = settingsRepository,
                uninstaller = uninstaller,
                homeTicks = homeTicks
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload the app list every time we come back — picks up installs,
        // and apps removed through the uninstall dialogs.
        lifecycleScope.launch { appRepository.refresh() }
        // Self-update: check GitHub Releases every few hours, and if we sent
        // the user to "Install unknown apps" and they allowed it, carry on.
        Updater.checkIfDue(this)
        if (Updater.state.value is Updater.State.NeedsPermission && packageManager.canRequestPackageInstalls()) {
            Updater.install(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeTicks.value = homeTicks.value + 1
    }
}
