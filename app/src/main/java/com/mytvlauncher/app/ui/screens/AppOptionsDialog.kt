package com.mytvlauncher.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mytvlauncher.app.data.LauncherSettings
import com.mytvlauncher.app.model.AppDetails
import com.mytvlauncher.app.model.LauncherApp
import com.mytvlauncher.app.ui.components.SettingsRow
import com.mytvlauncher.app.ui.components.glass
import com.mytvlauncher.app.ui.components.requestFocusWhenReady
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Screen 5: what long-press OK (or the Menu key) on any app opens. */
@Composable
fun AppOptionsDialog(
    app: LauncherApp,
    details: AppDetails?,
    settings: LauncherSettings,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onToggleFavourite: () -> Unit,
    onMove: () -> Unit,
    onToggleLiveTv: () -> Unit,
    onUninstall: () -> Unit,
    onAppInfo: () -> Unit
) {
    val theme = settings.theme
    val isFavourite = app.packageName in settings.favourites
    val isLive = settings.isLiveTv(app)
    val openRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { openRequester.requestFocusWhenReady(attempts = 20) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .glass(theme, radius = 18.dp, alpha = 0.96f)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.background.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(app.label, color = theme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    val line1 = listOfNotNull(
                        details?.versionName?.let { "Version $it" },
                        details?.apkSizeBytes?.takeIf { it > 0 }?.let { formatSize(it) }
                    ).joinToString("  |  ")
                    if (line1.isNotEmpty()) Text(line1, color = theme.textSecondary, fontSize = 11.sp, maxLines = 1)
                    details?.installedAt?.takeIf { it > 0 }?.let {
                        Text(
                            "Installed on " + SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(it)),
                            color = theme.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            val rowMod = Modifier.padding(vertical = 3.dp)
            val none: @Composable () -> Unit = {}
            SettingsRow("Open", theme, rowMod, icon = Icons.Filled.OpenInNew, focusRequester = openRequester, onClick = onOpen, trailing = none)
            SettingsRow(
                if (isFavourite) "Remove from Favourites" else "Add to Favourites",
                theme, rowMod,
                icon = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                onClick = onToggleFavourite, trailing = none
            )
            SettingsRow(
                "Move", theme, rowMod, icon = Icons.Filled.OpenWith,
                subtitle = if (isFavourite) "Change its place in Favourites" else "Adds it to Favourites, then reorder",
                onClick = onMove, trailing = none
            )
            SettingsRow(
                if (isLive) "Remove from Live TV" else "Add to Live TV",
                theme, rowMod, icon = Icons.Filled.LiveTv, onClick = onToggleLiveTv, trailing = none
            )
            SettingsRow(
                "Uninstall", theme, rowMod, icon = Icons.Filled.DeleteOutline,
                subtitle = if (app.isUserInstalled) null else "System app — can't be removed",
                enabled = app.isUserInstalled,
                onClick = onUninstall, trailing = none
            )
            SettingsRow("App Info", theme, rowMod, icon = Icons.Filled.Info, onClick = onAppInfo, trailing = none)
        }
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) String.format(Locale.getDefault(), "%.0f MB", mb)
    else String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
}
