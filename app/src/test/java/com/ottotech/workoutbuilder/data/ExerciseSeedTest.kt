package com.ottotech.workoutbuilder.data

import com.ottotech.workoutbuilder.data.seed.ExerciseSeed
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.domain.TagVocabulary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The contract for the seeded library. Because the content is ~100 rows of judgement
 * calls, these tests — not a manual read-through — are what keep the data honest.
 * A wrong tag makes an exercise unfindable by filter, which is worse than a typo.
 */
class ExerciseSeedTest {

    private val seed = ExerciseSeed.exercises

    /** Target counts from the design handoff. */
    private val targets = mapOf(
        Muscle.CHEST to 12,
        Muscle.BACK to 14,
        Muscle.LEGS to 18,
        Muscle.SHOULDERS to 10,
        Muscle.CORE to 11,
        Muscle.CARDIO to 8,
        Muscle.ARMS to 9,
        Muscle.MOBILITY to 15,
    )

    @Test
    fun `every muscle group meets its target count`() {
        targets.forEach { (muscle, target) ->
            val actual = seed.count { it.primaryMuscle == muscle }
            assertTrue(actual >= target, "$muscle has $actual, expected at least $target")
        }
    }

    @Test
    fun `ids are unique`() {
        val duplicates = seed.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue(duplicates.isEmpty(), "duplicate ids: $duplicates")
        assertEquals(seed.size, seed.map { it.id }.toSet().size)
    }

    @Test
    fun `ids are lowercase kebab slugs`() {
        val bad = seed.map { it.id }.filterNot { it.matches(Regex("^[a-z0-9]+(-[a-z0-9]+)*$")) }
        assertTrue(bad.isEmpty(), "malformed ids: $bad")
    }

    @Test
    fun `every exercise has at least one TYPE and one EQUIPMENT tag`() {
        val missing = seed.filterNot { ex ->
            ex.tags.any { it.facet == Facet.TYPE } && ex.tags.any { it.facet == Facet.EQUIPMENT }
        }
        assertTrue(missing.isEmpty(), "missing TYPE/EQUIPMENT: ${missing.map { it.id }}")
    }

    @Test
    fun `all tag values come from the controlled vocabulary`() {
        val invalid = seed.flatMap { ex ->
            ex.tags.filterNot { TagVocabulary.isValid(it) }.map { ex.id to it }
        }
        assertTrue(invalid.isEmpty(), "invalid tags: $invalid")
    }

    @Test
    fun `no exercise repeats its primary muscle as a secondary muscle tag`() {
        val offenders = seed.filter { ex ->
            ex.tags.any { it.facet == Facet.MUSCLE && it.value == ex.primaryMuscle.label }
        }
        assertTrue(offenders.isEmpty(), "primary repeated as secondary: ${offenders.map { it.id }}")
    }

    @Test
    fun `every exercise has a non-blank demo query`() {
        assertTrue(seed.none { it.demoQuery.isBlank() })
    }

    @Test
    fun `nothing in the seed is marked custom`() {
        assertTrue(seed.none { it.isCustom })
    }

    @Test
    fun `the eight Court Speed exercises are present with their verified urls`() {
        val expected = mapOf(
            "leg-swings" to "https://www.youtube.com/shorts/3l31E2cMGMk",
            "walking-lunge-twist" to "https://www.youtube.com/shorts/2crIg2iqi8w",
            "a-skips" to "https://www.youtube.com/shorts/fSZUFBbK7CE",
            "build-up-strides" to "https://www.youtube.com/watch?v=IQrbQMPbTtU",
            "lateral-shuffle" to "https://www.youtube.com/shorts/iQjCIghvheI",
            "skater-bounds" to "https://www.youtube.com/shorts/7-Qfb881k7o",
            "split-squat-jumps" to "https://www.youtube.com/shorts/e6s05vAhAZo",
            "squat-jumps-180" to "https://www.youtube.com/watch?v=_-Q5ywo24_s",
        )
        expected.forEach { (id, url) ->
            val ex = seed.firstOrNull { it.id == id }
            assertTrue(ex != null, "missing seeded exercise: $id")
            assertEquals(url, ex.demoUrl, "wrong demo url for $id")
        }
    }
}
