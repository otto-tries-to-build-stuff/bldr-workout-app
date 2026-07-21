package com.ottotech.workoutbuilder.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One test per invariant in the design spec §4. */
class SegmentCompilerTest {

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

    private fun workout(vararg sections: Section) = Workout(
        id = "w", name = "W", type = TrainingType.HIIT,
        createdAt = Instant.EPOCH, sections = sections.toList(),
    )

    @Test
    fun `single section single round emits one segment per item in order`() {
        val out = SegmentCompiler.compile(
            workout(
                Section(
                    "s", "Main",
                    items = listOf(timed("a", push.id, 30), timed("b", squat.id, 40)),
                ),
            ),
            lookup,
        )
        assertEquals(2, out.size)
        assertEquals(listOf("Push-up", "Air Squat"), out.map { it.exerciseName })
        assertEquals(listOf(30, 40), out.map { it.durationSec })
        assertTrue(out.all { it.kind == SegmentKind.WORK })
    }

    @Test
    fun `rest between exercises is inserted but not after the last item of a round`() {
        val out = SegmentCompiler.compile(
            workout(
                Section(
                    "s", "Main",
                    restBetweenExercises = Rest.Timed(15),
                    items = listOf(timed("a", push.id, 30), timed("b", squat.id, 30)),
                ),
            ),
            lookup,
        )
        assertEquals(3, out.size)
        assertEquals(
            listOf(SegmentKind.WORK, SegmentKind.REST, SegmentKind.WORK),
            out.map { it.kind },
        )
    }

    @Test
    fun `rounds expand and round rest sits between rounds but not after the last`() {
        val out = SegmentCompiler.compile(
            workout(
                Section(
                    "s", "Circuit", rounds = 3,
                    restBetweenRounds = Rest.Timed(60),
                    items = listOf(timed("a", push.id, 30)),
                ),
            ),
            lookup,
        )
        assertEquals(5, out.size)
        assertEquals(
            listOf(
                SegmentKind.WORK, SegmentKind.REST, SegmentKind.WORK,
                SegmentKind.REST, SegmentKind.WORK,
            ),
            out.map { it.kind },
        )
    }

    @Test
    fun `section rest sits between sections but not after the last`() {
        val out = SegmentCompiler.compile(
            workout(
                Section(
                    "s1", "A", restAfterSection = Rest.Timed(60),
                    items = listOf(timed("a", push.id, 30)),
                ),
                Section(
                    "s2", "B", restAfterSection = Rest.Timed(60),
                    items = listOf(timed("b", squat.id, 30)),
                ),
            ),
            lookup,
        )
        assertEquals(3, out.size)
        assertEquals(
            listOf(SegmentKind.WORK, SegmentKind.REST, SegmentKind.WORK),
            out.map { it.kind },
        )
    }

    @Test
    fun `single item section never emits an inter-exercise rest`() {
        val out = SegmentCompiler.compile(
            workout(
                Section(
                    "s", "Solo",
                    restBetweenExercises = Rest.Timed(15),
                    items = listOf(timed("a", push.id, 30)),
                ),
            ),
            lookup,
        )
        assertEquals(1, out.size)
    }

    @Test
    fun `null rest emits nothing rather than a zero length segment`() {
        val out = SegmentCompiler.compile(
            workout(Section("s", "Main", rounds = 2, items = listOf(timed("a", push.id, 30)))),
            lookup,
        )
        assertEquals(2, out.size)
        assertTrue(out.none { it.kind == SegmentKind.REST })
    }

    @Test
    fun `empty section contributes no segments at all`() {
        val out = SegmentCompiler.compile(
            workout(Section("s", "Empty", rounds = 3, restAfterSection = Rest.Timed(60))),
            lookup,
        )
        assertEquals(0, out.size)
    }

    @Test
    fun `manual rest produces a manual mode rest segment with no duration`() {
        val out = SegmentCompiler.compile(
            workout(
                Section(
                    "s", "Sprints", rounds = 2,
                    restBetweenRounds = Rest.Manual,
                    items = listOf(WorkoutItem("a", push.id, ItemMode.MANUAL)),
                ),
            ),
            lookup,
        )
        val rest = out.single { it.kind == SegmentKind.REST }
        assertEquals(ItemMode.MANUAL, rest.mode)
        assertEquals(null, rest.durationSec)
    }

    @Test
    fun `mixed modes are preserved in one workout`() {
        val out = SegmentCompiler.compile(
            workout(
                Section(
                    "s", "Mixed",
                    items = listOf(
                        timed("a", push.id, 60),
                        WorkoutItem("b", squat.id, ItemMode.REPS, sets = 3, reps = 8, weightKg = 60f),
                        WorkoutItem("c", push.id, ItemMode.MANUAL),
                    ),
                ),
            ),
            lookup,
        )
        assertEquals(listOf(ItemMode.TIMED, ItemMode.REPS, ItemMode.MANUAL), out.map { it.mode })
        assertEquals(3, out[1].sets)
        assertEquals(60f, out[1].weightKg)
    }

    @Test
    fun `label includes round marker only when rounds is greater than one`() {
        val multi = SegmentCompiler.compile(
            workout(Section("s", "Block A", rounds = 3, items = listOf(timed("a", push.id, 30)))),
            lookup,
        )
        assertEquals("BLOCK A · ROUND 1/3", multi.first().label)

        val single = SegmentCompiler.compile(
            workout(Section("s", "Warm Prep", items = listOf(timed("a", push.id, 30)))),
            lookup,
        )
        assertEquals("WARM PREP", single.first().label)
    }

    @Test
    fun `index and total are assigned across the whole compiled list`() {
        val out = SegmentCompiler.compile(
            workout(
                Section("s1", "A", items = listOf(timed("a", push.id, 30))),
                Section("s2", "B", items = listOf(timed("b", squat.id, 30))),
            ),
            lookup,
        )
        assertEquals(listOf(0, 1), out.map { it.index })
        assertTrue(out.all { it.total == 2 })
    }

    @Test
    fun `work segments carry exercise metadata for the execution screen`() {
        val out = SegmentCompiler.compile(
            workout(Section("s", "Main", items = listOf(timed("a", push.id, 30)))),
            lookup,
        )
        val s = out.single()
        assertEquals(Muscle.CHEST, s.primaryMuscle)
        assertEquals("Bodyweight", s.equipmentLabel)
        assertEquals("push up proper form", s.demoQuery)
    }
}
