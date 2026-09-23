# Plan 1D — Execution & Audio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A workout you have built can be run end to end — timed, reps and manual segments — with audio cues that keep firing while the screen is off and the phone is in a pocket.

**Architecture:** A pure `WorkoutSession` in `domain/` holds all timing and cue logic as a function of a clock reading passed in, so it is JVM-testable in milliseconds. An `ExecutionService` (foreground, `mediaPlayback`) owns the real clock, drives the session at 10 Hz, plays `SoundPool` tones, and publishes state as a `StateFlow`. The Compose screen observes that state and sends intents back; it never owns the clock.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, Room (existing), Android foreground service, `SoundPool`, `MediaSession`, `AudioManager` focus. No new third-party dependencies.

**Spec:** `docs/superpowers/specs/2026-09-23-plan-1d-execution-audio-design.md`, which extends `docs/superpowers/specs/2026-07-21-workout-builder-android-design.md` §6.5 and §7.

## Global Constraints

- `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36` — do not raise.
- **No new third-party dependencies.** The project uses manual DI (`WorkoutApp`) and framework audio deliberately.
- **`domain/` must contain no `import android.*`** — enforced by `DomainPurityTest`, which fails the build.
- **No literal hex outside `ui/theme/Colors.kt`.** Everything else reads `LocalAppColors.current`.
- Timing is wall-clock via `SystemClock.elapsedRealtime()`, never tick accumulation (spec §7).
- Tone frequencies, fixed: **720 Hz** countdown (0.18s, vol 0.45), **1100 Hz** go (0.32s, vol 0.55), **880 Hz** finish (0.7s, vol 0.55).
- Execution screen is **always dark in both themes**: background `#0B0B0D`, card `#18181C`, ring track `rgba(255,255,255,.12)`, ring progress `#FFFFFF`, text `#FFFFFF`, muted `#9A9A9A`.
- Every touch target ≥ `Dimens.minTouchTarget` (48dp).
- Test style: `kotlin.test`, backtick-named test functions, matching `WorkoutDraftTest`.
- Java toolchain: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` before any Gradle command.

## Review Focus

These are the input classes the spec implies but which no task's happy-path tests would exercise. Each has a test pinned into the task that owns the code.

1. **A workout that compiles to zero segments** (every section empty) — starting it must finish cleanly and return to Home without a crash or a bogus "workout done" record. *(Task 1, Step 1; Task 7, Step 3)*
2. **Displayed seconds must never show a wrong number** — no "0" held on screen while time remains, no skipped number. Ceiling, not floor. *(Task 1, Step 13)*
3. **Pausing inside the final three seconds** — the 3-2-1 beeps must not re-fire on resume. *(Task 1, Step 17)*
4. **Skip on the last segment** — finishes, never an index out of bounds. *(Task 1, Step 21)*
5. **A long sleep gap spanning several segments** — lands on the correct segment with one go tone, not a burst of replayed cues; and a manual segment inside the gap stops the catch-up rather than being consumed. *(Task 1, Steps 23 and 25)*

---

## File Structure

**Create:**
- `app/src/main/java/com/ottotech/workoutbuilder/domain/WorkoutSession.kt` — pure timing and cue state machine
- `app/src/main/java/com/ottotech/workoutbuilder/audio/ToneWav.kt` — pure PCM/WAV tone generation
- `app/src/main/java/com/ottotech/workoutbuilder/audio/CuePlayer.kt` — SoundPool wrapper, audio focus
- `app/src/main/java/com/ottotech/workoutbuilder/execution/ExecutionService.kt` — foreground service, clock owner
- `app/src/main/java/com/ottotech/workoutbuilder/execution/ExecutionNotification.kt` — notification channel and builder
- `app/src/main/java/com/ottotech/workoutbuilder/ui/execution/ExecutionScreen.kt` — screen host, mode dispatch
- `app/src/main/java/com/ottotech/workoutbuilder/ui/execution/TimerRing.kt` — countdown/count-up ring
- `app/src/main/java/com/ottotech/workoutbuilder/ui/execution/RepsPanel.kt` — set rows for REPS mode
- `app/src/main/java/com/ottotech/workoutbuilder/ui/execution/FinishScreen.kt` — end-of-workout screen
- `app/src/main/java/com/ottotech/workoutbuilder/ui/execution/ExecutionViewModel.kt` — binds screen to service state
- `app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutSessionTest.kt`
- `app/src/test/java/com/ottotech/workoutbuilder/audio/ToneWavTest.kt`

**Modify:**
- `app/src/main/AndroidManifest.xml` — permissions, service declaration
- `app/src/main/java/com/ottotech/workoutbuilder/ui/AppNav.kt:68` — replace the `TODO(Plan 1D)` no-op
- `app/src/main/java/com/ottotech/workoutbuilder/data/AppPrefs.kt` — add the `cuesMuted` flag
- `app/src/main/java/com/ottotech/workoutbuilder/ui/theme/Type.kt` — add `timerDigits`
- `app/src/main/java/com/ottotech/workoutbuilder/ui/theme/Colors.kt` — add the fixed execution palette
- `docs/superpowers/STATUS.md` — record the phase

---

### Task 1: `WorkoutSession` — the pure timing brain

This is the correctness-critical task of the phase. Everything else is plumbing around it.

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/domain/WorkoutSession.kt`
- Test: `app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutSessionTest.kt`

**Interfaces:**
- Consumes: `Segment`, `SegmentKind`, `ItemMode` from `domain/` (already exist).
- Produces:
  - `enum class Cue { COUNTDOWN, GO, FINISH }`
  - `data class SessionTick(val session: WorkoutSession, val cues: List<Cue>)`
  - `data class WorkoutSession(segments, index, startedAt, pausedAt, pausedTotal, lastCountdown, finished)`
  - `WorkoutSession.Companion.start(segments: List<Segment>, now: Long): SessionTick`
  - Methods: `tick(now: Long): SessionTick`, `skip(now: Long): SessionTick`, `complete(now: Long): SessionTick`, `pause(now: Long): WorkoutSession`, `resume(now: Long): WorkoutSession`
  - Queries: `current: Segment?`, `isPaused: Boolean`, `elapsedMs(now): Long`, `remainingMs(now): Long?`, `remainingSecDisplay(now): Int?`, `workIndex: Int`, `workTotal: Int`
  - All times are `SystemClock.elapsedRealtime()` milliseconds, passed in. The class never reads a clock.

- [ ] **Step 1: Write the failing test for starting a session**

Create `app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutSessionTest.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutSessionTest {

    /** A timed WORK segment of [sec] seconds. */
    private fun timed(sec: Int, name: String = "Move") = Segment(
        kind = SegmentKind.WORK,
        mode = ItemMode.TIMED,
        exerciseName = name,
        label = "BLOCK A",
        durationSec = sec,
    )

    /** A timed REST segment of [sec] seconds. */
    private fun rest(sec: Int) = Segment(
        kind = SegmentKind.REST,
        mode = ItemMode.TIMED,
        exerciseName = "Rest",
        label = "BLOCK A",
        durationSec = sec,
    )

    private fun manual(name: String = "Hold") = Segment(
        kind = SegmentKind.WORK,
        mode = ItemMode.MANUAL,
        exerciseName = name,
        label = "BLOCK A",
    )

    private fun reps(name: String = "Press", sets: Int = 3, reps: Int = 10) = Segment(
        kind = SegmentKind.WORK,
        mode = ItemMode.REPS,
        exerciseName = name,
        label = "BLOCK A",
        sets = sets,
        reps = reps,
    )

    private fun indexed(vararg segments: Segment): List<Segment> =
        segments.toList().mapIndexed { i, s -> s.copy(index = i, total = segments.size) }

    // ---- start ----

    @Test
    fun `start begins on the first segment and fires the go cue`() {
        val (session, cues) = WorkoutSession.start(indexed(timed(30)), now = 1_000L)
        assertEquals(0, session.index)
        assertEquals(1_000L, session.startedAt)
        assertFalse(session.finished)
        assertEquals(listOf(Cue.GO), cues)
    }

    @Test
    fun `starting a workout with no segments finishes immediately and plays nothing`() {
        val (session, cues) = WorkoutSession.start(emptyList(), now = 1_000L)
        assertTrue(session.finished)
        assertNull(session.current)
        assertTrue(cues.isEmpty(), "an empty workout must not play a finish tone")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest --tests '*WorkoutSessionTest*'
```

