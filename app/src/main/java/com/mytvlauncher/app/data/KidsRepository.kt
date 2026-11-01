package com.mytvlauncher.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class KidsState(
    /** Kids home is showing; leaving needs the PIN. */
    val active: Boolean,
    val allowed: Set<String>,
    val pin: String,
    /** Minutes per day; 0 = no limit. */
    val dailyLimitMin: Int,
    val usedTodayMin: Int
) {
    val minutesLeft: Int? get() = if (dailyLimitMin <= 0) null else (dailyLimitMin - usedTodayMin).coerceAtLeast(0)
    val timeUp: Boolean get() = minutesLeft == 0
}

/**
 * Kids mode: a separate, simple home with only the apps a grown-up allowed,
 * a PIN to leave, and an optional daily time limit.
 *
 * Time is measured the only way a launcher can without special access:
 * from when a kid opens an app on the kids home until the launcher comes
 * back to the front. The limit is enforced at the kids home (apps lock when
 * time's up); a running app isn't force-closed.
 */
class KidsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("kids_mode", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<KidsState> = _state

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun read(): KidsState {
        val usedDay = prefs.getString("used_day", null)
        return KidsState(
            active = prefs.getBoolean("active", false),
            allowed = prefs.getString("allowed", "")!!.split(',').filter { it.isNotBlank() }.toSet(),
            pin = prefs.getString("pin", "") ?: "",
            dailyLimitMin = prefs.getInt("limit", 0),
            usedTodayMin = if (usedDay == today()) prefs.getInt("used_sec", 0) / 60 else 0
        )
    }

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = read()
    }

    fun setActive(on: Boolean) = edit { putBoolean("active", on); remove("session_start") }
    fun setPin(pin: String) = edit { putString("pin", pin) }
    fun setLimit(minutes: Int) = edit { putInt("limit", minutes.coerceIn(0, 600)) }
    fun toggleAllowed(pkg: String) {
        val now = _state.value.allowed.let { if (pkg in it) it - pkg else it + pkg }
        edit { putString("allowed", now.joinToString(",")) }
    }

    /** A kid opened an app from the kids home. */
    fun startSession() = edit { putLong("session_start", System.currentTimeMillis()) }

    /** The launcher is back in front: count the time spent in the app. */
    fun endSession() {
        val start = prefs.getLong("session_start", 0)
        if (start == 0L) { _state.value = read(); return }
        val seconds = ((System.currentTimeMillis() - start) / 1000L).toInt().coerceIn(0, 6 * 3600)
        val day = today()
        val used = (if (prefs.getString("used_day", null) == day) prefs.getInt("used_sec", 0) else 0) + seconds
        edit {
            remove("session_start")
            putString("used_day", day)
            putInt("used_sec", used)
        }
    }

    fun resetToday() = edit { putString("used_day", today()); putInt("used_sec", 0) }
}
