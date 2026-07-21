package com.ottotech.workoutbuilder.domain

import com.ottotech.workoutbuilder.fixtures.CourtSpeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DurationEstimatorTest {

    private fun seg(kind: SegmentKind, mode: ItemMode, sec: Int? = null, sets: Int? = null) =
        Segment(kind = kind, mode = mode, exerciseName = "X", label = "L", durationSec = sec, sets = sets)

    @Test
    fun `timed segments contribute their duration`() {
        val out = DurationEstimator.estimateSeconds(
            listOf(
                seg(SegmentKind.WORK, ItemMode.TIMED, sec = 30),
                seg(SegmentKind.REST, ItemMode.TIMED, sec = 15),
            ),
        )
        assertEquals(45, out)
    }

    @Test
    fun `reps segments are estimated at forty five seconds per set`() {
        val out = DurationEstimator.estimateSeconds(listOf(seg(SegmentKind.WORK, ItemMode.REPS, sets = 3)))
        assertEquals(135, out)
    }

    @Test
    fun `reps segment with null sets counts as a single set`() {
        val out = DurationEstimator.estimateSeconds(listOf(seg(SegmentKind.WORK, ItemMode.REPS)))
        assertEquals(45, out)
    }

    @Test
    fun `manual work and manual rest use their own constants`() {
        val out = DurationEstimator.estimateSeconds(
            listOf(
                seg(SegmentKind.WORK, ItemMode.MANUAL),
                seg(SegmentKind.REST, ItemMode.MANUAL),
            ),
        )
        assertEquals(80, out)
    }

    @Test
    fun `minutes round to nearest`() {
        assertEquals(2, DurationEstimator.estimateMinutes(listOf(seg(SegmentKind.WORK, ItemMode.TIMED, sec = 100))))
        assertEquals(1, DurationEstimator.estimateMinutes(listOf(seg(SegmentKind.WORK, ItemMode.TIMED, sec = 80))))
    }

    @Test
    fun `empty list is zero`() {
        assertEquals(0, DurationEstimator.estimateSeconds(emptyList()))
    }

    @Test
    fun `court speed estimates to a plausible session length`() {
        val minutes = DurationEstimator.estimateMinutes(
            SegmentCompiler.compile(CourtSpeed.workout, CourtSpeed.exercises),
        )
        // 180s warm prep + 675s block A + 420s block B + 20s bonus = 1295s ≈ 22 min.
        // Asserted as a band, not an exact figure, so tuning the manual constants
        // does not break this test.
        assertTrue(minutes in 20..35, "expected 20-35 min, got $minutes")
    }
}
