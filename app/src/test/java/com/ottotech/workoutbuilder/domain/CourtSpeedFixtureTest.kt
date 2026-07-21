package com.ottotech.workoutbuilder.domain

import com.ottotech.workoutbuilder.fixtures.CourtSpeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Acceptance test. Rebuilds the real Court Speed workout in the data model and asserts
 * the compiled output matches `buildSegments()` in the reference web app.
 *
 * If the segment count is not 40, the compiler or the fixture is wrong — not this
 * expectation. Re-read spec §4 and the reference file before changing a number here.
 */
class CourtSpeedFixtureTest {

    private val segments = SegmentCompiler.compile(CourtSpeed.workout, CourtSpeed.exercises)

    @Test
    fun `compiles to forty segments matching the reference web app`() {
        // Warm Prep 4 + Block A 23 + transition 1 + Block B 11 + Bonus 1
        assertEquals(40, segments.size)
    }

    @Test
    fun `warm prep is four timed segments of forty five seconds`() {
        val warm = segments.take(4)
        assertEquals(
            listOf("Leg Swings", "Walking Lunge + Twist", "A-Skips", "Build-Up Strides"),
            warm.map { it.exerciseName },
        )
        assertTrue(warm.all { it.durationSec == 45 && it.kind == SegmentKind.WORK })
    }

    @Test
    fun `block A round one alternates work and rest ending on work`() {
        val round = segments.subList(4, 11)
        assertEquals(
            listOf(
                SegmentKind.WORK, SegmentKind.REST, SegmentKind.WORK, SegmentKind.REST,
                SegmentKind.WORK, SegmentKind.REST, SegmentKind.WORK,
            ),
            round.map { it.kind },
        )
        assertEquals(listOf(30, 15, 30, 15, 30, 15, 30), round.map { it.durationSec })
        assertTrue(round.all { it.label == "BLOCK A · TENNIS CIRCUIT · ROUND 1/3" })
    }

    @Test
    fun `block A has two round rests plus one transition rest`() {
        val sixtySecondRests = segments.filter {
            it.kind == SegmentKind.REST && it.durationSec == 60 && it.label.contains("BLOCK A")
        }
        assertEquals(3, sixtySecondRests.size)
    }

    @Test
    fun `block B is six manual sprints separated by five manual rests`() {
        val blockB = segments.filter { it.label.contains("BLOCK B") }
        assertEquals(11, blockB.size)
        assertEquals(6, blockB.count { it.kind == SegmentKind.WORK })
        assertEquals(5, blockB.count { it.kind == SegmentKind.REST })
        assertTrue(blockB.all { it.mode == ItemMode.MANUAL && it.durationSec == null })
    }

    @Test
    fun `the workout ends on the bonus flyer with no trailing rest`() {
        val last = segments.last()
        assertEquals("Flyer", last.exerciseName)
        assertEquals(SegmentKind.WORK, last.kind)
        assertEquals(ItemMode.MANUAL, last.mode)
    }

    @Test
    fun `verified demo urls survive compilation`() {
        val shuffle = segments.first { it.exerciseName == "Lateral Shuffle" }
        assertEquals("https://www.youtube.com/shorts/iQjCIghvheI", shuffle.demoUrl)
    }
}
