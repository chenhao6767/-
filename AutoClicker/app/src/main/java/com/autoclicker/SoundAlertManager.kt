package com.autoclicker

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import kotlin.math.*

/**
 * Plays short synthesised tones for match-success and match-failure events.
 *
 * Uses AudioTrack with PCM synthesis — no audio asset files needed.
 *
 * Tones:
 *   playSuccess() → ascending two-note chime  (pleasant ✅)
 *   playFailure() → single low blip           (subtle ❌)
 *   playStop()    → descending two-note chord  (run ended)
 *
 * Volume is controlled by [volume] (0.0–1.0).
 * Each sound type can be individually enabled/disabled.
 */
object SoundAlertManager {

    // ── Config ────────────────────────────────────────────────────────────────
    var volume:          Float   = 0.7f
    var successEnabled:  Boolean = true
    var failureEnabled:  Boolean = false   // off by default (too noisy per miss)
    var stopEnabled:     Boolean = true

    private const val SAMPLE_RATE = 44100

    // ── Public API ────────────────────────────────────────────────────────────

    /** Call when an image task matches successfully (click or skip). */
    fun playSuccess() {
        if (!successEnabled) return
        // C5 → E5  ascending chime
        playAsync(buildTone(523.25f, 80)  + buildTone(659.25f, 120))
    }

    /** Call when recognition scan finds NO match this loop pass. */
    fun playFailure() {
        if (!failureEnabled) return
        // Low G3 blip
        playAsync(buildTone(196f, 80))
    }

    /** Call when the run stops (limit reached or STOP task matched). */
    fun playStop() {
        if (!stopEnabled) return
        // E5 → C5  descending
        playAsync(buildTone(659.25f, 100) + buildTone(523.25f, 140))
    }

    /** Play a raw match-success sound immediately (also callable from AccessibilityService). */
    fun playMatchSuccess() = playSuccess()

    /** Play a no-match sound (usually disabled — call explicitly when needed). */
    fun playMatchFailure() = playFailure()

    // ── PCM synthesis ─────────────────────────────────────────────────────────

    /**
     * Build a mono PCM buffer for a sine tone at [freq] Hz lasting [durationMs] ms.
     * Applies a short fade-in/out to avoid clicking artefacts.
     */
    private fun buildTone(freq: Float, durationMs: Int): ShortArray {
        val samples    = (SAMPLE_RATE * durationMs / 1000)
        val fadeFrames = (SAMPLE_RATE * 0.010).toInt()   // 10 ms fade
        val buf        = ShortArray(samples)
        for (i in 0 until samples) {
            val t      = i.toDouble() / SAMPLE_RATE
            var sample = sin(2.0 * PI * freq * t) * volume * Short.MAX_VALUE
            // fade in
            if (i < fadeFrames)           sample *= i.toDouble() / fadeFrames
            // fade out
            if (i > samples - fadeFrames) sample *= (samples - i).toDouble() / fadeFrames
            buf[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buf
    }

    /** Concatenate two PCM buffers. */
    private operator fun ShortArray.plus(other: ShortArray): ShortArray {
        val out = ShortArray(size + other.size)
        copyInto(out)
        other.copyInto(out, size)
        return out
    }

    /** Write [pcm] to an AudioTrack on a background thread. */
    private fun playAsync(pcm: ShortArray) {
        Thread {
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val format = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                val track = AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(maxOf(minBuf, pcm.size * 2))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                // Wait for playback to finish, then release
                Thread.sleep((pcm.size * 1000L / SAMPLE_RATE) + 50)
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }.apply { isDaemon = true; start() }
    }
}
