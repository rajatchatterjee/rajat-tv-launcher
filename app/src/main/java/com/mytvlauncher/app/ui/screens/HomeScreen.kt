package com.mytvlauncher.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvlauncher.app.data.ContentItem
import com.mytvlauncher.app.data.ContentRow
import com.mytvlauncher.app.data.LauncherSettings
import com.mytvlauncher.app.model.LauncherApp
import com.mytvlauncher.app.ui.components.AppCard
import com.mytvlauncher.app.ui.components.Ambient
import com.mytvlauncher.app.ui.components.ButtonStyle
import com.mytvlauncher.app.ui.components.ContentCard
import com.mytvlauncher.app.ui.components.KenBurnsBanner
import com.mytvlauncher.app.ui.components.SectionTitle
import com.mytvlauncher.app.ui.components.TvButton
import com.mytvlauncher.app.ui.components.glass
import com.mytvlauncher.app.ui.components.relativeTime
import com.mytvlauncher.app.ui.components.requestFocusWhenReady
import kotlinx.coroutines.delay
import java.util.Calendar

/** What the big Home spotlight is showing. */
sealed class Spotlight {
    abstract val key: String
    data class Show(val item: ContentItem, val app: LauncherApp?) : Spotlight() {
        override val key get() = "show-" + item.id + item.packageName
    }
    data class App(val app: LauncherApp, val lastOpened: Long?) : Spotlight() {
        override val key get() = "app-" + app.packageName
    }
}

private const val ROTATE_MS = 8_000L
private const val IDLE_BEFORE_ROTATE_MS = 9_000L

/**
 * Home: a cinematic spotlight on top (a show's full-screen backdrop from the
 * content rows, or an app's banner when there's no show artwork) that
 * follows whatever you highlight and rotates on its own when the remote is
 * idle; below it, "Popular on <service>" rows for installed streaming apps
 * (TMDB — see ContentRepository), Favourite Apps and Recently Opened.
 *
 * The backdrop itself is drawn full-screen by LauncherRoot; this screen
 * reports the current backdrop URL through [onBackdrop].
 */
