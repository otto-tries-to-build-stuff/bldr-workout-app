package com.ottotech.workoutbuilder.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals

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

    // ---- card lines ----

    @Test
    fun `short meta reads count and type, without minutes`() {
        val out = WorkoutSummarizer.summarize(
            workout(
                sections = arrayOf(
                    Section("s", "A", items = listOf(timed("a", push.id, 60), timed("b", squat.id, 60))),
                ),
            ),
            lookup,
        )
        assertEquals("2 exercises · HIIT", out.shortMeta)
    }

    @Test
    fun `short meta singularises one exercise`() {
        val out = WorkoutSummarizer.summarize(
            workout(sections = arrayOf(Section("s", "A", items = listOf(timed("a", push.id, 60))))),
            lookup,
        )
        assertEquals("1 exercise · HIIT", out.shortMeta)
    }

    @Test
    fun `minutes label carries its unit`() {
        val out = WorkoutSummarizer.summarize(
            workout(sections = arrayOf(Section("s", "A", items = listOf(timed("a", push.id, 60))))),
            lookup,
        )
        assertEquals("1 MIN", out.minutesLabel)
    }

    // ---- lastDoneLine ----

    private val zone = ZoneOffset.UTC
    private val now: Instant = Instant.parse("2026-07-26T12:00:00Z")

    private fun daysAgo(days: Long) = now.minus(days, ChronoUnit.DAYS)

    @Test
    fun `a never-performed workout says so plainly`() {
        assertEquals("Never done", summary("w", lastPerformedAt = null).lastDoneLine(now, zone))
    }

    @Test
    fun `same day reads Today`() {
        assertEquals("Today", summary("w", lastPerformedAt = now.minus(3, ChronoUnit.HOURS)).lastDoneLine(now, zone))
    }

    @Test
    fun `one day reads Yesterday`() {
        assertEquals("Yesterday", summary("w", lastPerformedAt = daysAgo(1)).lastDoneLine(now, zone))
    }

    @Test
    fun `under a week counts days`() {
        assertEquals("4 days ago", summary("w", lastPerformedAt = daysAgo(4)).lastDoneLine(now, zone))
    }

    @Test
    fun `under two months counts weeks`() {
        assertEquals("1 week ago", summary("w", lastPerformedAt = daysAgo(7)).lastDoneLine(now, zone))
        assertEquals("3 weeks ago", summary("w", lastPerformedAt = daysAgo(21)).lastDoneLine(now, zone))
    }

    @Test
    fun `beyond two months counts months`() {
        assertEquals("2 months ago", summary("w", lastPerformedAt = daysAgo(70)).lastDoneLine(now, zone))
    }

    @Test
    fun `a workout done late last night reads Yesterday even though under 24 hours have passed`() {
        // Done at 23:00 yesterday, viewed at 08:00 today — 9 hours apart, but a different
        // calendar day. A block-of-24-hours count would wrongly read "Today".
        val performedLateLastNight = Instant.parse("2026-07-25T23:00:00Z")
        val viewedNextMorning = Instant.parse("2026-07-26T08:00:00Z")
        assertEquals(
            "Yesterday",
            summary("w", lastPerformedAt = performedLateLastNight).lastDoneLine(viewedNextMorning, zone),
        )
    }

    // ---- sortedForHome ----

    @Test
    fun `home order is newest created first`() {
        val out = WorkoutSummarizer.sortedForHome(
            listOf(
                summary("older", createdAt = Instant.ofEpochSecond(100)),
                summary("newest", createdAt = Instant.ofEpochSecond(900)),
                summary("middle", createdAt = Instant.ofEpochSecond(500)),
            ),
        )
        assertEquals(listOf("newest", "middle", "older"), out.map { it.id })
    }

    @Test
    fun `sorting an empty list is empty, not an error`() {
        assertEquals(emptyList(), WorkoutSummarizer.sortedForHome(emptyList()))
    }
}
