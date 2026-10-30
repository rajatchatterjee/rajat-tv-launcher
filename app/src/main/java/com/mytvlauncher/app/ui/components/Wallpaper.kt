package com.mytvlauncher.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.mytvlauncher.app.data.BackgroundStyle
import com.mytvlauncher.app.model.LauncherTheme
import com.mytvlauncher.app.model.Scene
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-screen launcher background: the theme's scene, then a scrim so
 * text and tiles stay readable. [thumbnail] draws just the scene (no
 * scrim, no blur) for the theme picker.
 */
@Composable
fun SceneBackground(
    theme: LauncherTheme,
    style: BackgroundStyle,
    modifier: Modifier = Modifier,
    thumbnail: Boolean = false
) {
    if (!thumbnail && style == BackgroundStyle.SOLID) {
        Box(modifier.fillMaxSize().background(theme.background))
        return
    }
    // Real blur needs Android 12+. On older TVs "Blur" falls back to a
    // slightly heavier scrim so the choice still visibly does something.
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blur = !thumbnail && style == BackgroundStyle.BLUR && canBlur
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (blur) Modifier.blur(14.dp) else Modifier)
                .drawBehind { drawScene(theme) }
        )
        if (!thumbnail && theme.scene != Scene.GRADIENT) {
            val dim = when (style) {
                BackgroundStyle.DIM -> 0.42f
                BackgroundStyle.BLUR -> if (canBlur) 0.10f else 0.22f
                BackgroundStyle.SOLID -> 0f
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Darken the left (sidebar + text) and bottom (tile rows),
                        // leave the middle of the scene to breathe.
                        drawRect(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 0.55f + dim / 2),
                                0.45f to Color.Black.copy(alpha = 0.18f + dim),
                                1f to Color.Black.copy(alpha = 0.28f + dim)
                            )
                        )
                        drawRect(
                            Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.55f)
                            )
                        )
                    }
            )
        }
    }
}

fun DrawScope.drawScene(theme: LauncherTheme) {
    when (theme.scene) {
        Scene.CINEMATIC_SUNSET -> drawSunset()
        Scene.AURORA -> drawAurora()
        Scene.MINIMAL_DARK -> drawDunes()
        Scene.OCEAN_BLUE -> drawOcean()
        Scene.FOREST -> drawForest()
        Scene.GRADIENT -> drawTimeOfDayGradient(theme)
    }
}

// ---------------------------------------------------------------------------

/** Jagged ridge line from left to right, closed down to [bottom]. Deterministic per seed. */
private fun DrawScope.ridge(seed: Int, baseY: Float, amp: Float, segments: Int, bottom: Float): Path {
    val r = Random(seed)
    val w = size.width
    return Path().apply {
        moveTo(0f, bottom)
        for (i in 0..segments) {
            val x = w * i / segments + (if (i in 1 until segments) (r.nextFloat() - 0.5f) * w / segments * 0.6f else 0f)
            val peak = if (i % 2 == 0) r.nextFloat() * 0.45f else 0.55f + r.nextFloat() * 0.45f
            lineTo(x, baseY - amp * peak)
        }
        lineTo(w, bottom)
        close()
    }
}

