package com.mytvlauncher.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.mytvlauncher.app.data.*
import com.mytvlauncher.app.model.AppCategory
import com.mytvlauncher.app.model.AppDetails
import com.mytvlauncher.app.model.LauncherApp
import com.mytvlauncher.app.model.LiveTvTag
import com.mytvlauncher.app.ui.components.LivingBackground
import com.mytvlauncher.app.ui.components.NavItem
import com.mytvlauncher.app.ui.components.SceneBackground
import com.mytvlauncher.app.ui.components.Sidebar
import com.mytvlauncher.app.ui.components.SpotlightBackdrop
import com.mytvlauncher.app.ui.components.TopBar
import com.mytvlauncher.app.ui.screens.*
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every launcher screen from the real composables at 1920×1080 and
 * saves PNGs to app/build/screenshots (CI publishes them to the
 * "screenshots" pre-release). Apps and show art are generated samples —
 * no real logos or posters; on the TV those come from the installed apps
 * and TMDB.
 *
 * Run: gradle testDebugUnitTest --tests '*ScreenshotTour*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class ScreenshotTour {

    @get:Rule val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var contentRepo: ContentRepository
    private lateinit var myList: MyListRepository
    private lateinit var kidsRepo: KidsRepository
    private lateinit var companion: CompanionServer
    private lateinit var apps: List<LauncherApp>

    private val weather = WeatherInfo(tempC = 27, weatherCode = 2, isDay = false)

    private val nav = listOf(
        NavItem("Home", Icons.Filled.Home),
        NavItem("Search", Icons.Filled.Search),
        NavItem("Apps", Icons.Filled.Apps),
        NavItem("Favourites", Icons.Filled.FavoriteBorder),
        NavItem("Live TV", Icons.Filled.LiveTv),
        NavItem("Settings", Icons.Filled.Settings)
    )

    // ---- Sample data -------------------------------------------------------

    private data class Spec(val label: String, val pkg: String, val color: Int, val cat: AppCategory, val live: Boolean = false, val tag: LiveTvTag = LiveTvTag.ENTERTAINMENT)

    private val specs = listOf(
        Spec("YouTube", "com.google.android.youtube.tv", 0xFFE53935.toInt(), AppCategory.VIDEO),
        Spec("Netflix", "com.netflix.ninja", 0xFFB71C1C.toInt(), AppCategory.VIDEO),
        Spec("Prime Video", "com.amazon.amazonvideo.livingroom", 0xFF1E88E5.toInt(), AppCategory.VIDEO),
        Spec("JioHotstar", "in.startv.hotstar.dplus.tv", 0xFF3949AB.toInt(), AppCategory.VIDEO),
        Spec("Spotify", "com.spotify.tv.android", 0xFF2E7D32.toInt(), AppCategory.MUSIC),
        Spec("JioCinema", "com.jio.media.ondemand", 0xFF8E24AA.toInt(), AppCategory.VIDEO),
        Spec("SonyLIV", "com.sonyliv", 0xFF6D4C41.toInt(), AppCategory.VIDEO, live = true, tag = LiveTvTag.SPORTS),
        Spec("ZEE5", "com.graymatrix.did", 0xFF5E35B1.toInt(), AppCategory.VIDEO, live = true),
        Spec("JioTV", "com.jio.jioplay.tv", 0xFFD81B60.toInt(), AppCategory.VIDEO, live = true),
        Spec("Tata Play", "com.ryzmedia.tatasky", 0xFF00897B.toInt(), AppCategory.VIDEO, live = true),
        Spec("News Live", "in.news.live.tv", 0xFFF4511E.toInt(), AppCategory.VIDEO, live = true, tag = LiveTvTag.NEWS),
        Spec("Hridu Math", "com.rajat.hridumath", 0xFFFFB300.toInt(), AppCategory.GAMES),
        Spec("Gaana", "com.gaana", 0xFFC62828.toInt(), AppCategory.MUSIC),
        Spec("Plex", "com.plexapp.android", 0xFFF9A825.toInt(), AppCategory.VIDEO),
        Spec("VLC", "org.videolan.vlc", 0xFFEF6C00.toInt(), AppCategory.VIDEO),
        Spec("Downloader", "com.esaba.downloader", 0xFFFB8C00.toInt(), AppCategory.TOOLS),
        Spec("File Manager", "com.tv.filemanager", 0xFF546E7A.toInt(), AppCategory.TOOLS),
        Spec("Chess", "com.chess.tv", 0xFF558B2F.toInt(), AppCategory.GAMES),
        Spec("Weather", "com.tv.weather", 0xFF039BE5.toInt(), AppCategory.OTHER),
        Spec("Photos", "com.tv.photos", 0xFF00ACC1.toInt(), AppCategory.OTHER)
    )

    private fun initials(label: String) = label.split(' ').mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")

    private fun icon(s: Spec): Bitmap {
        val b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = s.color
        c.drawRoundRect(0f, 0f, 192f, 192f, 44f, 44f, p)
        p.color = 0xFFFFFFFF.toInt(); p.textSize = 78f; p.typeface = Typeface.DEFAULT_BOLD; p.textAlign = Paint.Align.CENTER
        c.drawText(initials(s.label), 96f, 124f, p)
        return b
    }

    private fun banner(s: Spec): Bitmap {
        val w = 640; val h = 360
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), s.color, darker(s.color), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null; p.color = 0xFFFFFFFF.toInt(); p.textSize = 64f; p.typeface = Typeface.DEFAULT_BOLD; p.textAlign = Paint.Align.CENTER
        c.drawText(s.label, w / 2f, h / 2f + 22f, p)
        return b
    }

    private fun darker(c: Int): Int {
        val hsv = FloatArray(3); android.graphics.Color.colorToHSV(c, hsv); hsv[2] *= 0.45f
        return android.graphics.Color.HSVToColor(hsv)
    }

    /** Abstract "key art": layered gradients + glows. Never a real poster. */
    private fun art(seed: Int, w: Int, h: Int): Bitmap {
        val palettes = listOf(
            intArrayOf(0xFF1B0F2E.toInt(), 0xFFD1495B.toInt(), 0xFFF5A65B.toInt()),
            intArrayOf(0xFF0D2B45.toInt(), 0xFF1F77A8.toInt(), 0xFFE8C07D.toInt()),
            intArrayOf(0xFF101A3A.toInt(), 0xFF3B4FD8.toInt(), 0xFF9BE7FF.toInt()),
            intArrayOf(0xFF0C2A1F.toInt(), 0xFF2F8F5B.toInt(), 0xFFD7F0A2.toInt()),
            intArrayOf(0xFF2A0E0E.toInt(), 0xFF9C2A2A.toInt(), 0xFFFFC46B.toInt()),
            intArrayOf(0xFF1A1A2E.toInt(), 0xFF7F5AF0.toInt(), 0xFF2CB1BC.toInt())
        )
        val pal = palettes[seed % palettes.size]
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, h.toFloat(), w.toFloat(), 0f, pal[0], pal[1], Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        val r = java.util.Random(seed.toLong())
        repeat(3) {
            val cx = w * (0.4f + r.nextFloat() * 0.6f); val cy = h * r.nextFloat(); val rad = h * (0.3f + r.nextFloat() * 0.5f)
            p.shader = RadialGradient(cx, cy, rad, (pal[2] and 0x00FFFFFF) or 0x99000000.toInt(), 0, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, rad, p)
        }
        p.shader = null; p.color = 0x55000000
        val path = android.graphics.Path().apply {
            moveTo(0f, h.toFloat())
            var x = 0f
            while (x <= w) { lineTo(x, h * (0.72f + 0.12f * r.nextFloat())); x += w / 12f }
            lineTo(w.toFloat(), h.toFloat()); close()
        }
        c.drawPath(path, p)
        return b
    }

    private val shows = listOf(
        "The Monsoon Files" to "A Mumbai detective chases a pattern hidden in the city's floods.",
        "Desert Signal" to "A radio operator in Kutch picks up a broadcast from the future.",
        "Harbour Lights" to "Three siblings inherit a failing ferry company in Kochi.",
        "Northern Run" to "An ultra-marathon across Ladakh turns into a rescue.",
        "Paper Kites" to "A coming-of-age story set over one Makar Sankranti.",
        "Signal Lost" to "A space-station crew loses contact with Earth for 40 days.",
        "Chai & Code" to "Startup founders in Pune bet everything on one app.",
        "The Last Tiffin" to "A dabbawala uncovers a secret in a misdelivered lunch."
    )

    private fun item(i: Int, svc: String, pkg: String) = ContentItem(
        id = "tv-${1000 + i}", title = shows[i % shows.size].first, overview = shows[i % shows.size].second,
        year = (2023 + i % 3).toString(), rating = 7.0 + (i % 20) / 10.0,
        backdropUrl = "fake://art/$i/w1280", posterUrl = "fake://art/$i/w342",
        isTv = i % 2 == 0, serviceName = svc, packageName = pkg
    )

    private val rows by lazy {
        listOf(
            ContentRow("New on your apps", "", "", (0..5).map { item(it + 3, listOf("Netflix", "Prime Video", "JioHotstar")[it % 3], listOf("com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "in.startv.hotstar.dplus.tv")[it % 3]) }),
            ContentRow("Popular on Netflix", "Netflix", "com.netflix.ninja", (0..7).map { item(it, "Netflix", "com.netflix.ninja") }),
            ContentRow("Popular on Prime Video", "Prime Video", "com.amazon.amazonvideo.livingroom", (0..7).map { item(it + 2, "Prime Video", "com.amazon.amazonvideo.livingroom") })
        )
    }

    /** Coil loader that turns fake://art/<n>/… into generated art, synchronously. */
    private fun installFakeImages() {
        val loader = ImageLoader.Builder(ctx)
            .components {
                add(Fetcher.Factory<String> { data, options, _ ->
                    if (!data.startsWith("fake://art/")) null else Fetcher {
                        val n = data.removePrefix("fake://art/").substringBefore('/').toInt()
                        val poster = data.contains("w342")
                        val bmp = if (poster) art(n, 342, 513) else art(n, 1280, 720)
                        DrawableResult(BitmapDrawable(options.context.resources, bmp), false, DataSource.MEMORY) as FetchResult
                    }
                })
            }
            .dispatcher(Dispatchers.Unconfined)
            .allowHardware(false)
            .crossfade(false)
            .build()
        Coil.setImageLoader(loader)
    }

    @Before
    fun setUp() {
        installFakeImages()
        apps = specs.map { s ->
            LauncherApp(
                packageName = s.pkg, activityName = "${s.pkg}.Main", label = s.label,
                icon = icon(s).asImageBitmap(), banner = banner(s).asImageBitmap(),
                isUserInstalled = true, category = s.cat, isLiveTvCandidate = s.live, liveTvTag = s.tag
            )
        }
        settingsRepo = SettingsRepository(ctx)
        settingsRepo.setFavourites(listOf("com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "in.startv.hotstar.dplus.tv", "com.spotify.tv.android", "com.rajat.hridumath"))
        contentRepo = ContentRepository(ctx)
        myList = MyListRepository(ctx)
        if (myList.items.value.isEmpty()) {
            myList.toggle(item(5, "Netflix", "com.netflix.ninja"))
            myList.toggle(item(6, "Prime Video", "com.amazon.amazonvideo.livingroom"))
            myList.toggle(item(1, "JioHotstar", "in.startv.hotstar.dplus.tv"))
        }
        kidsRepo = KidsRepository(ctx)
        kidsRepo.setPin("1234")
        kidsRepo.setLimit(90)
        if (kidsRepo.state.value.allowed.isEmpty()) listOf("com.google.android.youtube.tv", "com.rajat.hridumath", "com.chess.tv", "com.spotify.tv.android").forEach { kidsRepo.toggleAllowed(it) }
        val appRepo = AppRepository(ctx)
        companion = CompanionServer(ctx, appRepo, contentRepo, myList)
    }

    private val recents get() = mapOf(
        "com.netflix.ninja" to System.currentTimeMillis() - 20 * 60_000L,
        "com.google.android.youtube.tv" to System.currentTimeMillis() - 3 * 3600_000L,
        "com.jio.jioplay.tv" to System.currentTimeMillis() - 26 * 3600_000L,
        "com.rajat.hridumath" to System.currentTimeMillis() - 50 * 3600_000L
    )

    // ---- Frame: same layout as LauncherRoot ---------------------------------

    @Composable
    private fun Frame(navIndex: Int, backdrop: String? = null, content: @Composable () -> Unit) {
        val settings by settingsRepo.state.collectAsState()
        Box(Modifier.fillMaxSize()) {
            SceneBackground(settings.theme, settings.backgroundStyle)
            if (backdrop != null) SpotlightBackdrop(backdrop)
            LivingBackground(settings.theme, weather, overPhoto = backdrop != null)
            Row(Modifier.fillMaxSize()) {
                Sidebar(theme = settings.theme, items = nav, selectedIndex = navIndex, onSelect = {})
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = 18.dp, end = 32.dp, top = 18.dp)) {
                    TopBar(settings = settings, weather = weather, onSettings = {}, modifier = Modifier.align(Alignment.End))
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.weight(1f)) { content() }
                }
            }
        }
    }

    private fun shoot(name: String, settle: Long = 2500, wait: Long = 0, dialog: Boolean = false, body: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { body() }
        if (wait > 0) Thread.sleep(wait)
        compose.mainClock.advanceTimeBy(settle)
        val path = "build/screenshots/$name.png"
        if (dialog) captureScreenRoboImage(path) else compose.onRoot().captureRoboImage(path)
    }

    private val noop: () -> Unit = {}

    @Composable
    private fun settingsPage(page: SettingsPage?, navIndex: Int = 5) {
        val settings by settingsRepo.state.collectAsState()
        val kids by kidsRepo.state.collectAsState()
        Frame(navIndex) {
            SettingsScreen(
                settings = settings, repo = settingsRepo, page = page, autoFocus = true,
                onPage = {}, onAppManagement = noop, kids = kids, kidsRepo = kidsRepo, apps = apps,
                onShowPinPad = { _, _ -> }, companion = companion
            )
        }
    }

    // ---- The tour -----------------------------------------------------------

    @Test fun s01_home() = shoot("01-home") {
        val settings by settingsRepo.state.collectAsState()
        var backdrop by remember { mutableStateOf<String?>(null) }
        val byPkg = apps.associateBy { it.packageName }
        val favs = settings.favourites.mapNotNull { byPkg[it] }
        val recent = recents.entries.sortedByDescending { it.value }.mapNotNull { (p, t) -> byPkg[p]?.let { it to t } }
        Frame(0, backdrop) {
            HomeScreen(
                favourites = favs, recent = recent, contentRows = rows, appsByPackage = byPkg,
                showTmdbHint = false, settings = settings, autoFocus = true,
                onLaunch = {}, onOpenTitle = {}, onOptions = {}, onBackdrop = { backdrop = it },
                contentRepo = contentRepo, myList = myList, onTrailer = {}, onToast = {}
            )
        }
    }

    @Test fun s02_search() = shoot("02-search") {
        val settings by settingsRepo.state.collectAsState()
        Frame(1) {
            SearchScreen(
                apps = apps, appsByPackage = apps.associateBy { it.packageName }, contentRepo = contentRepo, myList = myList,
                settings = settings, autoFocus = true, onLaunch = {}, onOpenTitle = {}, onOptions = {}, onToast = {}
            )
        }
    }

    @Test fun s03_apps() = shoot("03-apps") {
        val settings by settingsRepo.state.collectAsState()
        Frame(2) {
            AppsScreen(apps, settings, selectMode = false, uninstallRunning = false, uninstallRemaining = 0, autoFocus = true,
                onSelectModeChange = {}, onUninstall = {}, onLaunch = {}, onOptions = {}, onAddApp = noop)
        }
    }

    @Test fun s04_apps_select() = shoot("04-apps-select-to-uninstall") {
        val settings by settingsRepo.state.collectAsState()
        Frame(2) {
            AppsScreen(apps, settings, selectMode = true, uninstallRunning = false, uninstallRemaining = 0, autoFocus = true,
                onSelectModeChange = {}, onUninstall = {}, onLaunch = {}, onOptions = {}, onAddApp = noop)
        }
    }

    @Test fun s05_favourites() = shoot("05-favourites") {
        val settings by settingsRepo.state.collectAsState()
        val byPkg = apps.associateBy { it.packageName }
        Frame(3) {
            FavouritesScreen(settings.favourites.mapNotNull { byPkg[it] }, settings, autoFocus = true, moveRequest = null,
                onMoveRequestHandled = noop, onReorder = {}, onLaunch = {}, onOptions = {})
        }
    }

    @Test fun s06_live_tv() = shoot("06-live-tv") {
        val settings by settingsRepo.state.collectAsState()
        Frame(4) { LiveTvScreen(apps, recents, settings, autoFocus = true, onLaunch = {}, onOptions = {}) }
    }

    @Test fun s07_settings() = shoot("07-settings") { settingsPage(null) }
    @Test fun s08_theme() = shoot("08-settings-theme") { settingsPage(SettingsPage.THEME) }
    @Test fun s09_layout() = shoot("09-settings-layout-and-grid") { settingsPage(SettingsPage.LAYOUT) }
    @Test fun s10_home_button() = shoot("10-settings-home-button") { settingsPage(SettingsPage.HOME_BUTTON) }
    @Test fun s11_kids_settings() = shoot("11-settings-kids-mode") { settingsPage(SettingsPage.KIDS) }
    @Test fun s12_companion() = shoot("12-settings-phone-companion") { settingsPage(SettingsPage.COMPANION) }
    @Test fun s13_remote() = shoot("13-settings-remote") { settingsPage(SettingsPage.REMOTE) }
    @Test fun s14_clock() = shoot("14-settings-clock") { settingsPage(SettingsPage.CLOCK) }
    @Test fun s15_diagnostics() = shoot("15-settings-diagnostics", wait = 2500) { settingsPage(SettingsPage.DIAGNOSTICS) }
    @Test fun s16_about() = shoot("16-settings-about") { settingsPage(SettingsPage.ABOUT) }

    @Test fun s17_app_options() = shoot("17-app-options-menu", dialog = true) {
        val settings by settingsRepo.state.collectAsState()
        val byPkg = apps.associateBy { it.packageName }
        Frame(2) {
            AppsScreen(apps, settings, selectMode = false, uninstallRunning = false, uninstallRemaining = 0, autoFocus = false,
                onSelectModeChange = {}, onUninstall = {}, onLaunch = {}, onOptions = {}, onAddApp = noop)
        }
        AppOptionsDialog(
            app = byPkg.getValue("com.netflix.ninja"),
            details = AppDetails("8.110.0", System.currentTimeMillis() - 40L * 86_400_000, 68L * 1024 * 1024),
            settings = settings, onDismiss = noop, onOpen = noop, onToggleFavourite = noop, onMove = noop,
            onToggleLiveTv = noop, onUninstall = noop, onAppInfo = noop
        )
    }

    @Test fun s18_kids_home() = shoot("18-kids-home") {
        val settings by settingsRepo.state.collectAsState()
        val kids by kidsRepo.state.collectAsState()
        Box(Modifier.fillMaxSize()) {
            SceneBackground(settings.theme, settings.backgroundStyle)
            KidsHome(kids = kids, apps = apps, settings = settings, onLaunch = {}, onExit = noop)
        }
    }

    @Test fun s19_pin_pad() = shoot("19-kids-pin-pad") {
        val settings by settingsRepo.state.collectAsState()
        val kids by kidsRepo.state.collectAsState()
        Box(Modifier.fillMaxSize()) {
            SceneBackground(settings.theme, settings.backgroundStyle)
            KidsHome(kids = kids, apps = apps, settings = settings, onLaunch = {}, onExit = noop)
            PinPad("Enter the grown-up PIN", settings.theme, onDismiss = noop, onDone = { false })
        }
    }
}
