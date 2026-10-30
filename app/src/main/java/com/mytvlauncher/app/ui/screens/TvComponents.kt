package com.mytvlauncher.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mytvlauncher.app.model.LauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared focus treatment for every TV action; clickable supplies D-pad/Enter semantics. */
@Composable
fun Modifier.tvAction(theme: LauncherTheme, enabled: Boolean = true, selected: Boolean = false,
                      onClick: () -> Unit): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .border(if (focused) 3.dp else if (selected) 2.dp else 0.dp,
            if (focused || selected) theme.accent else Color.Transparent, RoundedCornerShape(12.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
}

@Composable
fun TvAction(label: String, theme: LauncherTheme, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Text(label, color = if (enabled) theme.textPrimary else theme.textSecondary,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(theme.surface)
            .tvAction(theme, enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp))
}

@Composable
fun Wallpaper(theme: LauncherTheme) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, theme.backgroundImageUrl) {
        value = withContext(Dispatchers.IO) {
            try {
                theme.backgroundImageUrl?.let { path ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 1920 || bounds.outHeight / sample > 1080) sample *= 2
                    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                }
            } catch (_: Exception) { null }
        }
    }
    val colors = when (theme.wallpaperStyle) {
        "aurora" -> listOf(Color(0xFF103B40), Color(0xFF22264E), Color(0xFF090D19))
        "dusk" -> listOf(Color(0xFF542E47), Color(0xFF292348), Color(0xFF131527))
        else -> listOf(theme.background, theme.background)
    }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors)))
    bitmap?.let {
        Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(theme.background.copy(alpha = 0.78f)))
    }
}
