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
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (pkg !in stockHomes) {
            // Left the TV's home screen for something real: the one-time pass
            // from "Open TV's home screen" is used up.
            if (pkg != "com.android.systemui" && prefs.getBoolean("bypass", false)) {
                prefs.edit().putBoolean("bypass", false).apply()
            }
            return
        }
        if (pkg == packageName || !enabled(this) || prefs.getBoolean("bypass", false)) return
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

        fun bypassed(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("bypass", false)

        fun clearBypass(context: Context) {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (p.getBoolean("bypass", false)) p.edit().putBoolean("bypass", false).apply()
        }

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

        /**
         * Show the TV's own home screen once, without the override snapping
         * back. The pass ends as soon as you open anything else from there.
         */
        fun openStockHome(context: Context): Boolean {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val target = try {
                context.packageManager.queryIntentActivities(home, 0)
                    .map { it.activityInfo }
                    .firstOrNull { it.packageName != context.packageName && it.packageName != "com.android.settings" && it.packageName != "com.android.tv.settings" }
            } catch (e: Exception) { null } ?: return false
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("bypass", true).apply()
            return try {
                context.startActivity(
                    Intent(home).setComponent(ComponentName(target.packageName, target.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (e: Exception) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("bypass", false).apply()
                false
            }
        }

        /**
         * Opens the TV's Accessibility screen. The generic
         * ACTION_ACCESSIBILITY_SETTINGS shows "No applications can handle this
         * action" on this OnePlus TV, so try the Android TV Settings screens
         * by name first, and only start intents that actually resolve.
         * Returns false if nothing could be opened.
         */
        fun openAccessibilitySettings(context: Context): Boolean {
            val tv = "com.android.tv.settings"
            val candidates = listOf(
                Intent().setComponent(ComponentName(tv, "$tv.accessibility.AccessibilityActivity")),
                Intent().setComponent(ComponentName(tv, "$tv.accessibility.AccessibilityServicesActivity")),
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).setPackage(tv),
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                Intent().setComponent(ComponentName(tv, "$tv.device.DevicePrefFragment")),
                Intent().setComponent(ComponentName(tv, "$tv.MainSettings")),
                Intent(Settings.ACTION_SETTINGS)
            )
            val pm = context.packageManager
            for (i in candidates) {
                val resolved = try { pm.resolveActivity(i, 0) } catch (_: Exception) { null } ?: continue
                // Some TVs resolve generic actions to a stub that only says
                // "No applications can handle this action" — skip the resolver.
                if (resolved.activityInfo?.name?.contains("Resolver", ignoreCase = true) == true) continue
                try {
                    context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return true
                } catch (_: Exception) { }
            }
            return false
        }
    }
}
