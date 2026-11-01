package com.mytvlauncher.app.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-update from this repo's GitHub Releases, so new builds don't need the
 * Downloader app. CI publishes every push to main as Release `build-<run>`
 * with `app-debug.apk`, and versionCode = run number, so "newer" is just
 * "higher run number". Install goes through PackageInstaller: Android still
 * shows one "Install" confirmation (only system apps can update silently),
 * and the launcher needs "Install unknown apps" allowed once.
 */
object Updater {

    private const val REPO = "rajatchatterjee/rajat-tv-launcher"
    private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val CHECK_EVERY_MS = 3 * 60 * 60 * 1000L
    const val ACTION_INSTALL_STATUS = "com.mytvlauncher.app.UPDATE_STATUS"

    sealed class State {
        object Idle : State()
        object Checking : State()
        data class UpToDate(val build: Long) : State()
        data class Available(val build: Long, val url: String) : State()
        data class Downloading(val build: Long, val percent: Int) : State()
        object NeedsPermission : State()
        object WaitingForConfirm : State()
        data class Error(val message: String) : State()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private var lastCheck = 0L

    fun currentBuild(context: Context): Long = try {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    } catch (e: Exception) { 0L }

    /** Called on every resume; only hits the network every few hours. */
    fun checkIfDue(context: Context) {
        if (System.currentTimeMillis() - lastCheck < CHECK_EVERY_MS) return
        check(context)
    }

    fun check(context: Context) {
        val s = _state.value
        if (s is State.Checking || s is State.Downloading) return
        lastCheck = System.currentTimeMillis()
        _state.value = State.Checking
        val app = context.applicationContext
        scope.launch {
            _state.value = try {
                val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000; readTimeout = 15_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                }
                val json = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                val build = json.getString("tag_name").removePrefix("build-").toLong()
                val assets = json.getJSONArray("assets")
                var url: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) url = a.getString("browser_download_url")
                }
                val mine = currentBuild(app)
                if (url != null && build > mine) State.Available(build, url) else State.UpToDate(mine)
            } catch (e: Exception) {
                State.Error("Couldn't reach GitHub (${e.javaClass.simpleName})")
            }
        }
    }

    /** Download the available build and hand it to Android's installer. */
    fun install(context: Context) {
        val s = _state.value
        val target = when (s) {
            is State.Available -> s
            is State.NeedsPermission, is State.Error, is State.WaitingForConfirm -> pending ?: return check(context)
            else -> return
        }
        pending = target
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = State.NeedsPermission
            openUnknownSourcesSettings(context)
            return
        }
        val app = context.applicationContext
        scope.launch {
            try {
                _state.value = State.Downloading(target.build, 0)
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                    .apply { setAppPackageName(app.packageName) }
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    val conn = (URL(target.url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
                    }
                    val total = conn.contentLengthLong
                    conn.inputStream.use { input ->
                        session.openWrite("app.apk", 0, if (total > 0) total else -1).use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var lastPct = -1
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    if (pct != lastPct) { lastPct = pct; _state.value = State.Downloading(target.build, pct) }
                                }
                            }
                            session.fsync(out)
                        }
                    }
                    val intent = Intent(app, UpdateStatusReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                    val pi = PendingIntent.getBroadcast(app, sessionId, intent, flags)
                    _state.value = State.WaitingForConfirm
                    session.commit(pi.intentSender)
                }
            } catch (e: Exception) {
                _state.value = State.Error("Download failed (${e.javaClass.simpleName})")
            }
        }
    }

    private var pending: State.Available? = null

    fun openUnknownSourcesSettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        )
        for (i in intents) {
            try { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {}
        }
    }

    internal fun onInstallStatus(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    _state.value = State.WaitingForConfirm
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = State.Idle // process is replaced anyway
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "cancelled"
                _state.value = State.Error("Install didn't finish: $msg")
            }
        }
    }
}

class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Updater.onInstallStatus(context, intent)
}
