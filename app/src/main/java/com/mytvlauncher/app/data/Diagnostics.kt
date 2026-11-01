package com.mytvlauncher.app.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Settings › Diagnostics. Answers two questions on the TV itself, with no
 * computer attached:
 *  1. Is any form of ADB (USB or network) actually switched on / listening?
 *  2. Can this launcher read other apps' Android TV preview rows
 *     (Netflix/Prime "channels", Watch Next)? AOSP says no without the
 *     signature|privileged ACCESS_ALL_EPG_DATA permission, but OEM builds
 *     vary, so this checks for real.
 */
object Diagnostics {

    data class Line(val label: String, val value: String, val good: Boolean? = null)

    /** Blocking — call from a background thread. */
    fun run(context: Context): List<Line> {
        val out = mutableListOf<Line>()
        val cr = context.contentResolver

        // --- Device ---
        out += Line("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
        out += Line("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}")
        out += Line("TV IP address", ipAddress(context) ?: "not connected")

        // --- ADB ---
        fun global(name: String): String = try {
            val v = Settings.Global.getString(cr, name)
            when (v) { null -> "not present"; "1" -> "ON"; "0" -> "off"; else -> v }
        } catch (e: Exception) { "unreadable" }

        val dev = global(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)
        val usb = global(Settings.Global.ADB_ENABLED)
        val wifi = global("adb_wifi_enabled")
        out += Line("Developer options", dev, dev == "ON")
        out += Line("USB debugging (adb_enabled)", usb, usb == "ON")
        out += Line("Wireless debugging (Android 11+)", wifi, if (wifi == "not present") null else wifi == "ON")

        val tcpProp = listOf("service.adb.tcp.port", "persist.adb.tcp.port")
            .map { it to getprop(it) }
        out += Line("adb TCP port property", tcpProp.joinToString { (k, v) -> "$k=${v.ifBlank { "—" }}" })

        val ports = (tcpProp.mapNotNull { it.second.toIntOrNull() }.filter { it > 0 } + 5555).distinct()
        ports.forEach { port ->
            val open = portOpen(port)
            out += Line(
                "adb listening on port $port",
                if (open) "YES — try: adb connect ${ipAddress(context) ?: "<tv-ip>"}:$port" else "no",
                open
            )
        }

        out += Line(
            "Developer options screen",
            if (devSettingsIntents(context).isNotEmpty()) "found (use the button below)" else "not found on this TV",
            devSettingsIntents(context).isNotEmpty()
        )

        // --- Preview rows (what Netflix/Prime publish for the Google home screen) ---
        out += providerProbe(context, "TV channels (all apps)", TvContract.Channels.CONTENT_URI)
        out += providerProbe(context, "Preview programs (posters)", TvContract.PreviewPrograms.CONTENT_URI)
        out += providerProbe(context, "Watch Next", TvContract.WatchNextPrograms.CONTENT_URI)

        return out
    }

    private fun providerProbe(context: Context, label: String, uri: Uri): Line = try {
        context.contentResolver.query(uri, arrayOf("package_name"), null, null, null).use { c ->
            if (c == null) return Line(label, "provider not available", false)
            val pkgs = mutableMapOf<String, Int>()
            val col = c.getColumnIndex("package_name")
            while (c.moveToNext()) {
                val p = if (col >= 0) c.getString(col) ?: "?" else "?"
                pkgs[p] = (pkgs[p] ?: 0) + 1
            }
            val others = pkgs.filterKeys { it != context.packageName }
            if (others.isEmpty()) Line(label, "0 rows visible from other apps (blocked)", false)
            else Line(label, "VISIBLE: " + others.entries.joinToString { "${it.key.substringAfterLast('.')}×${it.value}" }, true)
        }
    } catch (e: SecurityException) {
        Line(label, "blocked: ${e.message?.take(90)}", false)
    } catch (e: Exception) {
        Line(label, "error: ${e.javaClass.simpleName} ${e.message?.take(80) ?: ""}", false)
    }

    private fun getprop(name: String): String = try {
        val p = Runtime.getRuntime().exec(arrayOf("getprop", name))
        p.inputStream.bufferedReader().readText().trim().also { p.waitFor() }
    } catch (e: Exception) { "" }

    private fun portOpen(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 600); true }
    } catch (e: Exception) { false }

    private fun ipAddress(context: Context): String? = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
            ?.hostAddress
    } catch (e: Exception) { null }

    /** Every way we know of to reach developer options; OEMs hide the menu entry but often keep the screen. */
    fun devSettingsIntents(context: Context): List<Intent> {
        val candidates = listOf(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Intent().setComponent(ComponentName("com.android.tv.settings", "com.android.tv.settings.system.development.DevelopmentActivity")),
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$DevelopmentSettingsDashboardActivity")),
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.DevelopmentSettings"))
        )
        return candidates
            .map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .filter { it.resolveActivity(context.packageManager) != null }
    }

    /** About screen, where tapping "Build" 7 times unlocks developer options. */
    fun aboutIntent(): Intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
