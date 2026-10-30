package com.mytvlauncher.app.ui.screens

import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvlauncher.app.data.AppRepository
import com.mytvlauncher.app.data.BulkUninstallController
import com.mytvlauncher.app.data.ThemeRepository
import com.mytvlauncher.app.model.LauncherApp
import com.mytvlauncher.app.model.LauncherTheme
import java.text.SimpleDateFormat
import java.util.*

/**
 * The home screen: themed background, optional clock, and a grid of
 * every launchable app (including sideloaded ones). A "Manage apps"
 * button enters selection mode, where tapping tiles checks them off
 * instead of launching them; "Uninstall" then walks the system
 * confirmation dialog once per selected app (see BulkUninstallController
 * for why it can't be fully silent).
 */
@Composable
fun HomeScreen(
    appRepository: AppRepository,
    themeRepository: ThemeRepository,
    bulkUninstallController: BulkUninstallController
) {
    val context = LocalContext.current
    val theme by themeRepository.currentTheme.collectAsState()
    val apps = remember { appRepository.loadLauncherApps() }

    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<LauncherApp>() }

    fun exitSelectionMode() {
        selectionMode = false
        selected.clear()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {

            TopBar(
                theme = theme,
                selectionMode = selectionMode,
                selectedCount = selected.size,
                onSettingsClick = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                },
                onManageAppsClick = { selectionMode = true },
                onCancelSelection = { exitSelectionMode() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(theme.gridColumns),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(apps) { app ->
                    val canUninstall = appRepository.isUninstallable(app)
                    AppTile(
                        app = app,
                        theme = theme,
                        selectionMode = selectionMode,
                        selectable = canUninstall,
                        isSelected = selected.contains(app),
                        onClick = {
                            when {
                                !selectionMode -> appRepository.launch(app)
                                canUninstall && selected.contains(app) -> selected.remove(app)
                                canUninstall -> selected.add(app)
                            }
                        }
                    )
                }
            }

            if (selectionMode) {
                BulkActionBar(
                    theme = theme,
                    selectedCount = selected.size,
                    isRunning = bulkUninstallController.isRunning,
                    remaining = bulkUninstallController.remaining,
                    onUninstallClick = {
                        val toRemove = selected.toList()
                        bulkUninstallController.start(toRemove)
                        exitSelectionMode()
                    }
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    theme: LauncherTheme,
    selectionMode: Boolean,
    selectedCount: Int,
    onSettingsClick: () -> Unit,
    onManageAppsClick: () -> Unit,
    onCancelSelection: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Text(
                text = if (selectedCount == 0) "Select apps to uninstall" else "$selectedCount selected",
                color = theme.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        } else if (theme.showClock) {
            Clock(theme = theme)
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                IconButton(onClick = onCancelSelection) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cancel selection",
                        tint = theme.textSecondary
                    )
                }
            } else {
                IconButton(onClick = onManageAppsClick) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Manage apps / bulk uninstall",
                        tint = theme.textSecondary
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Theme settings",
                        tint = theme.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun Clock(theme: LauncherTheme) {
    var time by remember { mutableStateOf(currentTimeString()) }

    LaunchedEffect(Unit) {
        while (true) {
            time = currentTimeString()
            kotlinx.coroutines.delay(1000L * 15)
        }
    }

    Text(
        text = time,
        color = theme.textPrimary,
        fontSize = 28.sp,
        fontWeight = FontWeight.Medium
    )
}

private fun currentTimeString(): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

@Composable
private fun AppTile(
    app: LauncherApp,
    theme: LauncherTheme,
    selectionMode: Boolean,
    selectable: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val dimmed = selectionMode && !selectable

    Box(modifier = Modifier.aspectRatio(1f)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(theme.cardCornerRadiusDp.dp))
                .background(theme.surface)
                .border(
                    width = if (isSelected) 3.dp else if (focused) 3.dp else 0.dp,
                    color = if (isSelected) theme.accent else theme.accent,
                    shape = RoundedCornerShape(theme.cardCornerRadiusDp.dp)
                )
                .clickable(enabled = !selectionMode || selectable) { onClick() }
                .padding(12.dp)
                .alpha(if (dimmed) 0.35f else 1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIcon(drawable = app.icon, modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.label,
                color = theme.textPrimary,
                fontSize = 13.sp,
                maxLines = 1
            )
        }

        if (selectionMode && selectable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) theme.accent else theme.background)
                    .border(1.dp, theme.accent, CircleShape)
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BulkActionBar(
    theme: LauncherTheme,
    selectedCount: Int,
    isRunning: Boolean,
    remaining: Int,
    onUninstallClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isRunning) "Confirming uninstalls… $remaining left" else "You'll confirm each uninstall once",
            color = theme.textSecondary,
            fontSize = 13.sp
        )
        Button(
            onClick = onUninstallClick,
            enabled = selectedCount > 0 && !isRunning
        ) {
            Text(if (selectedCount == 0) "Uninstall" else "Uninstall ($selectedCount)")
        }
    }
}

@Composable
private fun AppIcon(drawable: Drawable, modifier: Modifier = Modifier) {
    val bitmap = remember(drawable) { drawable.toBitmap().asImageBitmap() }
    Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
}
