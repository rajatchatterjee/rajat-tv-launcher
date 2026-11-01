package com.mytvlauncher.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvlauncher.app.data.BackgroundStyle
import com.mytvlauncher.app.data.DateStyle
import com.mytvlauncher.app.data.LauncherSettings
import com.mytvlauncher.app.data.SettingsRepository
import com.mytvlauncher.app.model.LauncherTheme
import com.mytvlauncher.app.model.ThemePresets
import com.mytvlauncher.app.ui.components.RoundIconButton
import com.mytvlauncher.app.ui.components.SceneBackground
import com.mytvlauncher.app.ui.components.ScreenHeader
import com.mytvlauncher.app.ui.components.SettingsRow
import com.mytvlauncher.app.ui.components.StepperRow
import com.mytvlauncher.app.ui.components.ToggleRow
import com.mytvlauncher.app.ui.components.ToggleSwitch
import com.mytvlauncher.app.ui.components.TvChip
import com.mytvlauncher.app.ui.components.focusRingColor
import com.mytvlauncher.app.ui.components.glass
import com.mytvlauncher.app.ui.components.optionalFocusRequester
import com.mytvlauncher.app.ui.components.requestFocusWhenReady
import com.mytvlauncher.app.ui.components.tvClickable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SettingsPage(val title: String) {
    THEME("Theme & Appearance"),
    LAYOUT("Layout & Grid"),
    REMOTE("Remote & Navigation"),
    CLOCK("Clock & Date Format"),
    KIDS("Kids mode"),
    COMPANION("Phone companion"),
    DIAGNOSTICS("Diagnostics"),
    ABOUT("About")
}

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    repo: SettingsRepository,
    page: SettingsPage?,
    autoFocus: Boolean,
    onPage: (SettingsPage?) -> Unit,
    onAppManagement: () -> Unit,
    kids: com.mytvlauncher.app.data.KidsState,
    kidsRepo: com.mytvlauncher.app.data.KidsRepository,
    apps: List<com.mytvlauncher.app.model.LauncherApp>,
    onShowPinPad: (String, (String) -> Boolean) -> Unit,
    companion: com.mytvlauncher.app.data.CompanionServer
) {
    val context = LocalContext.current
    when (page) {
        SettingsPage.COMPANION -> SubPage(SettingsPage.COMPANION, settings, onBack = { onPage(null) }) { first ->
            CompanionContent(companion, settings, first)
        }
        SettingsPage.KIDS -> SubPage(SettingsPage.KIDS, settings, onBack = { onPage(null) }) { first ->
            KidsSettingsContent(kids, kidsRepo, apps, settings, first, onShowPinPad)
        }
        null -> SettingsHome(settings, autoFocus, onPage, onAppManagement)
        SettingsPage.THEME -> ThemePage(settings, repo, onBack = { onPage(null) })
        SettingsPage.LAYOUT -> SubPage(SettingsPage.LAYOUT, settings, onBack = { onPage(null) }) { first ->
            StepperRow(
                "Apps grid columns", settings.gridColumns, 4..8, settings.theme,
                subtitle = "How many apps per row on the Apps screen",
                focusRequester = first
            ) { repo.setGridColumns(it) }
            ToggleRow("Show app names", settings.showAppNames, settings.theme, subtitle = "Labels under app tiles") {
                repo.setShowAppNames(it)
            }
            ToggleRow(
                "Use app banners", settings.useBanners, settings.theme,
                subtitle = "Show each app's TV banner; off shows its icon instead"
            ) { repo.setUseBanners(it) }
            ToggleRow(
                "Living background", settings.livingBackground, settings.theme,
                subtitle = "Drifting light, particles and live weather (rain, clouds, sun, stars)"
            ) { repo.setLivingBackground(it) }
            ToggleRow(
                "Rotate Home spotlight", settings.heroRotation, settings.theme,
                subtitle = "The big Home banner cycles through shows and apps when idle"
            ) { repo.setHeroRotation(it) }
            SettingsRow(
                "Screensaver", settings.theme,
                subtitle = "Pick \"Rajat's TV Launcher\" in the TV's screen-saver settings for drifting show art, clock and weather"
            ) {
                val ctx = context
                val intents = listOf(
                    android.content.Intent(android.provider.Settings.ACTION_DREAM_SETTINGS),
                    android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                )
                for (i in intents) {
                    try { ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); break } catch (e: Exception) { }
                }
            }
            ToggleRow(
                "Navigation sounds", settings.navSounds, settings.theme,
                subtitle = "A soft tick when moving around, a chime on OK"
            ) { repo.setNavSounds(it) }
        }
        SettingsPage.REMOTE -> SubPage(SettingsPage.REMOTE, settings, onBack = { onPage(null) }) { first ->
            ToggleRow(
                "Zoom on focus", settings.focusZoom, settings.theme,
                subtitle = "The tile under the cursor grows slightly",
                focusRequester = first
            ) { repo.setFocusZoom(it) }
            InfoLine("OK", "Open the app", settings)
            InfoLine("Hold OK  /  Menu key", "App options — favourites, move, Live TV, uninstall, app info", settings)
            InfoLine("Back", "Return to Home (Back on Home does nothing, so you can't leave the launcher by accident)", settings)
            InfoLine("Home button", "Jumps straight back to the Home screen", settings)
            InfoLine("Favourites › Edit Order", "OK picks an app up, arrows move it, OK puts it down", settings)
        }
        SettingsPage.CLOCK -> SubPage(SettingsPage.CLOCK, settings, onBack = { onPage(null) }) { first ->
            ToggleRow("Show clock", settings.showClock, settings.theme, focusRequester = first) { repo.setShowClock(it) }
            ToggleRow("24-hour time", settings.use24Hour, settings.theme, subtitle = if (settings.use24Hour) "20:45" else "8:45 PM") {
                repo.setUse24Hour(it)
            }
            Text("Date format", color = settings.theme.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            DateStyle.values().forEach { style ->
                SettingsRow(
                    title = SimpleDateFormat(style.pattern, Locale.getDefault()).format(Date()),
                    theme = settings.theme,
                    onClick = { repo.setDateStyle(style) },
                    trailing = {
                        if (settings.dateStyle == style) {
                            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = settings.theme.accent, modifier = Modifier.size(20.dp))
                        }
                    }
                )
            }
        }
        SettingsPage.DIAGNOSTICS -> SubPage(SettingsPage.DIAGNOSTICS, settings, onBack = { onPage(null) }) { first ->
            DiagnosticsContent(settings, first)
        }
        SettingsPage.ABOUT -> SubPage(SettingsPage.ABOUT, settings, onBack = { onPage(null) }) { first ->
            val context = LocalContext.current
            val version = remember {
                try {
                    val info = context.packageManager.getPackageInfo(context.packageName, 0)
                    "${info.versionName} (build ${info.versionCode})"
                } catch (e: Exception) {
                    "unknown"
                }
            }
            SettingsRow("Rajat's TV Launcher", settings.theme, subtitle = "Version $version", focusRequester = first, onClick = {}, trailing = {})
            val upd by com.mytvlauncher.app.data.Updater.state.collectAsState()
            val (updTitle, updSub) = when (val s = upd) {
                is com.mytvlauncher.app.data.Updater.State.Available -> "Install build ${s.build}" to "Downloads from GitHub, then press Install"
                is com.mytvlauncher.app.data.Updater.State.Downloading -> "Downloading build ${s.build}… ${s.percent}%" to "Keep the TV on"
                is com.mytvlauncher.app.data.Updater.State.Checking -> "Checking GitHub…" to null
                is com.mytvlauncher.app.data.Updater.State.UpToDate -> "Up to date" to "Build ${s.build} is the latest · press to check again"
                is com.mytvlauncher.app.data.Updater.State.NeedsPermission -> "Allow \"Install unknown apps\"" to "Turn it on for Rajat's TV Launcher, come back, press again"
                is com.mytvlauncher.app.data.Updater.State.WaitingForConfirm -> "Waiting for Install confirmation" to "Press Install on the system dialog"
                is com.mytvlauncher.app.data.Updater.State.Error -> "Update problem — press to retry" to s.message
                else -> "Check for updates" to "Looks for a newer build on GitHub"
            }
            SettingsRow(updTitle, settings.theme, icon = Icons.Filled.SystemUpdate, subtitle = updSub) {
                val updater = com.mytvlauncher.app.data.Updater
                when (upd) {
                    is com.mytvlauncher.app.data.Updater.State.Available,
                    is com.mytvlauncher.app.data.Updater.State.NeedsPermission,
                    is com.mytvlauncher.app.data.Updater.State.WaitingForConfirm -> updater.install(context)
                    else -> updater.check(context)
                }
            }
            InfoLine("Installs from", "GitHub Releases of rajatchatterjee/rajat-tv-launcher — updates itself; Downloader only needed for a fresh install", settings)
            InfoLine("Made for", "OnePlus TV · Android TV", settings)
        }
    }
}

