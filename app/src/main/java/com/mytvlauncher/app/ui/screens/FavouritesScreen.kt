package com.mytvlauncher.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvlauncher.app.data.LauncherSettings
import com.mytvlauncher.app.model.LauncherApp
import com.mytvlauncher.app.ui.components.AppCard
import com.mytvlauncher.app.ui.components.ButtonStyle
import com.mytvlauncher.app.ui.components.ScreenHeader
import com.mytvlauncher.app.ui.components.TvButton
import com.mytvlauncher.app.ui.components.requestFocusWhenReady

private const val FAV_COLUMNS = 4

/**
 * Screen 6: Favourites in the user's own order. "Edit Order" turns on
 * reordering: OK picks an app up, the arrows move it, OK drops it.
 * [moveRequest] (from "Move" in the app options menu) opens straight into
 * edit mode with that app already picked up.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavouritesScreen(
    favourites: List<LauncherApp>,
    settings: LauncherSettings,
    autoFocus: Boolean,
    moveRequest: String?,
    onMoveRequestHandled: () -> Unit,
    onReorder: (List<String>) -> Unit,
    onLaunch: (LauncherApp) -> Unit,
    onOptions: (LauncherApp) -> Unit
) {
    val theme = settings.theme
    var editing by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<String?>(null) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requesterFor(pkg: String) = requesters.getOrPut(pkg) { FocusRequester() }

    LaunchedEffect(moveRequest) {
        if (moveRequest != null) {
            editing = true
            picked = moveRequest
            onMoveRequestHandled()
            requesterFor(moveRequest).requestFocusWhenReady()
        }
    }
    LaunchedEffect(autoFocus, favourites.isNotEmpty()) {
        if (autoFocus && moveRequest == null && favourites.isNotEmpty()) {
            requesterFor(favourites.first().packageName).requestFocusWhenReady()
        }
    }

    fun move(pkg: String, delta: Int) {
        val order = favourites.map { it.packageName }.toMutableList()
        val from = order.indexOf(pkg)
        val to = from + delta
        if (from < 0 || to < 0 || to >= order.size) return
        order.removeAt(from)
        order.add(to, pkg)
        onReorder(order)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ScreenHeader(
                "Favourites",
                if (editing) "OK picks an app up · arrows move it · OK drops it" else "Reorder your favourite apps",
                theme,
                Modifier.weight(1f)
            )
            if (favourites.size > 1) {
                TvButton(
                    text = if (editing) "Done" else "Edit Order",
                    theme = theme,
                    icon = if (editing) Icons.Filled.Check else Icons.Filled.Edit,
                    style = if (editing) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY
                ) {
                    editing = !editing
                    picked = null
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (favourites.isEmpty()) {
            EmptyHint(
                "No favourites yet. Long-press OK on any app (or press Menu) and choose Add to Favourites.",
                settings
            )
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(FAV_COLUMNS),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp, start = 6.dp, end = 6.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(favourites, key = { _, app -> app.packageName }) { _, app ->
                val isPicked = picked == app.packageName
                // While an app is picked up, arrows move it instead of moving focus.
                val moveKeys = Modifier.onPreviewKeyEvent { event ->
                    if (!isPicked) return@onPreviewKeyEvent false
                    val delta = when (event.key) {
                        Key.DirectionLeft -> -1
                        Key.DirectionRight -> 1
                        Key.DirectionUp -> -FAV_COLUMNS
                        Key.DirectionDown -> FAV_COLUMNS
                        Key.Back -> {
                            if (event.type == KeyEventType.KeyUp) picked = null
                            return@onPreviewKeyEvent true
                        }
                        else -> return@onPreviewKeyEvent false
                    }
                    if (event.type == KeyEventType.KeyDown) move(app.packageName, delta)
                    true
                }
                AppCard(
                    app = app,
                    settings = settings,
                    modifier = Modifier.animateItemPlacement(),
                    pickedUp = isPicked,
                    keyModifier = moveKeys,
                    focusRequester = requesterFor(app.packageName),
                    onLongClick = if (editing) null else ({ onOptions(app) }),
                    onClick = {
                        when {
                            !editing -> onLaunch(app)
                            isPicked -> picked = null
                            else -> picked = app.packageName
                        }
                    }
                )
            }
        }
        if (editing) {
            Text(
                "Press Back to put an app down. Remove apps with long-press → Remove from Favourites.",
                color = theme.textSecondary,
                fontSize = 11.sp
            )
        }
    }
}
