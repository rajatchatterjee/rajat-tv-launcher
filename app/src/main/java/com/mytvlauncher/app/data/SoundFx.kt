package com.mytvlauncher.app.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Soft navigation sounds: a short tick when the D-pad moves focus and a
 * brighter two-note chime on OK. The sounds are synthesised into small WAV
 * files on first use (no audio assets in the repo) and played via SoundPool.
 */
class SoundFx(context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val tick: Int
    private val select: Int

    init {
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        tick = pool.load(wav(File(dir, "tick_v1.wav")) { t -> tone(t, 1250.0, 0.030, 140.0) }, 1)
        select = pool.load(
            wav(File(dir, "select_v1.wav")) { t ->
                tone(t, 784.0, 0.11, 30.0) * 0.6 + (if (t > 0.045) tone(t - 0.045, 1175.0, 0.11, 30.0) * 0.6 else 0.0)
            },
            1
        )
    }

    fun tick() { pool.play(tick, 0.18f, 0.18f, 1, 0, 1f) }
    fun select() { pool.play(select, 0.22f, 0.22f, 1, 0, 1f) }

    private fun tone(t: Double, freq: Double, length: Double, decay: Double): Double =
        if (t < 0 || t > length) 0.0
        else sin(2 * PI * freq * t) * exp(-decay * t) * (if (t < 0.002) t / 0.002 else 1.0)

    /** Writes a 16-bit mono 44.1 kHz WAV of [lengthSec] using [sample] (−1..1), once. Returns its path. */
    private fun wav(file: File, lengthSec: Double = 0.16, sample: (Double) -> Double): String {
        if (file.exists() && file.length() > 44) return file.absolutePath
        val rate = 44_100
        val n = (rate * lengthSec).toInt()
        val data = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            val v = (sample(i.toDouble() / rate).coerceIn(-1.0, 1.0) * 32767).toInt()
            data.putShort(v.toShort())
        }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + n * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(n * 2)
        }
        FileOutputStream(file).use { it.write(header.array()); it.write(data.array()) }
        return file.absolutePath
    }
}