private fun DrawScope.drawSunset() {
    val w = size.width
    val h = size.height
    val horizon = h * 0.70f

    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF1B1035),
            0.32f to Color(0xFF4A1F5C),
            0.56f to Color(0xFFB2456E),
            0.80f to Color(0xFFF07F3C),
            1f to Color(0xFFFFC27A),
            startY = 0f, endY = horizon
        ),
        size = Size(w, horizon)
    )
    // Wispy cloud bands catching the light.
    for (i in 0 until 5) {
        val y = h * (0.16f + i * 0.07f)
        drawOval(
            Color(0xFFFFA77A).copy(alpha = 0.10f + i * 0.02f),
            topLeft = Offset(w * (0.18f + (i % 3) * 0.17f), y),
            size = Size(w * (0.35f + (i % 2) * 0.15f), h * 0.025f)
        )
    }
    val sun = Offset(w * 0.62f, horizon - h * 0.04f)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFE0A0).copy(alpha = 0.95f), Color(0xFFFF9A50).copy(alpha = 0.35f), Color.Transparent), sun, w * 0.30f),
        radius = w * 0.30f, center = sun
    )

    val far = ridge(7, horizon - h * 0.04f, h * 0.20f, 16, horizon)
    val mid = ridge(11, horizon, h * 0.30f, 10, horizon)
    val near = ridge(23, horizon + h * 0.01f, h * 0.13f, 8, horizon)
    drawPath(far, Color(0xFF7A3566).copy(alpha = 0.85f))
    drawPath(mid, Color(0xFF3E1A3E))
    drawPath(near, Color(0xFF1E0F22))

    // Lake + mirrored mountains.
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF4A2246), Color(0xFF140A18)), startY = horizon, endY = h),
        topLeft = Offset(0f, horizon), size = Size(w, h - horizon)
    )
    clipRect(top = horizon) {
        scale(1f, -1f, pivot = Offset(0f, horizon)) {
            drawPath(mid, Color(0xFF2A1230).copy(alpha = 0.55f))
            drawPath(near, Color(0xFF12081A).copy(alpha = 0.6f))
        }
        drawOval(
            Brush.radialGradient(listOf(Color(0xFFFFB066).copy(alpha = 0.45f), Color.Transparent), Offset(sun.x, horizon + h * 0.06f), w * 0.18f),
            topLeft = Offset(sun.x - w * 0.18f, horizon), size = Size(w * 0.36f, h * 0.16f)
        )
        val r = Random(5)
        repeat(18) {
            val y = horizon + (h - horizon) * r.nextFloat()
            val len = w * (0.04f + r.nextFloat() * 0.12f)
            val x = sun.x - len / 2 + (r.nextFloat() - 0.5f) * w * 0.3f
            drawLine(Color(0xFFFFC890).copy(alpha = 0.12f + r.nextFloat() * 0.15f), Offset(x, y), Offset(x + len, y), strokeWidth = h * 0.003f)
        }
    }
}

private fun DrawScope.drawAurora() {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF040A18), Color(0xFF07202E), Color(0xFF0C3A3C))))
    val r = Random(3)
    repeat(140) {
        drawCircle(
            Color.White.copy(alpha = 0.25f + r.nextFloat() * 0.7f),
            radius = (0.6f + r.nextFloat() * 1.4f) * w / 1000f,
            center = Offset(w * r.nextFloat(), h * 0.65f * r.nextFloat())
        )
    }
    val bands = listOf(Color(0xFF3DFFA8), Color(0xFF22E3C3), Color(0xFF8A6BFF))
    bands.forEachIndexed { i, color ->
        val top = FloatArray(41) { k ->
            val t = k / 40f
            h * (0.14f + 0.09f * i) + sin(t * 2 * PI.toFloat() * 1.15f + i * 1.7f) * h * 0.07f +
                sin(t * 2 * PI.toFloat() * 3.1f + i) * h * 0.02f
        }
        val depth = h * (0.26f - i * 0.05f)
        val path = Path().apply {
            moveTo(0f, top[0])
            for (k in 1..40) lineTo(w * k / 40f, top[k])
            for (k in 40 downTo 0) lineTo(w * k / 40f, top[k] + depth)
            close()
        }
        val minY = top.min()
        drawPath(
            path,
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.25f to color.copy(alpha = 0.55f - i * 0.12f),
                0.7f to color.copy(alpha = 0.12f),
                1f to Color.Transparent,
                startY = minY, endY = minY + depth + h * 0.14f
            )
        )
    }
    drawPath(ridge(19, h * 0.82f, h * 0.20f, 12, h), Color(0xFF0A1C26))
    drawPath(ridge(29, h * 0.90f, h * 0.10f, 9, h), Color(0xFF050E14))
}

private fun DrawScope.drawDunes() {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF18191D), Color(0xFF0B0C0F), Color(0xFF060708))))
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent), Offset(w * 0.7f, h * 0.25f), w * 0.4f),
        radius = w * 0.4f, center = Offset(w * 0.7f, h * 0.25f)
    )
    val dunes = listOf(
        Triple(0.55f, Color(0xFF26282D), Color(0xFF111215)),
        Triple(0.68f, Color(0xFF1C1E22), Color(0xFF0B0C0E)),
        Triple(0.82f, Color(0xFF141518), Color(0xFF070809))
    )
    dunes.forEachIndexed { i, (y, light, dark) ->
        val top = h * y
        val path = Path().apply {
            moveTo(0f, top + h * 0.05f)
            cubicTo(w * 0.25f, top - h * (0.10f - i * 0.02f), w * 0.55f, top + h * 0.12f, w, top - h * 0.03f * (i + 1))
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(light, dark), startY = top - h * 0.1f, endY = h))
        drawPath(path, Color.White.copy(alpha = 0.05f), style = Stroke(width = h * 0.002f))
    }
}