Expected: FAIL — `Unresolved reference: WorkoutSession`.

- [ ] **Step 3: Create the session type with `start` only**

Create `app/src/main/java/com/ottotech/workoutbuilder/domain/WorkoutSession.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

import kotlin.math.ceil

/** An audio cue the execution service should play. */
enum class Cue { COUNTDOWN, GO, FINISH }

/** A session transition: the new state, plus whatever should be heard at that instant. */
data class SessionTick(
    val session: WorkoutSession,
    val cues: List<Cue> = emptyList(),
)

/**
 * The running state of a workout, as a pure function of a clock reading.
 *
 * Deliberately Android-free and clock-free: every method takes `now` (a
 * `SystemClock.elapsedRealtime()` reading) as a parameter rather than reading a clock
 * itself. That is what makes "does it beep at 3, 2, 1 and advance at zero" a JVM test
 * measured in milliseconds instead of a stopwatch-and-phone exercise.
 *
 * Timing is wall-clock arithmetic, never tick accumulation (spec §7): a segment's remaining
 * time is its duration minus elapsed-since-start minus time spent paused. Scheduling delay
 * and device sleep therefore cannot make a 30-second interval drift.
 */
data class WorkoutSession(
    val segments: List<Segment>,
    val index: Int = 0,
    /** Clock reading at which the current segment began, back-dated by any carried overflow. */
    val startedAt: Long = 0L,
    /** Non-null while paused: the reading at which the pause began. */
    val pausedAt: Long? = null,
    /** Milliseconds spent paused during the current segment. */
    val pausedTotal: Long = 0L,
    /** The countdown second (3, 2 or 1) already beeped on this segment, so it fires once. */
    val lastCountdown: Int? = null,
    val finished: Boolean = false,
) {

    val current: Segment? get() = segments.getOrNull(index)

    val isPaused: Boolean get() = pausedAt != null

    companion object {
        fun start(segments: List<Segment>, now: Long): SessionTick =
            if (segments.isEmpty()) {
                // Nothing to run. Finish silently — a finish tone for a workout that never
                // started would be a lie, and Home must not record it as performed.
                SessionTick(WorkoutSession(segments, finished = true))
            } else {
                SessionTick(WorkoutSession(segments, index = 0, startedAt = now), listOf(Cue.GO))
            }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests '*WorkoutSessionTest*'
```

Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/domain/WorkoutSession.kt app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutSessionTest.kt
git commit -m "feat: add WorkoutSession with start

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

- [ ] **Step 6: Write the failing tests for elapsed and remaining time**

Append to `WorkoutSessionTest`:

```kotlin
    // ---- elapsed / remaining ----

    @Test
    fun `remaining time counts down from the segment duration`() {
        val s = WorkoutSession.start(indexed(timed(30)), now = 1_000L).session
        assertEquals(30_000L, s.remainingMs(1_000L))
        assertEquals(20_000L, s.remainingMs(11_000L))
    }

    @Test
    fun `a manual segment has no remaining time and counts elapsed upward`() {
        val s = WorkoutSession.start(indexed(manual()), now = 1_000L).session
        assertNull(s.remainingMs(5_000L))
        assertEquals(4_000L, s.elapsedMs(5_000L))
    }
```

- [ ] **Step 7: Run to verify it fails**

Expected: FAIL — `Unresolved reference: remainingMs`.

- [ ] **Step 8: Add the time queries**

Add inside `WorkoutSession`, below `isPaused`:

```kotlin
    /** Milliseconds actually spent on the current segment, excluding paused time. */
    fun elapsedMs(now: Long): Long {
        val end = pausedAt ?: now
        return (end - startedAt - pausedTotal).coerceAtLeast(0L)
    }

    /** Milliseconds left on the current segment, or null when it has no duration. */
    fun remainingMs(now: Long): Long? {
        val duration = current?.durationSec ?: return null
        return duration * 1000L - elapsedMs(now)
    }
```

- [ ] **Step 9: Run to verify it passes**

Expected: PASS (4 tests).

- [ ] **Step 10: Commit**

```bash
git add -A && git commit -m "feat: add elapsed and remaining time to WorkoutSession

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

- [ ] **Step 11: Write the failing test for auto-advance at zero**

Append:

```kotlin
    // ---- auto-advance ----

    @Test
    fun `a timed segment advances at zero and fires the go cue`() {
        val s = WorkoutSession.start(indexed(timed(30), timed(20)), now = 0L).session
        assertEquals(s, s.tick(29_000L).session, "must not advance early")

        val (next, cues) = s.tick(30_000L)
        assertEquals(1, next.index)
        assertEquals(listOf(Cue.GO), cues)
        assertEquals(20_000L, next.remainingMs(30_000L), "the new segment starts full")
    }

    @Test
    fun `a manual segment never advances on its own`() {
        val s = WorkoutSession.start(indexed(manual(), timed(20)), now = 0L).session
        assertEquals(0, s.tick(600_000L).session.index)
    }

    @Test
    fun `finishing the last segment fires the finish cue once`() {
        val s = WorkoutSession.start(indexed(timed(5)), now = 0L).session
        val (done, cues) = s.tick(5_000L)
        assertTrue(done.finished)
        assertEquals(listOf(Cue.FINISH), cues)
        assertEquals(done, done.tick(9_000L).session, "a finished session is inert")
        assertTrue(done.tick(9_000L).cues.isEmpty())
    }
```

- [ ] **Step 12: Run to verify it fails**

Expected: FAIL — `Unresolved reference: tick`.

- [ ] **Step 13: Write the failing test for the displayed seconds**

This pins Review Focus #2 — the number a person actually reads off the ring.

Append:

```kotlin
    // ---- displayed seconds ----

    @Test
    fun `displayed seconds round up so no number is skipped or held at zero`() {
        val s = WorkoutSession.start(indexed(timed(30)), now = 0L).session
        assertEquals(30, s.remainingSecDisplay(0L), "shows the full duration at the start")
        assertEquals(30, s.remainingSecDisplay(1L), "a millisecond in is still 30")
        assertEquals(29, s.remainingSecDisplay(1_000L))
        assertEquals(1, s.remainingSecDisplay(29_500L), "the last second shows 1, never 0")
        assertEquals(0, s.remainingSecDisplay(30_000L))
    }
