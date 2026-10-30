package com.mytvlauncher.app.data

import android.content.ActivityNotFoundException
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mytvlauncher.app.model.LauncherApp

/** Only the Android package installer can remove apps. Cancel stops the entire queue. */
class BulkUninstallController(activity: ComponentActivity, private val appRepository: AppRepository) {
    private val queue = ArrayDeque<LauncherApp>()
    private var active: LauncherApp? = null
    var remaining by mutableIntStateOf(0)
        private set
    var isRunning by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val current = active
        active = null
        if (!isRunning) return@registerForActivityResult
        // Do not trust result codes: package installers differ across TV vendors.
        if (current != null && appRepository.isInstalled(current.packageName)) {
            cancel()
            status = "Uninstall cancelled or unavailable. Remaining apps were kept."
        } else advance()
    }

    fun start(apps: List<LauncherApp>) {
        if (isRunning || active != null) return
        queue.clear()
        queue.addAll(apps.distinctBy { it.packageName }.filter(appRepository::isUninstallable))
        isRunning = queue.isNotEmpty()
        status = ""
        advance()
    }

    fun cancel() {
        queue.clear()
        remaining = 0
        isRunning = false
        status = "Queue stopped. Any open Android confirmation still needs your response."
    }

    private fun advance() {
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            // Recheck immediately before every request, including packages changed mid-run.
            if (!appRepository.isUninstallable(next)) continue
            active = next
            remaining = queue.size + 1
            status = "Confirm removal of ${next.label} • $remaining remaining"
            try {
                launcher.launch(appRepository.uninstallIntent(next))
                return
            } catch (_: ActivityNotFoundException) {
                active = null
                cancel()
                status = "This TV has no uninstall confirmation handler. Use Android Settings."
                return
            } catch (_: SecurityException) {
                active = null
                cancel()
                status = "Android blocked uninstall. Use Android Settings."
                return
            }
        }
        active = null
        remaining = 0
        isRunning = false
        status = "Finished. App list refreshed."
    }
}
