# Voice Announcements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** With a new Settings switch on, a running workout speaks "First up / Next up: <exercise>" in a 6-second get-ready step before every exercise, "Rest" as rests begin, and "Workout complete" at the end, using the phone's built-in text-to-speech.

**Architecture:** The pure `WorkoutSession` inserts the get-ready steps (reusing the existing `LEAD_IN` segment kind) and emits a `speech` line on each `SessionTick`, exactly as it already emits tone `Cue`s. `CuePlayer` gains a `TextToSpeech` engine so tones and voice share one music-ducking policy. `ExecutionService` reads the new pref, passes it to the session, and speaks what ticks emit.

**Tech Stack:** Kotlin, Jetpack Compose, `android.speech.tts.TextToSpeech`, kotlin.test on the JVM. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-10-03-voice-announcements-design.md`

## Global Constraints

- No new dependencies. minSdk 26.
- Spoken lines, exactly: `First up: <name>` (first get-ready step), `Next up: <name>` (every later one), `Rest`, `Workout complete`. Nothing else is spoken.
- Get-ready step: 6 s with voice on. Voice off: today's single 3 s lead-in, unchanged.
- Settings label: `Voice announcements`, under `Workout cues`, **default on**. `Workout cues` off (= muted) silences voice too.
- Voice setting is read when a workout starts. The in-workout mute silences tones and voice.
- No speech engine / no English voice → silent voice, workout runs normally.
- Every Gradle command needs `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` first (java is not on PATH). Run from the repo root in Git Bash.

## Review Focus

1. **Waking from a long sleep lands inside a get-ready step** → one "Next up" line, no burst of stale lines. *Pinned by a Task 1 test.*
2. **Pause mid-sentence** → the voice stops at once; Resume does not repeat the line. *Task 3 wiring; checked on device in Task 4.*
3. **"Workout complete" cut off** because the service tears down 100 ms after finishing → the service waits (≤4 s) for the line to end before releasing audio. *Task 2 `awaitQuiet` + Task 3; checked in Task 4.*
4. **Phone with no TTS engine or no English voice** → a logged warning, silent voice, beeps and timing unchanged. *Task 2 code path; logcat check in Task 4.*
5. **Music ducking ends mid-sentence** because the tone-sized 1.5 s focus release fires while a ~2.5 s line is still playing → focus is not released while a line is speaking. *Task 2 `releaseFocus` guard; checked by ear in Task 4 (user's phone).*

---

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `app/src/main/java/com/ottotech/workoutbuilder/domain/WorkoutSession.kt` | Modify | Get-ready insertion, `SessionTick.speech`, rest-countdown suppression, no go tone into a get-ready step |
| `app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutSessionTest.kt` | Modify | Tests for all of the above |
| `app/src/main/java/com/ottotech/workoutbuilder/audio/CuePlayer.kt` | Modify | Text-to-speech: `voiceOn`, `say`, `stopSpeech`, `awaitQuiet`; focus held for whole sentence |
| `app/src/main/java/com/ottotech/workoutbuilder/data/AppPrefs.kt` | Modify | `voiceOn` pref, default true |
| `app/src/main/java/com/ottotech/workoutbuilder/ui/settings/SettingsViewModel.kt` | Modify | `voiceOn` state + `onVoiceOn` |
| `app/src/main/java/com/ottotech/workoutbuilder/ui/settings/SettingsScreen.kt` | Modify | Second switch row via a shared private `SettingSwitch` |
| `app/src/main/java/com/ottotech/workoutbuilder/execution/ExecutionService.kt` | Modify | Read pref, start session with voice, speak ticks, stop speech on pause/mute, wait for last line on finish |
| `app/src/main/java/com/ottotech/workoutbuilder/ui/execution/ExecutionScreen.kt` | Modify | Lead-in caption "First up" vs "Next up" |
| `docs/superpowers/STATUS.md` | Modify | Record the branch and what's outstanding |

---

### Task 1: Session logic — get-ready steps and spoken lines

**Files:**
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/domain/WorkoutSession.kt`
- Test: `app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutSessionTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `data class SessionTick(val session: WorkoutSession, val cues: List<Cue> = emptyList(), val speech: String? = null)` — existing two-value destructuring (`val (s, cues) = …`) keeps working.
  - `WorkoutSession.start(segments: List<Segment>, now: Long, leadInSec: Int = 0, voice: Boolean = false): SessionTick`

- [ ] **Step 1: Write the failing tests**

Append inside `class WorkoutSessionTest`, after the last existing test:

```kotlin
    // ---- voice: get-ready steps and spoken lines ----

    /** A workout started with voice on and a 6 s get-ready step, as the service starts it. */
    private fun voiced(vararg segments: Segment) =
        WorkoutSession.start(indexed(*segments), now = 0L, leadInSec = 6, voice = true)

    @Test
    fun `with voice on, every exercise gets a get-ready step and rests do not`() {
        val s = voiced(timed(30, "Squat"), rest(10), timed(30, "Lunge"), timed(30, "Plank")).session
        assertEquals(
            listOf(
                SegmentKind.LEAD_IN, SegmentKind.WORK, SegmentKind.REST,
                SegmentKind.LEAD_IN, SegmentKind.WORK, SegmentKind.LEAD_IN, SegmentKind.WORK,
            ),
            s.segments.map { it.kind },
        )
        assertEquals(listOf("Squat", "Squat", "Rest", "Lunge", "Lunge", "Plank", "Plank"), s.segments.map { it.exerciseName })
        assertTrue(s.segments.filter { it.kind == SegmentKind.LEAD_IN }.all { it.durationSec == 6 && it.mode == ItemMode.TIMED })
        assertEquals(3, s.workTotal, "get-ready steps are not exercises")
    }

    @Test
    fun `with voice off, only the first segment gets a lead-in, as before`() {
        val s = WorkoutSession.start(indexed(timed(30), rest(10), timed(30)), now = 0L, leadInSec = 3).session
        assertEquals(listOf(SegmentKind.LEAD_IN, SegmentKind.WORK, SegmentKind.REST, SegmentKind.WORK), s.segments.map { it.kind })
    }

    @Test
    fun `a voiced workout opens on a silent get-ready step that says first up`() {
        val tick = voiced(timed(30, "Squat"))
        assertEquals(SegmentKind.LEAD_IN, tick.session.current?.kind)
        assertEquals(6, tick.session.remainingSecDisplay(0L))
        assertTrue(tick.cues.isEmpty(), "no beep until the 3-2-1 at the end of the step")
        assertEquals("First up: Squat", tick.speech)
    }

    @Test
    fun `a get-ready step beeps 3-2-1 at its end, then go starts the exercise with nothing said`() {
        var s = voiced(timed(30, "Squat")).session
        val beeps = listOf(3_500L, 4_500L, 5_500L).map { t -> s.tick(t).also { s = it.session }.cues }
        assertEquals(List(3) { listOf(Cue.COUNTDOWN) }, beeps)
        val go = s.tick(6_000L)
        assertEquals(SegmentKind.WORK, go.session.current?.kind)
        assertEquals(listOf(Cue.GO), go.cues)
        assertNull(go.speech, "the get-ready step already named it")
    }

    @Test
    fun `entering a rest says rest, and leaving it says next up with no go tone`() {
        // LEAD_IN 0-6 · WORK 6-16 · REST 16-26 · LEAD_IN 26-32 · WORK 32-42
        val s = voiced(timed(10), rest(10), timed(10, "Lunge")).session.tick(6_000L).session
        val intoRest = s.tick(16_000L)
        assertEquals(SegmentKind.REST, intoRest.session.current?.kind)
        assertEquals("Rest", intoRest.speech)
        assertEquals(listOf(Cue.GO), intoRest.cues)
        val intoGetReady = intoRest.session.tick(26_000L)
        assertEquals(SegmentKind.LEAD_IN, intoGetReady.session.current?.kind)
        assertEquals("Next up: Lunge", intoGetReady.speech)
        assertTrue(intoGetReady.cues.isEmpty(), "the voice, not a go tone, opens a get-ready step")
    }

    @Test
    fun `a rest followed by a get-ready step counts down silently`() {
        var s = voiced(timed(10), rest(10), timed(10)).session.tick(6_000L).session.tick(16_000L).session
        assertEquals(SegmentKind.REST, s.current?.kind) // ends at 26s
        for (t in listOf(23_500L, 24_500L, 25_500L)) {
            val tick = s.tick(t)
            assertTrue(tick.cues.isEmpty(), "rest must not beep at ${t}ms — the get-ready step does the 3-2-1")
            s = tick.session
        }
    }

    @Test
    fun `an exercise followed by a get-ready step still beeps its 3-2-1 to finish`() {
        val s = voiced(timed(10), timed(10)).session.tick(6_000L).session // exercise ends at 16s
        assertEquals(listOf(Cue.COUNTDOWN), s.tick(13_500L).cues)
    }

    @Test
    fun `with voice off, a rest still beeps its 3-2-1`() {
        // Voice off: a rest is never followed by a lead-in, so it beeps exactly as before.
        var s = WorkoutSession.start(indexed(timed(10), rest(10), timed(10)), now = 0L).session
        s = s.tick(10_000L).session // into the rest, ends at 20s
        assertEquals(listOf(Cue.COUNTDOWN), s.tick(17_500L).cues)
    }

    @Test
    fun `finishing says workout complete`() {
        val s = voiced(timed(10)).session.tick(6_000L).session
        val end = s.tick(16_000L)
        assertTrue(end.session.finished)
        assertEquals(listOf(Cue.FINISH), end.cues)
        assertEquals("Workout complete", end.speech)
    }

    @Test
    fun `skipping a rest lands on the get-ready step and names the exercise`() {
        val s = voiced(timed(10), rest(10), timed(10, "Lunge")).session.tick(6_000L).session.tick(16_000L).session
        val skipped = s.skip(17_000L)
        assertEquals(SegmentKind.LEAD_IN, skipped.session.current?.kind)
        assertEquals("Next up: Lunge", skipped.speech)
        assertTrue(skipped.cues.isEmpty())
    }

    @Test
    fun `skipping a get-ready step starts the exercise with go and nothing said`() {
        val skipped = voiced(timed(10, "Squat")).session.skip(1_000L)
        assertEquals(SegmentKind.WORK, skipped.session.current?.kind)
        assertEquals(listOf(Cue.GO), skipped.cues)
        assertNull(skipped.speech)
    }

    @Test
    fun `restarting a get-ready step says its line again, but not while paused`() {
        val s = voiced(timed(10, "Squat")).session
        val restarted = s.restart(2_000L)
        assertEquals("First up: Squat", restarted.speech)
        assertTrue(restarted.cues.isEmpty())
        assertNull(s.pause(2_000L).restart(3_000L).speech)
    }

    @Test
    fun `waking after a long sleep speaks only the segment it lands on`() {
        // LEAD_IN 0-6 · Squat 6-16 · REST 16-26 · LEAD_IN 26-32 · Lunge 32-42
        val woke = voiced(timed(10, "Squat"), rest(10), timed(10, "Lunge")).session.tick(28_000L)
        assertEquals(3, woke.session.index)
        assertEquals(SegmentKind.LEAD_IN, woke.session.current?.kind)
        assertEquals("Next up: Lunge", woke.speech)
        assertTrue(woke.cues.isEmpty())
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest --tests "com.ottotech.workoutbuilder.domain.WorkoutSessionTest"
```
Expected: compilation FAILS — `voice` is not a parameter of `start`, `speech` is not a property of `SessionTick`.

- [ ] **Step 3: Implement**

In `WorkoutSession.kt`:

3a. Replace the `SessionTick` declaration with:

```kotlin
/** A session transition: the new state, plus whatever should be heard at that instant. */
data class SessionTick(
    val session: WorkoutSession,
    val cues: List<Cue> = emptyList(),
    /**
     * A line for the voice to say at this transition, or null. Emitted whether or not voice is
     * switched on — the service decides whether to speak it.
     */
    val speech: String? = null,
)
```

3b. In `tick`, change the `due` line to:

```kotlin
        val due = second in 1..3 && (lastCountdown == null || second < lastCountdown) && countsDown
```

and add this property directly above `tick`:

```kotlin
    /**
     * A rest that hands over to a get-ready step stays silent: that step's own 3-2-1 is the
     * countdown, and beeping here too would be beeps, voice, beeps. With voice off a rest is
     * never followed by a lead-in, so this is always true there.
     */
    private val countsDown: Boolean
        get() = !(current?.kind == SegmentKind.REST && segments.getOrNull(index + 1)?.kind == SegmentKind.LEAD_IN)
```

3c. In `advance`, change the return line to:

```kotlin
        return if (next >= segments.size) finish() else entered(beginSegment(next, now - carry), next)
```

3d. Add these two functions directly below `beginSegment`:

```kotlin
    /**
     * The tick for having just begun segment [at] of [session]. A get-ready step opens with the
     * voice and no tone — "go" is kept for the exercise itself.
     */
    private fun entered(session: WorkoutSession, at: Int): SessionTick {
        val cues = if (session.segments[at].kind == SegmentKind.LEAD_IN) emptyList() else listOf(Cue.GO)
        return SessionTick(session, cues, speechFor(at))
    }

    /** What the voice says as segment [at] begins — see [SessionTick.speech]. */
    private fun speechFor(at: Int): String? {
        val segment = segments[at]
        return when (segment.kind) {
            SegmentKind.LEAD_IN -> (if (at == 0) "First up: " else "Next up: ") + segment.exerciseName
            SegmentKind.REST -> "Rest"
            SegmentKind.WORK -> null // its get-ready step already named it
        }
    }
```

3e. Replace `finish()` with:

```kotlin
    private fun finish() = SessionTick(
        copy(index = segments.size, finished = true, lastCountdown = null),
        listOf(Cue.FINISH),
        "Workout complete",
    )
```

3f. In `skip`, change the return line to:

```kotlin
        return if (next >= segments.size) finish() else entered(beginSegment(next, now), next)
```

3g. In `restart`, change the return line to:

```kotlin
        return if (isPaused) SessionTick(beginSegment(index, now, paused = true)) else entered(beginSegment(index, now), index)
```

3h. Replace the whole `companion object` with:

```kotlin
    companion object {
        /**
         * [leadInSec] puts a "3, 2, 1" in front of the first segment. It is an ordinary timed
         * [SegmentKind.LEAD_IN] segment, so [tick]'s last-three-seconds beeps are the count,
         * advancing past it plays go, and pause / skip / restart need no special case.
         *
         * With [voice] on, a lead-in — a get-ready step — goes in front of **every** exercise,
         * not just the first, giving the voice room to name it before the 3-2-1.
         */
        fun start(segments: List<Segment>, now: Long, leadInSec: Int = 0, voice: Boolean = false): SessionTick {
            // Nothing to run. Finish silently — a finish tone for a workout that never
            // started would be a lie, and Home must not record it as performed.
            if (segments.isEmpty()) return SessionTick(WorkoutSession(segments, finished = true))
            if (leadInSec <= 0) return SessionTick(WorkoutSession(segments, startedAt = now), listOf(Cue.GO))
            // A copy of the segment it precedes, so a lead-in can name what's coming.
            fun leadIn(before: Segment) =
                before.copy(kind = SegmentKind.LEAD_IN, mode = ItemMode.TIMED, durationSec = leadInSec)
            val withLeadIns = if (voice) {
                // A compiled workout always opens on an exercise (SegmentCompiler only puts
                // rests after items), so this also covers the first segment.
                segments.flatMap { if (it.kind == SegmentKind.WORK) listOf(leadIn(it), it) else listOf(it) }
            } else {
                listOf(leadIn(segments.first())) + segments
            }
            val session = WorkoutSession(withLeadIns, startedAt = now)
            return session.tick(now).copy(speech = session.speechFor(0))
        }
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run:
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest --tests "com.ottotech.workoutbuilder.domain.WorkoutSessionTest"
```
Expected: PASS — all new tests and every pre-existing test in the class (voice-off behaviour is unchanged).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/domain/WorkoutSession.kt app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutSessionTest.kt
git commit -m "feat: get-ready steps and spoken lines in the workout session

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: CuePlayer speaks

**Files:**
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/audio/CuePlayer.kt`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces (used by Task 3):
  - `var voiceOn: Boolean` — set before `prime()`.
  - `suspend fun prime()` — now also starts and waits for the speech engine when `voiceOn`.
  - `fun say(text: String)`
  - `fun stopSpeech()`
  - `suspend fun awaitQuiet(timeoutMs: Long)`

No JVM test: `TextToSpeech` is an Android framework class and the project has no instrumented tests. Verified by the build here and on the emulator in Task 4.

- [ ] **Step 1: Add imports**

Add to the import block:

```kotlin
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
```

- [ ] **Step 2: Update the class doc**

Replace the first line of the class KDoc, `Plays the three cue tones.`, with:

```kotlin
 * Plays the three cue tones, and speaks the session's lines through the phone's built-in
 * text-to-speech when voice is on — one class, so tones and voice share one audio-focus policy.
```

- [ ] **Step 3: Add speech state**

Directly below `var muted: Boolean = false`, add:

```kotlin
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
```

- [ ] **Step 4: Replace `prime()`**

Replace the whole `prime()` function (keep its KDoc, and append the paragraph below to it):

```kotlin
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
```

(The old body began `if (soundIds.isNotEmpty()) return`; the new `if (soundIds.isEmpty())` block replaces it, because a second workout in the same service instance may now need the speech engine even though tones are loaded.)

- [ ] **Step 5: Keep focus through a sentence**

Replace the `releaseFocus` property with:

```kotlin
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
```

and add directly below `holdFocusForBurst()`:

```kotlin
    /** Pushes an already-held focus's release out from now — never requests focus afresh. */
    private fun extendFocus() {
        if (!synchronized(focusLock) { focusHeld }) return
        mainHandler.removeCallbacks(releaseFocus)
        mainHandler.postDelayed(releaseFocus, FOCUS_RELEASE_DELAY_MS)
    }
```

- [ ] **Step 6: Add `say`, `stopSpeech`, `awaitQuiet`, `lineEnded`**

Add directly below `play(cue: Cue)`:

```kotlin
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
```

- [ ] **Step 7: Release the engine**

In `release()`, directly after `released = true`, add:

```kotlin
        speakingId = null
        tts?.shutdown()
        tts = null
        ttsReady = false
```

- [ ] **Step 8: Build**

Run:
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL. (If `Dispatchers.Main` fails to resolve, `kotlinx-coroutines-android` is missing from the classpath — stop and report; do not add a dependency without asking.)

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/audio/CuePlayer.kt
git commit -m "feat: CuePlayer speaks lines through the phone's text-to-speech

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Settings switch and wiring it into the workout

**Files:**
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/data/AppPrefs.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/execution/ExecutionService.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/execution/ExecutionScreen.kt`

**Interfaces:**
- Consumes: `WorkoutSession.start(..., leadInSec, voice)` and `SessionTick.speech` (Task 1); `CuePlayer.voiceOn`, `say`, `stopSpeech`, `awaitQuiet` (Task 2).
- Produces: `AppPrefs.voiceOn: Boolean`; `SettingsUiState.voiceOn`; `SettingsViewModel.onVoiceOn(Boolean)`.

- [ ] **Step 1: The pref**

In `AppPrefs.kt`, add below `cuesMuted`:

```kotlin
    /** Spoken exercise names during a workout. On by default; read when a workout starts. */
    var voiceOn: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ON, value).apply()
```

and in its companion: `const val KEY_VOICE_ON = "voice_on"`.

- [ ] **Step 2: Settings state**

In `SettingsViewModel.kt`:
- Add `val voiceOn: Boolean,` to `SettingsUiState`, directly after `val soundOn: Boolean,`.
- In the `_state` initialiser, add `voiceOn = prefs.voiceOn,` after `soundOn = !prefs.cuesMuted,`.
- Add below `onSoundOn`:

```kotlin
    fun onVoiceOn(on: Boolean) {
        prefs.voiceOn = on
        _state.update { it.copy(voiceOn = on) }
    }
```

- [ ] **Step 3: Settings screen**

In `SettingsScreen.kt`, replace the whole `item { Row( ... "Workout cues" ... ) }` block under `SectionLabel("Sound")` with:

```kotlin
            item { SettingSwitch("Workout cues", checked = state.soundOn, onCheckedChange = viewModel::onSoundOn) }
            item { SettingSwitch("Voice announcements", checked = state.voiceOn, onCheckedChange = viewModel::onVoiceOn) }
```

and add at the end of the file:

```kotlin
/** A labelled on/off row in the Sound section. */
@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = Dimens.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AppType.cardTitle, color = colors.onBackground, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.surfaceInverse,
                checkedThumbColor = colors.onSurfaceInverse,
                checkedBorderColor = colors.surfaceInverse,
                uncheckedTrackColor = colors.surface,
                uncheckedThumbColor = colors.textMuted,
                uncheckedBorderColor = colors.border,
            ),
        )
    }
}
```

- [ ] **Step 4: Service — start**

In `ExecutionService.begin()`, replace from `cues.muted = app.prefs.cuesMuted` through `sessionCues.forEach { play(it) } // the lead-in's opening "3" beep` with:

