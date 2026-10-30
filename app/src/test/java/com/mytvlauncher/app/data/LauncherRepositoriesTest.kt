package com.mytvlauncher.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import com.mytvlauncher.app.model.LauncherApp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherRepositoriesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = AppRepository(context)

    @Before fun reset() {
        context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun install(pkg: String, flags: Int): LauncherApp {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply { packageName = pkg; this.flags = flags }
        })
        // A phone launcher entry can be marked sideloaded even for a system app.
        return LauncherApp(pkg, "Main", pkg, ColorDrawable(), true)
    }

    @Test fun systemAndUpdatedSystemAppsAreProtectedEvenWithPhoneLauncherEntry() {
        assertFalse(repository.isUninstallable(install("test.system", ApplicationInfo.FLAG_SYSTEM)))
        assertFalse(repository.isUninstallable(install("test.updated", ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)))
        assertTrue(repository.isUninstallable(install("test.user", 0)))
        assertFalse(repository.isUninstallable(install(context.packageName, 0)))
    }

    @Test fun removedPackagesAreRejectedAndUninstallRequestsReturnAResult() {
        val app = install("test.removable", 0)
        val intent = repository.uninstallIntent(app)
        assertEquals(android.content.Intent.ACTION_UNINSTALL_PACKAGE, intent.action)
        assertEquals("package:test.removable", intent.data.toString())
        assertTrue(intent.getBooleanExtra(android.content.Intent.EXTRA_RETURN_RESULT, false))
        shadowOf(context.packageManager).removePackage(app.packageName)
        assertFalse(repository.isUninstallable(app))
    }

    @Test fun preferencesSurviveRepositoryRecreationAndPresetChanges() {
        val theme = ThemeRepository(context)
        theme.setDisplayName("  Alex  ")
        theme.setWallpaperStyle("aurora")
        theme.setGridColumns(100)
        theme.setShowClock(false)
        theme.selectPreset("forest")
        val restored = ThemeRepository(context).currentTheme.value
        assertEquals("Alex", restored.displayName)
        assertEquals("aurora", restored.wallpaperStyle)
        assertEquals(8, restored.gridColumns)
        assertEquals("forest", restored.id)
        assertFalse(restored.showClock)
    }
}
