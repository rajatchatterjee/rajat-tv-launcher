package com.mytvlauncher.app.data

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mytvlauncher.app.model.LauncherApp

/**
 * Walks through the system's uninstall-confirmation dialog once per
 * selected app. Android has no bulk-delete API for a regular app, so
 * this is the honest version of "bulk uninstall": the user still taps
 * "Uninstall" (or "Cancel") on each one, but doesn't have to leave the
 * launcher or navigate Settings to get there — just keep confirming
 * until the queue (shown in `remaining`) is empty.
 */
class BulkUninstallController(
    activity: ComponentActivity,
    private val appRepository: AppRepository
) {
    private var queue: MutableList<LauncherApp> = mutableListOf()

    var remaining by mutableStateOf(0)
        private set

    var isRunning by mutableStateOf(false)
        private set

    private val launcher: ActivityResultLauncher<android.content.Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Whatever happened (confirmed, cancelled, or the app wasn't
            // uninstallable), move on to the next one in the queue.
            advance()
        }

    fun start(apps: List<LauncherApp>) {
        queue = apps.filter { appRepository.isUninstallable(it) }.toMutableList()
        remaining = queue.size
        isRunning = queue.isNotEmpty()
        if (isRunning) advance()
    }

    fun cancel() {
        queue.clear()
        remaining = 0
        isRunning = false
    }

    private fun advance() {
        if (queue.isEmpty()) {
            isRunning = false
            remaining = 0
            return
        }
        val next = queue.removeAt(0)
        remaining = queue.size
        launcher.launch(appRepository.uninstallIntent(next))
    }
}