```kotlin
                val voice = app.prefs.voiceOn
                cues.muted = app.prefs.cuesMuted
                cues.voiceOn = voice
                cues.prime()
                val start = WorkoutSession.start(
                    segments,
                    now(),
                    leadInSec = if (voice) GET_READY_SEC else LEAD_IN_SEC,
                    voice = voice,
                )
                acquireWakeLock()
                _state.value = ExecutionState(
                    workoutName = workout.name,
                    workoutId = workoutId,
                    session = start.session,
                    muted = app.prefs.cuesMuted,
                )
                start.cues.forEach { play(it) } // voice off: the lead-in's opening "3" beep
                start.speech?.let { cues.say(it) } // voice on: "First up: …"
```

and in the companion, below `LEAD_IN_SEC`:

```kotlin
        /** With voice on, each exercise's get-ready step: ~3 s to name it, then "3, 2, 1". */
        private const val GET_READY_SEC = 6

        /** How long teardown waits for "Workout complete" to finish before releasing audio. */
        private const val FINISH_SPEECH_TIMEOUT_MS = 4_000L
```

- [ ] **Step 5: Service — speak what ticks emit**

Replace `updateWithCues` with (KDoc unchanged, plus "and spoken lines" appended to its first sentence):

```kotlin
    private inline fun updateWithCues(transform: (WorkoutSession) -> com.ottotech.workoutbuilder.domain.SessionTick) {
        var applied = false
        var pendingCues: List<Cue> = emptyList()
        var pendingSpeech: String? = null
        var committed: WorkoutSession? = null
        _state.update { current ->
            applied = false
            val session = current.session ?: return@update current
            val tick = transform(session)
            if (tick.session == session && tick.cues.isEmpty() && tick.speech == null) return@update current
            applied = true
            pendingCues = tick.cues
            pendingSpeech = tick.speech
            committed = tick.session
            current.copy(session = tick.session)
        }
        if (applied) {
            pendingCues.forEach { play(it) }
            pendingSpeech?.let { cues.say(it) }
            refreshNotification()
            // As with pendingCues above: `committed` is the session from whichever invocation
            // of the update block actually won the compare-and-set, so this check and the
            // markPerformed write it can trigger happen exactly once per real transition —
            // never from a retried, discarded invocation, and never inside the update{}
            // lambda itself (which could re-run under contention and write the row twice).
            val next = committed
            if (next != null && next.finished && !performedRecorded && next.segments.isNotEmpty()) {
                performedRecorded = true
                scope.launch { app.workouts.markPerformed(runningWorkoutId) }
            }
        }
    }
```