```

- [ ] **Step 14: Run to verify it fails**

Expected: FAIL — `Unresolved reference: remainingSecDisplay`.

- [ ] **Step 15: Implement tick, advance, and the displayed seconds**

Add inside `WorkoutSession`:

```kotlin
    /**
     * Seconds to show on the ring. Rounds **up**, so a segment shows its full duration the
     * instant it starts and shows "1" through the whole final second. Rounding down would
     * display 29 immediately and hold 0 on screen for a full second before advancing.
     */
    fun remainingSecDisplay(now: Long): Int? {
        val ms = remainingMs(now) ?: return null
        return ceil(ms / 1000.0).toInt().coerceAtLeast(0)
    }

    /**
     * Advances the clock. Returns the same session when nothing is due, so the caller can
     * compare instances cheaply and only re-publish state that changed.
     */
    fun tick(now: Long): SessionTick {
        if (finished || isPaused) return SessionTick(this)
        val remaining = remainingMs(now) ?: return SessionTick(this) // manual / reps wait for a tap
        if (remaining <= 0L) return advance(now, overflow = -remaining)

        val second = ceil(remaining / 1000.0).toInt()
        val due = second in 1..3 && (lastCountdown == null || second < lastCountdown)
        return if (due) {
            SessionTick(copy(lastCountdown = second), listOf(Cue.COUNTDOWN))
        } else {
            SessionTick(this)
        }
    }

    /**
     * Moves past the current segment, carrying [overflow] — the milliseconds by which the
     * clock has already run past its end — into the next one, so a late tick does not
     * silently lengthen the workout.
     *
     * A long gap (device asleep, service descheduled) can span several segments. Those are
     * consumed by arithmetic and land on the right one with a single go cue, rather than
     * replaying each intermediate segment's cues in a burst. A segment with no duration
     * stops the catch-up: it waits for a person, and cannot be slept through.
     */
    private fun advance(now: Long, overflow: Long): SessionTick {
        var next = index + 1
        var carry = overflow
        while (next < segments.size) {
            val durationMs = (segments[next].durationSec ?: break) * 1000L
            if (carry < durationMs) break
            carry -= durationMs
            next++
        }
        return if (next >= segments.size) {
            SessionTick(copy(index = segments.size, finished = true, lastCountdown = null), listOf(Cue.FINISH))
        } else {
            SessionTick(
                copy(
                    index = next,
                    startedAt = now - carry,
                    pausedAt = null,
                    pausedTotal = 0L,
                    lastCountdown = null,
                ),
                listOf(Cue.GO),
            )
        }
    }
```

- [ ] **Step 16: Run to verify it passes**

Expected: PASS (8 tests).

- [ ] **Step 17: Write the failing tests for countdown cues and pause/resume**

Step 17's second test pins Review Focus #3.

Append:

```kotlin
    // ---- countdown cues ----

    @Test
    fun `countdown fires once each at three two and one on work and rest alike`() {
        var s = WorkoutSession.start(indexed(rest(10)), now = 0L).session
        val heard = mutableListOf<Pair<Long, Cue>>()
        // 10 Hz, exactly as the service drives it
        for (ms in 0L..10_000L step 100L) {
            val (next, cues) = s.tick(ms)
            s = next
            cues.forEach { heard += ms to it }
        }
        assertEquals(
            listOf(7_000L, 8_000L, 9_000L),
            heard.filter { it.second == Cue.COUNTDOWN }.map { it.first },
            "exactly three countdown beeps, at 3, 2 and 1 seconds remaining",
        )
    }

    // ---- pause / resume ----

    @Test
    fun `pausing freezes remaining time and resuming continues from the same point`() {
        val s = WorkoutSession.start(indexed(timed(30)), now = 0L).session
        val paused = s.pause(10_000L)
        assertEquals(20_000L, paused.remainingMs(10_000L))
        assertEquals(20_000L, paused.remainingMs(40_000L), "time does not move while paused")
        assertTrue(paused.tick(40_000L).cues.isEmpty(), "a paused session is silent")

        val resumed = paused.resume(40_000L)
        assertEquals(20_000L, resumed.remainingMs(40_000L))
        assertEquals(10_000L, resumed.remainingMs(50_000L))
    }

    @Test
    fun `pausing inside the final three seconds does not replay the countdown on resume`() {
        var s = WorkoutSession.start(indexed(timed(10)), now = 0L).session
        for (ms in 0L..7_500L step 100L) s = s.tick(ms).session   // 3-second beep heard

        val resumed = s.pause(7_500L).resume(60_000L)
        val heard = mutableListOf<Cue>()
        var t = resumed
        for (ms in 60_000L..62_600L step 100L) {
            val (next, cues) = t.tick(ms)
            t = next
            heard += cues
        }
        assertEquals(
            listOf(Cue.COUNTDOWN, Cue.COUNTDOWN),
            heard.filter { it == Cue.COUNTDOWN },
            "only the 2 and 1 beeps remain; the 3 beep already played",
        )
    }
```

- [ ] **Step 18: Run to verify it fails**

Expected: FAIL — `Unresolved reference: pause`.

- [ ] **Step 19: Implement pause and resume**

Add inside `WorkoutSession`:

```kotlin
    fun pause(now: Long): WorkoutSession =
        if (finished || isPaused) this else copy(pausedAt = now)

    /** Folds the pause into [pausedTotal], so remaining time resumes where it stopped. */
    fun resume(now: Long): WorkoutSession {
        val since = pausedAt ?: return this
        return copy(pausedAt = null, pausedTotal = pausedTotal + (now - since))
    }
```

- [ ] **Step 20: Run to verify it passes**

Expected: PASS (11 tests).

- [ ] **Step 21: Write the failing tests for skip and complete**

The second test pins Review Focus #4.

Append:

```kotlin
    // ---- skip / complete ----

    @Test
    fun `skip jumps to the next segment and restarts its clock`() {
        val s = WorkoutSession.start(indexed(timed(30), timed(20)), now = 0L).session
        val (next, cues) = s.skip(5_000L)
        assertEquals(1, next.index)
        assertEquals(listOf(Cue.GO), cues)
        assertEquals(20_000L, next.remainingMs(5_000L), "the skipped-to segment starts full")
    }

    @Test
    fun `skipping the last segment finishes instead of running off the end`() {
        val s = WorkoutSession.start(indexed(timed(30)), now = 0L).session
        val (done, cues) = s.skip(5_000L)
        assertTrue(done.finished)
        assertNull(done.current)
        assertEquals(listOf(Cue.FINISH), cues)
    }

    @Test
    fun `complete advances a manual segment and a reps segment`() {
        val s = WorkoutSession.start(indexed(manual(), reps(), timed(20)), now = 0L).session
        val afterManual = s.complete(9_000L).session
        assertEquals(1, afterManual.index)
        assertEquals(2, afterManual.complete(30_000L).session.index)
    }

    @Test
    fun `skip clears a pause so the next segment runs`() {
        val s = WorkoutSession.start(indexed(timed(30), timed(20)), now = 0L).session
        val next = s.pause(5_000L).skip(6_000L).session
        assertFalse(next.isPaused)
        assertEquals(20_000L, next.remainingMs(6_000L))
    }
```

- [ ] **Step 22: Run to verify it fails**

Expected: FAIL — `Unresolved reference: skip`.

- [ ] **Step 23: Implement skip and complete, and write the sleep-gap tests**

Add inside `WorkoutSession`:

```kotlin
    /**
     * Moves to the next segment on the person's instruction, whatever its mode and whatever
     * time is left. Unlike [tick]'s auto-advance there is no overflow to carry: the new
     * segment starts now.
     */
    fun skip(now: Long): SessionTick {
        if (finished) return SessionTick(this)
        val next = index + 1
        return if (next >= segments.size) {
            SessionTick(copy(index = segments.size, finished = true, lastCountdown = null), listOf(Cue.FINISH))
        } else {
            SessionTick(
                copy(
                    index = next,
                    startedAt = now,
                    pausedAt = null,
                    pausedTotal = 0L,
                    lastCountdown = null,
                ),
                listOf(Cue.GO),
            )
        }
    }

    /**
     * "Done" on a manual or reps segment. Mechanically identical to [skip] — named
     * separately because the intent differs, and the screen shows a different control.
     */
    fun complete(now: Long): SessionTick = skip(now)
```

Then append these tests, which pin Review Focus #5:

```kotlin
    // ---- sleep gaps ----

    @Test
    fun `a long gap lands on the correct segment with a single go cue`() {
        // 10s + 10s + 10s; the phone sleeps from 1s to 25s — mid-way through the third.
        val s = WorkoutSession.start(indexed(timed(10), timed(10), timed(10)), now = 0L).session
        val (landed, cues) = s.tick(25_000L)
        assertEquals(2, landed.index)
        assertEquals(listOf(Cue.GO), cues, "one cue for where we landed, not a burst of replays")
        assertEquals(5_000L, landed.remainingMs(25_000L), "the overflow is carried, not discarded")
    }

    @Test
    fun `a gap spanning the whole workout finishes it`() {
        val s = WorkoutSession.start(indexed(timed(10), timed(10)), now = 0L).session
        val (done, cues) = s.tick(600_000L)
        assertTrue(done.finished)
        assertEquals(listOf(Cue.FINISH), cues)
    }