private fun DrawScope.drawOcean() {
    val w = size.width
    val h = size.height
    val horizon = h * 0.48f
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF071C3D), Color(0xFF15508F), Color(0xFF6FAAD9)), startY = 0f, endY = horizon),
        size = Size(w, horizon)
    )
    val r = Random(9)
    repeat(7) {
        drawOval(
            Color.White.copy(alpha = 0.05f + r.nextFloat() * 0.05f),
            topLeft = Offset(w * r.nextFloat() * 0.85f, horizon * (0.15f + r.nextFloat() * 0.6f)),
            size = Size(w * (0.15f + r.nextFloat() * 0.2f), h * 0.03f)
        )
    }
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF1A5C99), Color(0xFF0B3466), Color(0xFF041A36)), startY = horizon, endY = h),
        topLeft = Offset(0f, horizon), size = Size(w, h - horizon)
    )
    val glow = Offset(w * 0.55f, horizon)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFCFE8FF).copy(alpha = 0.55f), Color.Transparent), glow, w * 0.25f),
        radius = w * 0.25f, center = glow
    )
    // Wave lines, closer together near the horizon.
    for (j in 0 until 16) {
        val t = j / 15f
        val y = horizon + (h - horizon) * t.pow(1.6f) + h * 0.01f
        val amp = h * (0.004f + 0.012f * t)
        val freq = 6f + j
        val path = Path().apply {
            moveTo(0f, y)
            for (k in 1..60) {
                val x = w * k / 60f
                lineTo(x, y + sin(k / 60f * freq * PI.toFloat() + j) * amp)
            }
        }
        drawPath(path, Color.White.copy(alpha = 0.06f + 0.10f * (1 - t)), style = Stroke(width = h * (0.0015f + 0.003f * t)))
    }
}

private fun DrawScope.drawForest() {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0E241D), Color(0xFF2E5A42), Color(0xFF9DBB84)), startY = 0f, endY = h * 0.62f))
    drawRect(Color(0xFF0B1A13), topLeft = Offset(0f, h * 0.62f), size = Size(w, h * 0.38f))
    val light = Offset(w * 0.55f, h * 0.48f)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFF2F0C8).copy(alpha = 0.6f), Color.Transparent), light, w * 0.3f),
        radius = w * 0.3f, center = light
    )
    // Three rows of pines, back to front.
    val rows = listOf(
        Triple(0.62f, 0.16f, Color(0xFF3F6B50)),
        Triple(0.74f, 0.26f, Color(0xFF1F3E2E)),
        Triple(0.92f, 0.42f, Color(0xFF0B1E15))
    )
    rows.forEachIndexed { row, (baseFrac, heightFrac, color) ->
        val r = Random(40 + row)
        val count = 26 - row * 7
        val base = h * baseFrac
        val path = Path()
        for (i in 0 until count) {
            val cx = w * (i + r.nextFloat() * 0.6f) / count
            // Leave a clearing in the middle for the path.
            if (row > 0 && cx > w * 0.42f && cx < w * 0.66f) continue
            val th = h * heightFrac * (0.7f + r.nextFloat() * 0.5f)
            val tw = th * 0.32f
            path.moveTo(cx, base - th)
            path.lineTo(cx + tw / 2, base)
            path.lineTo(cx - tw / 2, base)
            path.close()
        }
        drawPath(path, color)
        drawRect(color, topLeft = Offset(0f, base - 1f), size = Size(w, h - base + 1f))
    }
    // The light path through the trees.
    val road = Path().apply {
        moveTo(w * 0.52f, h * 0.64f)
        lineTo(w * 0.56f, h * 0.64f)
        lineTo(w * 0.72f, h)
        lineTo(w * 0.40f, h)
        close()
    }
    drawPath(road, Brush.verticalGradient(listOf(Color(0xFFCFD8A8).copy(alpha = 0.45f), Color(0xFF6F8455).copy(alpha = 0.35f)), startY = h * 0.64f, endY = h))
}

/** The pre-redesign background: theme colour blended toward a time-of-day tint. */
private fun DrawScope.drawTimeOfDayGradient(theme: LauncherTheme) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val tint = when (hour) {
        in 5..7 -> Color(0xFFFFB37B)
        in 8..16 -> theme.accent
        in 17..19 -> Color(0xFFFF7A59)
        else -> Color(0xFF0B1030)
    }
    drawRect(Brush.verticalGradient(listOf(theme.background, lerp(theme.background, tint, 0.16f))))
}
