package com.ottotech.workoutbuilder.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToneWavTest {

    private fun ascii(bytes: ByteArray, from: Int, len: Int) =
        String(bytes, from, len, Charsets.US_ASCII)

    private fun leShort(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun leInt(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)

    @Test
    fun `produces a valid mono 22050Hz 16-bit wav header`() {
        val wav = ToneWav.bytes(720.0, 0.18, 0.45)
        val samples = (22050 * 0.18).toInt()

        assertEquals("RIFF", ascii(wav, 0, 4))
        assertEquals("WAVE", ascii(wav, 8, 4))
        assertEquals("fmt ", ascii(wav, 12, 4))
        assertEquals("data", ascii(wav, 36, 4))
        assertEquals(44 + samples * 2, wav.size)
        assertEquals(36 + samples * 2, leInt(wav, 4))
        assertEquals(16, leInt(wav, 16), "PCM fmt chunk size")
        assertEquals(1, leShort(wav, 20), "PCM format")
        assertEquals(1, leShort(wav, 22), "mono")
        assertEquals(22050, leInt(wav, 24))
        assertEquals(22050 * 2, leInt(wav, 28), "byte rate")
        assertEquals(2, leShort(wav, 32), "block align")
        assertEquals(16, leShort(wav, 34), "bits per sample")
        assertEquals(samples * 2, leInt(wav, 40))
    }

    @Test
    fun `starts silent, becomes loud, and decays`() {
        val wav = ToneWav.bytes(720.0, 0.18, 0.45)
        fun peak(fromSec: Double, toSec: Double): Int {
            val from = 44 + (22050 * fromSec).toInt() * 2
            val to = 44 + (22050 * toSec).toInt() * 2
            return (from until to step 2).maxOf { kotlin.math.abs(leShort(wav, it).toShort().toInt()) }
        }
        // 5ms anti-click fade-in: the very first samples are near zero
        assertTrue(peak(0.0, 0.0005) < 2_000, "must fade in, or it clicks")
        assertTrue(peak(0.01, 0.03) > 8_000, "must actually be audible")
        assertTrue(peak(0.16, 0.18) < peak(0.01, 0.03), "must decay")
    }

    @Test
    fun `the three cue tones match the reference app`() {
        assertEquals(ToneSpec(720.0, 0.18, 0.45), ToneWav.COUNTDOWN)
        assertEquals(ToneSpec(1100.0, 0.32, 0.55), ToneWav.GO)
        assertEquals(ToneSpec(880.0, 0.7, 0.55), ToneWav.FINISH)
    }
}