```

- [ ] **Step 24: Run to verify it passes**

Expected: PASS (17 tests).

- [ ] **Step 25: Write the failing test for a manual segment stopping the catch-up**

Append:

```kotlin
    @Test
    fun `a manual segment inside a gap stops the catch-up instead of being slept through`() {
        val s = WorkoutSession.start(indexed(timed(10), manual(), timed(10)), now = 0L).session
        val (landed, cues) = s.tick(600_000L)
        assertEquals(1, landed.index, "it waits for a person; it cannot be consumed by elapsed time")
        assertEquals(listOf(Cue.GO), cues)
    }
```

- [ ] **Step 26: Run to verify it passes**

The `?: break` in `advance` already implements this. Expected: PASS (18 tests). If it fails, fix `advance` — do not weaken the test.

- [ ] **Step 27: Write the failing test for the progress counter**

The header reads `2/5` and the progress bar has one segment per WORK segment, rests excluded (spec §6.5).

Append:

```kotlin
    // ---- progress counter ----

    @Test
    fun `progress counts work segments only, excluding rests`() {
        val s = WorkoutSession.start(indexed(timed(10), rest(20), timed(10), rest(20), timed(10)), now = 0L).session
        assertEquals(3, s.workTotal, "three exercises, not five segments")
        assertEquals(1, s.workIndex)

        val onRest = s.tick(10_000L).session
        assertEquals(1, onRest.workIndex, "a rest still reads as the exercise just done")

        val onSecond = onRest.tick(30_000L).session
        assertEquals(2, onSecond.workIndex)
    }
```

- [ ] **Step 28: Run to verify it fails**

Expected: FAIL — `Unresolved reference: workTotal`.

- [ ] **Step 29: Implement the progress counter**

Add inside `WorkoutSession`:

```kotlin
    /** Number of WORK segments — the progress bar's denominator; rests are not steps. */
    val workTotal: Int get() = segments.count { it.kind == SegmentKind.WORK }

    /**
     * 1-based count of WORK segments reached so far. A rest reports the exercise that
     * preceded it, so the header does not flicker backwards between exercises.
     */
    val workIndex: Int
        get() = segments.take((index + 1).coerceAtMost(segments.size))
            .count { it.kind == SegmentKind.WORK }
            .coerceAtLeast(1)
```

- [ ] **Step 30: Run the whole suite**

```bash
./gradlew :app:testDebugUnitTest --rerun
```

Expected: BUILD SUCCESSFUL, 19 new tests in `WorkoutSessionTest`, and all pre-existing tests still green (135 total).

- [ ] **Step 31: Commit**

```bash
git add -A && git commit -m "feat: complete the WorkoutSession timing and cue state machine

Covers auto-advance, countdown cues, pause/resume, skip/complete,
sleep-gap catch-up and the work-segment progress counter.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: Tone generation

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/audio/ToneWav.kt`
- Test: `app/src/test/java/com/ottotech/workoutbuilder/audio/ToneWavTest.kt`

**Interfaces:**
- Produces: `object ToneWav { fun bytes(freqHz: Double, durationSec: Double, volume: Double): ByteArray }` — a complete mono 22050 Hz 16-bit PCM WAV file, header included.
- Also produces the three named cue specs: `ToneWav.COUNTDOWN`, `ToneWav.GO`, `ToneWav.FINISH`, each a `ToneSpec(freqHz, durationSec, volume)`.
- Pure Kotlin, no Android imports, so it is JVM-tested. It lives in `audio/` rather than `domain/` because it is not workout logic.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/ottotech/workoutbuilder/audio/ToneWavTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests '*ToneWavTest*'
```

Expected: FAIL — `Unresolved reference: ToneWav`.

- [ ] **Step 3: Implement the generator**

Create `app/src/main/java/com/ottotech/workoutbuilder/audio/ToneWav.kt`:

```kotlin
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
```

- [ ] **Step 4: Run to verify it passes**

Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: generate cue tones as WAV bytes at runtime

Ports makeWav() from the reference web app so the cues sound identical.
No audio assets shipped.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: Foreground service, permissions, and the route from Home

After this task, tapping a workout on Home starts a real foreground service with a live
notification and a placeholder screen showing the current segment name. No audio and no
designed UI yet — those are Tasks 4 and 5. This is the task that proves the plumbing.

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/execution/ExecutionService.kt`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/execution/ExecutionNotification.kt`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/execution/ExecutionScreen.kt`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/execution/ExecutionViewModel.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/AppNav.kt:68`

**Interfaces:**
- Consumes: `WorkoutSession`, `SessionTick`, `Cue` (Task 1); `WorkoutRepository.load(id)`, `ExerciseRepository.observeAll()`, `SegmentCompiler.compile(workout, exercises)`.
- Produces:
  - `ExecutionService.state: StateFlow<ExecutionState>` where
    `data class ExecutionState(val workoutName: String, val session: WorkoutSession?, val muted: Boolean)`
  - Companion commands: `ExecutionService.start(context, workoutId)`, `.pause(context)`, `.resume(context)`, `.skip(context)`, `.complete(context)`, `.stop(context)`
  - `Routes.EXECUTION = "execution/{workoutId}"` and `Routes.execution(workoutId: String): String`

- [ ] **Step 1: Add permissions and the service to the manifest**

In `app/src/main/AndroidManifest.xml`, add above the existing `INTERNET` permission:

```xml
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
```

And inside `<application>`, after the `<activity>` block:

```xml
        <service
            android:name=".execution.ExecutionService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" />
```

- [ ] **Step 2: Create the notification helper**

Create `app/src/main/java/com/ottotech/workoutbuilder/execution/ExecutionNotification.kt`:

Two constraints shape this file, both verified against the repo rather than assumed:

- **The project ships no app icon.** `app/src/main/res/` contains only `values` and
  `values-night`; there is no `mipmap`, so `R.mipmap.ic_launcher` does not exist and would not
  compile. Use the framework drawable `android.R.drawable.ic_media_play`. A real icon is a
  separate piece of work, not this phase's.
- **Framework `Notification.Builder`, not `NotificationCompat`.** Task 8 attaches a media
  session to this notification, and the compat `MediaStyle` lives in `androidx.media`, which
  this project does not depend on. `minSdk` is 26, so the framework notification API — channels,
  actions and `MediaStyle` — is available in full without adding anything.

```kotlin
package com.ottotech.workoutbuilder.execution

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import com.ottotech.workoutbuilder.MainActivity

/**
 * The persistent notification that keeps the service in the foreground.
 *
 * Not decoration: a `mediaPlayback` foreground service is required to post one, and it is
 * what lets the timer keep running once the app is backgrounded.
 */
object ExecutionNotification {

    const val CHANNEL_ID = "workout_execution"
    const val ID = 1

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Running workout",
            NotificationManager.IMPORTANCE_LOW, // silent: the cues are the audio, not this
        ).apply { setShowBadge(false) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        title: String,
        text: String,
        paused: Boolean,
        sessionToken: MediaSession.Token? = null,
    ): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(label: String, command: String) = Notification.Action.Builder(
            null as android.graphics.drawable.Icon?,
            label,
            PendingIntent.getService(
                context,
                command.hashCode(),
                Intent(context, ExecutionService::class.java).setAction(command),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()

        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                if (paused) action("Resume", ExecutionService.ACTION_RESUME)
                else action("Pause", ExecutionService.ACTION_PAUSE),
            )
            .addAction(action("Skip", ExecutionService.ACTION_SKIP))
            .apply {
                // Task 8 supplies the token; until then the notification is plain.
                sessionToken?.let { style = Notification.MediaStyle().setMediaSession(it) }
            }
            .build()
    }
}
```

