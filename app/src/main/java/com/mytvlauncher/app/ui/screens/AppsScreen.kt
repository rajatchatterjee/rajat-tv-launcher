package com.mytvlauncher.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvlauncher.app.data.LauncherSettings
import com.mytvlauncher.app.model.AppCategory
import com.mytvlauncher.app.model.LauncherApp
import com.mytvlauncher.app.ui.components.AddAppCard
import com.mytvlauncher.app.ui.components.AppCard
import com.mytvlauncher.app.ui.components.ButtonStyle
import com.mytvlauncher.app.ui.components.ScreenHeader
import com.mytvlauncher.app.ui.components.TvButton
import com.mytvlauncher.app.ui.components.TvChip
import com.mytvlauncher.app.ui.components.requestFocusWhenReady

/**
 * Screens 2 and 4 of the mockup: the full app grid with category chips,
 * and — when [selectMode] is on — "Select Apps" with bulk delete.
 */
@Composable
fun AppsScreen(
    apps: List<LauncherApp>,
    settings: LauncherSettings,
    selectMode: Boolean,
    uninstallRunning: Boolean,
    uninstallRemaining: Int,
    autoFocus: Boolean,
    onSelectModeChange: (Boolean) -> Unit,
    onUninstall: (List<LauncherApp>) -> Unit,
    onLaunch: (LauncherApp) -> Unit,
    onOptions: (LauncherApp) -> Unit,
    onAddApp: () -> Unit
) {
    val theme = settings.theme
    var filter by rememberSaveable { mutableStateOf<String?>(null) } // null = All
    val selected = remember { mutableStateListOf<String>() }
    val firstTile = remember { FocusRequester() }
    val cancelRequester = remember { FocusRequester() }

    LaunchedEffect(selectMode) {
        if (!selectMode) selected.clear()
    }
    LaunchedEffect(autoFocus, selectMode, apps.isNotEmpty()) {
        if (autoFocus && apps.isNotEmpty()) firstTile.requestFocusWhenReady()
    }

    val categories = remember(apps) {
        AppCategory.values().filter { cat -> apps.any { it.category == cat } }
    }
    val shown = remember(apps, filter, selectMode) {
        if (selectMode || filter == null) apps else apps.filter { it.category.name == filter }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (selectMode) {
                ScreenHeader("Select Apps", "Choose one or more apps to perform actions", theme, Modifier.weight(1f))
                Text(
                    when {
                        uninstallRunning -> "Confirming… $uninstallRemaining left"
                        else -> "${selected.size} selected"
                    },
                    color = theme.textSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.width(14.dp))
                TvButton(
                    text = if (selected.isEmpty()) "Delete" else "Delete (${selected.size})",
                    theme = theme,
                    icon = Icons.Filled.Delete,
                    style = ButtonStyle.DANGER,
                    enabled = selected.isNotEmpty() && !uninstallRunning
                ) {
                    val chosen = apps.filter { it.packageName in selected }
                    onUninstall(chosen)
                    onSelectModeChange(false)
                }
                Spacer(Modifier.width(10.dp))
                TvButton("Cancel", theme, focusRequester = cancelRequester) { onSelectModeChange(false) }
            } else {
                Text("All Apps", color = theme.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvChip("All", selected = filter == null, theme = theme) { filter = null }
                    categories.forEach { cat ->
                        TvChip(cat.label, selected = filter == cat.name, theme = theme) { filter = cat.name }
                    }
                }
                Spacer(Modifier.width(14.dp))
                TvButton("Select", theme, icon = Icons.Filled.CheckBox) { onSelectModeChange(true) }
            }
        }

        if (selectMode) {
            Text(
                "System apps are dimmed — Android doesn't allow removing them. You'll confirm each uninstall once.",
                color = theme.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        if (shown.isEmpty() && apps.isNotEmpty()) {
            EmptyHint("No apps in this category.", settings)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(settings.gridColumns),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp, start = 4.dp, end = 4.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(shown, key = { _, app -> app.packageName }) { index, app ->
                val canRemove = app.isUserInstalled
                AppCard(
                    app = app,
                    settings = settings,
                    selected = if (selectMode) app.packageName in selected else null,
                    dimmed = selectMode && !canRemove,
                    focusRequester = if (index == 0) firstTile else null,
                    onLongClick = if (selectMode) null else ({ onOptions(app) }),
                    onClick = {
                        when {
                            !selectMode -> onLaunch(app)
                            app.packageName in selected -> selected.remove(app.packageName)
                            canRemove -> selected.add(app.packageName)
                        }
                    }
                )
            }
            if (!selectMode) {
                item(key = "add-app") { AddAppCard(settings, onClick = onAddApp) }
            }
        }
    }
}
