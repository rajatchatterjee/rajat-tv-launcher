package com.mytvlauncher.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvlauncher.app.data.LauncherSettings
import com.mytvlauncher.app.model.LauncherApp
import com.mytvlauncher.app.ui.components.AppCard
import com.mytvlauncher.app.ui.components.SectionTitle
import com.mytvlauncher.app.ui.components.glass
import com.mytvlauncher.app.ui.components.relativeTime
import com.mytvlauncher.app.ui.components.requestFocusWhenReady
import java.util.Calendar

/**
 * Screen 1 of the mockup: greeting, Favourite Apps, and a row of recently
 * opened apps where the mockup has "Continue Watching".
 *
 * (Android doesn't let a third-party launcher read other apps' Watch Next
 * shows — that needs a system-only permission — so the honest equivalent
 * here is the apps you were last in, with their banners.)
 */
@Composable
fun HomeScreen(
    favourites: List<LauncherApp>,
    recent: List<Pair<LauncherApp, Long>>,
    settings: LauncherSettings,
    autoFocus: Boolean,
    onLaunch: (LauncherApp) -> Unit,
    onOptions: (LauncherApp) -> Unit
) {
    val theme = settings.theme
    val firstRequester = remember { FocusRequester() }
    val hasContent = favourites.isNotEmpty() || recent.isNotEmpty()
    LaunchedEffect(hasContent) {
        if (autoFocus && hasContent) firstRequester.requestFocusWhenReady()
    }

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Text(greeting(), color = theme.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text("Relax. Watch. Enjoy.", color = theme.textSecondary, fontSize = 14.sp)

        Spacer(Modifier.height(26.dp))
        SectionTitle("Favourite Apps", theme)
        if (favourites.isEmpty()) {
            EmptyHint(
                "No favourites yet. Long-press OK on any app (or press Menu) and choose Add to Favourites.",
                settings
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
            ) {
                itemsIndexed(favourites, key = { _, app -> app.packageName }) { index, app ->
                    AppCard(
                        app = app,
                        settings = settings,
                        modifier = Modifier.width(124.dp),
                        focusRequester = if (index == 0) firstRequester else null,
                        onLongClick = { onOptions(app) },
                        onClick = { onLaunch(app) }
                    )
                }
            }
        }

        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SectionTitle("Recently Opened", theme)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
            ) {
                itemsIndexed(recent, key = { _, pair -> "recent-" + pair.first.packageName }) { index, (app, ts) ->
                    AppCard(
                        app = app,
                        settings = settings.copy(showAppNames = true),
                        modifier = Modifier.width(168.dp),
                        subtitle = relativeTime(ts),
                        focusRequester = if (favourites.isEmpty() && index == 0) firstRequester else null,
                        onLongClick = { onOptions(app) },
                        onClick = { onLaunch(app) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmptyHint(text: String, settings: LauncherSettings, modifier: Modifier = Modifier) {
    Text(
        text,
        color = settings.theme.textSecondary,
        fontSize = 13.sp,
        modifier = modifier
            .glass(settings.theme, alpha = 0.45f)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

private fun greeting(): String {
    val part = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..21 -> "Good Evening"
        else -> "Good Night"
    }
    return "$part, Rajat"
}
