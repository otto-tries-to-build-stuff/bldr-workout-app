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
}
