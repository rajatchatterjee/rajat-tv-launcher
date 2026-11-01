package com.mytvlauncher.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvlauncher.app.data.KidsRepository
import com.mytvlauncher.app.data.KidsState
import com.mytvlauncher.app.data.LauncherSettings
import com.mytvlauncher.app.model.LauncherApp
import com.mytvlauncher.app.model.LauncherTheme
import com.mytvlauncher.app.ui.components.AppCard
import com.mytvlauncher.app.ui.components.ButtonStyle
import com.mytvlauncher.app.ui.components.SettingsRow
import com.mytvlauncher.app.ui.components.TvButton
import com.mytvlauncher.app.ui.components.glass
import com.mytvlauncher.app.ui.components.requestFocusWhenReady

/** The kids home: only allowed apps, big tiles, time left, PIN to leave. */
@Composable
fun KidsHome(
    kids: KidsState,
    apps: List<LauncherApp>,
    settings: LauncherSettings,
    onLaunch: (LauncherApp) -> Unit,
    onExit: () -> Unit
) {
    val theme = settings.theme
    val allowed = remember(apps, kids.allowed) { apps.filter { it.packageName in kids.allowed } }
    val first = remember { FocusRequester() }
    var askPin by remember { mutableStateOf(false) }
    LaunchedEffect(allowed.size, kids.timeUp) { if (!kids.timeUp) first.requestFocusWhenReady() }
    BackHandler { } // Back does nothing on the kids home

    Box(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hi there!", color = theme.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (kids.timeUp) "That's all the TV time for today." else "Pick something to play",
                        color = theme.textSecondary, fontSize = 16.sp
                    )
                }
                kids.minutesLeft?.let { left ->
                    Row(
                        Modifier.clip(CircleShape).background((if (left <= 10) Color(0xFFE5484D) else theme.accent).copy(alpha = 0.85f))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(formatMinutes(left) + " left today", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(14.dp))
                }
                TvButton("Grown-ups", theme, icon = Icons.Filled.Lock) { askPin = true }
            }
            Spacer(Modifier.height(24.dp))
            when {
                kids.timeUp -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "Time's up! Come back tomorrow.\nA grown-up can unlock more time.",
                        color = theme.textPrimary, fontSize = 22.sp, textAlign = TextAlign.Center, lineHeight = 32.sp
                    )
                }
                allowed.isEmpty() -> EmptyHint("No apps allowed yet. A grown-up can pick them in Settings › Kids mode.", settings)
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    itemsIndexed(allowed, key = { _, a -> a.packageName }) { i, app ->
                        AppCard(
                            app = app,
                            settings = settings.copy(showAppNames = true, focusZoom = true),
                            focusRequester = if (i == 0) first else null,
                            onClick = { onLaunch(app) }
                        )
                    }
                }
            }
        }
        if (askPin) {
            PinPad(
                title = "Enter the grown-up PIN",
                theme = theme,
                onDismiss = { askPin = false },
                onDone = { entered ->
                    if (entered == kids.pin) { askPin = false; onExit(); true } else false
                }
            )
        }
    }
}

fun formatMinutes(m: Int): String = when {
    m >= 60 && m % 60 == 0 -> "${m / 60} h"
    m >= 60 -> "${m / 60} h ${m % 60} min"
    else -> "$m min"
}

/**
 * Four-digit PIN pad for the remote. [onDone] returns false for a wrong PIN
 * (the pad shakes its message and clears).
 */
