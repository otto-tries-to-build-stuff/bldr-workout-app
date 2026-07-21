package com.ottotech.workoutbuilder.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterStateTest {

    private fun ex(id: String, muscle: Muscle, vararg tags: Tag) =
        Exercise(id = id, name = id, primaryMuscle = muscle, tags = tags.toSet(), demoQuery = "$id form")

    private val bench = ex("bench", Muscle.CHEST, Tag(Facet.TYPE, "Strength"), Tag(Facet.EQUIPMENT, "Barbell"))
    private val pushUp = ex("push-up", Muscle.CHEST, Tag(Facet.TYPE, "Strength"), Tag(Facet.EQUIPMENT, "Bodyweight"))
    private val swing = ex("swing", Muscle.LEGS, Tag(Facet.TYPE, "HIIT"), Tag(Facet.EQUIPMENT, "Kettlebell"))
    private val all = listOf(bench, pushUp, swing)

    // ---- toggle ----

    @Test
    fun `toggle adds a value that is not selected`() {
        val out = FilterState().toggle(Facet.EQUIPMENT, "Barbell")
        assertEquals(setOf("Barbell"), out.facets[Facet.EQUIPMENT])
    }

    @Test
    fun `toggle accumulates within a facet`() {
        val out = FilterState()
            .toggle(Facet.EQUIPMENT, "Barbell")
            .toggle(Facet.EQUIPMENT, "Kettlebell")
        assertEquals(setOf("Barbell", "Kettlebell"), out.facets[Facet.EQUIPMENT])
    }

    @Test
    fun `toggle removes a value that is selected`() {
        val out = FilterState()
            .toggle(Facet.EQUIPMENT, "Barbell")
            .toggle(Facet.EQUIPMENT, "Kettlebell")
            .toggle(Facet.EQUIPMENT, "Barbell")
        assertEquals(setOf("Kettlebell"), out.facets[Facet.EQUIPMENT])
    }

    @Test
    fun `toggling the last value drops the facet key entirely`() {
        // An empty set applies no constraint anyway, but leaving the key behind would
        // make activeFacetCount and the Filters badge disagree with the UI.
        val out = FilterState().toggle(Facet.TYPE, "HIIT").toggle(Facet.TYPE, "HIIT")
        assertTrue(out.facets.isEmpty())
        assertEquals(0, out.activeFacetCount)
        assertTrue(out.isEmpty)
    }

    @Test
    fun `toggle leaves other facets alone`() {
        val out = FilterState()
            .toggle(Facet.TYPE, "HIIT")
            .toggle(Facet.EQUIPMENT, "Kettlebell")
        assertEquals(setOf("HIIT"), out.facets[Facet.TYPE])
        assertEquals(setOf("Kettlebell"), out.facets[Facet.EQUIPMENT])
        assertEquals(2, out.activeFacetCount)
    }

    // ---- selectOnly ----

    @Test
    fun `selectOnly replaces the whole facet selection`() {
        val out = FilterState()
            .toggle(Facet.TYPE, "HIIT")
            .toggle(Facet.TYPE, "Mobility")
            .selectOnly(Facet.TYPE, "Strength")
        assertEquals(setOf("Strength"), out.facets[Facet.TYPE])
    }

    @Test
    fun `selectOnly with null clears the facet - this is the All chip`() {
        val out = FilterState().toggle(Facet.TYPE, "HIIT").selectOnly(Facet.TYPE, null)
        assertTrue(out.facets[Facet.TYPE].isNullOrEmpty())
    }

    @Test
    fun `the All chip does not disturb other facets`() {
        val out = FilterState()
            .toggle(Facet.TYPE, "HIIT")
            .toggle(Facet.EQUIPMENT, "Kettlebell")
            .selectOnly(Facet.TYPE, null)
        assertEquals(setOf("Kettlebell"), out.facets[Facet.EQUIPMENT])
    }

    // ---- clearFacets ----

    @Test
    fun `clearFacets empties every facet but keeps the search query`() {
        // Clear all lives inside the Filters sheet; search is a control outside it.
        val out = FilterState(query = "kettlebell")
            .toggle(Facet.TYPE, "HIIT")
            .toggle(Facet.PATTERN, "Hinge")
            .clearFacets()
        assertEquals(0, out.activeFacetCount)
        assertEquals("kettlebell", out.query)
    }

    // ---- isSelected ----

    @Test
    fun `isSelected reports chip state`() {
        val state = FilterState().toggle(Facet.TYPE, "HIIT")
        assertTrue(state.isSelected(Facet.TYPE, "HIIT"))
        assertFalse(state.isSelected(Facet.TYPE, "Strength"))
        assertFalse(state.isSelected(Facet.EQUIPMENT, "HIIT"))
    }

    // ---- categoriesIn ----

    @Test
    fun `categories are the muscles the library actually has, in enum order`() {
        assertEquals(listOf(Muscle.CHEST, Muscle.LEGS), ExerciseFilter.categoriesIn(all))
    }

    @Test
    fun `a muscle with no exercises is not a category at all`() {
        // Distinct from a category that filters down to zero, which stays on screen dimmed.
        assertFalse(Muscle.MOBILITY in ExerciseFilter.categoriesIn(all))
    }

    @Test
    fun `categories of an empty library are empty`() {
        assertEquals(emptyList(), ExerciseFilter.categoriesIn(emptyList()))
    }

    // ---- forMuscle ----

    @Test
    fun `forMuscle narrows the active filter to one home category`() {
        val out = ExerciseFilter.forMuscle(
            all,
            FilterState(facets = mapOf(Facet.EQUIPMENT to setOf("Bodyweight"))),
            Muscle.CHEST,
        )
        assertEquals(listOf(pushUp), out)
    }

    @Test
    fun `forMuscle with no filter returns the whole category`() {
        assertEquals(listOf(bench, pushUp), ExerciseFilter.forMuscle(all, FilterState(), Muscle.CHEST))
    }

    @Test
    fun `forMuscle can return nothing without error`() {
        val out = ExerciseFilter.forMuscle(
            all,
            FilterState(facets = mapOf(Facet.TYPE to setOf("HIIT"))),
            Muscle.CHEST,
        )
        assertEquals(emptyList(), out)
    }
}