`setOnlyAlertOnce(true)` matters more than it looks: this notification is rebuilt ten times a
second as the countdown changes, and without it some launchers re-animate the shade each time.

- [ ] **Step 3: Create the service**

Create `app/src/main/java/com/ottotech/workoutbuilder/execution/ExecutionService.kt`:

```kotlin
package com.ottotech.workoutbuilder.execution

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import com.ottotech.workoutbuilder.WorkoutApp
import com.ottotech.workoutbuilder.domain.Cue
import com.ottotech.workoutbuilder.domain.SegmentCompiler
import com.ottotech.workoutbuilder.domain.WorkoutSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ExecutionState(
    val workoutName: String = "",
    val session: WorkoutSession? = null,
    val muted: Boolean = false,
)

/**
 * Owns the clock and the audio for a running workout.
 *
 * The UI deliberately holds none of this: a Compose screen dies with the Activity, and the
 * whole point of the phase is cues that keep firing with the screen off. The service ticks
 * the pure [WorkoutSession] at 10 Hz and publishes the result; the screen is a view of
 * [state] and nothing more.
 */
class ExecutionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val app: WorkoutApp get() = application as WorkoutApp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ExecutionNotification.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> intent.getStringExtra(EXTRA_WORKOUT_ID)?.let { begin(it) }
            ACTION_PAUSE -> update { it.pause(now()) }
            ACTION_RESUME -> update { it.resume(now()) }
            ACTION_SKIP -> updateWithCues { it.skip(now()) }
            ACTION_COMPLETE -> updateWithCues { it.complete(now()) }
            ACTION_STOP -> stopEverything()
        }
        return START_NOT_STICKY
    }

    private fun now() = SystemClock.elapsedRealtime()

    private fun begin(workoutId: String) {
        startForeground(ExecutionNotification.ID, ExecutionNotification.build(this, "Starting…", "", paused = false))
        scope.launch {
            val workout = app.workouts.load(workoutId) ?: run { stopEverything(); return@launch }
            val library = app.exercises.observeAll().first().associateBy { it.id }
            val segments = SegmentCompiler.compile(workout, library)
            val (session, _) = WorkoutSession.start(segments, now())
            _state.value = ExecutionState(workout.name, session, app.prefs.cuesMuted)
            refreshNotification()
            runClock()
        }
    }

    /** 10 Hz. Fine enough that a cue lands within 100ms of its second, cheap enough to ignore. */
    private suspend fun runClock() {
        while (scope.isActive) {
            val session = _state.value.session ?: return
            if (!session.finished) {
                updateWithCues { it.tick(now()) }
            }
            delay(TICK_MS)
        }
    }

    private inline fun update(transform: (WorkoutSession) -> WorkoutSession) {
        val session = _state.value.session ?: return
        _state.value = _state.value.copy(session = transform(session))
        refreshNotification()
    }

    private inline fun updateWithCues(transform: (WorkoutSession) -> com.ottotech.workoutbuilder.domain.SessionTick) {
        val session = _state.value.session ?: return
        val (next, cues) = transform(session)
        if (next == session && cues.isEmpty()) return
        _state.value = _state.value.copy(session = next)
        cues.forEach { play(it) }
        refreshNotification()
    }

    /** Replaced with real audio in Task 4. */
    private fun play(cue: Cue) = Unit

    private fun refreshNotification() {
        val state = _state.value
        val session = state.session ?: return
        val segment = session.current
        val text = when {
            session.finished -> "Workout complete"
            segment == null -> ""
            else -> session.remainingSecDisplay(now())?.let { "$it s left" } ?: segment.label
        }
        val title = if (session.finished) state.workoutName else segment?.exerciseName ?: state.workoutName
        getSystemService(android.app.NotificationManager::class.java)
            .notify(ExecutionNotification.ID, ExecutionNotification.build(this, title, text, session.isPaused))
    }

    private fun stopEverything() {
        _state.value = ExecutionState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "start"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
        const val ACTION_SKIP = "skip"
        const val ACTION_COMPLETE = "complete"
        const val ACTION_STOP = "stop"
        const val EXTRA_WORKOUT_ID = "workoutId"
        private const val TICK_MS = 100L

        /**
         * Process-wide, deliberately not per-instance.
         *
         * The screen navigates and starts collecting *before* `startForegroundService` has
         * actually constructed the service, so a flow hanging off the instance would be null
         * at exactly the moment the UI needs it and the screen would sit blank forever. A
         * companion-level flow always exists; it simply holds an empty state until the
         * service fills it. That also makes a bound service unnecessary — this app has one
         * execution screen and one service, and binding would add a connection lifecycle to
         * juggle through rotation for no benefit.
         */
        private val _state = MutableStateFlow(ExecutionState())
        val state: StateFlow<ExecutionState> = _state.asStateFlow()

        private fun command(context: Context, action: String, workoutId: String? = null) {
            val intent = Intent(context, ExecutionService::class.java).setAction(action)
            workoutId?.let { intent.putExtra(EXTRA_WORKOUT_ID, it) }
            context.startForegroundService(intent)
        }

        fun start(context: Context, workoutId: String) = command(context, ACTION_START, workoutId)
        fun pause(context: Context) = command(context, ACTION_PAUSE)
        fun resume(context: Context) = command(context, ACTION_RESUME)
        fun skip(context: Context) = command(context, ACTION_SKIP)
        fun complete(context: Context) = command(context, ACTION_COMPLETE)
        fun stop(context: Context) = command(context, ACTION_STOP)
    }
}
```

- [ ] **Step 4: Add the mute flag to AppPrefs**

In `app/src/main/java/com/ottotech/workoutbuilder/data/AppPrefs.kt`, add inside the class:

```kotlin
    /**
     * Cue mute. Lives here rather than in Settings because Settings is Phase 2; the Phase 2
     * toggle will read this same key, so nothing has to move.
     */
    var cuesMuted: Boolean
        get() = prefs.getBoolean(KEY_CUES_MUTED, false)
        set(value) = prefs.edit().putBoolean(KEY_CUES_MUTED, value).apply()
```

And in its `companion object`:

```kotlin
        const val KEY_CUES_MUTED = "cues_muted"
```

- [ ] **Step 5: Create a placeholder execution screen**

Create `app/src/main/java/com/ottotech/workoutbuilder/ui/execution/ExecutionScreen.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.execution

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.ExecutionColors

@Composable
fun ExecutionScreen(viewModel: ExecutionViewModel, onExit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(ExecutionColors.background), Alignment.Center) {
        Text(
            state.session?.current?.exerciseName ?: "…",
            style = AppType.moveName,
            color = ExecutionColors.text,
        )
    }
}
```

- [ ] **Step 6: Add the execution colour tokens**

In `app/src/main/java/com/ottotech/workoutbuilder/ui/theme/Colors.kt`, append at the end of the file:

```kotlin
/**
 * The execution screen is dark in **both** themes (spec §5) — a workout is run in a gym, at
 * arm's length, and a white screen at 6am is hostile. These are therefore fixed values, not
 * theme roles, and are the one deliberate exception to reading colours from [LocalAppColors].
 */
object ExecutionColors {
    val background = Color(0xFF0B0B0D)
    val card = Color(0xFF18181C)
    val border = Color(0x24FFFFFF)        // rgba(255,255,255,.14)
    val controlBorder = Color(0x33FFFFFF) // rgba(255,255,255,.2)
    val ringTrack = Color(0x1FFFFFFF)     // rgba(255,255,255,.12)
    val ringProgress = Color(0xFFFFFFFF)
    val text = Color(0xFFFFFFFF)
    val textMuted = Color(0xFF9A9A9A)
}
```

- [ ] **Step 7: Create the ViewModel**

