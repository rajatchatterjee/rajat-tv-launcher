package com.mytvlauncher.app.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mytvlauncher.app.data.ContentItem
import com.mytvlauncher.app.model.LauncherTheme

/**
 * Full-screen backdrop behind Home for the spotlighted show: crossfades
 * between images, drifts with a slow Ken Burns zoom/pan, and reports the
 * image's colour to [Ambient]. Scrims keep the left (text) and bottom
 * (rows) readable.
 */
@Composable
fun SpotlightBackdrop(url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier.fillMaxSize()) {
        Crossfade(targetState = url, animationSpec = tween(1100), label = "backdrop") { u ->
            if (u != null) {
                val drift = rememberInfiniteTransition(label = "kenburns")
                val p by drift.animateFloat(
                    0f, 1f,
                    infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Reverse),
                    label = "kb"
                )
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(u)
                        .allowHardware(false) // so Ambient can read pixels
                        .listener(onSuccess = { _, result ->
                            (result.drawable as? BitmapDrawable)?.bitmap?.let { bmp -> Ambient.from(bmp)?.let { Ambient.color = it } }
                        })
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val z = 1.04f + 0.10f * p
                            scaleX = z; scaleY = z
                            translationX = -size.width * 0.03f * p
                            translationY = size.height * 0.015f * p
                        }
                )
            }
        }
        if (url != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 0.92f),
                                0.38f to Color.Black.copy(alpha = 0.62f),
                                0.70f to Color.Black.copy(alpha = 0.10f),
                                1f to Color.Black.copy(alpha = 0.05f)
                            )
                        )
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.35f),
                                0.22f to Color.Transparent,
                                0.48f to Color.Transparent,
                                0.72f to Color.Black.copy(alpha = 0.78f),
                                1f to Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    }
            )
        }
    }
}

/** An app banner, drifting slowly inside a framed card (spotlight for apps without show artwork). */
@Composable
fun KenBurnsBanner(banner: ImageBitmap, modifier: Modifier = Modifier) {
    val drift = rememberInfiniteTransition(label = "banner-kb")
    val p by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Reverse), label = "bkb")
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .shadow(30.dp, shape, spotColor = Ambient.color ?: Color.Black)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
    ) {
        Image(
            bitmap = banner,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val z = 1.0f + 0.08f * p
                scaleX = z; scaleY = z
                translationX = size.width * 0.02f * (p - 0.5f)
            }
        )
        // A moving sheen across the card.
        Box(
            Modifier.fillMaxSize().drawBehind {
                val x = size.width * (-0.4f + 1.8f * p)
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                        start = androidx.compose.ui.geometry.Offset(x - size.width * 0.25f, 0f),
                        end = androidx.compose.ui.geometry.Offset(x + size.width * 0.25f, size.height)
                    )
                )
            }
        )
    }
}

private val ContentShape = RoundedCornerShape(12.dp)

/** A 16:9 show/film card for the content rows: backdrop art, title, service icon. */
@Composable
fun ContentCard(
    item: ContentItem,
    serviceIcon: ImageBitmap?,
    theme: LauncherTheme,
    zoomOnFocus: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    saved: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (focused) 1f else 0f, tween(220), label = "lift")
    val zoom = if (zoomOnFocus) 0.10f else 0.03f
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .graphicsLayer {
                scaleX = 1f + zoom * lift; scaleY = 1f + zoom * lift
                translationY = -8.dp.toPx() * lift
            }
            .shadow((2 + 22 * lift).dp, ContentShape, clip = false, spotColor = if (focused) (Ambient.color ?: theme.accent) else Color.Black)
            .clip(ContentShape)
            .background(theme.surface)
            .optionalFocusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onLongClick = onLongClick, onClick = onClick)
    ) {
        AsyncImage(
            model = item.backdropUrl?.replace("/w1280/", "/w500/") ?: item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
            )
        )
        Row(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (serviceIcon != null) {
                Image(serviceIcon, contentDescription = null, modifier = Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(5.dp))
            }
            Text(
                item.title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (saved) {
            androidx.compose.material.Icon(
                Icons.Filled.Bookmark,
                contentDescription = "In My List",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(2.dp)
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .border(if (focused) 2.5.dp else 1.dp, if (focused) focusRingColor(theme) else Color.White.copy(alpha = 0.07f), ContentShape)
        )
    }
}