@Composable
fun HomeScreen(
    favourites: List<LauncherApp>,
    recent: List<Pair<LauncherApp, Long>>,
    contentRows: List<ContentRow>,
    appsByPackage: Map<String, LauncherApp>,
    showTmdbHint: Boolean,
    settings: LauncherSettings,
    autoFocus: Boolean,
    onLaunch: (LauncherApp) -> Unit,
    onOpenTitle: (ContentItem) -> Unit,
    onOptions: (LauncherApp) -> Unit,
    onBackdrop: (String?) -> Unit
) {
    val theme = settings.theme
    val firstRequester = remember { FocusRequester() }
    val rows = remember(contentRows, appsByPackage) { contentRows.filter { it.packageName in appsByPackage } }
    val hasContent = rows.isNotEmpty() || favourites.isNotEmpty() || recent.isNotEmpty()
    LaunchedEffect(hasContent) {
        if (autoFocus && hasContent) firstRequester.requestFocusWhenReady()
    }

    // ---- Spotlight pool and selection ----
    val pool: List<Spotlight> = remember(rows, favourites, recent) {
        if (rows.isNotEmpty()) {
            // Round-robin the top shows of each service so the spotlight mixes them.
            val perRow = rows.map { r -> r.items.take(4).map { Spotlight.Show(it, appsByPackage[r.packageName]) } }
            (0 until 4).flatMap { i -> perRow.mapNotNull { it.getOrNull(i) } }
        } else {
            val recentMap = recent.associate { it.first.packageName to it.second }
            (favourites + recent.map { it.first })
                .distinctBy { it.packageName }
                .filter { it.banner != null }
                .map { Spotlight.App(it, recentMap[it.packageName]) }
        }
    }
    var rotateIndex by remember { mutableStateOf(0) }
    var focusedSpot by remember { mutableStateOf<Spotlight?>(null) }
    var focusTick by remember { mutableStateOf(0) }

    // Whenever focus moves, the spotlight follows it; after a quiet spell it
    // goes back to rotating on its own.
    LaunchedEffect(focusTick, pool.size, settings.heroRotation) {
        if (pool.isEmpty() || !settings.heroRotation) return@LaunchedEffect
        delay(IDLE_BEFORE_ROTATE_MS)
        val resumeFrom = focusedSpot?.let { f -> pool.indexOfFirst { it.key == f.key } } ?: -1
        if (resumeFrom >= 0) rotateIndex = resumeFrom
        focusedSpot = null
        while (true) {
            delay(ROTATE_MS)
            rotateIndex = (rotateIndex + 1) % pool.size
        }
    }
    val current: Spotlight? = focusedSpot ?: pool.getOrNull(rotateIndex % pool.size.coerceAtLeast(1))
    val spotlight: (Spotlight) -> Unit = { focusedSpot = it; focusTick++ }

    val backdrop = (current as? Spotlight.Show)?.item?.backdropUrl
    LaunchedEffect(backdrop) { onBackdrop(backdrop) }
    LaunchedEffect(current?.key) {
        val app = (current as? Spotlight.App)?.app ?: return@LaunchedEffect
        app.banner?.let { b ->
            try { Ambient.from(b.asAndroidBitmap())?.let { Ambient.color = it } } catch (_: Exception) {}
        }
    }
    DisposableEffect(Unit) { onDispose { onBackdrop(null) } }

    val openSpot: (Spotlight) -> Unit = { s ->
        when (s) {
            is Spotlight.Show -> onOpenTitle(s.item)
            is Spotlight.App -> onLaunch(s.app)
        }
    }

    Column(Modifier.fillMaxSize()) {
        SpotlightPanel(
            current = current,
            poolSize = pool.size,
            poolIndex = current?.let { c -> pool.indexOfFirst { it.key == c.key } } ?: -1,
            settings = settings,
            onOpen = openSpot,
            onFocused = { current?.let { focusedSpot = it; focusTick++ } },
            modifier = Modifier.fillMaxWidth().height(206.dp)
        )

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            rows.forEachIndexed { rowIndex, row ->
                item(key = "row-" + row.packageName) {
                    Column {
                        SectionTitle(row.title, theme)
                        val app = appsByPackage[row.packageName]
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp)
                        ) {
                            itemsIndexed(row.items, key = { _, c -> c.id }) { index, item ->
                                ContentCard(
                                    item = item,
                                    serviceIcon = app?.icon,
                                    theme = theme,
                                    zoomOnFocus = settings.focusZoom,
                                    modifier = Modifier.width(196.dp),
                                    focusRequester = if (rowIndex == 0 && index == 0) firstRequester else null,
                                    onFocused = { spotlight(Spotlight.Show(item, app)) },
                                    onClick = { onOpenTitle(item) }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "favourites") {
                Column {
                    SectionTitle("Favourite Apps", theme)
                    if (favourites.isEmpty()) {
                        EmptyHint(
                            "No favourites yet. Long-press OK on any app (or press Menu) and choose Add to Favourites.",
                            settings
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp)
                        ) {
                            itemsIndexed(favourites, key = { _, app -> app.packageName }) { index, app ->
                                AppCard(
                                    app = app,
                                    settings = settings,
                                    modifier = Modifier.width(132.dp),
                                    focusRequester = if (rows.isEmpty() && index == 0) firstRequester else null,
                                    onFocused = { if (app.banner != null) spotlight(Spotlight.App(app, null)) },
                                    onLongClick = { onOptions(app) },
                                    onClick = { onLaunch(app) }
                                )
                            }
                        }
                    }
                }
            }

            if (recent.isNotEmpty()) {
                item(key = "recent") {
                    Column {
                        SectionTitle("Recently Opened", theme)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp)
                        ) {
                            itemsIndexed(recent, key = { _, pair -> "recent-" + pair.first.packageName }) { index, (app, ts) ->
                                AppCard(
                                    app = app,
                                    settings = settings.copy(showAppNames = true),
                                    modifier = Modifier.width(168.dp),
                                    subtitle = relativeTime(ts),
                                    focusRequester = if (rows.isEmpty() && favourites.isEmpty() && index == 0) firstRequester else null,
                                    onFocused = { if (app.banner != null) spotlight(Spotlight.App(app, ts)) },
                                    onLongClick = { onOptions(app) },
                                    onClick = { onLaunch(app) }
                                )
                            }
                        }
                    }
                }
            }

            if (showTmdbHint) {
                item(key = "tmdb-hint") {
                    EmptyHint(
                        "Tip: add a free TMDB key (GitHub secret TMDB_API_KEY) to see what's popular on Netflix, Prime Video, JioHotstar and your other apps right here.",
                        settings
                    )
                }
            }
        }
    }
}

