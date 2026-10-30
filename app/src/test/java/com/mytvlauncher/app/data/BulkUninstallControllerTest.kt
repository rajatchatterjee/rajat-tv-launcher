package com.mytvlauncher.app.data

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import com.mytvlauncher.app.model.LauncherApp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BulkUninstallControllerTest {
    private lateinit var activity: ComponentActivity
    private lateinit var controller: BulkUninstallController

    @Before fun setup() {
        val lifecycle = Robolectric.buildActivity(ComponentActivity::class.java).create()
        activity = lifecycle.get()
        controller = BulkUninstallController(activity, AppRepository(activity))
        lifecycle.start().resume()
    }

    private fun install(pkg: String, flags: Int = 0): LauncherApp {
        shadowOf(activity.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply { packageName = pkg; this.flags = flags }
        })
        return LauncherApp(pkg, "Main", pkg, ColorDrawable(), true)
    }

    @Test fun cancellingFirstSystemDialogStopsTheEntireQueue() {
        controller.start(listOf(install("test.first"), install("test.second")))
        val request = shadowOf(activity).nextStartedActivityForResult
        assertEquals("package:test.first", request.intent.data.toString())
        activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_CANCELED, null)
        assertFalse(controller.isRunning)
        assertEquals(0, controller.remaining)
        assertNull(shadowOf(activity).nextStartedActivityForResult)
    }

    @Test fun successfulRemovalAdvancesOnceAndRechecksProtection() {
        val first = install("test.first")
        val second = install("test.second")
        controller.start(listOf(first, first, second))
        val request = shadowOf(activity).nextStartedActivityForResult
        assertEquals(2, controller.remaining)
        assertNull(shadowOf(activity).nextStartedActivityForResult)
        shadowOf(activity.packageManager).removePackage(first.packageName)
        install(second.packageName, ApplicationInfo.FLAG_SYSTEM)
        activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK, null)
        assertFalse(controller.isRunning)
        assertNull(shadowOf(activity).nextStartedActivityForResult)
    }

    @Test fun successfulRemovalLaunchesNextAndStopPreventsFurtherRequests() {
        val first = install("test.first")
        val second = install("test.second")
        controller.start(listOf(first, second, install("test.third")))
        var request = shadowOf(activity).nextStartedActivityForResult
        controller.start(listOf(install("test.reentrant")))
        assertNull(shadowOf(activity).nextStartedActivityForResult)
        shadowOf(activity.packageManager).removePackage(first.packageName)
        activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK, null)
        request = shadowOf(activity).nextStartedActivityForResult
        assertEquals("package:test.second", request.intent.data.toString())
        controller.cancel()
        shadowOf(activity.packageManager).removePackage(second.packageName)
        activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK, null)
        assertFalse(controller.isRunning)
        assertNull(shadowOf(activity).nextStartedActivityForResult)
    }
}
