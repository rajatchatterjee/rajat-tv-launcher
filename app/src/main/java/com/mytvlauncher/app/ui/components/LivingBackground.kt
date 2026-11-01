package com.mytvlauncher.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import com.mytvlauncher.app.data.WeatherInfo
import com.mytvlauncher.app.model.LauncherTheme
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * The launcher's "mood" colour: taken from whatever the Home spotlight is
 * showing (a show's backdrop or an app's banner) and used to tint the
 * living background and focus glows, so the whole screen shifts with it.
 */
object Ambient {
    var color by mutableStateOf<Color?>(null)

    /** Average colour of a bitmap, nudged brighter/more saturated so it reads as light. */
    fun from(bitmap: Bitmap): Color? = try {
        val small = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        var r = 0L; var g = 0L; var b = 0L
        for (x in 0 until 8) for (y in 0 until 8) {
            val p = small.getPixel(x, y)
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
        }
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV((r / 64).toInt(), (g / 64).toInt(), (b / 64).toInt(), hsv)
        hsv[1] = (hsv[1] * 1.4f).coerceIn(0.25f, 0.9f)
        hsv[2] = hsv[2].coerceIn(0.55f, 0.95f)
        Color(android.graphics.Color.HSVToColor(hsv))
    } catch (e: Exception) { null }
}

/** Seconds since this composable appeared, ticking every frame. Read it only inside draw code. */
@Composable
fun rememberFrameSeconds(): State<Float> {
    // Only tick while the launcher is on screen, so nothing animates (or
    // burns CPU) behind Netflix etc.
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    return produceState(0f, owner) {
        owner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            var start = -1L
            val offset = value
            while (true) {
                withFrameNanos { now ->
                    if (start < 0) start = now
                    value = offset + (now - start) / 1_000_000_000f
                }
            }
        }
    }
}

private class Mote(r: Random) {
    val x = r.nextFloat(); val y = r.nextFloat()
    val speed = 0.004f + r.nextFloat() * 0.012f
    val size = 1.2f + r.nextFloat() * 2.6f
    val phase = r.nextFloat() * 6.28f
    val wobble = 0.2f + r.nextFloat() * 0.6f
}

private class Drop(r: Random) {
    val x = r.nextFloat() * 1.3f - 0.15f; val y = r.nextFloat()
    val speed = 0.9f + r.nextFloat() * 0.7f
    val len = 0.025f + r.nextFloat() * 0.035f
    val alpha = 0.12f + r.nextFloat() * 0.22f
}

private enum class Sky { CLEAR, CLOUDY, FOG, RAIN, STORM, SNOW }

private fun skyFor(code: Int): Sky = when (code) {
    0, 1 -> Sky.CLEAR
    2, 3 -> Sky.CLOUDY
    45, 48 -> Sky.FOG
    in 51..67, in 80..82 -> Sky.RAIN
    in 71..77, 85, 86 -> Sky.SNOW
    in 95..99 -> Sky.STORM
    else -> Sky.CLOUDY
}

/**
 * Animated layer drawn over the wallpaper (and over a show's backdrop):
 * slowly drifting pools of light tinted by [Ambient.color], rising dust
 * motes, and a live-weather layer — rain streaks (with lightning in
 * thunderstorms), drifting clouds, fog, snow, a breathing sun glow by day
 * or twinkling stars at night.
 *
 * Everything is drawn in one Canvas whose draw phase reads the frame clock,
 * so only drawing reruns each frame — no recomposition.
 */
@Composable
fun LivingBackground(theme: LauncherTheme, weather: WeatherInfo?, overPhoto: Boolean, modifier: Modifier = Modifier) {
    val tint by animateColorAsState(Ambient.color ?: theme.accent, tween(1800), label = "ambient")
    val time = rememberFrameSeconds()
    val motes = remember { Random(7).let { r -> List(34) { Mote(r) } } }
    val drops = remember { Random(11).let { r -> List(110) { Drop(r) } } }
    val sky = weather?.let { skyFor(it.weatherCode) }
    val isDay = weather?.isDay ?: true

    Canvas(modifier.fillMaxSize()) {
        val t = time.value
        val glow = if (overPhoto) 0.10f else 0.20f
        drawLightPools(t, tint, glow)
        when (sky) {
            Sky.CLEAR -> if (isDay) drawSun(t) else drawStars(t, motes)
            Sky.CLOUDY -> drawClouds(t, 0.07f)
            Sky.FOG -> { drawClouds(t, 0.05f); drawFog(t) }
            Sky.RAIN -> { drawClouds(t, 0.06f); drawRain(t, drops, heavy = false) }
            Sky.STORM -> { drawClouds(t, 0.08f); drawRain(t, drops, heavy = true); drawLightning(t) }
            Sky.SNOW -> drawSnow(t, motes)
            null -> {}
        }
        if (sky != Sky.SNOW && !(sky == Sky.CLEAR && !isDay)) drawMotes(t, motes, tint)
    }
}

