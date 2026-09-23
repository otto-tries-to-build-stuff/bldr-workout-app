package com.ottotech.workoutbuilder.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ottotech.workoutbuilder.domain.Cue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

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

    private val loadedCount = AtomicInteger(0)
    private val loaded = CompletableDeferred<Unit>()

    init {
        pool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0 && loadedCount.incrementAndGet() >= EXPECTED_SOUNDS) {
                loaded.complete(Unit)
            }
        }
    }

    /**
     * `SoundPool.load()` only queues a decode on a background thread and returns before it's
     * done; playing a sample before its [SoundPool.OnLoadCompleteListener] callback fires is a
     * silent no-op (logcat: "soundID N not READY"). [prime] suspends until all three tones are
     * actually decoded, not just queued, so the very first cue — played immediately after
     * [prime] returns — is never dropped.
     *
     * Suspends rather than blocks: a `CountDownLatch.await()` here would park a real OS thread
     * on the shared `Dispatchers.Default` pool (sized `max(2, cores)`), queuing any unrelated
     * work behind it for up to the full timeout on a low-core device. `CompletableDeferred` +
     * [withTimeoutOrNull] gets the same "wait for load, give up after 2s" behaviour without
     * occupying a pool thread while it waits. The only caller ([execution.ExecutionService])
     * is already inside a coroutine.
     */
    suspend fun prime() {
        if (soundIds.isNotEmpty()) return
        soundIds[Cue.COUNTDOWN] = load("cue_countdown.wav", ToneWav.COUNTDOWN)
        soundIds[Cue.GO] = load("cue_go.wav", ToneWav.GO)
        soundIds[Cue.FINISH] = load("cue_finish.wav", ToneWav.FINISH)
        val completed = withTimeoutOrNull(PRIME_TIMEOUT_MS) { loaded.await() }
        if (completed == null) {
            Log.w(TAG, "Cue tones did not finish decoding within ${PRIME_TIMEOUT_MS}ms; some cues may not play")
        }
    }

    private fun load(fileName: String, spec: ToneSpec): Int {
        val bytes = ToneWav.bytes(spec)
        val file = File(context.cacheDir, fileName)
        // A process killed mid-write can leave a truncated WAV that exists forever but never
        // decodes; comparing to the exact expected size (not just presence) catches that and
        // regenerates it, instead of silently disabling that cue until storage is cleared.
        if (!file.exists() || file.length() != bytes.size.toLong()) {
            file.writeBytes(bytes)
        }
        return pool.load(file.absolutePath, 1)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val focusLock = Any()
    private var focusHeld = false

    private val releaseFocus = Runnable {
        synchronized(focusLock) {
            if (focusHeld) {
                audioManager.abandonAudioFocusRequest(focusRequest)
                focusHeld = false
            }
        }
    }

    /**
     * Holds audio focus across a burst of cues instead of requesting and abandoning it per
     * tone. `pool.play()` only queues playback; the focus round-trip that actually ducks
     * whatever app is playing music is asynchronous IPC, so a request immediately followed by
     * an abandon (as the previous version did) can mean the duck never audibly happens, or
     * un-ducks within milliseconds — and the 3-2-1 countdown fires three cues a second apart,
     * which would have churned focus three times in three seconds. Instead: request focus only
     * when not already held, and push the release out by [FOCUS_RELEASE_DELAY_MS] on every
     * cue, so one held-for-a-beat-longer focus grant covers the whole burst and is abandoned
     * once, shortly after the last cue in it.
     */
    private fun holdFocusForBurst() {
        synchronized(focusLock) {
            if (!focusHeld) {
                audioManager.requestAudioFocus(focusRequest)
                focusHeld = true
            }
        }
        mainHandler.removeCallbacks(releaseFocus)
        mainHandler.postDelayed(releaseFocus, FOCUS_RELEASE_DELAY_MS)
    }

    /**
     * Set first in [release], checked first in [play]: once release has begun, `play` becomes
     * a no-op instead of racing a `SoundPool.play()`/`release()` call against each other, which
     * is undefined behaviour per `SoundPool`'s contract. `release()` runs on the main thread
     * from `Service.onDestroy()`; `play()` can be called from `Dispatchers.Default` (the
     * service's clock loop), so the two can genuinely overlap.
     */
    @Volatile private var released = false

    fun play(cue: Cue) {
        if (muted || released) return
        val id = soundIds[cue] ?: return
        holdFocusForBurst()
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        released = true
        mainHandler.removeCallbacks(releaseFocus)
        synchronized(focusLock) {
            if (focusHeld) {
                audioManager.abandonAudioFocusRequest(focusRequest)
                focusHeld = false
            }
        }
        pool.release()
        soundIds.clear()
    }

    private companion object {
        const val TAG = "CuePlayer"
        const val EXPECTED_SOUNDS = 3
        const val PRIME_TIMEOUT_MS = 2_000L
        const val FOCUS_RELEASE_DELAY_MS = 1_500L
    }
}
