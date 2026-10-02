package com.ottotech.workoutbuilder.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkoutOverviewTest {

    private fun item(mode: ItemMode, durationSec: Int? = null, sets: Int? = null, reps: Int? = null, weightKg: Float? = null) =
        WorkoutItem(id = "i", exerciseId = "e", mode = mode, durationSec = durationSec, sets = sets, reps = reps, weightKg = weightKg)

    private fun section(
        rounds: Int = 1,
        betweenExercises: Rest? = null,
        betweenRounds: Rest? = null,
        after: Rest? = null,
    ) = Section(
        id = "s",
        name = "Block",
        rounds = rounds,
        restBetweenExercises = betweenExercises,
        restBetweenRounds = betweenRounds,
        restAfterSection = after,
    )

    // ---- item detail ----

    @Test
    fun `timed items show seconds under a minute`() {
        assertEquals("45s", WorkoutOverview.itemDetail(item(ItemMode.TIMED, durationSec = 45)))
    }

    @Test
    fun `timed items show minutes from a minute up`() {
        assertEquals("1 min", WorkoutOverview.itemDetail(item(ItemMode.TIMED, durationSec = 60)))
        assertEquals("1 min 30s", WorkoutOverview.itemDetail(item(ItemMode.TIMED, durationSec = 90)))
    }

    @Test
    fun `reps items show sets by reps, with weight when set`() {
        assertEquals("3 × 10", WorkoutOverview.itemDetail(item(ItemMode.REPS, sets = 3, reps = 10)))
        assertEquals("4 × 8 · 60 kg", WorkoutOverview.itemDetail(item(ItemMode.REPS, sets = 4, reps = 8, weightKg = 60f)))
        assertEquals("4 × 8 · 22.5 kg", WorkoutOverview.itemDetail(item(ItemMode.REPS, sets = 4, reps = 8, weightKg = 22.5f)))
    }

    @Test
    fun `manual items wait for a tap`() {
        assertEquals("Until done", WorkoutOverview.itemDetail(item(ItemMode.MANUAL)))
    }

    // ---- rest line ----

    @Test
    fun `a section with no rest has no rest line`() {
        assertNull(WorkoutOverview.restLine(section()))
    }

    @Test
    fun `rest line joins every rest the section has`() {
        assertEquals(
            "15s rest between exercises · 1 min between rounds · 2 min after",
            WorkoutOverview.restLine(
                section(
                    rounds = 3,
                    betweenExercises = Rest.Timed(15),
                    betweenRounds = Rest.Timed(60),
                    after = Rest.Timed(120),
                ),
            ),
        )
    }

    @Test
    fun `rest between rounds is left out of a single-round section`() {
        assertEquals(
            "15s rest between exercises",
            WorkoutOverview.restLine(section(rounds = 1, betweenExercises = Rest.Timed(15), betweenRounds = Rest.Timed(60))),
        )
    }

    @Test
    fun `manual rest reads as resting until ready`() {
        assertEquals(
            "Rest until ready between exercises",
            WorkoutOverview.restLine(section(betweenExercises = Rest.Manual)),
        )
    }
}
