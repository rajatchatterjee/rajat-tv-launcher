package com.mytvlauncher.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.mytvlauncher.app.data.AppRepository
import com.mytvlauncher.app.data.BulkUninstallController
import com.mytvlauncher.app.data.ThemeRepository
import com.mytvlauncher.app.data.WeatherInfo
import com.mytvlauncher.app.data.WeatherRepository
import com.mytvlauncher.app.model.LauncherApp
import com.mytvlauncher.app.model.LauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private enum class NavDestination { HOME, DRAWER }

// Pune, Maharashtra — used only if location access isn't granted or a
// fix isn't available yet, so the weather widget still shows something
// relevant rather than nothing.
private const val FALLBACK_LAT = 18.5204
private const val FALLBACK_LON = 73.8567

/**
 * The whole launcher UI: a persistent left nav rail, a Home screen
 * (greeting, clock, weather, a quick-launch row) and a full-screen App
 * Drawer for the complete grid and bulk uninstall. Background is a
 * soft gradient that shifts with the time of day, layered under
 * whichever theme is selected.
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
    var destination by remember { mutableStateOf(NavDestination.HOME) }

    var weather by remember { mutableStateOf<WeatherInfo?>(null) }
    val weatherRepository = remember { WeatherRepository() }
    val coroutineScope = rememberCoroutineScope()

    fun refreshWeather() {
        coroutineScope.launch {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val (lat, lon) = (if (hasPermission) deviceLocationOrNull(context) else null)
                ?: (FALLBACK_LAT to FALLBACK_LON)
            weather = withContext(Dispatchers.IO) {
                weatherRepository.fetchCurrentWeather(lat, lon)
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Whatever the user chose, re-check and re-fetch: if they granted
        // it, this call now gets a real fix instead of the Pune fallback.
        refreshWeather()
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        refreshWeather()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(timeOfDayBrush(theme))
    ) {
        NavRail(
            theme = theme,
            current = destination,
            onHome = { destination = NavDestination.HOME },
            onApps = { destination = NavDestination.DRAWER },
            onSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) }
        )

        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 6 }) togetherWith
                        (fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { -it / 6 })
                },
                label = "home-drawer"
            ) { dest ->
                when (dest) {
                    NavDestination.HOME -> HomeContent(
                        apps = apps,
                        theme = theme,
                        weather = weather,
                        appRepository = appRepository,
                        onOpenDrawer = { destination = NavDestination.DRAWER }
                    )
                    NavDestination.DRAWER -> AppDrawerScreen(
                        apps = apps,
                        theme = theme,
                        appRepository = appRepository,
                        bulkUninstallController = bulkUninstallController,
                        onBack = { destination = NavDestination.HOME }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavRail(
    theme: LauncherTheme,
    current: NavDestination,
    onHome: () -> Unit,
    onApps: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(76.dp)
            .background(theme.surface.copy(alpha = 0.6f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NavRailItem(
            icon = Icons.Filled.Home,
            selected = current == NavDestination.HOME,
            theme = theme,
            contentDescription = "Home",
            onClick = onHome
        )
        Spacer(modifier = Modifier.height(28.dp))
        NavRailItem(
            icon = Icons.Filled.Apps,
            selected = current == NavDestination.DRAWER,
            theme = theme,
            contentDescription = "All apps",
            onClick = onApps
        )
        Spacer(modifier = Modifier.height(28.dp))
        NavRailItem(
            icon = Icons.Filled.Settings,
            selected = false,
            theme = theme,
            contentDescription = "Settings",
            onClick = onSettings
        )
    }
}

@Composable
private fun NavRailItem(
    icon: ImageVector,
    selected: Boolean,
    theme: LauncherTheme,
    contentDescription: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val highlight = selected || focused
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) theme.accent.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                width = if (highlight) 1.5.dp else 0.dp,
                color = theme.accent,
                shape = RoundedCornerShape(16.dp)
            )
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (highlight) theme.accent else theme.textSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun HomeContent(
    apps: List<LauncherApp>,
    theme: LauncherTheme,
    weather: WeatherInfo?,
    appRepository: AppRepository,
    onOpenDrawer: () -> Unit
) {
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
            WeatherChip(weather = weather, theme = theme)
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                itemsIndexed(quickLaunchApps) { index, app ->
                    StaggeredEntrance(index = index) {
                        AppTile(
                            app = app,
                            theme = theme,
                            selectionMode = false,
                            selectable = true,
                            isSelected = false,
                            sizeDp = 96,
                            onClick = { appRepository.launch(app) }
                        )
                    }
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

/** Fades + slides an item in shortly after composition, staggered by index. */
@Composable
private fun StaggeredEntrance(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60L * index)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 3 }
    ) {
        content()
    }
}

