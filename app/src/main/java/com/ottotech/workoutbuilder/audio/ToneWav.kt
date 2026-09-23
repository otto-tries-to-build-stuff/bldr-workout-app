package com.ottotech.workoutbuilder.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

data class ToneSpec(val freqHz: Double, val durationSec: Double, val volume: Double)

/**
 * Generates the cue tones as WAV bytes at runtime — no audio assets are shipped.
 *
 * A direct port of `makeWav()` in `web-app-reference/HIIT Workout.html:1466`, kept
 * sample-for-sample so the Android app sounds exactly like the web version the user already
 * trained to: mono 22050 Hz 16-bit PCM, a sine wave with a 5ms fade-in (without which each
 * cue starts on a click) and an exponential decay envelope.
 *
 * Pure Kotlin and JVM-testable: the envelope is the part that is easy to get silently wrong.
 */
object ToneWav {

    val COUNTDOWN = ToneSpec(720.0, 0.18, 0.45)
    val GO = ToneSpec(1100.0, 0.32, 0.55)
    val FINISH = ToneSpec(880.0, 0.7, 0.55)

    private const val SAMPLE_RATE = 22050
    private const val FADE_IN_SEC = 0.005

    fun bytes(spec: ToneSpec): ByteArray = bytes(spec.freqHz, spec.durationSec, spec.volume)

    fun bytes(freqHz: Double, durationSec: Double, volume: Double): ByteArray {
        val samples = (SAMPLE_RATE * durationSec).toInt()
        val out = ByteArray(44 + samples * 2)
        var p = 0

        fun ascii(s: String) { s.forEach { out[p++] = it.code.toByte() } }
        fun int32(v: Int) {
            out[p++] = (v and 0xFF).toByte()
            out[p++] = ((v shr 8) and 0xFF).toByte()
            out[p++] = ((v shr 16) and 0xFF).toByte()
            out[p++] = ((v shr 24) and 0xFF).toByte()
        }
        fun int16(v: Int) {
            out[p++] = (v and 0xFF).toByte()
            out[p++] = ((v shr 8) and 0xFF).toByte()
        }

        ascii("RIFF"); int32(36 + samples * 2); ascii("WAVE")
        ascii("fmt "); int32(16); int16(1); int16(1)
        int32(SAMPLE_RATE); int32(SAMPLE_RATE * 2); int16(2); int16(16)
        ascii("data"); int32(samples * 2)

        for (i in 0 until samples) {
            val t = i.toDouble() / SAMPLE_RATE
            val fadeIn = min(1.0, t / FADE_IN_SEC)
            val decay = exp(-3.0 * t / durationSec)
            val sample = sin(2.0 * PI * freqHz * t) * fadeIn * decay * volume
            int16((sample.coerceIn(-1.0, 1.0) * 32767).toInt())
        }
        return out
    }
}
