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

    // ---- auto-advance ----

    @Test
    fun `a timed segment advances at zero and fires the go cue`() {
        val s = WorkoutSession.start(indexed(timed(30), timed(20)), now = 0L).session
        // 29s in is inside the final-3-seconds countdown window, so this tick legitimately
        // fires the "1" beep (lastCountdown was still null) — only the segment index is
        // pinned here; the countdown behavior itself is covered separately below.
        assertEquals(0, s.tick(29_000L).session.index, "must not advance early")

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

    // ---- countdown catch-up ----

    // A scheduling gap (Doze, a GC pause, the app backgrounded) can land mid-segment
    // after skipping straight past the 3 and 2 beeps into the final second. Replaying all
    // three beeps in the half-second before the go tone would be worse to hear than a
    // single slightly-late beep, so only the second landed on plays and the missed ones
    // are never replayed. advance() makes the same trade across segment boundaries; this
    // pins the equivalent decision inside a single segment.
    @Test
    fun `a gap past the countdown window fires only the beep it lands on`() {
        var s = WorkoutSession.start(indexed(timed(10)), now = 0L).session
        s = s.tick(0L).session
        val (landed, cues) = s.tick(9_500L)
        assertEquals(listOf(Cue.COUNTDOWN), cues, "only the beep for the second it lands on")

        val heard = mutableListOf<Cue>()
        var t = landed
        for (ms in 9_600L..10_000L step 100L) {
            val (next, c) = t.tick(ms)
            t = next
            heard += c
        }
        assertTrue(
            heard.none { it == Cue.COUNTDOWN },
            "the missed 3 and 2 beeps must not replay after the gap",
        )
    }

    // A gap that lands before a beep's second has been reached does not swallow it — only
    // a gap that jumps past a second's boundary loses that beep.
    @Test
    fun `a gap landing mid window still fires the remaining beeps`() {
        var s = WorkoutSession.start(indexed(timed(10)), now = 0L).session
        s = s.tick(0L).session
        val heard = mutableListOf<Pair<Long, Cue>>()
        val (landed, firstCues) = s.tick(7_500L)
        firstCues.forEach { heard += 7_500L to it }
        var t = landed
        for (ms in 7_600L..10_000L step 100L) {
            val (next, cues) = t.tick(ms)
            t = next
            cues.forEach { heard += ms to it }
        }
        assertEquals(
            listOf(7_500L, 8_000L, 9_000L),
            heard.filter { it.second == Cue.COUNTDOWN }.map { it.first },
            "landing at 2.5s remaining still hears 3, then 2 and 1 on schedule",
        )
    }

    @Test
    fun `a segment shorter than the countdown window only beeps for the seconds it has`() {
        var s = WorkoutSession.start(indexed(timed(2)), now = 0L).session
        val heard = mutableListOf<Pair<Long, Cue>>()
        for (ms in 0L..2_000L step 100L) {
            val (next, cues) = s.tick(ms)
            s = next
            cues.forEach { heard += ms to it }
        }
        assertEquals(
            listOf(0L, 1_000L),
            heard.filter { it.second == Cue.COUNTDOWN }.map { it.first },
            "a 2-second segment only ever has a 2 and a 1 to count down",
        )
        assertTrue(s.finished, "the segment still advances at zero")
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

    @Test
    fun `a manual segment inside a gap stops the catch-up instead of being slept through`() {
        val s = WorkoutSession.start(indexed(timed(10), manual(), timed(10)), now = 0L).session
        val (landed, cues) = s.tick(600_000L)
        assertEquals(1, landed.index, "it waits for a person; it cannot be consumed by elapsed time")
        assertEquals(listOf(Cue.GO), cues)
    }

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
}
