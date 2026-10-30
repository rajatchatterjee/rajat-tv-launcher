package com.mytvlauncher.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.mytvlauncher.app.model.LauncherApp

/**
 * Finds every app on the TV that declares a LEANBACK_LAUNCHER (or
 * regular launcher) entry point, so the home grid can show them —
 * including apps the user sideloaded.
 */
class AppRepository(private val context: Context) {

    fun loadLauncherApps(): List<LauncherApp> {
        val pm = context.packageManager
        val results = mutableListOf<LauncherApp>()
        val seenPackages = mutableSetOf<String>()

        // 1. Apps with a proper TV (Leanback) entry point.
        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        for (resolveInfo in pm.queryIntentActivities(tvIntent, 0)) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg == context.packageName) continue // don't list ourselves
            if (!seenPackages.add(pkg)) continue
            results += LauncherApp(
                packageName = pkg,
                activityName = resolveInfo.activityInfo.name,
                label = resolveInfo.loadLabel(pm).toString(),
                icon = resolveInfo.loadIcon(pm),
                isSideloaded = resolveInfo.activityInfo.applicationInfo.isSideloaded()
            )
        }

        // 2. Sideloaded phone/tablet apps that lack a Leanback entry
        //    point won't show up above — surface them too via the
        //    normal launcher intent, same approach as "Sideload Launcher".
        val phoneIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        for (resolveInfo in pm.queryIntentActivities(phoneIntent, 0)) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg == context.packageName) continue
            if (!seenPackages.add(pkg)) continue
            results += LauncherApp(
                packageName = pkg,
                activityName = resolveInfo.activityInfo.name,
                label = resolveInfo.loadLabel(pm).toString(),
                icon = resolveInfo.loadIcon(pm),
                isSideloaded = true
            )
        }

        return results.sortedBy { it.label.lowercase() }
    }

    fun launch(app: LauncherApp) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * The system uninstall-confirmation intent for one package. Android
     * requires explicit user confirmation per app — there is no API for
     * a regular (non-device-owner) app to delete another app silently.
     * For a bulk uninstall, the caller launches these one at a time via
     * an ActivityResultLauncher, moving to the next package only after
     * each dialog is dismissed (see BulkUninstallController).
     */
    fun uninstallIntent(app: LauncherApp): Intent =
        Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = android.net.Uri.parse("package:${app.packageName}")
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }

    /** Check package flags, not the discovery label: phone-launcher system apps
     * can also be marked sideloaded. Recheck before every removal request. */
    fun isUninstallable(app: LauncherApp): Boolean = try {
        val info = context.packageManager.getApplicationInfo(app.packageName, 0)
        app.packageName != context.packageName &&
            (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        false
    }

    fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        false
    }

    private fun ApplicationInfo.isSideloaded(): Boolean =
        (flags and ApplicationInfo.FLAG_SYSTEM) == 0
}
