package com.mytvlauncher.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Slider
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(32.dp)
    ) {
        Column {
            Text(
                text = "Customize your home screen",
                color = theme.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Show clock", color = theme.textSecondary, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = theme.showClock,
                    onCheckedChange = { themeRepository.setShowClock(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Grid columns: ${theme.gridColumns}", color = theme.textSecondary, fontSize = 16.sp)
            Slider(
                value = theme.gridColumns.toFloat(),
                onValueChange = { themeRepository.setGridColumns(it.toInt()) },
                valueRange = 4f..8f,
                steps = 3,
                modifier = Modifier.width(320.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onDone) {
                Text("Done")
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
                .clickable { onClick() }
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = preset.name, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun ColorRow(colors: List<Color>, onPick: (Color) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color.Gray, CircleShape)
                    .clickable { onPick(color) }
            )
        }
    }
}

private fun Color.toHex(): String {
    val argb = this.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}