@Composable
private fun WeatherChip(weather: WeatherInfo?, theme: LauncherTheme) {
    if (weather == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(theme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = weatherIconFor(weather.weatherCode),
            contentDescription = null,
            tint = theme.accent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${weather.tempC}°C",
            color = theme.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Maps Open-Meteo's WMO weather codes down to a handful of icon buckets. */
private fun weatherIconFor(code: Int): ImageVector = when (code) {
    0 -> Icons.Filled.WbSunny
    in 1..3 -> Icons.Filled.Cloud
    in 45..48 -> Icons.Filled.Cloud
    in 51..67, in 80..82 -> Icons.Filled.Grain
    in 71..77 -> Icons.Filled.AcUnit
    in 95..99 -> Icons.Filled.Bolt
    else -> Icons.Filled.Cloud
}

/** Best-effort last-known location; null if permission isn't granted or none is cached. */
private fun deviceLocationOrNull(context: android.content.Context): Pair<Double, Double>? {
    return try {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        for (provider in providers) {
            val location = try {
                locationManager.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            }
            if (location != null) return location.latitude to location.longitude
        }
        null
    } catch (e: Exception) {
        null
    }
}

/**
 * A soft vertical gradient from the theme's own background into a hue
 * tied to the time of day — warm at sunrise/sunset, cool at night,
 * a faint accent wash at midday — so the wallpaper feels alive without
 * fighting the chosen theme's palette.
 */
@Composable
private fun timeOfDayBrush(theme: LauncherTheme): Brush {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val daypartTint = when (hour) {
        in 5..7 -> Color(0xFFFFB37B)   // sunrise
        in 8..16 -> theme.accent        // midday — lean on the theme's own accent
        in 17..19 -> Color(0xFFFF7A59)  // sunset
        else -> Color(0xFF0B1030)       // night
    }
    val blended = lerp(theme.background, daypartTint, 0.16f)
    return Brush.verticalGradient(listOf(theme.background, blended))
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
            itemsIndexed(apps) { index, app ->
                val canUninstall = appRepository.isUninstallable(app)
                StaggeredEntrance(index = index % 12) {
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
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "tile-scale")
    val elevation by animateFloatAsState(if (focused) 18f else 3f, label = "tile-elevation")
    val corner = RoundedCornerShape(theme.cardCornerRadiusDp.dp)

    val sizeModifier = if (sizeDp != null) Modifier.size(sizeDp.dp) else Modifier.aspectRatio(1f)
    val badgeSize = if (sizeDp != null) (sizeDp * 0.62f).dp else 52.dp
    val iconSize = if (sizeDp != null) (sizeDp * 0.42f).dp else 34.dp

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
            Box(contentAlignment = Alignment.Center) {
                // Soft glow behind the icon — makes it "pop" off the tile
                // rather than sitting flat against the surface.
                Box(
                    modifier = Modifier
                        .size(badgeSize * 1.15f)
                        .blur(18.dp)
                        .clip(CircleShape)
                        .background(theme.accent.copy(alpha = if (focused) 0.45f else 0.22f))
                )
                AppIcon(drawable = app.icon, modifier = Modifier.size(iconSize))
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
