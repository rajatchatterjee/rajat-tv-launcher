package com.mytvlauncher.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mytvlauncher.app.data.AppRepository
import com.mytvlauncher.app.data.BulkUninstallController
import com.mytvlauncher.app.data.SettingsRepository
import com.mytvlauncher.app.model.LauncherApp
import com.mytvlauncher.app.ui.components.NavItem
import com.mytvlauncher.app.ui.components.SceneBackground
import com.mytvlauncher.app.ui.components.Sidebar
import com.mytvlauncher.app.ui.components.TopBar
import com.mytvlauncher.app.ui.components.rememberWeather
import com.mytvlauncher.app.ui.screens.AppOptionsDialog
import com.mytvlauncher.app.ui.screens.AppsScreen
import com.mytvlauncher.app.ui.screens.FavouritesScreen
import com.mytvlauncher.app.ui.screens.HomeScreen
import com.mytvlauncher.app.ui.screens.LiveTvScreen
import com.mytvlauncher.app.ui.screens.SettingsPage
import com.mytvlauncher.app.ui.screens.SettingsScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class Destination { HOME, SEARCH, APPS, FAVOURITES, LIVE_TV, SETTINGS }

private val navItems = listOf(
    NavItem("Home", Icons.Filled.Home),
    NavItem("Search", Icons.Filled.Search),
    NavItem("Apps", Icons.Filled.Apps),
    NavItem("Favourites", Icons.Filled.FavoriteBorder),
    NavItem("Live TV", Icons.Filled.LiveTv),
    NavItem("Settings", Icons.Filled.Settings)
)

/**
 * The whole launcher: wallpaper, sidebar, top bar, and whichever screen
 * is selected, plus the app-options dialog on top.
 */