@Composable
fun PinPad(title: String, theme: LauncherTheme, onDismiss: () -> Unit, onDone: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }
    BackHandler { onDismiss() }
    fun press(d: String) {
        if (pin.length >= 4) return
        pin += d
        error = false
        if (pin.length == 4) {
            if (!onDone(pin)) { error = true; pin = "" }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.glass(theme, radius = 22.dp, alpha = 0.95f).padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = theme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier.size(14.dp).clip(CircleShape)
                            .background(if (i < pin.length) theme.accent else theme.textSecondary.copy(alpha = 0.3f))
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(if (error) "Wrong PIN, try again" else " ", color = Color(0xFFFF8A80), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫"))
            keys.forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { k ->
                        when (k) {
                            "" -> Spacer(Modifier.size(width = 62.dp, height = 44.dp))
                            "⌫" -> Box(Modifier.width(62.dp)) {
                                TvButton("", theme, icon = Icons.Filled.Backspace, modifier = Modifier.fillMaxWidth()) {
                                    pin = pin.dropLast(1)
                                }
                            }
                            else -> TvButton(
                                k, theme,
                                modifier = Modifier.width(62.dp),
                                focusRequester = if (r == 0 && k == "1") first else null
                            ) { press(k) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            TvButton("Cancel", theme) { onDismiss() }
        }
    }
}

/** Settings › Kids mode. */
@Composable
fun KidsSettingsContent(
    kids: KidsState,
    repo: KidsRepository,
    apps: List<LauncherApp>,
    settings: LauncherSettings,
    first: FocusRequester,
    onShowPinPad: (title: String, onDone: (String) -> Boolean) -> Unit
) {
    val theme = settings.theme
    SettingsRow(
        "Start kids mode", theme,
        subtitle = when {
            kids.pin.length != 4 -> "Set a grown-up PIN first"
            kids.allowed.isEmpty() -> "Pick at least one app below"
            else -> "Shows only the apps ticked below; the PIN is needed to leave"
        },
        focusRequester = first
    ) {
        when {
            kids.pin.length != 4 -> onShowPinPad("Choose a 4-digit grown-up PIN") { p -> repo.setPin(p); true }
            kids.allowed.isNotEmpty() -> repo.setActive(true)
        }
    }
    SettingsRow(
        if (kids.pin.length == 4) "Change grown-up PIN" else "Set grown-up PIN", theme,
        subtitle = "Needed to leave kids mode"
    ) {
        if (kids.pin.length == 4) {
            onShowPinPad("Enter the current PIN") { old ->
                if (old != kids.pin) false
                else { onShowPinPad("Choose a new 4-digit PIN") { p -> repo.setPin(p); true }; true }
            }
        } else onShowPinPad("Choose a 4-digit grown-up PIN") { p -> repo.setPin(p); true }
    }
    val steps = listOf(0, 30, 45, 60, 90, 120, 150, 180, 240)
    Row(
        Modifier.fillMaxWidth().glass(theme, radius = 12.dp, alpha = 0.55f).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Daily time limit", color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                if (kids.dailyLimitMin == 0) "No limit" else "${formatMinutes(kids.dailyLimitMin)} a day · ${formatMinutes(kids.usedTodayMin)} used today",
                color = theme.textSecondary, fontSize = 12.sp
            )
        }
        val idx = steps.indexOfFirst { it >= kids.dailyLimitMin }.let { if (it < 0) steps.lastIndex else it }
        TvButton("–", theme) { repo.setLimit(steps[(idx - 1).coerceAtLeast(0)]) }
        Spacer(Modifier.width(8.dp))
        TvButton("+", theme) { repo.setLimit(steps[(idx + 1).coerceAtMost(steps.lastIndex)]) }
        Spacer(Modifier.width(8.dp))
        TvButton("Reset today", theme) { repo.resetToday() }
    }
    Text("Apps kids can use", color = theme.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    apps.sortedBy { it.label.lowercase() }.forEach { app ->
        SettingsRow(
            app.label, theme,
            onClick = { repo.toggleAllowed(app.packageName) },
            trailing = {
                if (app.packageName in kids.allowed) {
                    Icon(Icons.Filled.Check, contentDescription = "Allowed", tint = theme.accent, modifier = Modifier.size(20.dp))
                }
            }
        )
    }
}
