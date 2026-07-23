package com.ottotech.workoutbuilder.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkoutSummaryTest {

    private val push = Exercise(
        id = "push-up", name = "Push-up", primaryMuscle = Muscle.CHEST,
        tags = setOf(Tag(Facet.EQUIPMENT, "Bodyweight")), demoQuery = "push up proper form",
    )
    private val squat = Exercise(
        id = "air-squat", name = "Air Squat", primaryMuscle = Muscle.LEGS,
        tags = setOf(Tag(Facet.EQUIPMENT, "Bodyweight")), demoQuery = "air squat proper form",
    )
    private val lookup = mapOf(push.id to push, squat.id to squat)

    private fun timed(id: String, exId: String, sec: Int) =
        WorkoutItem(id = id, exerciseId = exId, mode = ItemMode.TIMED, durationSec = sec)

    private fun workout(
        id: String = "w",
        createdAt: Instant = Instant.EPOCH,
        lastPerformedAt: Instant? = null,
        vararg sections: Section,
    ) = Workout(
        id = id, name = "W", type = TrainingType.HIIT,
        createdAt = createdAt, lastPerformedAt = lastPerformedAt, sections = sections.toList(),
    )

    private fun summary(
        id: String,
        createdAt: Instant = Instant.EPOCH,
        lastPerformedAt: Instant? = null,
    ) = WorkoutSummary(
        id = id, name = id, type = TrainingType.HIIT, exerciseCount = 1,
        estimatedMinutes = 1, createdAt = createdAt, lastPerformedAt = lastPerformedAt,
    )

    // ---- summarize ----

    @Test
    fun `exercise count is items across all sections, not round-expanded segments`() {
        val out = WorkoutSummarizer.summarize(
            workout(
                sections = arrayOf(
                    Section("s1", "A", rounds = 3, items = listOf(timed("a", push.id, 30), timed("b", squat.id, 30))),
                    Section("s2", "B", items = listOf(timed("c", push.id, 30))),
                ),
            ),
            lookup,
        )
        assertEquals(3, out.exerciseCount)
    }

    @Test
    fun `estimated minutes come from the compiled segments including rests`() {
        // 2 rounds x (60s work) + 1 x 60s round rest = 180s = 3 min
        val out = WorkoutSummarizer.summarize(
            workout(
                sections = arrayOf(
                    Section(
                        "s", "A", rounds = 2, restBetweenRounds = Rest.Timed(60),
                        items = listOf(timed("a", push.id, 60)),
                    ),
                ),
            ),
            lookup,
        )
        assertEquals(3, out.estimatedMinutes)
    }

    @Test
    fun `identity fields are carried through unchanged`() {
        val created = Instant.ofEpochSecond(1000)
        val performed = Instant.ofEpochSecond(2000)
        val out = WorkoutSummarizer.summarize(
            workout(
                id = "abc", createdAt = created, lastPerformedAt = performed,
                sections = arrayOf(Section("s", "A", items = listOf(timed("a", push.id, 30)))),
            ),
            lookup,
        )
        assertEquals("abc", out.id)
        assertEquals(TrainingType.HIIT, out.type)
        assertEquals(created, out.createdAt)
        assertEquals(performed, out.lastPerformedAt)
    }

    @Test
    fun `a workout referencing a missing exercise summarises instead of throwing`() {
        // SegmentCompiler requires every exerciseId to resolve. Home must survive a
        // dangling reference — a deleted custom exercise must not take the screen down.
        val out = WorkoutSummarizer.summarize(
            workout(
                sections = arrayOf(
                    Section("s", "A", items = listOf(timed("a", push.id, 60), timed("b", "deleted-id", 60))),
                ),
            ),
            lookup,
        )
        assertEquals(1, out.exerciseCount)
        assertEquals(1, out.estimatedMinutes)
    }

    @Test
    fun `an empty workout summarises to zeroes`() {
        val out = WorkoutSummarizer.summarize(workout(), lookup)
        assertEquals(0, out.exerciseCount)
        assertEquals(0, out.estimatedMinutes)
    }

    // ---- metaLine ----

    @Test
    fun `meta line reads count, minutes and type`() {
        val out = WorkoutSummarizer.summarize(
            workout(
                sections = arrayOf(
                    Section("s", "A", items = listOf(timed("a", push.id, 60), timed("b", squat.id, 60))),
                ),
            ),
            lookup,
        )
        assertEquals("2 exercises · 2 min · HIIT", out.metaLine)
    }

    @Test
    fun `meta line singularises one exercise`() {
        val out = WorkoutSummarizer.summarize(
            workout(sections = arrayOf(Section("s", "A", items = listOf(timed("a", push.id, 60))))),
            lookup,
        )
        assertEquals("1 exercise · 1 min · HIIT", out.metaLine)
    }

    // ---- UpNext ----

    @Test
    fun `up next is null when there is nothing to do`() {
        assertNull(UpNext.select(emptyList()))
    }

    @Test
    fun `up next is the workout performed longest ago`() {
        val out = UpNext.select(
            listOf(
                summary("recent", lastPerformedAt = Instant.ofEpochSecond(3000)),
                summary("stale", lastPerformedAt = Instant.ofEpochSecond(1000)),
                summary("middle", lastPerformedAt = Instant.ofEpochSecond(2000)),
            ),
        )
        assertEquals("stale", out?.id)
    }

    @Test
    fun `a never-performed workout outranks every performed one`() {
        val out = UpNext.select(
            listOf(
                summary("ancient", lastPerformedAt = Instant.EPOCH),
                summary("fresh", createdAt = Instant.ofEpochSecond(500), lastPerformedAt = null),
            ),
        )
        assertEquals("fresh", out?.id)
    }

    @Test
    fun `among never-performed workouts the newest wins`() {
        val out = UpNext.select(
            listOf(
                summary("older", createdAt = Instant.ofEpochSecond(100)),
                summary("newest", createdAt = Instant.ofEpochSecond(900)),
                summary("middle", createdAt = Instant.ofEpochSecond(500)),
            ),
        )
        assertEquals("newest", out?.id)
    }

    @Test
    fun `a single workout is always up next`() {
        assertEquals("only", UpNext.select(listOf(summary("only")))?.id)
    }
}
