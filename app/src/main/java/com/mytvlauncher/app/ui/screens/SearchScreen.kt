package com.mytvlauncher.app.ui.screens

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvlauncher.app.data.ContentItem
import com.mytvlauncher.app.data.ContentRepository
import com.mytvlauncher.app.data.LauncherSettings
import com.mytvlauncher.app.data.MyListRepository
import com.mytvlauncher.app.data.SearchHit
import com.mytvlauncher.app.model.LauncherApp
import com.mytvlauncher.app.ui.components.AppCard
import com.mytvlauncher.app.ui.components.ContentCard
import com.mytvlauncher.app.ui.components.RoundIconButton
import com.mytvlauncher.app.ui.components.SectionTitle
import com.mytvlauncher.app.ui.components.focusRingColor
import com.mytvlauncher.app.ui.components.glass
import com.mytvlauncher.app.ui.components.requestFocusWhenReady
import kotlinx.coroutines.delay

/**
 * Universal search: type (or speak) a title and see which of *your* apps
 * stream it in India (TMDB watch providers), plus matching installed apps.
 * OK opens the title in the first app that has it; hold OK saves it to My List.
 */
@Composable
fun SearchScreen(
    apps: List<LauncherApp>,
    appsByPackage: Map<String, LauncherApp>,
    contentRepo: ContentRepository,
    myList: MyListRepository,
    settings: LauncherSettings,
    autoFocus: Boolean,
    initialQuery: String = "",
    onLaunch: (LauncherApp) -> Unit,
    onOpenTitle: (ContentItem) -> Unit,
    onOptions: (LauncherApp) -> Unit,
    onToast: (String) -> Unit
) {
    val theme = settings.theme
    val context = LocalContext.current
    var query by remember { mutableStateOf(initialQuery) }
    var submitted by remember { mutableStateOf(0) }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val saved by myList.items.collectAsState()
    val fieldRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus) fieldRequester.requestFocusWhenReady() }

    val installed = remember(apps) { apps.map { it.packageName }.toSet() }
    val appHits = remember(query, apps) {
        val q = query.trim()
        if (q.isEmpty()) emptyList() else apps.filter { it.label.contains(q, ignoreCase = true) }.take(12)
    }
    LaunchedEffect(query, submitted) {
        val q = query.trim()
        if (q.length < 2) { hits = emptyList(); loading = false; failed = false; return@LaunchedEffect }
        delay(650)
        loading = true
        val result = runCatching { contentRepo.search(q, installed) }
        hits = result.getOrDefault(emptyList())
        failed = result.isFailure
        loading = false
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
            query = it
            submitted++
        }
    }
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a show, film or app")
    }
    val canSpeak = remember { speechIntent.resolveActivity(context.packageManager) != null }

    val onApps = hits.filter { it.onYourApps.isNotEmpty() }
    val elsewhere = hits.filter { it.onYourApps.isEmpty() }
    val toggle: (ContentItem) -> Unit = { item ->
        onToast(if (myList.toggle(item)) "Added “${item.title}” to My List" else "Removed from My List")
    }
    val open: (SearchHit) -> Unit = { hit ->
        if (hit.onYourApps.isNotEmpty()) onOpenTitle(hit.item)
        else onToast(
            if (hit.elsewhere.isEmpty()) "“${hit.item.title}” isn't streaming in India right now"
            else "Not on your apps — streaming on ${hit.elsewhere.joinToString()}"
        )
    }

    Column(Modifier.fillMaxSize().padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            var fieldFocused by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier
                    .weight(1f)
                    .glass(theme, radius = 14.dp, alpha = 0.7f)
                    .border(if (fieldFocused) 2.dp else 0.dp, if (fieldFocused) focusRingColor(theme) else Color.Transparent, shape)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Search shows, films and apps", color = theme.textSecondary, fontSize = 16.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = theme.textPrimary, fontSize = 16.sp),
                        cursorBrush = SolidColor(theme.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitted++ }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fieldRequester)
                            .onFocusChanged { fieldFocused = it.isFocused }
                    )
                }
            }
            if (canSpeak) {
                Spacer(Modifier.width(12.dp))
                RoundIconButton(Icons.Filled.Mic, theme, size = 44.dp, contentDescription = "Voice search") {
                    try { speech.launch(speechIntent) } catch (e: Exception) { onToast("Voice search isn't available on this TV") }
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (query.isBlank()) {
                item(key = "hint") {
                    EmptyHint(
                        if (contentRepo.hasKey) "Type or say a title, an actor's film, or an app. You'll see which of your apps has it. Hold OK on a result to save it to My List."
                        else "Search your apps by name. (Add a TMDB key to also search shows and films.)",
                        settings
                    )
                }
                if (saved.isNotEmpty()) item(key = "mylist") { ResultRow("My List", saved.map { SearchHit(it, listOf(it.serviceName to it.packageName), emptyList()) }, appsByPackage, settings, saved, open, toggle) }
            }
            if (appHits.isNotEmpty()) {
                item(key = "apps") {
                    Column {
                        SectionTitle("Apps", theme)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp)) {
                            items(appHits, key = { it.packageName }) { app ->
                                AppCard(
                                    app = app, settings = settings, modifier = Modifier.width(132.dp),
                                    onLongClick = { onOptions(app) }, onClick = { onLaunch(app) }
                                )
                            }
                        }
                    }
                }
            }
            if (onApps.isNotEmpty()) item(key = "yours") { ResultRow("On your apps", onApps, appsByPackage, settings, saved, open, toggle) }
            if (elsewhere.isNotEmpty()) item(key = "else") { ResultRow("Not on your apps", elsewhere, appsByPackage, settings, saved, open, toggle) }
            if (query.trim().length >= 2 && !loading && hits.isEmpty() && appHits.isEmpty()) {
                item(key = "none") {
                    EmptyHint(if (failed) "Couldn't reach the internet for show results." else "Nothing found for “${query.trim()}”.", settings)
                }
            }
            if (loading) item(key = "loading") { Text("Searching…", color = theme.textSecondary, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun ResultRow(
    title: String,
    hits: List<SearchHit>,
    appsByPackage: Map<String, LauncherApp>,
    settings: LauncherSettings,
    saved: List<ContentItem>,
    onOpen: (SearchHit) -> Unit,
    onToggle: (ContentItem) -> Unit
) {
    val theme = settings.theme
    Column {
        SectionTitle(title, theme)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp)) {
            itemsIndexed(hits, key = { _, h -> title + h.item.id }) { _, hit ->
                Column(Modifier.width(196.dp)) {
                    ContentCard(
                        item = hit.item,
                        serviceIcon = appsByPackage[hit.item.packageName]?.icon,
                        theme = theme,
                        zoomOnFocus = settings.focusZoom,
                        saved = saved.any { it.id == hit.item.id },
                        onLongClick = { onToggle(hit.item) },
                        onFocused = {},
                        onClick = { onOpen(hit) }
                    )
                    Spacer(Modifier.height(6.dp))
                    val caption = when {
                        hit.onYourApps.isNotEmpty() -> "On " + hit.onYourApps.joinToString(" · ") { it.first }
                        hit.elsewhere.isNotEmpty() -> "On " + hit.elsewhere.joinToString(" · ")
                        else -> "Not streaming in India"
                    }
                    Text(
                        caption,
                        color = if (hit.onYourApps.isNotEmpty()) theme.textPrimary else theme.textSecondary,
                        fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