@Composable
private fun SettingsHome(
    settings: LauncherSettings,
    autoFocus: Boolean,
    onPage: (SettingsPage?) -> Unit,
    onAppManagement: () -> Unit
) {
    val theme = settings.theme
    val first = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) first.requestFocusWhenReady() }
    val context = LocalContext.current
    val build = remember { com.mytvlauncher.app.data.Updater.currentBuild(context) }
    val upd by com.mytvlauncher.app.data.Updater.state.collectAsState()
    Column(Modifier.widthIn(max = 560.dp).fillMaxHeight()) {
        ScreenHeader("Settings", "Build $build", theme)
        Spacer(Modifier.height(16.dp))
        // Scrollable: there are more rows than fit on a TV screen, and a
        // plain Column let focus move onto rows drawn off-screen.
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val updTitle = when (val s = upd) {
                is com.mytvlauncher.app.data.Updater.State.Available -> "Update to build ${s.build}"
                is com.mytvlauncher.app.data.Updater.State.Downloading -> "Downloading build ${s.build}… ${s.percent}%"
                is com.mytvlauncher.app.data.Updater.State.Checking -> "Checking for updates…"
                is com.mytvlauncher.app.data.Updater.State.UpToDate -> "Up to date (build ${s.build})"
                is com.mytvlauncher.app.data.Updater.State.NeedsPermission -> "Allow \"Install unknown apps\", then press again"
                is com.mytvlauncher.app.data.Updater.State.WaitingForConfirm -> "Press Install on the system dialog"
                is com.mytvlauncher.app.data.Updater.State.Error -> "Update problem — press to retry"
                else -> "Check for updates"
            }
            SettingsRow(updTitle, theme, icon = Icons.Filled.SystemUpdate, subtitle = "Installed: build $build", focusRequester = first) {
                when (upd) {
                    is com.mytvlauncher.app.data.Updater.State.Available,
                    is com.mytvlauncher.app.data.Updater.State.NeedsPermission,
                    is com.mytvlauncher.app.data.Updater.State.WaitingForConfirm -> com.mytvlauncher.app.data.Updater.install(context)
                    else -> com.mytvlauncher.app.data.Updater.check(context)
                }
            }
            SettingsRow("Theme & Appearance", theme, icon = Icons.Filled.Palette) { onPage(SettingsPage.THEME) }
            SettingsRow("App Management", theme, icon = Icons.Filled.Apps, subtitle = "Select and uninstall apps") { onAppManagement() }
            SettingsRow("Layout & Grid", theme, icon = Icons.Filled.GridView) { onPage(SettingsPage.LAYOUT) }
            SettingsRow("Remote & Navigation", theme, icon = Icons.Filled.SettingsRemote) { onPage(SettingsPage.REMOTE) }
            SettingsRow("Clock & Date Format", theme, icon = Icons.Filled.Schedule) { onPage(SettingsPage.CLOCK) }
            SettingsRow("Kids mode", theme, icon = Icons.Filled.ChildCare, subtitle = "Only the apps you allow, PIN to leave, daily limit") { onPage(SettingsPage.KIDS) }
            SettingsRow("Phone companion", theme, icon = Icons.Filled.PhoneAndroid, subtitle = "Search and open things on the TV from your phone") { onPage(SettingsPage.COMPANION) }
            SettingsRow("Diagnostics", theme, icon = Icons.Filled.BugReport, subtitle = "ADB and preview-row checks") { onPage(SettingsPage.DIAGNOSTICS) }
            SettingsRow("About", theme, icon = Icons.Filled.Info) { onPage(SettingsPage.ABOUT) }
        }
    }
}