Create `app/src/main/java/com/ottotech/workoutbuilder/ui/execution/ExecutionViewModel.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.execution

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ottotech.workoutbuilder.execution.ExecutionService
import com.ottotech.workoutbuilder.execution.ExecutionState
import kotlinx.coroutines.flow.StateFlow

/**
 * A view of the service's state, plus the commands the screen can send.
 *
 * It holds no clock and no session of its own: when the screen is destroyed and rebuilt
 * (rotation, or returning from the notification) it re-reads whatever the service has, which
 * is the only copy that matters.
 */
class ExecutionViewModel(app: Application) : AndroidViewModel(app) {

    /** Straight through: the service's flow is process-wide and already the single source. */
    val state: StateFlow<ExecutionState> = ExecutionService.state

    private val context get() = getApplication<Application>()

    fun start(workoutId: String) = ExecutionService.start(context, workoutId)
    fun pause() = ExecutionService.pause(context)
    fun resume() = ExecutionService.resume(context)
    fun skip() = ExecutionService.skip(context)
    fun complete() = ExecutionService.complete(context)
    fun stop() = ExecutionService.stop(context)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ExecutionViewModel(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application,
                )
            }
        }
    }
}
```

- [ ] **Step 8: Wire the route and replace the Home no-op**

In `app/src/main/java/com/ottotech/workoutbuilder/ui/AppNav.kt`, add to `Routes`:

```kotlin
    const val EXECUTION = "execution/{workoutId}"

    fun execution(workoutId: String) = "execution/$workoutId"
```

Replace lines 67-69 (the `TODO(Plan 1D)` no-op) with:

```kotlin
                    onStartWorkout = { workoutId -> nav.navigate(Routes.execution(workoutId)) },
```

And add a new `composable` block at the end of the `NavHost`:

```kotlin
        composable(
            route = Routes.EXECUTION,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
        ) { entry ->
            val workoutId = entry.arguments?.getString("workoutId").orEmpty()
            val executionVm: ExecutionViewModel = viewModel(factory = ExecutionViewModel.Factory)

            // Android 13+ will not show the notification unless the user has granted
            // POST_NOTIFICATIONS, and without the notification the foreground service — and
            // with it the whole point of this phase — is on borrowed time. Ask once, at the
            // first workout start, which is the moment it is self-explanatory. Starting the
            // workout does not wait on the answer: a declined prompt should cost the
            // notification, not the workout.
            val askNotifications = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { /* granted or not, the workout runs */ }

            LaunchedEffect(workoutId) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                executionVm.start(workoutId)   // once per entry, not per recomposition
            }
            ExecutionScreen(
                viewModel = executionVm,
                onExit = {
                    executionVm.stop()
                    nav.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
```

Add the imports: `android.Manifest`, `android.os.Build`,
`androidx.activity.compose.rememberLauncherForActivityResult`,
`androidx.activity.result.contract.ActivityResultContracts`,
`androidx.compose.runtime.LaunchedEffect`,
`com.ottotech.workoutbuilder.ui.execution.ExecutionScreen`,
`com.ottotech.workoutbuilder.ui.execution.ExecutionViewModel`.

- [ ] **Step 9: Confirm `HomeScreen`'s callback passes the workout id**

Read `app/src/main/java/com/ottotech/workoutbuilder/ui/home/HomeScreen.kt` and check the signature of `onStartWorkout`. If it is `() -> Unit`, change it to `(String) -> Unit` and pass `summary.id` at the call site on the workout card. If it already takes the id, change nothing.

- [ ] **Step 10: Build**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Verify on the emulator**

```bash
./gradlew :app:installDebug
```

Start the emulator if it is not running, launch the app, tap a workout on Home. Expect: a one-time notification permission prompt, then the screen goes black with the first exercise name centred, and a "Running workout" notification appears showing a countdown that changes. Swipe the app away — the notification must stay and keep counting.

If the app was already installed from an earlier run, the permission prompt will not reappear; `adb shell pm clear com.ottotech.workoutbuilder` resets it (note that this also reseeds the sample workout, per the existing `STATUS.md` entry).

- [ ] **Step 12: Commit**

```bash
git add -A && git commit -m "feat: run workouts in a foreground service

Adds the mediaPlayback service that owns the clock, its notification, the
execution route from Home, and a placeholder screen. No audio yet.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: Audio cues

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/audio/CuePlayer.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/execution/ExecutionService.kt` (replace the `play` stub)

**Interfaces:**
- Consumes: `ToneWav`, `ToneSpec` (Task 2); `Cue` (Task 1).
- Produces: `class CuePlayer(context: Context)` with `fun prime()`, `fun play(cue: Cue)`, `var muted: Boolean`, `fun release()`.

- [ ] **Step 1: Create the cue player**

Create `app/src/main/java/com/ottotech/workoutbuilder/audio/CuePlayer.kt`:

```kotlin
package com.ottotech.workoutbuilder.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import com.ottotech.workoutbuilder.domain.Cue
import java.io.File

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

    fun prime() {
        if (soundIds.isNotEmpty()) return
        soundIds[Cue.COUNTDOWN] = load("cue_countdown.wav", ToneWav.COUNTDOWN)
        soundIds[Cue.GO] = load("cue_go.wav", ToneWav.GO)
        soundIds[Cue.FINISH] = load("cue_finish.wav", ToneWav.FINISH)
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
```

- [ ] **Step 2: Wire it into the service**

In `ExecutionService.kt`:

Add the field, below `_state`:

```kotlin
    private val cues by lazy { CuePlayer(this) }
```

Replace the `play` stub with:

```kotlin
    private fun play(cue: Cue) = cues.play(cue)
```

In `begin`, immediately before `_state.value = ExecutionState(...)`:

```kotlin
            cues.muted = app.prefs.cuesMuted
            cues.prime()
```

In `onDestroy`, before `scope.cancel()`:

```kotlin
        cues.release()
```

Add the import `com.ottotech.workoutbuilder.audio.CuePlayer`.

- [ ] **Step 3: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verify audio on the emulator**

```bash
./gradlew :app:installDebug
```

Run a workout with a short timed segment. Expect: a tone on starting, three beeps in the last three seconds of each timed segment (rests included), a tone on each transition, and one longer tone at the end. Unmute the emulator's audio first.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: play the cue tones from the execution service

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: The execution screen — timed and manual

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/execution/TimerRing.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/execution/ExecutionScreen.kt` (replace the placeholder)
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/theme/Dimens.kt`

**Interfaces:**
- Consumes: `ExecutionState`, `ExecutionViewModel` (Task 3); `WorkoutSession` queries (Task 1); `ExecutionColors` (Task 3).
- Produces: `@Composable fun TimerRing(progress: Float, centreTop: String, centreBottom: String, modifier: Modifier, onTap: (() -> Unit)?)`.

Layout per spec §6.5, top to bottom: header row (`WORKOUT NAME · 2/5`, mute, close ✕) → segmented progress bar (one segment per WORK segment) → centred move name → muscle tag → ring → Watch demo card → Up next card → Pause / Skip controls.

- [ ] **Step 1: Add the timer type and dimension tokens**

In `Type.kt`, inside `AppType`, after `moveName`:

```kotlin
    val timerDigits = TextStyle(
        fontSize = 46.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.03).em,
        fontFeatureSettings = "tnum", // tabular figures: digits must not jitter as they change
    )
    val timerCaption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
```

**`Dimens` already has `ringSize` (240dp), `ringStroke` (14dp), `progressBarHeight` (4dp) and
`progressBarGap` (5dp)** — added by the design correction pass and so far unused. Use them as
they are. Do **not** add them again, and do not "restore" the spec §6.5 figures of 158–176dp /
10dp: those are handoff numbers at ~62% of real device scale, which is the exact bug the
correction pass fixed. `Dimens.kt` is the authority.

- [ ] **Step 2: Write the ring**

