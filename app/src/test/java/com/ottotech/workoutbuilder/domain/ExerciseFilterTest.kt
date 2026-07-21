package com.ottotech.workoutbuilder.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ExerciseFilterTest {

    private fun ex(id: String, name: String, muscle: Muscle, vararg tags: Tag) =
        Exercise(id = id, name = name, primaryMuscle = muscle, tags = tags.toSet(), demoQuery = "$name form")

    private val bench = ex(
        "bench", "Bench Press", Muscle.CHEST,
        Tag(Facet.TYPE, "Strength"), Tag(Facet.EQUIPMENT, "Barbell"),
        Tag(Facet.MUSCLE, "Arms"), Tag(Facet.PATTERN, "Push"),
    )

    private val pushUp = ex(
        "push-up", "Push-up", Muscle.CHEST,
        Tag(Facet.TYPE, "Strength"), Tag(Facet.EQUIPMENT, "Bodyweight"), Tag(Facet.PATTERN, "Push"),
    )

    private val swing = ex(
        "swing", "Kettlebell Swing", Muscle.LEGS,
        Tag(Facet.TYPE, "Strength"), Tag(Facet.TYPE, "HIIT"),
        Tag(Facet.EQUIPMENT, "Kettlebell"), Tag(Facet.MUSCLE, "Core"), Tag(Facet.PATTERN, "Hinge"),
    )

    private val all = listOf(bench, pushUp, swing)

    @Test
    fun `empty filter returns everything`() {
        assertEquals(all, ExerciseFilter.apply(all, FilterState()))
    }

    @Test
    fun `multiple values within a facet combine with OR`() {
        val out = ExerciseFilter.apply(
            all,
            FilterState(facets = mapOf(Facet.EQUIPMENT to setOf("Barbell", "Kettlebell"))),
        )
        assertEquals(listOf(bench, swing), out)
    }

    @Test
    fun `different facets combine with AND`() {
        val out = ExerciseFilter.apply(
            all,
            FilterState(
                facets = mapOf(
                    Facet.TYPE to setOf("HIIT"),
                    Facet.EQUIPMENT to setOf("Kettlebell"),
                ),
            ),
        )
        assertEquals(listOf(swing), out)
    }

    @Test
    fun `AND across facets can produce an empty result without error`() {
        val out = ExerciseFilter.apply(
            all,
            FilterState(
                facets = mapOf(
                    Facet.TYPE to setOf("HIIT"),
                    Facet.EQUIPMENT to setOf("Barbell"),
                ),
            ),
        )
        assertEquals(emptyList(), out)
    }

    @Test
    fun `a facet present with an empty set applies no constraint`() {
        val out = ExerciseFilter.apply(all, FilterState(facets = mapOf(Facet.TYPE to emptySet())))
        assertEquals(all, out)
    }

    @Test
    fun `muscle facet matches secondary tags only not the primary muscle`() {
        assertEquals(
            listOf(bench),
            ExerciseFilter.apply(all, FilterState(facets = mapOf(Facet.MUSCLE to setOf("Arms")))),
        )
        assertEquals(
            emptyList(),
            ExerciseFilter.apply(all, FilterState(facets = mapOf(Facet.MUSCLE to setOf("Chest")))),
        )
    }

    @Test
    fun `query matches exercise name case insensitively`() {
        assertEquals(listOf(bench), ExerciseFilter.apply(all, FilterState(query = "bench")))
    }

    @Test
    fun `query matches tag values so kettlebell finds the swing`() {
        assertEquals(listOf(swing), ExerciseFilter.apply(all, FilterState(query = "kettlebell")))
    }

    @Test
    fun `query and facets combine to narrow the result`() {
        // "push" alone also matches Bench Press, via its PATTERN:Push tag — searching
        // tag values is deliberate, so this is the correct wider result.
        assertEquals(
            listOf(bench, pushUp),
            ExerciseFilter.apply(all, FilterState(query = "push")),
        )

        // Adding an equipment facet ANDs with the query and narrows to the bodyweight one.
        assertEquals(
            listOf(pushUp),
            ExerciseFilter.apply(
                all,
                FilterState(facets = mapOf(Facet.EQUIPMENT to setOf("Bodyweight")), query = "push"),
            ),
        )
    }

    @Test
    fun `counts by muscle reflect the active filter`() {
        val unfiltered = ExerciseFilter.countsByMuscle(all, FilterState())
        assertEquals(2, unfiltered[Muscle.CHEST])
        assertEquals(1, unfiltered[Muscle.LEGS])

        val bodyweightOnly = ExerciseFilter.countsByMuscle(
            all,
            FilterState(facets = mapOf(Facet.EQUIPMENT to setOf("Bodyweight"))),
        )
        assertEquals(1, bodyweightOnly[Muscle.CHEST])
        assertEquals(0, bodyweightOnly[Muscle.LEGS])
    }
}
