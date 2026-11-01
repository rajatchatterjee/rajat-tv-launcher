package com.mytvlauncher.app.home

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.mytvlauncher.app.MainActivity

/**
 * Home button override for TVs whose Accessibility screen hides other apps'
 * services (this OnePlus TV only shows "High contrast"). Uses two permissions
 * the TV *does* offer under Settings › Apps › Special app access:
 *
 *  - Usage access: lets us see which app just came to the front.
 *  - Display over other apps: lets a background service start our activity
 *    (Android 10+ blocks background activity starts otherwise).
 *
 * A small foreground service polls usage events twice a second while the
 * screen is on; when the TV's own home app comes to the front, it opens
 * Rajat's TV Launcher on top. Shares the on/off and "open the TV's home
 * screen once" (bypass) prefs with [HomeOverrideService].
 */
class HomeWatcherService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var stockHomes: Set<String> = emptySet()
    private var lastQuery = 0L
    private var lastForeground: String? = null
    private var lastRedirect = 0L

    private val tick = object : Runnable {
        override fun run() {
            try { poll() } catch (_: Exception) { }
            handler.postDelayed(this, if (screenOn()) 450 else 3000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        stockHomes = HomeOverrideService.otherHomePackages(this)
        lastQuery = System.currentTimeMillis() - 60_000 // catch a launcher already showing (boot)
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!shouldRun(this)) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun screenOn(): Boolean = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive

    private fun poll() {
        if (!shouldRun(this)) { stopSelf(); return }
        if (!screenOn()) return
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastQuery, now)
        lastQuery = now
        val e = UsageEvents.Event()
        var newest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) newest = e.packageName
        }
        val pkg = newest ?: return
        if (pkg == lastForeground) return
        lastForeground = pkg
        onForeground(pkg)
    }

    private fun onForeground(pkg: String) {
        if (pkg !in stockHomes) {
            if (pkg != "com.android.systemui") HomeOverrideService.clearBypass(this)
            return
        }
        if (pkg == packageName || !HomeOverrideService.enabled(this) || HomeOverrideService.bypassed(this)) return
        val now = System.currentTimeMillis()
        if (now - lastRedirect < 1000) return
        lastRedirect = now
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        } catch (_: Exception) { }
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = "home_watcher"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(channel, "Home button", NotificationManager.IMPORTANCE_MIN))
        }
        val n: Notification = Notification.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Rajat's TV Launcher")
            .setContentText("Keeping the Home button pointed here")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(41, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(41, n)
        }
    }

    companion object {
        fun hasUsageAccess(context: Context): Boolean = try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }

        /** Background activity starts need this on Android 10+; older TVs don't. */
        fun hasOverlay(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)

        fun ready(context: Context) = hasUsageAccess(context) && hasOverlay(context)

        fun shouldRun(context: Context) = ready(context) && HomeOverrideService.enabled(context)

        /** Start or stop the watcher to match permissions + the on/off switch. */
        fun sync(context: Context) {
            val intent = Intent(context, HomeWatcherService::class.java)
            try {
                if (shouldRun(context)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                    else context.startService(intent)
                } else {
                    context.stopService(intent)
                }
            } catch (_: Exception) { }
        }

        private fun openFirst(context: Context, intents: List<Intent>): Boolean {
            val pm = context.packageManager
            for (i in intents) {
                val r = try { pm.resolveActivity(i, 0) } catch (_: Exception) { null } ?: continue
                if (r.activityInfo?.name?.contains("Resolver", ignoreCase = true) == true) continue
                try { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true } catch (_: Exception) { }
            }
            return false
        }

        fun openUsageAccess(context: Context): Boolean = openFirst(context, listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        ))

        fun openOverlay(context: Context): Boolean = openFirst(context, listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        ))
    }
}

/** Restart the watcher after the TV boots or the launcher is updated. */
class HomeWatcherBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = HomeWatcherService.sync(context)
}
