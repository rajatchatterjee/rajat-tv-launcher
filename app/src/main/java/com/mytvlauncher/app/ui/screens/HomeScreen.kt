package com.mytvlauncher.app.ui.screens

import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.mytvlauncher.app.data.AppRepository
import com.mytvlauncher.app.data.BulkUninstallController
import com.mytvlauncher.app.data.ThemeRepository
import com.mytvlauncher.app.model.LauncherApp
import com.mytvlauncher.app.model.LauncherTheme
import java.text.SimpleDateFormat
import java.util.*

/**
 * Two screens live here: a clean Home (greeting, clock, a quick-launch
 * row, and a button into the full app list) and a separate App Drawer
 * (the full grid, with selection mode for bulk uninstall). Switching
 * between them is just a boolean — no second Activity needed, so the
 * theme and app list stay shared.
 */
@Composable
fun HomeScreen(
    appRepository: AppRepository,
    themeRepository: ThemeRepository,
    bulkUninstallController: BulkUninstallController
) {
    val theme by themeRepository.currentTheme.collectAsState()
    val apps = remember { appRepository.loadLauncherApps() }
    var drawerOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        AnimatedContent(targetState = drawerOpen, label = "home-drawer") { showDrawer ->
            if (showDrawer) {
                AppDrawerScreen(
                    apps = apps,
                    theme = theme,
                    appRepository = appRepository,
                    bulkUninstallController = bulkUninstallController,
                    onBack = { drawerOpen = false }
                )
            } else {
                HomeContent(
                    apps = apps,
                    theme = theme,
                    appRepository = appRepository,
                    onOpenDrawer = { drawerOpen = true }
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    apps: List<LauncherApp>,
    theme: LauncherTheme,
    appRepository: AppRepository,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val quickLaunchApps = remember(apps) { apps.take(8) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            if (theme.showClock) Clock(theme = theme) else Spacer(modifier = Modifier.width(1.dp))

            IconButton(onClick = {
                context.startActivity(Intent(context, SettingsActivity::class.java))
            }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Theme settings",
                    tint = theme.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (quickLaunchApps.isNotEmpty()) {
            Text(
                text = "Quick launch",
                color = theme.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(quickLaunchApps) { app ->
                    AppTile(
                        app = app,
                        theme = theme,
                        selectionMode = false,
                        selectable = true,
                        isSelected = false,
                        sizeDp = 92,
                        onClick = { appRepository.launch(app) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }

        Button(
            onClick = onOpenDrawer,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("All apps")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Rajat's TV Launcher",
            color = theme.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun AppDrawerScreen(
    apps: List<LauncherApp>,
    theme: LauncherTheme,
    appRepository: AppRepository,
    bulkUninstallController: BulkUninstallController,
    onBack: () -> Unit
) {
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<LauncherApp>() }

    fun exitSelectionMode() {
        selectionMode = false
        selected.clear()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (selectionMode) exitSelectionMode() else onBack() }) {
                    Icon(
                        imageVector = if (selectionMode) Icons.Filled.Close else Icons.Filled.ArrowBack,
                        contentDescription = if (selectionMode) "Cancel selection" else "Back to home",
                        tint = theme.textSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (!selectionMode) "All apps"
                    else if (selected.isEmpty()) "Select apps to uninstall"
                    else "${selected.size} selected",
                    color = theme.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (!selectionMode) {
                IconButton(onClick = { selectionMode = true }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Manage apps / bulk uninstall",
                        tint = theme.textSecondary
                    )
                }
            }
        }

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

@Composable
private fun Clock(theme: LauncherTheme) {
    var time by remember { mutableStateOf(currentTimeString()) }

    LaunchedEffect(Unit) {
        while (true) {
            time = currentTimeString()
            kotlinx.coroutines.delay(1000L * 15)
        }
    }

    Column {
        Text(
            text = greeting(),
            color = theme.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = time,
                color = theme.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = currentDateString(),
                color = theme.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
}

/** A time-of-day greeting personalized for the TV's owner. */
private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val timeOfDay = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
    return "$timeOfDay, Rajat"
}

private fun currentTimeString(): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

private fun currentDateString(): String =
    SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())

@Composable
private fun AppTile(
    app: LauncherApp,
    theme: LauncherTheme,
    selectionMode: Boolean,
    selectable: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    sizeDp: Int? = null
) {
    var focused by remember { mutableStateOf(false) }
    val dimmed = selectionMode && !selectable
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "tile-scale")
    val elevation by animateFloatAsState(if (focused) 14f else 2f, label = "tile-elevation")
    val corner = RoundedCornerShape(theme.cardCornerRadiusDp.dp)

    val sizeModifier = if (sizeDp != null) Modifier.size(sizeDp.dp) else Modifier.aspectRatio(1f)

    Box(modifier = sizeModifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(elevation = elevation.dp, shape = corner, clip = false)
                .clip(corner)
                .background(theme.surface)
                .border(
                    width = if (isSelected || focused) 2.5.dp else 0.dp,
                    color = theme.accent,
                    shape = corner
                )
                .focusable()
                .onFocusChanged { focused = it.isFocused }
                .clickable(enabled = !selectionMode || selectable) { onClick() }
                .padding(10.dp)
                .alpha(if (dimmed) 0.35f else 1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (sizeDp != null) (sizeDp * 0.56f).dp else 48.dp)
                    .clip(CircleShape)
                    .background(theme.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    drawable = app.icon,
                    modifier = Modifier.size(if (sizeDp != null) (sizeDp * 0.36f).dp else 30.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.label,
                color = theme.textPrimary,
                fontSize = 12.sp,
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