@Composable
fun LauncherRoot(
    appRepository: AppRepository,
    settingsRepository: SettingsRepository,
    uninstaller: BulkUninstallController,
    kidsRepository: com.mytvlauncher.app.data.KidsRepository,
    homeTicks: StateFlow<Int>
) {
    val context = LocalContext.current
    val settings by settingsRepository.state.collectAsState()
    val apps by appRepository.apps.collectAsState()
    val recents by appRepository.recents.collectAsState()
    val homeTick by homeTicks.collectAsState()
    val kids by kidsRepository.state.collectAsState()
    // A PIN pad shown over everything: (title, onDone → accepted?).
    var pinRequest by remember { mutableStateOf<Pair<String, (String) -> Boolean>?>(null) }
    val theme = settings.theme

    var destination by remember { mutableStateOf(Destination.HOME) }
    var selectMode by remember { mutableStateOf(false) }
    var settingsPage by remember { mutableStateOf<SettingsPage?>(null) }
    var optionsFor by remember { mutableStateOf<LauncherApp?>(null) }
    var moveRequest by remember { mutableStateOf<String?>(null) }
    var sidebarFocused by remember { mutableStateOf(false) }

    fun goTo(dest: Destination) {
        destination = dest
        selectMode = false
        settingsPage = null
    }

    // Pressing the remote's Home button while already in the launcher.
    LaunchedEffect(homeTick) { if (homeTick > 0) goTo(Destination.HOME) }

    LaunchedEffect(apps) { settingsRepository.seedFavouritesIfNeeded(apps) }

    // Location for the weather (falls back to Pune if refused).
    var permissionTick by remember { mutableStateOf(0) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionTick++
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    val weather = rememberWeather(permissionTick)

    // "Popular on Netflix / Prime…" rows (TMDB), refreshed when the installed
    // apps change or the 6-hour cache expires.
    val contentRepo = remember { com.mytvlauncher.app.data.ContentRepository(context.applicationContext) }
    val contentRows by contentRepo.rows.collectAsState()
    LaunchedEffect(apps) { if (apps.isNotEmpty()) contentRepo.refreshIfDue(apps.map { it.packageName }.toSet()) }
    var spotlightBackdrop by remember { mutableStateOf<String?>(null) }
    val myList = remember { com.mytvlauncher.app.data.MyListRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val openTitle: (com.mytvlauncher.app.data.ContentItem) -> Unit = { item ->
        val app = appRepository.appFor(item.packageName)
        if (app != null) appRepository.openTitle(app, item.title)
        else appRepository.toastMessage("Open ${item.serviceName.ifBlank { "the app" }} to watch “${item.title}”")
    }
    val playTrailer: (com.mytvlauncher.app.data.ContentItem) -> Unit = { item ->
        scope.launch {
            val key = contentRepo.trailerKey(item)
            if (key != null) appRepository.openYouTube(key) else appRepository.toastMessage("No trailer found for “${item.title}”")
        }
    }

    // Back never leaves the launcher: it steps back toward Home and stops there.
    BackHandler {
        when {
            selectMode -> selectMode = false
            destination == Destination.SETTINGS && settingsPage != null -> settingsPage = null
            destination != Destination.HOME -> goTo(Destination.HOME)
        }
    }

    val appsByPackage = remember(apps) { apps.associateBy { it.packageName } }
    val favouriteApps = remember(apps, settings.favourites) { settings.favourites.mapNotNull { appsByPackage[it] } }
    val recentApps = remember(apps, recents) {
        recents.entries.sortedByDescending { it.value }
            .mapNotNull { (pkg, ts) -> appsByPackage[pkg]?.let { it to ts } }
            .take(8)
    }
    // Decided once per screen change: if the user switched screens from the
    // sidebar, leave focus there; otherwise (Back, Home button, Settings
    // shortcuts) put it on the new screen's first item. Not recomputed when
    // focus later leaves the sidebar, so it never yanks focus mid-navigation.
    val autoFocus = remember(destination, settingsPage, selectMode) { !sidebarFocused }
    val launch: (LauncherApp) -> Unit = { appRepository.launch(it) }
    val openOptions: (LauncherApp) -> Unit = { optionsFor = it }

    Box(Modifier.fillMaxSize()) {
        SceneBackground(theme, settings.backgroundStyle)
        // Full-screen show artwork behind Home, fading in over the wallpaper.
        androidx.compose.animation.AnimatedVisibility(
            visible = destination == Destination.HOME && spotlightBackdrop != null,
            enter = fadeIn(tween(900)),
            exit = fadeOut(tween(500))
        ) {
            com.mytvlauncher.app.ui.components.SpotlightBackdrop(spotlightBackdrop)
        }
        if (settings.livingBackground) {
            com.mytvlauncher.app.ui.components.LivingBackground(
                theme = theme,
                weather = weather,
                overPhoto = destination == Destination.HOME && spotlightBackdrop != null
            )
        }

        if (kids.active) {
            com.mytvlauncher.app.ui.screens.KidsHome(
                kids = kids,
                apps = apps,
                settings = settings,
                onLaunch = { app ->
                    kidsRepository.startSession()
                    appRepository.launch(app)
                },
                onExit = { kidsRepository.setActive(false); goTo(Destination.HOME) }
            )
        } else Row(Modifier.fillMaxSize()) {
            Sidebar(
                theme = theme,
                items = navItems,
                selectedIndex = destination.ordinal,
                onSelect = { goTo(Destination.values()[it]) },
                modifier = Modifier.onFocusChanged { sidebarFocused = it.hasFocus }
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 18.dp, end = 32.dp, top = 18.dp)
            ) {
                TopBar(
                    settings = settings,
                    weather = weather,
                    onSettings = { goTo(Destination.SETTINGS) },
                    modifier = Modifier.align(Alignment.End)
                )
                Spacer(Modifier.height(4.dp))
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "destination",
                    modifier = Modifier.weight(1f)
                ) { dest ->
                    when (dest) {
                        Destination.HOME -> HomeScreen(
                            favourites = favouriteApps,
                            recent = recentApps,
                            contentRows = contentRows,
                            appsByPackage = appsByPackage,
                            showTmdbHint = !contentRepo.hasKey,
                            settings = settings,
                            autoFocus = autoFocus,
                            onLaunch = launch,
                            onOpenTitle = openTitle,
                            onOptions = openOptions,
                            onBackdrop = { spotlightBackdrop = it },
                            contentRepo = contentRepo,
                            myList = myList,
                            onTrailer = playTrailer,
                            onToast = { appRepository.toastMessage(it) }
                        )
                        Destination.SEARCH -> com.mytvlauncher.app.ui.screens.SearchScreen(
                            apps = apps,
                            appsByPackage = appsByPackage,
                            contentRepo = contentRepo,
                            myList = myList,
                            settings = settings,
                            autoFocus = autoFocus,
                            onLaunch = launch,
                            onOpenTitle = openTitle,
                            onOptions = openOptions,
                            onToast = { appRepository.toastMessage(it) }
                        )
                        Destination.APPS -> AppsScreen(
                            apps = apps,
                            settings = settings,
                            selectMode = selectMode,
                            uninstallRunning = uninstaller.isRunning,
                            uninstallRemaining = uninstaller.remaining,
                            autoFocus = autoFocus,
                            onSelectModeChange = { selectMode = it },
                            onUninstall = { uninstaller.start(it) },
                            onLaunch = launch,
                            onOptions = openOptions,
                            onAddApp = { appRepository.openPlayStore() }
                        )
                        Destination.FAVOURITES -> FavouritesScreen(
                            favourites = favouriteApps,
                            settings = settings,
                            autoFocus = autoFocus,
                            moveRequest = moveRequest,
                            onMoveRequestHandled = { moveRequest = null },
                            onReorder = { settingsRepository.setFavourites(it) },
                            onLaunch = launch,
                            onOptions = openOptions
                        )
                        Destination.LIVE_TV -> LiveTvScreen(
                            apps = apps,
                            recents = recents,
                            settings = settings,
                            autoFocus = autoFocus,
                            onLaunch = launch,
                            onOptions = openOptions
                        )
                        Destination.SETTINGS -> SettingsScreen(
                            settings = settings,
                            repo = settingsRepository,
                            page = settingsPage,
                            autoFocus = autoFocus,
                            onPage = { settingsPage = it },
                            onAppManagement = {
                                goTo(Destination.APPS)
                                selectMode = true
                            },
                            kids = kids,
                            kidsRepo = kidsRepository,
                            apps = apps,
                            onShowPinPad = { title, done -> pinRequest = title to done }
                        )
                    }
                }
            }
        }

        pinRequest?.let { req ->
            key(req) {
                com.mytvlauncher.app.ui.screens.PinPad(
                    title = req.first,
                    theme = theme,
                    onDismiss = { pinRequest = null },
                    onDone = { p ->
                        val ok = req.second(p)
                        // A handler may chain to a second pad (change PIN); only close our own.
                        if (ok && pinRequest === req) pinRequest = null
                        ok
                    }
                )
            }
        }

        if (!kids.active) optionsFor?.let { app ->
            val details = remember(app) { appRepository.details(app) }
            AppOptionsDialog(
                app = app,
                details = details,
                settings = settings,
                onDismiss = { optionsFor = null },
                onOpen = {
                    optionsFor = null
                    appRepository.launch(app)
                },
                onToggleFavourite = {
                    settingsRepository.toggleFavourite(app.packageName)
                    optionsFor = null
                },
                onMove = {
                    settingsRepository.addFavourite(app.packageName)
                    optionsFor = null
                    moveRequest = app.packageName
                    goTo(Destination.FAVOURITES)
                },
                onToggleLiveTv = {
                    settingsRepository.setLiveTv(app.packageName, include = !settings.isLiveTv(app))
                    optionsFor = null
                },
                onUninstall = {
                    optionsFor = null
                    uninstaller.start(listOf(app))
                },
                onAppInfo = {
                    optionsFor = null
                    appRepository.openAppInfo(app)
                }
            )
        }
    }
}