Create `app/src/main/java/com/ottotech/workoutbuilder/ui/execution/TimerRing.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.execution

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.ExecutionColors

/**
 * The countdown ring. Starts at 12 o'clock and sweeps clockwise (spec §6.5).
 *
 * [progress] is 0f..1f of the ring that should be drawn. No animation spec: the value is
 * already driven by the service's 10 Hz tick, and animating on top of a moving target makes
 * the sweep lag behind the digits.
 */
@Composable
fun TimerRing(
    progress: Float,
    centreTop: String,
    centreBottom: String,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
) {
    val tappable = if (onTap != null) {
        Modifier.clip(CircleShape).clickable(onClick = onTap)
    } else {
        Modifier
    }
    Box(modifier.size(Dimens.ringSize).then(tappable), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(Dimens.ringSize)) {
            val stroke = Dimens.ringStroke.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = ExecutionColors.ringTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = ExecutionColors.ringProgress,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(centreTop, style = AppType.timerDigits, color = ExecutionColors.text)
            Text(centreBottom, style = AppType.timerCaption, color = ExecutionColors.textMuted)
        }
    }
}
```

- [ ] **Step 3: Replace the placeholder screen**

Rewrite `ExecutionScreen.kt`. The screen re-reads the clock every 100ms so the ring and digits
track the service; it keeps no session state of its own.

```kotlin
package com.ottotech.workoutbuilder.ui.execution

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ottotech.workoutbuilder.domain.ItemMode
import com.ottotech.workoutbuilder.domain.SegmentKind
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.ExecutionColors
import kotlinx.coroutines.delay

@Composable
fun ExecutionScreen(viewModel: ExecutionViewModel, onExit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = state.session

    // The service owns the clock; the screen samples it so the ring moves smoothly.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(100L)
        }
    }

    // Spec §7: the screen stays awake for the length of the workout. Scoped to this screen
    // and released on the way out, so it can never leak into the rest of the app.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var confirmExit by remember { mutableStateOf(false) }
    BackHandler { confirmExit = true }

    if (session == null) {
        Box(Modifier.fillMaxSize().background(ExecutionColors.background))
        return
    }
    if (session.finished) {
        FinishScreen(workoutName = state.workoutName, onDone = onExit)
        return
    }

    val segment = session.current ?: return

    Column(
        Modifier
            .fillMaxSize()
            .background(ExecutionColors.background)
            .padding(horizontal = Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExecutionHeader(
            title = "${state.workoutName.uppercase()} · ${session.workIndex}/${session.workTotal}",
            muted = state.muted,
            onToggleMute = viewModel::toggleMute,
            onClose = { confirmExit = true },
        )
        ProgressBar(done = session.workIndex, total = session.workTotal)

        Spacer(Modifier.height(24.dp))
        Text(
            segment.exerciseName,
            style = AppType.moveName,
            color = ExecutionColors.text,
            textAlign = TextAlign.Center,
        )
        segment.primaryMuscle?.let {
            Spacer(Modifier.height(8.dp))
            Text(it.label.uppercase(), style = AppType.muscleTag, color = ExecutionColors.textMuted)
        }

        Spacer(Modifier.height(28.dp))
        when (segment.mode) {
            ItemMode.TIMED -> {
                val remaining = session.remainingSecDisplay(now) ?: 0
                val total = (segment.durationSec ?: 1).coerceAtLeast(1)
                TimerRing(
                    progress = remaining.toFloat() / total,
                    centreTop = formatClock(remaining),
                    centreBottom = "REMAINING",
                )
            }
            ItemMode.MANUAL -> TimerRing(
                progress = 1f,
                centreTop = formatClock((session.elapsedMs(now) / 1000L).toInt()),
                centreBottom = "ELAPSED",
                onTap = viewModel::complete,
            )
            ItemMode.REPS -> RepsPanel(segment = segment, onDone = viewModel::complete)
        }

        Spacer(Modifier.weight(1f))
        if (segment.kind == SegmentKind.WORK) {
            WatchDemoCard(segment)
            Spacer(Modifier.height(10.dp))
        }
        UpNextCard(session.segments.getOrNull(session.index + 1))
        Spacer(Modifier.height(14.dp))
        ExecutionControls(
            paused = session.isPaused,
            mode = segment.mode,
            onPauseResume = { if (session.isPaused) viewModel.resume() else viewModel.pause() },
            onAdvance = viewModel::skip,
            onDone = viewModel::complete,
        )
        Spacer(Modifier.height(20.dp))
    }

    if (confirmExit) {
        DiscardWorkoutDialog(
            onKeepGoing = { confirmExit = false },
            onQuit = { confirmExit = false; onExit() },
        )
    }
}

/** mm:ss for a minute or more, bare seconds below that — matches the reference app. */
private fun formatClock(seconds: Int): String =
    if (seconds >= 60) "%d:%02d".format(seconds / 60, seconds % 60) else seconds.toString()
```

Then write the private composables used above — `ExecutionHeader`, `ProgressBar`, `WatchDemoCard`, `UpNextCard`, `ExecutionControls`, `DiscardWorkoutDialog` — in the same file, following the existing patterns:

- `ExecutionHeader`: a `Row` with the title (`AppType.sectionLabel`, `ExecutionColors.textMuted`), a spacer, a mute glyph and a `✕`, each in a 48dp `Box` (`Dimens.minTouchTarget`).
- `ProgressBar`: a `Row` of `total` weighted `Box`es, height `Dimens.progressBarHeight`, `Arrangement.spacedBy(Dimens.progressBarGap)`, radius 2dp; the first `done` filled `ExecutionColors.ringProgress`, the rest `ExecutionColors.ringTrack`.
- `WatchDemoCard`: card background `ExecutionColors.card`, radius 13dp, fires the intent with
  `LocalContext.current.startActivity(...)`. **The existing helper does not fit as written:**
  `demoIntent(exercise: Exercise)` in `ui/DemoLink.kt` takes an `Exercise`, but the execution
  screen holds a `Segment` — the compiler denormalised `demoUrl` and `demoQuery` onto it
  precisely so running a workout needs no database lookup. Add an overload and make the
  existing function delegate to it, so the URL-versus-search rule stays in one place:

  ```kotlin
  fun demoIntent(demoUrl: String?, demoQuery: String?): Intent {
      val url = demoUrl?.takeIf { it.isNotBlank() }
          ?: "https://www.youtube.com/results?search_query=" + Uri.encode(demoQuery.orEmpty())
      return Intent(Intent.ACTION_VIEW, Uri.parse(url))
  }

  fun demoIntent(exercise: Exercise): Intent = demoIntent(exercise.demoUrl, exercise.demoQuery)
  ```

  Hide the card entirely when both `demoUrl` and `demoQuery` are null or blank — an empty
  YouTube search is worse than no button.
- `UpNextCard`: 1dp `ExecutionColors.border`; shows `UP NEXT` (`AppType.heroLabel`) over the next segment's name, or "Last one" when null.
- `ExecutionControls`: two buttons — Pause/Resume always; `Skip ›` for TIMED, `Done ›` for MANUAL and REPS (spec §6.5).
- `DiscardWorkoutDialog`: copy the pattern from `BuilderScreen`'s discard dialog, including `containerColor = ExecutionColors.card`. Text: "Quit this workout?" / "Your progress won't be saved." / "Keep going" / "Quit".

- [ ] **Step 4: Add `toggleMute` to the ViewModel and the service**

In `ExecutionViewModel`:

```kotlin
    fun toggleMute() = ExecutionService.toggleMute(context)
```

In `ExecutionService`, add `ACTION_TOGGLE_MUTE = "toggle_mute"`, a `fun toggleMute(context: Context) = command(context, ACTION_TOGGLE_MUTE)` in the companion, and in `onStartCommand`:

```kotlin
            ACTION_TOGGLE_MUTE -> {
                val muted = !_state.value.muted
                app.prefs.cuesMuted = muted
                cues.muted = muted
                _state.value = _state.value.copy(muted = muted)
            }
```

