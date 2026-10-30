package com.mytvlauncher.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mytvlauncher.app.data.LauncherSettings
import com.mytvlauncher.app.data.WeatherInfo
import com.mytvlauncher.app.data.WeatherRepository
import com.mytvlauncher.app.model.LauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NavItem(val label: String, val icon: ImageVector)

@Composable
fun Sidebar(
    theme: LauncherTheme,
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(176.dp)
            .padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
            CrownMark(Modifier.size(width = 18.dp, height = 14.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Rajat ") }
                    withStyle(SpanStyle(color = theme.accent, fontWeight = FontWeight.SemiBold)) { append("TV") }
                    append(" Launcher")
                },
                color = theme.textPrimary,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(30.dp))
        items.forEachIndexed { index, item ->
            SidebarItem(item, selected = index == selectedIndex, theme = theme) { onSelect(index) }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SidebarItem(item: NavItem, selected: Boolean, theme: LauncherTheme, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        when {
            selected -> theme.accent
            focused -> theme.textPrimary.copy(alpha = 0.16f)
            else -> Color.Transparent
        },
        label = "nav-bg"
    )
    val content = if (selected) Color.White else theme.textPrimary.copy(alpha = if (focused) 1f else 0.82f)
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(if (focused) 2.dp else 0.dp, if (focused) focusRingColor(theme) else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(item.label, color = content, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Top-right cluster from the mockup: date · time, weather, network, settings. */
@Composable
fun TopBar(settings: LauncherSettings, weather: WeatherInfo?, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val theme = settings.theme
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var network by remember { mutableStateOf(networkKind(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            network = networkKind(context)
            delay(15_000)
        }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (settings.showClock) {
            val time = SimpleDateFormat(if (settings.use24Hour) "HH:mm" else "h:mm a", Locale.getDefault()).format(now)
            val date = SimpleDateFormat(settings.dateStyle.pattern, Locale.getDefault()).format(now)
            Text(date, color = theme.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            Text(time, color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(16.dp))
        }
        if (weather != null) {
            Icon(weatherIconFor(weather.weatherCode), contentDescription = null, tint = theme.textPrimary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("${weather.tempC}°", color = theme.textPrimary, fontSize = 13.sp)
            Spacer(Modifier.width(16.dp))
        }
        Icon(
            when (network) {
                NetworkKind.WIFI -> Icons.Filled.Wifi
                NetworkKind.ETHERNET -> Icons.Filled.SettingsEthernet
                NetworkKind.NONE -> Icons.Filled.WifiOff
            },
            contentDescription = network.name,
            tint = if (network == NetworkKind.NONE) Color(0xFFFF8A80) else theme.textPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        RoundIconButton(Icons.Filled.Settings, theme, size = 32.dp, contentDescription = "Settings", onClick = onSettings)
    }
}

enum class NetworkKind { WIFI, ETHERNET, NONE }

private fun networkKind(context: Context): NetworkKind = try {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    when {
        caps == null -> NetworkKind.NONE
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.ETHERNET
        else -> NetworkKind.WIFI
    }
} catch (e: Exception) {
    NetworkKind.WIFI
}

private fun weatherIconFor(code: Int): ImageVector = when (code) {
    0 -> Icons.Filled.WbSunny
    in 51..67, in 80..82 -> Icons.Filled.Grain
    in 71..77 -> Icons.Filled.AcUnit
    in 95..99 -> Icons.Filled.Bolt
    else -> Icons.Filled.Cloud
}

// Pune, Maharashtra — used if location permission is denied or no fix is cached.
private const val FALLBACK_LAT = 18.5204
private const val FALLBACK_LON = 73.8567

/** Current weather, refreshed every 30 minutes. Null until the first fetch succeeds. */
@Composable
fun rememberWeather(refreshKey: Any): WeatherInfo? {
    val context = LocalContext.current
    var weather by remember { mutableStateOf<WeatherInfo?>(null) }
    val repo = remember { WeatherRepository() }
    LaunchedEffect(refreshKey) {
        while (true) {
            val (lat, lon) = deviceLocationOrNull(context) ?: (FALLBACK_LAT to FALLBACK_LON)
            withContext(Dispatchers.IO) { repo.fetchCurrentWeather(lat, lon) }?.let { weather = it }
            delay(30 * 60_000L)
        }
    }
    return weather
}

private fun deviceLocationOrNull(context: Context): Pair<Double, Double>? = try {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        != PackageManager.PERMISSION_GRANTED
    ) {
        null
    } else {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstNotNullOfOrNull { provider ->
                try {
                    lm?.getLastKnownLocation(provider)
                } catch (e: SecurityException) {
                    null
                }
            }
            ?.let { it.latitude to it.longitude }
    }
} catch (e: Exception) {
    null
}