@Composable
private fun SubPage(
    page: SettingsPage,
    settings: LauncherSettings,
    onBack: () -> Unit,
    content: @Composable ColumnScope.(first: FocusRequester) -> Unit
) {
    val first = remember(page) { FocusRequester() }
    LaunchedEffect(page) { first.requestFocusWhenReady() }
    Column(Modifier.widthIn(max = 560.dp).fillMaxHeight()) {
        PageHeader(page.title, null, settings.theme, onBack)
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content(first)
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String?, theme: LauncherTheme, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Icons.Filled.ArrowBack, theme, size = 34.dp, contentDescription = "Back", onClick = onBack)
        Spacer(Modifier.width(12.dp))
        ScreenHeader(title, subtitle, theme)
    }
}

@Composable
private fun InfoLine(label: String, text: String, settings: LauncherSettings) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(settings.theme, radius = 12.dp, alpha = 0.4f)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, color = settings.theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(170.dp))
        Text(text, color = settings.theme.textSecondary, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------------------
// Theme & Appearance (mockup screen 3)

@Composable
private fun ThemePage(settings: LauncherSettings, repo: SettingsRepository, onBack: () -> Unit) {
    val theme = settings.theme
    val firstThemeRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstThemeRequester.requestFocusWhenReady() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Theme Settings", "Choose a look that matches your style", theme, onBack)
        Spacer(Modifier.height(16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp)
        ) {
            itemsIndexed(ThemePresets.all, key = { _, t -> t.id }) { index, preset ->
                ThemeThumb(
                    preset = preset,
                    selected = preset.id == theme.id,
                    current = theme,
                    focusRequester = if (index == ThemePresets.all.indexOfFirst { it.id == theme.id }.coerceAtLeast(0)) firstThemeRequester else null
                ) { repo.selectTheme(preset.id) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(theme.textSecondary.copy(alpha = 0.2f)))
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.Top) {
            Column {
                Text("Accent Colour", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemePresets.accentChoices.forEach { color ->
                        AccentDot(color, selected = theme.accent == color, theme = theme) { repo.setAccent(color) }
                    }
                }
            }
            Spacer(Modifier.width(32.dp))
            Column {
                Text("Background Style", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BackgroundStyle.values().forEach { style ->
                        TvChip(style.label, selected = settings.backgroundStyle == style, theme = theme) {
                            repo.setBackgroundStyle(style)
                        }
                    }
                }
            }
            Spacer(Modifier.width(32.dp))
            Column {
                Text("Show Clock", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                ClockToggle(settings.showClock, theme) { repo.setShowClock(!settings.showClock) }
            }
        }
        if (theme.scene != com.mytvlauncher.app.model.Scene.GRADIENT && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            Text(
                "Blur needs Android 12 or newer; on this TV it shows as a softer dim.",
                color = theme.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun ThemeThumb(
    preset: LauncherTheme,
    selected: Boolean,
    current: LauncherTheme,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "thumb-scale")
    val shape = RoundedCornerShape(12.dp)
    Column(horizontalAlignment = Alignment.Start) {
        Box(
            Modifier
                .size(width = 150.dp, height = 88.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .border(
                    if (focused || selected) 2.5.dp else 1.dp,
                    when {
                        focused -> focusRingColor(current)
                        selected -> current.accent
                        else -> Color.White.copy(alpha = 0.1f)
                    },
                    shape
                )
                .optionalFocusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .tvClickable(onClick = onClick)
        ) {
            SceneBackground(preset, BackgroundStyle.SOLID, thumbnail = true)
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(current.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(preset.name, color = current.textPrimary, fontSize = 12.sp)
    }
}

@Composable
private fun AccentDot(color: Color, selected: Boolean, theme: LauncherTheme, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (focused) 3.dp else 0.dp, if (focused) focusRingColor(theme) else Color.Transparent, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ClockToggle(checked: Boolean, theme: LauncherTheme, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .border(if (focused) 2.dp else 0.dp, if (focused) focusRingColor(theme) else Color.Transparent, RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(4.dp)
    ) {
        ToggleSwitch(checked, theme)
    }
}

// ---------------------------------------------------------------------------
// Diagnostics: ADB + preview-row access checks, run on the TV itself.

@Composable
private fun DiagnosticsContent(settings: LauncherSettings, first: FocusRequester) {
    val context = LocalContext.current
    val theme = settings.theme
    var lines by remember { mutableStateOf<List<com.mytvlauncher.app.data.Diagnostics.Line>?>(null) }
    var runId by remember { mutableStateOf(0) }
    LaunchedEffect(runId) {
        lines = null
        lines = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.mytvlauncher.app.data.Diagnostics.run(context)
        }
    }
    fun launch(intent: android.content.Intent) {
        try { context.startActivity(intent) } catch (e: Exception) {
            android.widget.Toast.makeText(context, "This TV blocked that screen", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    SettingsRow("Run checks again", theme, subtitle = "Re-test after changing a setting", focusRequester = first) { runId++ }
    val devIntents = remember(runId) { com.mytvlauncher.app.data.Diagnostics.devSettingsIntents(context) }
    if (devIntents.isNotEmpty()) {
        SettingsRow("Open Developer options", theme, subtitle = "Look for USB / Network / Wireless debugging") { launch(devIntents.first()) }
    }
    SettingsRow("Open About (tap Build 7× to unlock)", theme, subtitle = "Device Preferences › About") {
        launch(com.mytvlauncher.app.data.Diagnostics.aboutIntent())
    }
    val result = lines
    if (result == null) {
        Text("Running checks…", color = theme.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    } else {
        result.forEach { line ->
            val mark = when (line.good) { true -> "✓  "; false -> "✗  "; null -> "" }
            InfoLine(line.label, mark + line.value, settings)
        }
    }
}

// ---------------------------------------------------------------------------
// Phone companion: link + QR code for the LAN web page.

@Composable
private fun CompanionContent(companion: com.mytvlauncher.app.data.CompanionServer, settings: LauncherSettings, first: FocusRequester) {
    val theme = settings.theme
    var enabled by remember { mutableStateOf(companion.enabled) }
    var tokenTick by remember { mutableStateOf(0) }
    val url = remember(enabled, tokenTick) { if (enabled) companion.url() else null }
    ToggleRow(
        "Phone companion", enabled, theme,
        subtitle = "A page on your home Wi-Fi to search, play and launch from your phone",
        focusRequester = first
    ) { companion.enabled = it; enabled = it }
    if (enabled) {
        if (url == null) {
            InfoLine("Not connected", "Connect the TV to Wi-Fi or Ethernet to use the companion", settings)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                val qr = remember(url) { qrBitmap(url, 360) }
                if (qr != null) {
                    androidx.compose.foundation.Image(
                        qr.asImageBitmap(), contentDescription = "QR code for the phone companion",
                        modifier = Modifier.size(170.dp).clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Scan with your phone's camera", color = theme.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("or open", color = theme.textSecondary, fontSize = 12.sp)
                    Text(url, color = theme.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text("Phone and TV must be on the same Wi-Fi. Add it to your home screen for one-tap access.", color = theme.textSecondary, fontSize = 12.sp)
                }
            }
            SettingsRow("New link", theme, subtitle = "Makes the old link and QR stop working") {
                companion.newToken(); tokenTick++
            }
        }
    }
}

private fun qrBitmap(text: String, size: Int): android.graphics.Bitmap? = try {
    val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
        text, com.google.zxing.BarcodeFormat.QR_CODE, size, size,
        mapOf(com.google.zxing.EncodeHintType.MARGIN to 1)
    )
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888)
} catch (e: Exception) { null }
