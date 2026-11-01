package com.mytvlauncher.app.home

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.mytvlauncher.app.MainActivity

/**
 * Home button override for TVs that don't let you pick a default launcher
 * (this OnePlus TV is one). Whenever another home app's window comes to the
 * front — the remote's Home button, waking up, backing out of an app — we
 * bring Rajat's TV Launcher up on top of it. Same approach Projectivy uses.
 *
 * Only listens for window-state changes (package names); it never reads
 * window content. Can be paused from Settings › Home button without
 * turning the accessibility service off.
 */
class HomeOverrideService : AccessibilityService() {

    private var stockHomes: Set<String> = emptySet()
    private var lastRedirect = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        stockHomes = otherHomePackages(this)
        // The TV may already be sitting on its own home screen (e.g. at boot).
        if (enabled(this)) redirect()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg !in stockHomes) return
        if (!enabled(this)) return
        redirect()
    }

    private fun redirect() {
        val now = System.currentTimeMillis()
        if (now - lastRedirect < 700) return // don't fight a launcher that bounces
        lastRedirect = now
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME) // MainActivity treats this as "Home pressed"
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try { startActivity(intent) } catch (_: Exception) { }
    }

    override fun onInterrupt() {}

    companion object {
        private const val PREFS = "home_override"

        /** The user's pause switch (the accessibility toggle itself is in TV settings). */
        fun enabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", true)

        fun setEnabled(context: Context, on: Boolean) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", on).apply()

        /** Is our accessibility service switched on in TV settings? */
        fun isServiceOn(context: Context): Boolean {
            val me = ComponentName(context, HomeOverrideService::class.java)
            val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Every other installed home-screen app (the TV's built-in launcher and friends). */
        fun otherHomePackages(context: Context): Set<String> {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val found = try {
                context.packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }
            } catch (e: Exception) { emptyList() }
            // Settings' fallback home ("FallbackHome") shows during boot only; never redirect from Settings.
            return (found + KNOWN_TV_LAUNCHERS).toSet() - context.packageName - "com.android.settings" - "com.android.tv.settings"
        }

        private val KNOWN_TV_LAUNCHERS = listOf(
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher",
            "com.android.tv.launcher",
            "com.oneplus.tv.launcher",
            "com.oneplus.tvlauncher"
        )

        fun openAccessibilitySettings(context: Context) {
            for (i in listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))) {
                try { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) { }
            }
        }
    }
}
