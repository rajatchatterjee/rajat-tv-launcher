package com.mytvlauncher.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvlauncher.app.data.LauncherSettings
import com.mytvlauncher.app.model.LauncherApp
import com.mytvlauncher.app.model.LauncherTheme

private val CardShape = RoundedCornerShape(12.dp)

/**
 * A 16:9 app card. Uses the app's own TV banner when it has one (and the
 * user hasn't turned banners off), otherwise its icon on a tinted card.
 *
 * [selected] non-null puts the card in selection mode with a checkbox;
 * [pickedUp] is the "being moved" look used when reordering Favourites.
 */
@Composable
fun AppCard(
    app: LauncherApp,
    settings: LauncherSettings,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean? = null,
    dimmed: Boolean = false,
    pickedUp: Boolean = false,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    keyModifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val theme = settings.theme
    var focused by remember { mutableStateOf(false) }
    val zoom = if (settings.focusZoom) 1.08f else 1.0f
    val scale by animateFloatAsState(
        when {
            pickedUp -> 1.12f
            focused -> zoom
            else -> 1f
        },
        label = "card-scale"
    )
    val elevation by animateFloatAsState(if (focused || pickedUp) 16f else 2f, label = "card-elev")

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                // Focus/keys/click BEFORE graphicsLayer so D-pad navigation
                // uses the tile's resting bounds, not the lifted ones (see
                // ContentCard in Spotlight.kt).
                .then(keyModifier)
                .optionalFocusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .tvClickable(enabled = !dimmed, onLongClick = onLongClick, onClick = onClick)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    // Focused tiles lift toward you, not just grow.
                    translationY = -6.dp.toPx() * ((elevation - 2f) / 14f)
                    alpha = if (dimmed) 0.35f else 1f
                }
                .shadow(elevation.dp, CardShape, clip = false, spotColor = if (focused) (Ambient.color ?: theme.accent) else Color.Black)
                .clip(CardShape)
        ) {
            CardFace(app, settings)
            val ring = when {
                pickedUp -> theme.accent
                focused -> focusRingColor(theme)
                selected == true -> theme.accent
                else -> Color.White.copy(alpha = 0.07f)
            }
            Box(
                Modifier
                    .matchParentSize()
                    .border(if (focused || pickedUp || selected == true) 2.5.dp else 1.dp, ring, CardShape)
            )
            if (selected != null && !dimmed) {
                CheckBadge(selected, theme, Modifier.align(Alignment.TopStart).padding(6.dp))
            }
            if (pickedUp) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(theme.accent.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.OpenWith, contentDescription = "Moving", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
        if (settings.showAppNames) {
            Spacer(Modifier.height(6.dp))
            Text(
                app.label,
                color = if (focused) theme.textPrimary else theme.textPrimary.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Text(subtitle, color = theme.textSecondary, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun CardFace(app: LauncherApp, settings: LauncherSettings) {
    val banner = app.banner
    if (settings.useBanners && banner != null) {
        Image(
            bitmap = banner,
            contentDescription = app.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        val theme = settings.theme
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(theme.surface.copy(alpha = 0.95f), lerpToAccent(theme))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.fillMaxHeight(0.56f).aspectRatio(1f)
            )
        }
    }
}

private fun lerpToAccent(theme: LauncherTheme): Color =
    androidx.compose.ui.graphics.lerp(theme.surface, theme.accent, 0.22f).copy(alpha = 0.95f)

@Composable
private fun CheckBadge(checked: Boolean, theme: LauncherTheme, modifier: Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (checked) theme.accent else Color.Black.copy(alpha = 0.45f))
            .border(1.5.dp, if (checked) theme.accent else Color.White.copy(alpha = 0.8f), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

/** The "+ Add App" tile at the end of the Apps grid. */
@Composable
fun AddAppCard(settings: LauncherSettings, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = settings.theme
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused && settings.focusZoom) 1.08f else 1f, label = "add-scale")
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CardShape)
                .background(theme.surface.copy(alpha = 0.55f))
                .border(if (focused) 2.5.dp else 1.dp, if (focused) focusRingColor(theme) else theme.textSecondary.copy(alpha = 0.3f), CardShape)
                .onFocusChanged { focused = it.isFocused }
                .tvClickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add app", tint = theme.textPrimary, modifier = Modifier.size(28.dp))
        }
        if (settings.showAppNames) {
            Spacer(Modifier.height(6.dp))
            Text("+ Add App", color = theme.textSecondary, fontSize = 11.sp)
        }
    }
}

/** Short "2h ago" style label for the Recently Opened row. */
fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - timestamp) / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes} min ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 24 * 2 -> "Yesterday"
        else -> "${minutes / (60 * 24)} days ago"
    }
}
