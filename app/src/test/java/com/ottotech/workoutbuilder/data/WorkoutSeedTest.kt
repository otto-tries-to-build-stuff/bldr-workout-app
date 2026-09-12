package com.ottotech.workoutbuilder.data

import com.ottotech.workoutbuilder.data.seed.ExerciseSeed
import com.ottotech.workoutbuilder.data.seed.WorkoutSeed
import com.ottotech.workoutbuilder.domain.DurationEstimator
import com.ottotech.workoutbuilder.domain.ItemMode
import com.ottotech.workoutbuilder.domain.SegmentCompiler
import com.ottotech.workoutbuilder.domain.SegmentKind
import com.ottotech.workoutbuilder.domain.WorkoutSummarizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Acceptance test. Compiles the shipped Court Speed workout against the shipped exercise
 * library and asserts the output matches `buildSegments()` in the reference web app.
 *
 * If the segment count is not 40, the compiler or the seed is wrong — not this
 * expectation. Re-read spec §4 and `web-app-reference/HIIT Workout.html` before changing a
 * number here.
 */
class WorkoutSeedTest {

    private val library = ExerciseSeed.exercises.associateBy { it.id }
    private val segments = SegmentCompiler.compile(WorkoutSeed.courtSpeed, library)

    @Test
    fun `every exercise the seeded workout references exists in the seeded library`() {
        val missing = WorkoutSeed.workouts
            .flatMap { it.sections }
            .flatMap { it.items }
            .map { it.exerciseId }
            .filterNot { it in library }
            .distinct()
        assertTrue(missing.isEmpty(), "seeded workouts reference unknown exercises: $missing")
    }

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

    @Test
    fun `court speed estimates to a plausible session length`() {
        // 180s warm prep + 675s block A + 420s block B + 20s bonus = 1295s ≈ 22 min.
        // Asserted as a band, not an exact figure, so tuning the manual constants in
        // DurationEstimator does not break this test.
        val minutes = DurationEstimator.estimateMinutes(segments)
        assertTrue(minutes in 20..35, "expected 20-35 min, got $minutes")
    }

    @Test
    fun `court speed summarises to the card lines Home will render`() {
        val summary = WorkoutSummarizer.summarize(WorkoutSeed.courtSpeed, library)
        assertEquals(10, summary.exerciseCount)
        assertEquals(22, summary.estimatedMinutes)
        assertEquals("10 exercises · HIIT", summary.shortMeta)
        assertEquals("22 MIN", summary.minutesLabel)
    }
}
