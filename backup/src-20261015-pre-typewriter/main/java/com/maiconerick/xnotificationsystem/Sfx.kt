package com.maiconerick.xnotificationsystem

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.maiconerick.xnotificationsystem.island.Kind
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Tiny synthesized chimes (no audio assets) + a haptic tick for island events. */
class Sfx(private val ctx: Context) {
    private data class Note(val freq: Float, val startMs: Int, val durMs: Int)

    private val rate = 44_100
    private val worker = Executors.newSingleThreadExecutor()
    private val cache = HashMap<Kind, ShortArray>()

    fun ping(kind: Kind, sound: Boolean, haptics: Boolean) {
        if (haptics) haptic(kind)
        if (sound) worker.execute { play(kind) }
    }

    private fun notes(kind: Kind) = when (kind) {
        Kind.Permission -> listOf(Note(784f, 0, 140), Note(587f, 120, 220))
        Kind.Finished -> listOf(Note(659f, 0, 110), Note(880f, 90, 110), Note(1175f, 180, 260))
        Kind.RateLimit -> listOf(Note(370f, 0, 100), Note(311f, 110, 200))
        Kind.Chat -> listOf(Note(1046f, 0, 90))
        else -> listOf(Note(880f, 0, 90), Note(1318f, 70, 180))
    }

    private fun render(kind: Kind): ShortArray {
        val ns = notes(kind)
        val totalMs = ns.maxOf { it.startMs + it.durMs } + 40
        val out = FloatArray(totalMs * rate / 1000)
        for (n in ns) {
            val start = n.startMs * rate / 1000
            val len = n.durMs * rate / 1000
            for (i in 0 until len) {
                val idx = start + i
                if (idx >= out.size) break
                val t = i / rate.toFloat()
                val attack = (t / 0.006f).coerceAtMost(1f)
                val env = attack * exp(-t * 5.5f / (n.durMs / 1000f)).toFloat()
                val w = 2f * PI.toFloat() * n.freq * t
                out[idx] += (sin(w) + 0.18f * sin(2f * w) + 0.06f * sin(3f * w)) * env * 0.22f
            }
        }
        return ShortArray(out.size) { (out[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
    }

    private fun play(kind: Kind) {
        val pcm = cache.getOrPut(kind) { render(kind) }
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep(pcm.size * 1000L / rate + 60)
            track.release()
        }
    }

    private fun haptic(kind: Kind) {
        val vib: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vib.hasVibrator()) return
        val effect = when (kind) {
            Kind.Permission, Kind.RateLimit -> VibrationEffect.EFFECT_DOUBLE_CLICK
            Kind.Chat -> VibrationEffect.EFFECT_TICK
            else -> VibrationEffect.EFFECT_CLICK
        }
        runCatching { vib.vibrate(VibrationEffect.createPredefined(effect)) }
    }

    fun release() = worker.shutdown()
}
