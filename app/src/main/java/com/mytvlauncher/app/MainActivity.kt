package com.mytvlauncher.app

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.mytvlauncher.app.data.AppRepository
import com.mytvlauncher.app.data.BulkUninstallController
import com.mytvlauncher.app.data.CompanionServer
import com.mytvlauncher.app.data.ContentRepository
import com.mytvlauncher.app.data.KidsRepository
import com.mytvlauncher.app.data.MyListRepository
import com.mytvlauncher.app.data.SoundFx
import com.mytvlauncher.app.data.SettingsRepository
import com.mytvlauncher.app.data.Updater
import com.mytvlauncher.app.ui.LauncherRoot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var appRepository: AppRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var uninstaller: BulkUninstallController
    private lateinit var kidsRepository: KidsRepository
    private var sounds: SoundFx? = null
    private lateinit var contentRepository: ContentRepository
    private lateinit var myListRepository: MyListRepository
    private lateinit var companion: CompanionServer

    /** Bumped when the remote's Home button is pressed while we're already showing. */
    private val homeTicks = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appRepository = AppRepository(applicationContext)
        settingsRepository = SettingsRepository(applicationContext)
        // Must be created in onCreate (before STARTED) — ActivityResultLauncher
        // registration requires it.
        uninstaller = BulkUninstallController(this, appRepository)
        kidsRepository = KidsRepository(applicationContext)
        sounds = try { SoundFx(applicationContext) } catch (e: Exception) { null }
        contentRepository = ContentRepository(applicationContext)
        myListRepository = MyListRepository(applicationContext)
        companion = CompanionServer(applicationContext, appRepository, contentRepository, myListRepository)
        if (companion.enabled) companion.start()

        setContent {
            LauncherRoot(
                appRepository = appRepository,
                settingsRepository = settingsRepository,
                uninstaller = uninstaller,
                kidsRepository = kidsRepository,
                contentRepo = contentRepository,
                myList = myListRepository,
                companion = companion,
                homeTicks = homeTicks
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload the app list every time we come back — picks up installs,
        // and apps removed through the uninstall dialogs.
        lifecycleScope.launch { appRepository.refresh() }
        // Kids mode: count time spent in the app a kid opened.
        kidsRepository.endSession()
        // Self-update: check GitHub Releases every few hours, and if we sent
        // the user to "Install unknown apps" and they allowed it, carry on.
        Updater.checkIfDue(this)
        if (Updater.state.value is Updater.State.NeedsPermission && packageManager.canRequestPackageInstalls()) {
            Updater.install(this)
        }
    }

    /** Navigation sounds for every D-pad move / OK press, app-wide. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && settingsRepository.state.value.navSounds) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> sounds?.tick()
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> sounds?.select()
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeTicks.value = homeTicks.value + 1
    }
}