private fun DrawScope.drawLightPools(t: Float, tint: Color, alpha: Float) {
    val w = size.width; val h = size.height
    val r = max(w, h) * 0.42f
    val pools = listOf(
        Triple(0.30f, 0.35f, 0.050f),
        Triple(0.72f, 0.55f, 0.037f),
        Triple(0.50f, 0.85f, 0.029f)
    )
    pools.forEachIndexed { i, (cx, cy, speed) ->
        val c = Offset(
            w * (cx + 0.14f * sin(t * speed * 2 * PI.toFloat() / 4f + i * 2.1f)),
            h * (cy + 0.10f * sin(t * speed * 2 * PI.toFloat() / 3f + i * 1.3f))
        )
        val a = alpha * (0.75f + 0.25f * sin(t * 0.35f + i))
        drawCircle(
            Brush.radialGradient(listOf(tint.copy(alpha = a), Color.Transparent), center = c, radius = r),
            radius = r, center = c
        )
    }
}

private fun DrawScope.drawMotes(t: Float, motes: List<Mote>, tint: Color) {
    val w = size.width; val h = size.height
    motes.forEach { m ->
        val y = ((m.y - t * m.speed) % 1f + 1f) % 1f
        val x = m.x + 0.015f * sin(t * m.wobble + m.phase)
        val fade = sin(y * PI.toFloat()) // fade in at the bottom, out at the top
        val twinkle = 0.55f + 0.45f * sin(t * 1.7f + m.phase)
        drawCircle(
            Color.White.copy(alpha = 0.10f + 0.10f * twinkle * fade).compositeOver(tint, 0.25f),
            radius = m.size, center = Offset(x * w, y * h)
        )
    }
}

private fun Color.compositeOver(other: Color, amount: Float): Color =
    Color(
        red = red + (other.red - red) * amount,
        green = green + (other.green - green) * amount,
        blue = blue + (other.blue - blue) * amount,
        alpha = alpha
    )

private fun DrawScope.drawSun(t: Float) {
    val c = Offset(size.width * 0.86f, size.height * 0.08f)
    val r = size.minDimension * (0.55f + 0.04f * sin(t * 0.5f))
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFFFFE6A8).copy(alpha = 0.22f),
            0.35f to Color(0xFFFFC46B).copy(alpha = 0.09f),
            1f to Color.Transparent,
            center = c, radius = r
        ),
        radius = r, center = c
    )
}

private fun DrawScope.drawStars(t: Float, motes: List<Mote>) {
    motes.forEachIndexed { i, m ->
        val a = 0.15f + 0.45f * ((sin(t * (0.6f + m.wobble) + m.phase) + 1f) / 2f)
        drawCircle(Color.White.copy(alpha = a), radius = m.size * 0.7f, center = Offset(m.x * size.width, m.y * size.height * 0.6f))
        if (i % 9 == 0) {
            drawCircle(Color.White.copy(alpha = a * 0.25f), radius = m.size * 3f, center = Offset(m.x * size.width, m.y * size.height * 0.6f))
        }
    }
}

private fun DrawScope.drawClouds(t: Float, alpha: Float) {
    val w = size.width; val h = size.height
    for (i in 0 until 4) {
        val speed = 0.006f + i * 0.002f
        val x = (((0.25f * i + t * speed) % 1.4f) - 0.2f) * w
        val y = h * (0.10f + 0.07f * i)
        val r = h * (0.22f + 0.05f * (i % 2))
        val c = Offset(x, y)
        scale(scaleX = 2.4f, scaleY = 1f, pivot = c) {
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = alpha), Color.Transparent), center = c, radius = r),
                radius = r, center = c
            )
        }
    }
}

private fun DrawScope.drawFog(t: Float) {
    val drift = 0.5f + 0.5f * sin(t * 0.12f)
    drawRect(
        Brush.verticalGradient(
            0.55f to Color.Transparent,
            1f to Color.White.copy(alpha = 0.06f + 0.04f * drift)
        )
    )
}

private fun DrawScope.drawRain(t: Float, drops: List<Drop>, heavy: Boolean) {
    val w = size.width; val h = size.height
    val count = if (heavy) drops.size else drops.size * 2 / 3
    val slant = 0.18f
    for (i in 0 until count) {
        val d = drops[i]
        val y = ((d.y + t * d.speed * (if (heavy) 1.3f else 1f)) % 1.1f) - 0.05f
        val x = d.x + y * slant
        val start = Offset(x * w, y * h)
        val end = Offset((x + d.len * slant) * w, (y + d.len) * h)
        drawLine(Color(0xFFCFE3FF).copy(alpha = d.alpha), start, end, strokeWidth = 1.6f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawLightning(t: Float) {
    val cycle = t % 11f
    val flash = when {
        cycle < 0.08f -> 1f - cycle / 0.08f
        cycle in 0.18f..0.30f -> 0.6f * (1f - (cycle - 0.18f) / 0.12f)
        else -> 0f
    }
    if (flash > 0f) drawRect(Color.White.copy(alpha = 0.16f * flash))
}

private fun DrawScope.drawSnow(t: Float, motes: List<Mote>) {
    val w = size.width; val h = size.height
    motes.forEach { m ->
        val y = ((m.y + t * m.speed * 2.2f) % 1f)
        val x = m.x + 0.02f * sin(t * m.wobble + m.phase)
        drawCircle(Color.White.copy(alpha = 0.55f), radius = m.size * 1.1f, center = Offset(x * w, y * h))
    }
}