- [ ] **Step 6: Service — pause, mute, finish**

6a. Add below `updateWithCues`:

```kotlin
    /** Pausing also cuts off a line mid-sentence; resuming does not repeat it. */
    private fun pauseSession() {
        update { it.pause(now()) }
        cues.stopSpeech()
    }
```

6b. Change the media session's `override fun onPause() { update { it.pause(now()) } }` to `override fun onPause() { pauseSession() }`, and in `onStartCommand` change `ACTION_PAUSE -> update { it.pause(now()) }` to `ACTION_PAUSE -> pauseSession()`.

6c. In `handleToggleMute()`, after `cues.muted = muted`, add:

```kotlin
        if (muted) cues.stopSpeech()
```

6d. In `runClock()`, inside `if (session.finished) {`, insert before `stopForFinish()`:

```kotlin
                // Let "Workout complete" finish — teardown releases the voice mid-word otherwise.
                cues.awaitQuiet(FINISH_SPEECH_TIMEOUT_MS)
```

- [ ] **Step 7: Lead-in caption**

In `ExecutionScreen.kt`:
- Change the call to `LeadIn(seconds = session.remainingSecDisplay(now) ?: 0, upNext = segment.exerciseName, first = session.index == 0)`.
- Change the signature to `private fun ColumnScope.LeadIn(seconds: Int, upNext: String, first: Boolean)`.
- Change `Text("First up", ...)` to `Text(if (first) "First up" else "Next up", style = AppType.timerCaption, color = ExecutionColors.textMuted)`.
- Change `Text(firstUp, ...)` to `Text(upNext, ...)`.
- Change the KDoc's first sentence to: `The "3, 2, 1" before an exercise — always the first one, and every one with voice on.`

