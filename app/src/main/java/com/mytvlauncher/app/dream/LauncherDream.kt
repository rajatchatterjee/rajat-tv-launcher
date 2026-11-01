package com.mytvlauncher.app.dream

import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.imageLoader
import coil.request.ImageRequest
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screensaver ("Daydream"): the cached show backdrops from Home, slowly
 * drifting and crossfading every 12 s, with a big clock, the date, the
 * weather and the show's title. Pick it in TV Settings › Screen saver
 * (Settings › Layout & Grid › Screensaver opens that page).
 *
 * Plain Views rather than Compose — a DreamService isn't a lifecycle owner,
 * and this is simple enough not to need one.
 */
class LauncherDream : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var front: ImageView
    private lateinit var back: ImageView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var caption: TextView
    private var slides: List<Pair<String, String>> = emptyList()
    private var index = 0

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        back = image().also { root.addView(it) }
        front = image().also { root.addView(it) }
        root.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BL_TR,
                intArrayOf(Color.argb(235, 0, 0, 0), Color.argb(90, 0, 0, 0), Color.argb(10, 0, 0, 0))
            )
        }, FrameLayout.LayoutParams(-1, -1))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(56), 0, 0, dp(44))
        }
        clock = text(64f, true).also { info.addView(it) }
        date = text(18f, false).also { info.addView(it) }
        caption = text(15f, false).also { it.alpha = 0.8f; it.setPadding(0, dp(10), 0, 0); info.addView(it) }
        root.addView(info, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START))
        setContentView(root)

        slides = loadSlides()
        tickClock()
        next()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    private fun image() = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        alpha = 0f
        layoutParams = FrameLayout.LayoutParams(-1, -1)
    }

    private fun text(size: Float, bold: Boolean) = TextView(this).apply {
        setTextColor(Color.WHITE)
        textSize = size
        if (bold) typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        setShadowLayer(12f, 0f, 2f, Color.argb(160, 0, 0, 0))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tickClock() {
        val now = Date()
        clock.text = SimpleDateFormat("h:mm", Locale.getDefault()).format(now)
        val weather = getSharedPreferences("weather_cache", MODE_PRIVATE)
        val temp = weather.getInt("temp", Int.MIN_VALUE)
        date.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now) +
            (if (temp != Int.MIN_VALUE) "   ·   $temp°" else "")
        handler.postDelayed({ tickClock() }, 20_000)
    }

    /** (backdrop url, caption) from the Home content cache, shuffled. */
    private fun loadSlides(): List<Pair<String, String>> = try {
        val rows = JSONArray(getSharedPreferences("content_cache", MODE_PRIVATE).getString("rows", "[]"))
        val out = mutableListOf<Pair<String, String>>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val items = row.getJSONArray("items")
            for (j in 0 until items.length()) {
                val c = items.getJSONObject(j)
                val url = c.optString("backdrop")
                if (url.isNotBlank()) {
                    val service = c.optString("service").ifBlank { row.optString("service") }
                    out += url to (c.optString("title") + if (service.isNotBlank()) "  ·  $service" else "")
                }
            }
        }
        out.distinctBy { it.first }.shuffled()
    } catch (e: Exception) { emptyList() }

    private fun next() {
        if (slides.isEmpty()) {
            caption.text = "Rajat's TV Launcher"
            return
        }
        val (url, title) = slides[index % slides.size]
        index++
        val request = ImageRequest.Builder(this)
            .data(url)
            .target(onSuccess = { drawable ->
                back.setImageDrawable(drawable)
                back.scaleX = 1.02f; back.scaleY = 1.02f; back.translationX = 0f
                back.animate().cancel()
                // Fade with ObjectAnimators so the long Ken Burns drift below
                // (a ViewPropertyAnimator) keeps its own duration.
                ObjectAnimator.ofFloat(back, View.ALPHA, 1f).setDuration(1800).start()
                ObjectAnimator.ofFloat(front, View.ALPHA, 0f).setDuration(1800).start()
                back.animate().scaleX(1.14f).scaleY(1.14f).translationX(-back.width * 0.04f)
                    .setDuration(14_000).setInterpolator(LinearInterpolator()).start()
                caption.text = title
                val t = front; front = back; back = t
            })
            .build()
        imageLoader.enqueue(request)
        handler.postDelayed({ next() }, 12_000)
    }
}