@Composable
private fun SpotlightPanel(
    current: Spotlight?,
    poolSize: Int,
    poolIndex: Int,
    settings: LauncherSettings,
    onOpen: (Spotlight) -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = settings.theme
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Text(greeting(), color = theme.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            AnimatedContent(
                targetState = current,
                contentKey = { it?.key },
                transitionSpec = {
                    (fadeIn(tween(600, delayMillis = 120)) + slideInVertically(tween(600)) { it / 6 }) togetherWith
                        fadeOut(tween(250))
                },
                label = "spotlight-text"
            ) { spot ->
                SpotlightText(spot, settings)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current != null) {
                    val label = when (current) {
                        is Spotlight.Show -> "Open in ${current.item.serviceName}"
                        is Spotlight.App -> "Open ${current.app.label}"
                    }
                    TvButton(
                        label, theme,
                        icon = Icons.Filled.PlayArrow,
                        style = ButtonStyle.PRIMARY,
                        modifier = Modifier.onFocusChanged { if (it.isFocused) onFocused() }
                    ) { onOpen(current) }
                    Spacer(Modifier.width(16.dp))
                }
                if (poolSize > 1) Dots(poolSize, poolIndex, theme.textPrimary)
            }
        }
        // Apps have no full-screen art, so show their banner as a drifting card.
        val app = (current as? Spotlight.App)?.app
        if (app?.banner != null) {
            AnimatedContent(
                targetState = app,
                contentKey = { it.packageName },
                transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(400)) },
                label = "spotlight-banner"
            ) { a ->
                a.banner?.let { KenBurnsBanner(it, Modifier.height(170.dp).padding(end = 8.dp)) }
            }
        }
    }
}

@Composable
private fun SpotlightText(spot: Spotlight?, settings: LauncherSettings) {
    val theme = settings.theme
    Column(Modifier.widthIn(max = 480.dp)) {
        when (spot) {
            null -> {
                Text(greeting(), color = theme.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("Relax. Watch. Enjoy.", color = theme.textSecondary, fontSize = 14.sp)
            }
            is Spotlight.Show -> {
                val item = spot.item
                Text(
                    item.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetaChip(item.serviceName, Ambient.color ?: theme.accent)
                    val meta = listOfNotNull(if (item.isTv) "Series" else "Film", item.year).joinToString("  ·  ")
                    Spacer(Modifier.width(8.dp))
                    Text(meta, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    item.rating?.let {
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC94D), modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(String.format("%.1f", it), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    item.overview, color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp
                )
            }
            is Spotlight.App -> {
                Text(spot.app.label, color = theme.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(
                    spot.lastOpened?.let { "Last opened ${relativeTime(it)}" } ?: "One of your favourites",
                    color = theme.textSecondary, fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun MetaChip(text: String, color: Color) {
    Text(
        text,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.55f))
            .padding(horizontal = 9.dp, vertical = 2.dp)
    )
}

@Composable
private fun Dots(count: Int, active: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count.coerceAtMost(12)) { i ->
            val w by animateDpAsState(if (i == active) 18.dp else 6.dp, tween(400), label = "dot")
            Box(
                Modifier
                    .height(6.dp)
                    .width(w)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (i == active) 0.9f else 0.3f))
            )
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
