package com.mytvlauncher.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.mytvlauncher.app.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManageAppsScreenTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Before fun installLongAppList() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE).edit().clear().commit()
        val pm = shadowOf(context.packageManager)
        repeat(60) { index ->
            val pkg = "test.app$index"
            val info = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = "App %02d".format(index) }
            pm.installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = info })
            pm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
                ResolveInfo().apply {
                    activityInfo = ActivityInfo().apply {
                        packageName = pkg; name = "$pkg.Main"; applicationInfo = info
                        nonLocalizedLabel = info.nonLocalizedLabel; exported = true; enabled = true
                    }
                })
        }
    }

    @Test fun uninstallActionStaysVisibleAfterScrollingToLastAppAndReviewCanBeCancelled() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Manage Apps").performClick()
            compose.onNodeWithText("Uninstall (0)").assertIsNotEnabled()
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(59)
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithText("App 59").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.onNodeWithText("App 59").performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onRoot().performKeyInput { pressKey(Key.Menu) }
            compose.onNodeWithText("Uninstall (1)").assertIsFocused()
            compose.onNodeWithText("Uninstall (1)").assertIsDisplayed().performClick()
            compose.onNodeWithText("Remove 1 apps?").assertIsDisplayed()
            compose.onNodeWithText("Keep apps").performClick()
            compose.onNodeWithText("Uninstall (1)").assertIsDisplayed()
            compose.onNodeWithText("Clear selection").performClick()
            compose.onNodeWithText("Uninstall (0)").assertIsNotEnabled()
        }
    }
}
