package com.mytvlauncher.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvlauncher.app.data.ThemeRepository
import com.mytvlauncher.app.model.LauncherTheme
import com.mytvlauncher.app.model.ThemePresets

/**
 * Where "design your own" actually happens: pick a starting preset,
 * then override background/accent color and layout density. Everything
 * writes straight to SharedPreferences via ThemeRepository, so
 * HomeScreen picks up changes immediately (it collects the same
 * StateFlow).
 */
@Composable
fun SettingsScreen(themeRepository: ThemeRepository, onDone: () -> Unit) {
    val theme by themeRepository.currentTheme.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var displayName by remember { mutableStateOf(theme.displayName) }
    var wallpaperMessage by remember { mutableStateOf("") }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            wallpaperMessage = "Loading wallpaper…"
            val result = withContext(Dispatchers.IO) {
                // Own a bounded local copy; no persistent external URI grant is needed.
                val temporary = java.io.File(context.filesDir, "wallpaper-pending")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= 20 * 1024 * 1024) { "Choose an image smaller than 20 MB" }
                                output.write(buffer, 0, count)
                            }
                        }
                    } ?: error("Unable to open image")
                    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(temporary.path, options)
                    require(options.outWidth > 0 && options.outHeight > 0) { "Choose a supported image" }
                    val destination = java.io.File.createTempFile("wallpaper-", ".img", context.filesDir)
                    check(temporary.renameTo(destination)) { "Unable to save wallpaper" }
                    Result.success(destination.path)
                } catch (e: Exception) {
                    Result.failure<String>(e)
                } finally { temporary.delete() }
            }
            result.fold(onSuccess = { path ->
                val previous = theme.backgroundImageUrl
                themeRepository.setWallpaper(path)
                previous?.let { old ->
                    val file = java.io.File(old)
                    if (file.parentFile == context.filesDir && file.name.startsWith("wallpaper-")) file.delete()
                }
                wallpaperMessage = "Wallpaper saved"
            }, onFailure = { wallpaperMessage = it.message ?: "Unable to load wallpaper" })
        }
    }

    MaterialTheme(colors = darkColors(primary = theme.accent, surface = theme.surface,
        background = theme.background, onSurface = theme.textPrimary)) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(32.dp)
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = "Customize your home screen",
                color = theme.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text("Display name", color = theme.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = displayName, onValueChange = { displayName = it.take(40) },
                    singleLine = true, label = { Text("Name (optional)") },
                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = theme.textPrimary,
                        unfocusedBorderColor = theme.textSecondary, unfocusedLabelColor = theme.textSecondary))
                TvAction("Save name", theme) { themeRepository.setDisplayName(displayName) }
            }
            Text("Leave blank for a simple greeting.", color = theme.textSecondary)
            Spacer(Modifier.height(24.dp))
            Text("Wallpaper", color = theme.textSecondary)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("none" to "Solid", "aurora" to "Aurora", "dusk" to "Dusk").forEach { (id, label) ->
                    TvAction(label + if (theme.backgroundImageUrl == null && theme.wallpaperStyle == id) " ✓" else "", theme) {
                        themeRepository.setWallpaperStyle(id)
                    }
                }
                TvAction("Choose image", theme) {
                    try { wallpaperPicker.launch(arrayOf("image/*")) }
                    catch (_: android.content.ActivityNotFoundException) {
                        wallpaperMessage = "No image picker on this TV. Choose a built-in wallpaper."
                    }
                }
            }
            if (wallpaperMessage.isNotEmpty()) Text(wallpaperMessage, color = theme.textSecondary)
            Spacer(Modifier.height(24.dp))
            Text(text = "Presets", color = theme.textSecondary, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ThemePresets.all.forEach { preset ->
                    PresetSwatch(
                        preset = preset,
                        selected = preset.id == theme.id,
                        onClick = { themeRepository.selectPreset(preset.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "Accent color", color = theme.textSecondary, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            ColorRow(
                colors = listOf(
                    Color(0xFF5B8CFF), Color(0xFFFF7A59), Color(0xFF4CAF7D),
                    Color(0xFFE44C9A), Color(0xFFF2C14E), Color(0xFFFFFFFF)
                ),
                onPick = { color -> themeRepository.setCustomAccent(color.toHex()) }
            )

            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "Background color", color = theme.textSecondary, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            ColorRow(
                colors = listOf(
                    Color(0xFF0B0E14), Color(0xFF1A0F1A), Color(0xFF0E1A14),
                    Color(0xFFF4F4F6), Color(0xFF101010), Color(0xFF14202B)
                ),
                onPick = { color -> themeRepository.setCustomBackground(color.toHex()) }
            )

            Spacer(modifier = Modifier.height(32.dp))
            Row(modifier = Modifier.tvAction(theme) { themeRepository.setShowClock(!theme.showClock) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Show clock", color = theme.textSecondary, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = theme.showClock,
                    onCheckedChange = null
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Grid columns: ${theme.gridColumns}", color = theme.textSecondary, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvAction("Fewer columns", theme, enabled = theme.gridColumns > 4) {
                    themeRepository.setGridColumns(theme.gridColumns - 1)
                }
                TvAction("More columns", theme, enabled = theme.gridColumns < 8) {
                    themeRepository.setGridColumns(theme.gridColumns + 1)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            TvAction("Done", theme, onClick = onDone)
        }
    }
    }
}

@Composable
private fun PresetSwatch(preset: LauncherTheme, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(preset.background)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) preset.accent else Color.Gray,
                    shape = RoundedCornerShape(12.dp)
                )
                .semantics { contentDescription = preset.name }
                .tvAction(preset, selected = selected, onClick = onClick)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = preset.name, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun ColorRow(colors: List<Color>, onPick: (Color) -> Unit) {
    val theme = com.mytvlauncher.app.model.ThemePresets.Midnight
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color.Gray, CircleShape)
                    .semantics { contentDescription = "Color ${color.toHex()}" }
                    .tvAction(theme) { onPick(color) }
            )
        }
    }
}

private fun Color.toHex(): String {
    val argb = this.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}
