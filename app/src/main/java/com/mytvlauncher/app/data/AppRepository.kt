package com.mytvlauncher.app.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.mytvlauncher.app.model.AppClassifier
import com.mytvlauncher.app.model.AppDetails
import com.mytvlauncher.app.model.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Finds every app with a TV (Leanback) or regular launcher entry point,
 * launches them, and remembers when each was last opened (for the
 * "Recently Opened" row and Live TV's "Recent" sort).
 */
class AppRepository(private val context: Context) {

    private val usagePrefs = context.getSharedPreferences("launcher_usage", Context.MODE_PRIVATE)

    private val _apps = MutableStateFlow<List<LauncherApp>>(emptyList())
    val apps: StateFlow<List<LauncherApp>> = _apps.asStateFlow()

    /** packageName -> last launch time (ms). */
    private val _recents = MutableStateFlow(readRecents())
    val recents: StateFlow<Map<String, Long>> = _recents.asStateFlow()

    suspend fun refresh() {
        val loaded = withContext(Dispatchers.IO) { loadLauncherApps() }
        _apps.value = loaded
        // Forget usage of apps that have since been uninstalled.
        val installed = loaded.map { it.packageName }.toSet()
        if (_recents.value.keys.any { it !in installed }) {
            writeRecents(_recents.value.filterKeys { it in installed })
        }
    }

    private fun loadLauncherApps(): List<LauncherApp> {
        val pm = context.packageManager
        val results = mutableListOf<LauncherApp>()
        val seen = mutableSetOf<String>()

        fun add(info: ResolveInfo) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName || !seen.add(pkg)) return
            try {
                val appInfo = info.activityInfo.applicationInfo
                val label = info.loadLabel(pm).toString()
                val banner = info.activityInfo.loadBanner(pm) ?: appInfo.loadBanner(pm)
                results += LauncherApp(
                    packageName = pkg,
                    activityName = info.activityInfo.name,
                    label = label,
                    icon = info.loadIcon(pm).toImageBitmap(144, 144),
                    banner = banner?.toImageBitmap(320, 180),
                    isUserInstalled = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0,
                    category = AppClassifier.category(pkg, label, appInfo),
                    isLiveTvCandidate = AppClassifier.isLiveTvCandidate(pkg, label),
                    liveTvTag = AppClassifier.liveTvTag(pkg, label)
                )
            } catch (e: Exception) {
                // One app with a broken icon shouldn't take down the whole list.
                seen.remove(pkg)
            }
        }

        // 1. Apps with a proper TV (Leanback) entry point.
        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        pm.queryIntentActivities(tvIntent, 0).forEach(::add)

        // 2. Phone/tablet apps (usually sideloaded) with only a normal launcher entry.
        val phoneIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(phoneIntent, 0).forEach(::add)

        return results.sortedBy { it.label.lowercase() }
    }

    fun launch(app: LauncherApp) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            writeRecents(_recents.value + (app.packageName to System.currentTimeMillis()))
        } catch (e: Exception) {
            toast("Couldn't open ${app.label}")
        }
    }

    /**
     * Open a show/movie from a content row. TMDB doesn't give per-service deep
     * links, so ask the app to search for the title (most TV streaming apps
     * accept ACTION_SEARCH); if it doesn't, just open the app.
     */
    fun openTitle(app: LauncherApp, title: String) {
        val search = Intent(Intent.ACTION_SEARCH).apply {
            setPackage(app.packageName)
            putExtra(android.app.SearchManager.QUERY, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (search.resolveActivity(context.packageManager) != null) {
            try {
                context.startActivity(search)
                writeRecents(_recents.value + (app.packageName to System.currentTimeMillis()))
                return
            } catch (e: Exception) { /* fall through to a plain launch */ }
        }
        launch(app)
    }

    /** Play a YouTube video, preferring the TV YouTube app. */
    fun openYouTube(videoKey: String) {
        val uri = Uri.parse("https://www.youtube.com/watch?v=$videoKey")
        val tvApp = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube.tv").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val any = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        for (i in listOf(tvApp, any)) {
            try { context.startActivity(i); return } catch (e: Exception) { }
        }
        toast("Couldn't open YouTube")
    }

    fun toastMessage(text: String) = toast(text)

    fun appFor(packageName: String): LauncherApp? = _apps.value.firstOrNull { it.packageName == packageName }

    fun details(app: LauncherApp): AppDetails? = try {
        val info = context.packageManager.getPackageInfo(app.packageName, 0)
        val appInfo = info.applicationInfo
        val apkFiles = listOfNotNull(appInfo?.sourceDir) + (appInfo?.splitSourceDirs?.toList() ?: emptyList())
        AppDetails(
            versionName = info.versionName,
            installedAt = info.firstInstallTime,
            apkSizeBytes = apkFiles.sumOf { File(it).length() }
        )
    } catch (e: Exception) {
        null
    }

    /** The system "App info" page for this package (force stop, clear data, etc.). */
    fun openAppInfo(app: LauncherApp) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("This TV doesn't have an App info screen")
        }
    }

    /** "+ Add App": opens the Play Store so the user can install something. */
    fun openPlayStore() {
        val pm = context.packageManager
        val store = "com.android.vending"
        val intent = pm.getLeanbackLaunchIntentForPackage(store) ?: pm.getLaunchIntentForPackage(store)
        try {
            if (intent == null) throw ActivityNotFoundException()
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast("Play Store isn't available on this TV")
        }
    }

    /**
     * The system uninstall-confirmation intent for one package. Android
     * requires explicit user confirmation per app — there is no API for
     * a regular app to delete another app silently (see BulkUninstallController).
     */
    fun uninstallIntent(app: LauncherApp): Intent =
        Intent(Intent.ACTION_DELETE).apply { data = Uri.parse("package:${app.packageName}") }

    /** System apps (TV Settings, Play Store, stock launcher...) are never offered for uninstall. */
    fun isUninstallable(app: LauncherApp): Boolean = app.isUserInstalled

    // ---- usage ----

    private fun readRecents(): Map<String, Long> {
        val raw = usagePrefs.getString("recents", null) ?: return emptyMap()
        return raw.split(';').mapNotNull { entry ->
            val parts = entry.split('=')
            val ts = parts.getOrNull(1)?.toLongOrNull()
            if (parts.size == 2 && ts != null) parts[0] to ts else null
        }.toMap()
    }

    private fun writeRecents(map: Map<String, Long>) {
        val trimmed = map.entries.sortedByDescending { it.value }.take(30).associate { it.key to it.value }
        usagePrefs.edit()
            .putString("recents", trimmed.entries.joinToString(";") { "${it.key}=${it.value}" })
            .apply()
        _recents.value = trimmed
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun Drawable.toImageBitmap(fallbackW: Int, fallbackH: Int): ImageBitmap {
        // Cap the size: icons/banners are shown small, and dozens of
        // full-resolution bitmaps would waste a budget TV's memory.
        val w = if (intrinsicWidth > 0) intrinsicWidth.coerceAtMost(fallbackW) else fallbackW
        val h = if (intrinsicHeight > 0 && intrinsicWidth > 0)
            (w.toFloat() * intrinsicHeight / intrinsicWidth).toInt().coerceAtLeast(1)
        else fallbackH
        return toBitmap(w, h).asImageBitmap()
    }
}
