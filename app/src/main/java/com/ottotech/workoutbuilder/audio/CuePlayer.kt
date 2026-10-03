package com.ottotech.workoutbuilder.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.ottotech.workoutbuilder.domain.Cue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Plays the three cue tones, and speaks the session's lines through the phone's built-in
 * text-to-speech when voice is on — one class, so tones and voice share one audio-focus policy.
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

    /** Whether spoken lines play at all — the Settings switch, set before [prime]. */
    var voiceOn: Boolean = false

    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private val lineCounter = AtomicInteger(0)

    /**
     * The id of the line being spoken, or null when quiet. Only the newest line counts:
     * `QUEUE_FLUSH` drops older ones, so whether their own callbacks arrive doesn't matter.
     */
    @Volatile private var speakingId: String? = null

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
     *
     * With [voiceOn], the speech engine is started here too and waited for under the same
     * budget, so the opening "First up" is not lost to engine start-up.
     */
    suspend fun prime() {
        val speechInit = if (voiceOn && tts == null) startSpeech() else null
        if (soundIds.isEmpty()) {
            soundIds[Cue.COUNTDOWN] = load("cue_countdown.wav", ToneWav.COUNTDOWN)
            soundIds[Cue.GO] = load("cue_go.wav", ToneWav.GO)
            soundIds[Cue.FINISH] = load("cue_finish.wav", ToneWav.FINISH)
        }
        val completed = withTimeoutOrNull(PRIME_TIMEOUT_MS) {
            loaded.await()
            speechInit?.await()
        }
        if (completed == null) {
            Log.w(TAG, "Cue tones or voice did not finish loading within ${PRIME_TIMEOUT_MS}ms; some cues may not play")
        }
    }

    /**
     * Binds the phone's text-to-speech engine. Built on the main thread, where its init
     * callback is delivered. The returned signal completes once init has finished, whether or
     * not it succeeded — a phone with no engine or no English voice just stays silent.
     */
    private suspend fun startSpeech(): CompletableDeferred<Unit> {
        val done = CompletableDeferred<Unit>()
        withContext(Dispatchers.Main) {
            var engine: TextToSpeech? = null
            // A missing engine can report failure from inside the constructor, before
            // `engine` is assigned — hence the null-safe configure.
            engine = TextToSpeech(context) { status ->
                ttsReady = status == TextToSpeech.SUCCESS && engine?.let(::configure) == true
                if (!ttsReady) Log.w(TAG, "Text-to-speech unavailable (status $status); the voice will be silent")
                done.complete(Unit)
            }
            tts = engine
        }
        return done
    }

    /** English (exercise names are English): the phone's own variant if it is in English, else US. */
    private fun configure(engine: TextToSpeech): Boolean {
        val locale = Locale.getDefault().takeIf { it.language == Locale.ENGLISH.language } ?: Locale.US
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return false
        engine.setAudioAttributes(speechAttributes)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = lineEnded(utteranceId)
            // The two-argument onError delegates to this one by default; override only this.
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = lineEnded(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = lineEnded(utteranceId)
        })
        return true
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
        // A line outlasts the tone-sized release delay; un-ducking music mid-sentence would
        // be worse than not ducking at all. lineEnded() re-posts this once the line is done.
        if (speakingId != null) return@Runnable
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

    /** Pushes an already-held focus's release out from now — never requests focus afresh. */
    private fun extendFocus() {
        if (!synchronized(focusLock) { focusHeld }) return
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

    /** Speaks [text], replacing any line still being spoken. Silent when muted or voice is off. */
    fun say(text: String) {
        if (muted || released || !voiceOn || !ttsReady) return
        val engine = tts ?: return
        val id = "line-${lineCounter.incrementAndGet()}"
        speakingId = id
        holdFocusForBurst()
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) lineEnded(id)
    }

    /** Cuts the current line short — on pause and on mute. */
    fun stopSpeech() {
        if (released) return
        speakingId = null
        tts?.stop()
        extendFocus()
    }

    /**
     * Suspends until the current line has finished, or [timeoutMs] passes. The service calls
     * this before tearing down after the last segment, so [release] does not cut off
     * "Workout complete".
     */
    suspend fun awaitQuiet(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) { while (speakingId != null) delay(50) }
    }

    private fun lineEnded(id: String?) {
        if (id == null || id != speakingId) return
        speakingId = null
        extendFocus()
    }

    fun release() {
        released = true
        speakingId = null
        tts?.shutdown()
        tts = null
        ttsReady = false
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