- [ ] **Step 5: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Verify on the emulator**

Install and run a timed workout. Check: the ring sweeps down smoothly from 12 o'clock, digits do not jitter, the progress bar has one segment per exercise (not per rest), pause freezes both ring and digits, skip advances, ✕ asks before quitting, and the mute glyph silences the cues and survives an app restart.

Also check the screen does not dim or lock while a workout runs, and that it *does* go back to normal once you leave the execution screen — a wake lock that outlives its screen is a battery bug that only shows up hours later.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: build the timed and manual execution screen

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: Reps mode

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/execution/RepsPanel.kt`

**Interfaces:**
- Consumes: `Segment` (`sets`, `reps`, `weightKg`, `primaryMuscle`, `equipmentLabel`), `ExecutionColors`, `AppType.setRowLabel`.
- Produces: `@Composable fun RepsPanel(segment: Segment, onDone: () -> Unit)`.

Per spec §6.5: exercise name (`AppType.repsExerciseName`) → `Chest · Barbell` sub-line → Watch demo card → `SETS` label → checkable set rows (20dp checkbox, radius 6dp; completed rows struck through and `textDisabled`) → `Next exercise ›`.

- [ ] **Step 1: Write the panel**

Create `RepsPanel.kt` with a `Column` of `segment.sets ?: 1` rows. Each row is a `Row` in a 48dp-tall clickable `Box` (clip before `clickable`, so the ripple follows the shape — the same fix commit `dc7b16f` made elsewhere), holding a 20dp checkbox `Box` (radius 6dp, 1dp `ExecutionColors.controlBorder`, filled `ExecutionColors.ringProgress` with a dark tick when checked) and the label.

Label text: `"Set ${i + 1} · ${segment.reps} reps"`, plus `" · ${weight}kg"` when `segment.weightKg != null`. Completed rows use `textDecoration = TextDecoration.LineThrough` and `ExecutionColors.textMuted`.

Checked state is `remember(segment.index) { mutableStateListOf<Boolean>() }` — keyed on the segment index so it resets when the exercise changes. This is genuinely screen-local: which sets you have ticked is not something the service or the database needs to know.

The sub-line joins `segment.primaryMuscle?.label` and `segment.equipmentLabel` with `" · "`, skipping nulls (muscle alone when the exercise has no equipment tag).

- [ ] **Step 2: Build and verify on the emulator**

```bash
./gradlew :app:installDebug
```

Build a workout with a reps-mode exercise, run it, and check: the right number of set rows, ticking strikes a row through, the sub-line reads `Chest · Barbell`, and `Next exercise ›` advances. Check an exercise with no equipment tag shows the muscle alone.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: add reps mode to the execution screen

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: Finish screen and recording the workout as done

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/execution/FinishScreen.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/execution/ExecutionService.kt`
- Modify: `app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutSessionTest.kt`

**Interfaces:**
- Consumes: `WorkoutRepository.markPerformed(id)`; `WorkoutSession.finished`.
- Produces: `@Composable fun FinishScreen(workoutName: String, onDone: () -> Unit)`.

- [ ] **Step 1: Write the finish screen**

Create `FinishScreen.kt`: a centred `Column` on `ExecutionColors.background` — `WORKOUT COMPLETE` (`AppType.heroLabel`, muted), the workout name (`AppType.moveName`), and a full-width `Done` button (`AppType.primaryButton`, white on `ExecutionColors.card`, radius 16dp, height `Dimens.minTouchTarget`) calling `onDone`.

- [ ] **Step 2: Record the workout as performed, once**

In `ExecutionService`, add a field:

```kotlin
    /** Guards against the 10 Hz tick writing the row repeatedly once finished. */
    private var performedRecorded = false
```

And in `updateWithCues`, after `_state.value = _state.value.copy(session = next)`:

```kotlin
        if (next.finished && !performedRecorded && next.segments.isNotEmpty()) {
            performedRecorded = true
            scope.launch { app.workouts.markPerformed(runningWorkoutId) }
        }
```

Store `runningWorkoutId` when `begin` loads the workout:

```kotlin
    private var runningWorkoutId: String = ""
```

Set it in `begin` immediately after the workout loads.

- [ ] **Step 3: Verify the empty-workout guard**

This is Review Focus #1's second half. `next.segments.isNotEmpty()` is the guard: a workout whose sections are all empty compiles to zero segments, finishes at `start`, never reaches `updateWithCues`, and so is never recorded.

Confirm by reading the code path, then verify on the emulator: build a workout with a name and an empty section, save it, tap it on Home. Expect: it returns to Home immediately, and the card still reads "Never done".

- [ ] **Step 4: Verify the finish flow end to end**

Run a short workout to completion on the emulator. Expect: the finish tone plays, the finish screen appears, `Done` returns to Home, the notification is gone, and that workout's card now reads "Today".

- [ ] **Step 5: Run the full test suite**

```bash
./gradlew :app:testDebugUnitTest --rerun
```

Expected: all green.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: add the finish screen and record workouts as performed

Calls the previously callerless markPerformed, so Home's last-done line
stops saying 'Never done' for every workout. A workout that compiles to
no segments is not recorded.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 8: Lock screen and headset controls

**Files:**
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/execution/ExecutionService.kt`

**Interfaces:**
- Consumes: the existing `ACTION_PAUSE` / `ACTION_RESUME` / `ACTION_SKIP` commands; the
  `sessionToken` parameter `ExecutionNotification.build` already accepts.
- Produces: an `android.media.session.MediaSession` held by the service.

Use the **framework** `android.media.session.MediaSession`, not `MediaSessionCompat`. The
compat class lives in `androidx.media`, which this project does not depend on, and `minSdk 26`
makes the framework class available unconditionally.

- [ ] **Step 1: Add the media session**

In `ExecutionService`:

```kotlin
    private lateinit var mediaSession: MediaSession
```

In `onCreate`, after `ExecutionNotification.ensureChannel(this)`:

```kotlin
        mediaSession = MediaSession(this, "workout").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { update { it.resume(now()) } }
                override fun onPause() { update { it.pause(now()) } }
                override fun onSkipToNext() { updateWithCues { it.skip(now()) } }
            })
            isActive = true
        }
```

In `refreshNotification`, before building, publish the transport state so the lock screen
shows the right button:

```kotlin
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT,
                )
                .setState(
                    if (session.isPaused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    1f,
                )
                .build(),
        )
```

and pass `mediaSession.sessionToken` as the `sessionToken` argument to
`ExecutionNotification.build`.

In `onDestroy`, before `scope.cancel()`:

```kotlin
        mediaSession.release()
```

Imports: `android.media.session.MediaSession`, `android.media.session.PlaybackState`.

- [ ] **Step 2: Build and verify on the emulator**

Expect the notification to render in media style, and the lock screen to show transport controls that pause and skip the workout.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: expose pause and skip to the lock screen and headset

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 9: Real-phone verification and documentation

**Files:**
- Modify: `docs/superpowers/STATUS.md`

- [ ] **Step 1: Run the full suite and record the count**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest --rerun
```

Then count with the one-liner in `STATUS.md`. Record the exact total.

- [ ] **Step 2: Install on the real phone**

```bash
./gradlew :app:installDebug
```

- [ ] **Step 3: Work through the hands-on checklist**

Run every item in §5 of the spec (`docs/superpowers/specs/2026-09-23-plan-1d-execution-audio-design.md`), including the full-length pocket test. Record the outcome of each — especially item 11, the battery-saver survival test, which decides whether the deferred exemption prompt becomes its own plan.

- [ ] **Step 4: Update STATUS.md**

Add the Plan 1D row to the phase table. Add an "Environment facts" entry for anything learned that is not obvious from the code — at minimum: the outcome of the battery-saver test on this specific phone model, and the fact that cue tones are generated into the cache directory rather than shipped as assets.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "docs: record Plan 1D completion and its verification results

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```
