package com.mytvlauncher.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.mytvlauncher.app.data.LauncherSettings
import com.mytvlauncher.app.model.LauncherApp
import com.mytvlauncher.app.model.LiveTvTag
import com.mytvlauncher.app.ui.components.AppCard
import com.mytvlauncher.app.ui.components.ScreenHeader
import com.mytvlauncher.app.ui.components.TvChip
import com.mytvlauncher.app.ui.components.relativeTime
import com.mytvlauncher.app.ui.components.requestFocusWhenReady

/**
 * Screen 7: live-TV and channel apps in one place. Apps are picked up
 * automatically by name (JioTV, Airtel Xstream, Tata Play, ZEE5, news
 * apps...) and can be added or removed from the app options menu.
 */
@Composable
fun LiveTvScreen(
    apps: List<LauncherApp>,
    recents: Map<String, Long>,
    settings: LauncherSettings,
    autoFocus: Boolean,
    onLaunch: (LauncherApp) -> Unit,
    onOptions: (LauncherApp) -> Unit
) {
    val theme = settings.theme
    var tab by rememberSaveable { mutableStateOf<String?>(null) } // null = Recent
    val firstTile = remember { FocusRequester() }

    val liveApps = remember(apps, settings.liveTvAdded, settings.liveTvRemoved) {
        apps.filter { settings.isLiveTv(it) }
    }
    val shown = remember(liveApps, tab, recents) {
        if (tab == null) {
            liveApps.sortedWith(compareByDescending<LauncherApp> { recents[it.packageName] ?: 0L }.thenBy { it.label.lowercase() })
        } else {
            liveApps.filter { it.liveTvTag.name == tab }
        }
    }
    LaunchedEffect(autoFocus, liveApps.isNotEmpty()) {
        if (autoFocus && liveApps.isNotEmpty()) firstTile.requestFocusWhenReady()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ScreenHeader("Live TV", "Channels, news and sports apps", theme, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvChip("Recent", selected = tab == null, theme = theme) { tab = null }
            LiveTvTag.values().forEach { tag ->
                TvChip(tag.label, selected = tab == tag.name, theme = theme) { tab = tag.name }
            }
        }
        Spacer(Modifier.height(14.dp))

        when {
            liveApps.isEmpty() -> EmptyHint(
                "No live TV apps found. Long-press OK on any app and choose Add to Live TV.",
                settings
            )
            shown.isEmpty() -> EmptyHint("Nothing in this category yet.", settings)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp, start = 6.dp, end = 6.dp),
            modifier = Modifier.weight(1f).widthIn(max = 640.dp)
        ) {
            itemsIndexed(shown, key = { _, app -> app.packageName }) { index, app ->
                AppCard(
                    app = app,
                    settings = settings,
                    subtitle = recents[app.packageName]?.let { "Opened " + relativeTime(it).lowercase() },
                    focusRequester = if (index == 0) firstTile else null,
                    onLongClick = { onOptions(app) },
                    onClick = { onLaunch(app) }
                )
            }
        }
    }
}
