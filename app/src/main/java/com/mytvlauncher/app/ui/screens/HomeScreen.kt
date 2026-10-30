package com.mytvlauncher.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.mytvlauncher.app.data.AppRepository
import com.mytvlauncher.app.data.BulkUninstallController
import com.mytvlauncher.app.data.ThemeRepository
import com.mytvlauncher.app.model.LauncherApp
import com.mytvlauncher.app.model.LauncherTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(appRepository: AppRepository, themeRepository: ThemeRepository,
               bulkUninstallController: BulkUninstallController) {
    val theme by themeRepository.currentTheme.collectAsState()
    val context = LocalContext.current
    var apps by remember { mutableStateOf(appRepository.loadLauncherApps()) }
    var managing by rememberSaveable { mutableStateOf(false) }
    var selectedPackages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var confirm by remember { mutableStateOf(false) }
    val uninstallFocus = remember { FocusRequester() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                apps = appRepository.loadLauncherApps()
                selectedPackages = selectedPackages.filter { pkg -> apps.any { it.packageName == pkg } }
                themeRepository.refresh()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(bulkUninstallController.isRunning) {
        if (!bulkUninstallController.isRunning) apps = appRepository.loadLauncherApps()
    }
    BackHandler(managing) {
        if (bulkUninstallController.isRunning) bulkUninstallController.cancel()
        managing = false
        selectedPackages = emptyList()
    }
    MaterialTheme(colors = darkColors(primary = theme.accent, surface = theme.surface,
        background = theme.background, onSurface = theme.textPrimary, onPrimary = theme.background)) {
        Box(Modifier.fillMaxSize()) {
            Wallpaper(theme)
            Column(Modifier.fillMaxSize().onPreviewKeyEvent {
                if (managing && selectedPackages.isNotEmpty() && !bulkUninstallController.isRunning &&
                    it.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU) {
                    if (it.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP) uninstallFocus.requestFocus()
                    true
                } else false
            }.padding(horizontal = 40.dp, vertical = 28.dp)) {
                if (managing) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Manage Apps", color = theme.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Text("Select apps to remove • System apps are protected", color = theme.textSecondary)
                        }
                        TvAction("Back to home", theme) {
                            if (bulkUninstallController.isRunning) bulkUninstallController.cancel()
                            managing = false
                            selectedPackages = emptyList()
                        }
                    }
                } else {
                    HomeHeader(theme)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvAction("Manage Apps", theme) { managing = true }
                        TvAction("Personalise", theme) {
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        }
                    }
                }
                if (managing) {
                    Spacer(Modifier.height(12.dp))
                    Text(bulkUninstallController.status.ifEmpty { "Android asks you to confirm each removal. Cancel stops the queue." },
                        color = theme.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvAction("Uninstall (${selectedPackages.size})", theme,
                            enabled = selectedPackages.isNotEmpty() && !bulkUninstallController.isRunning,
                            modifier = Modifier.focusRequester(uninstallFocus)) { confirm = true }
                        TvAction(if (bulkUninstallController.isRunning) "Stop queue" else "Clear selection", theme) {
                            if (bulkUninstallController.isRunning) bulkUninstallController.cancel()
                            selectedPackages = emptyList()
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(if (managing) "${selectedPackages.size} selected  ·  Menu jumps to uninstall" else "Your apps  ·  ${apps.size}",
                    color = theme.textSecondary, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                if (apps.isEmpty()) Text("No launchable apps installed", color = theme.textPrimary)
                LazyVerticalGrid(columns = GridCells.Fixed(theme.gridColumns),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f)) {
                    items(apps, key = { it.packageName }) { app ->
                        val eligible = appRepository.isUninstallable(app)
                        AppTile(app, theme, managing, eligible, app.packageName in selectedPackages,
                            enabled = !bulkUninstallController.isRunning && (!managing || eligible)) {
                            if (managing) {
                                selectedPackages = if (app.packageName in selectedPackages)
                                    selectedPackages - app.packageName else selectedPackages + app.packageName
                            } else try {
                                appRepository.launch(app)
                            } catch (_: android.content.ActivityNotFoundException) {
                                Toast.makeText(context, "App is no longer available", Toast.LENGTH_SHORT).show()
                                apps = appRepository.loadLauncherApps()
                            } catch (_: SecurityException) {
                                Toast.makeText(context, "Android blocked opening this app", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                if (!managing) {
                    Text("D-pad to browse  ·  OK to open", color = theme.textSecondary, fontSize = 12.sp)
                }
            }
        }
        if (confirm) {
            val selected = apps.filter { it.packageName in selectedPackages && appRepository.isUninstallable(it) }
            AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Remove ${selected.size} apps?") },
                text = {
                    Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                        Text(selected.joinToString("\n") { it.label } + "\n\nEach app requires Android confirmation. Cancelling keeps the remaining apps.")
                    }
                },
                confirmButton = { TvAction("Continue", theme, enabled = selected.isNotEmpty()) {
                    confirm = false
                    selectedPackages = emptyList()
                    bulkUninstallController.start(selected)
                } },
                dismissButton = { TvAction("Keep apps", theme) { confirm = false } })
        }
    }
}

@Composable
private fun HomeHeader(theme: LauncherTheme) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); kotlinx.coroutines.delay(15000) } }
    val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text("MAKE YOURSELF AT HOME", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(greeting + theme.displayName.takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty(),
                color = theme.textPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            Text("Your TV. Your space.", color = theme.textSecondary, fontSize = 16.sp)
        }
        if (theme.showClock) Column(horizontalAlignment = Alignment.End) {
            Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(now), color = theme.textPrimary, fontSize = 26.sp)
            Text(SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now), color = theme.textSecondary)
        }
    }
}

@Composable
private fun AppTile(app: LauncherApp, theme: LauncherTheme, managing: Boolean,
                    eligible: Boolean, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bitmap = remember(app.icon) { app.icon.toBitmap(96, 96).asImageBitmap() }
    Column(Modifier.fillMaxWidth().height(136.dp).clip(RoundedCornerShape(12.dp))
        .background(theme.surface.copy(alpha = 0.94f)).tvAction(theme, enabled, selected, onClick)
        .padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(8.dp))
        Text(app.label, color = theme.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
        if (managing) Text(if (!eligible) "Protected" else if (selected) "✓ Selected" else "Select",
            color = if (selected) theme.accent else theme.textSecondary, fontSize = 12.sp)
    }
}
