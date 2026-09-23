package com.ottotech.workoutbuilder.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import com.ottotech.workoutbuilder.domain.Cue
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Plays the three cue tones.
 *
 * `SoundPool` rather than `MediaPlayer`: these are short, repeated, latency-sensitive sounds,
 * which is exactly what it is built for — a cue that arrives 300ms late is a cue that lied.
 * Tones are generated to the cache directory on [prime] and loaded once, before the first
 * segment starts, so no cue pays for generation.
 */
class CuePlayer(private val context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(attributes)
        .build()

    private val soundIds = mutableMapOf<Cue, Int>()

    /** Music ducks for a cue rather than stopping — spec §7. */
    private val focusRequest = AudioFocusRequest
        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    var muted: Boolean = false

    /**
     * `SoundPool.load()` only queues a decode on a background thread and returns before it's
     * done; playing a sample before its [SoundPool.OnLoadCompleteListener] callback fires is a
     * silent no-op (logcat: "soundID N not READY"). [prime] blocks until all three tones are
     * actually decoded, not just queued, so the very first cue — played immediately after
     * [prime] returns — is never dropped. It always runs off the main thread (inside the
     * service's own coroutine), so blocking here costs nothing the user can feel.
     */
    private val loadLatch = CountDownLatch(3)

    init {
        pool.setOnLoadCompleteListener { _, _, status -> if (status == 0) loadLatch.countDown() }
    }

    fun prime() {
        if (soundIds.isNotEmpty()) return
        soundIds[Cue.COUNTDOWN] = load("cue_countdown.wav", ToneWav.COUNTDOWN)
        soundIds[Cue.GO] = load("cue_go.wav", ToneWav.GO)
        soundIds[Cue.FINISH] = load("cue_finish.wav", ToneWav.FINISH)
        loadLatch.await(2, TimeUnit.SECONDS)
    }

    private fun load(fileName: String, spec: ToneSpec): Int {
        val file = File(context.cacheDir, fileName)
        if (!file.exists()) file.writeBytes(ToneWav.bytes(spec))
        return pool.load(file.absolutePath, 1)
    }

    fun play(cue: Cue) {
        if (muted) return
        val id = soundIds[cue] ?: return
        audioManager.requestAudioFocus(focusRequest)
        pool.play(id, 1f, 1f, 1, 0, 1f)
        // Hand focus straight back: the tone is ~0.2-0.7s and holding focus would keep the
        // user's music ducked for the rest of the workout.
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    fun release() {
        pool.release()
        soundIds.clear()
    }
}