- [ ] **Step 8: Build and run every test**

Run:
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: BUILD SUCCESSFUL, all tests green (197 before this plan + 13 from Task 1 = 210).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/data/AppPrefs.kt app/src/main/java/com/ottotech/workoutbuilder/ui/settings/SettingsViewModel.kt app/src/main/java/com/ottotech/workoutbuilder/ui/settings/SettingsScreen.kt app/src/main/java/com/ottotech/workoutbuilder/execution/ExecutionService.kt app/src/main/java/com/ottotech/workoutbuilder/ui/execution/ExecutionScreen.kt
git commit -m "feat: Voice announcements switch, spoken during workouts

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Verify on the emulator, hand over the APK

**Files:**
- Modify: `docs/superpowers/STATUS.md`

- [ ] **Step 1: Install on the emulator**

Start the Pixel 8 Pro AVD from Android Studio's Device Manager if it isn't running, then:
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
ADB=/c/Users/ottol/AppData/Local/Android/Sdk/platform-tools/adb.exe
./gradlew :app:installDebug && $ADB shell monkey -p com.ottotech.workoutbuilder 1
```

- [ ] **Step 2: Settings**

Open Settings, scroll to Sound. Screenshot (`$ADB exec-out screencap -p > <scratchpad>/settings.png`). Expected: "Workout cues" and "Voice announcements", both on. Toggle Voice off and on; leave it on.

- [ ] **Step 3: A voiced workout**

Clear logcat (`$ADB logcat -c`), start Court Speed from Home. Screenshot at ~1 s: GET READY, a 6/5 countdown, "First up", the exercise name. Let it run through the first exercise into the next get-ready step; screenshot: "Next up". Then:
```bash
$ADB logcat -d | grep -iE "tts|TextToSpeech|CuePlayer"
```
Expected: no "Text-to-speech unavailable" warning; TTS engine activity present. Pause during a get-ready step — the line stops; resume — nothing repeats.

- [ ] **Step 4: Voice off**

Turn Voice announcements off, start a workout. Expected: today's 3 s GET READY with "First up", no get-ready steps later, rests beep 3-2-1.

- [ ] **Step 5: APK to Drive**

```bash
cp app/build/outputs/apk/debug/app-debug.apk "/g/My Drive/BLDR/bldr-voice-announcements.apk"
```

- [ ] **Step 6: STATUS.md**

Add a row to the phase table:

```markdown
| **Voice announcements** | **Complete on `voice-announcements`, not yet merged** (branched from `bldr-brand`). Plan: `plans/2026-10-03-voice-announcements.md`. Built-in phone voice says "First up / Next up: <exercise>" in a 6 s get-ready step before every exercise, "Rest", and "Workout complete"; Settings → Sound → Voice announcements (default on). 210 tests green; verified on the emulator. **Outstanding, needs the user:** hear it on the real S22 — with music playing (ducks for the whole sentence), screen off in a pocket, pause mid-sentence, and that "Workout complete" isn't cut off. |
```

and set **Last updated** to 2026-10-03.

- [ ] **Step 7: Commit**

```bash
git add docs/superpowers/STATUS.md
git commit -m "docs: status for voice announcements

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
