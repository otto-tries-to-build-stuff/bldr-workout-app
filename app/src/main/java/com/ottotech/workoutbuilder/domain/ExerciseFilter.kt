package com.ottotech.workoutbuilder.domain

/**
 * The Library's active filter. [facets] holds the selected values per dimension;
 * [query] is the search box.
 */
data class FilterState(
    val facets: Map<Facet, Set<String>> = emptyMap(),
    val query: String = "",
) {
    /** Drives the count badge on the Filters control. */
    val activeFacetCount: Int get() = facets.count { it.value.isNotEmpty() }

    val isEmpty: Boolean get() = activeFacetCount == 0 && query.isBlank()
}

/**
 * Filtering rules (spec §6.3a):
 * - multiple values **within** a facet combine with OR
 * - facets combine with **AND**
 * - a facet with no selection applies no constraint
 * - search matches exercise names *and* tag values
 *
 * The MUSCLE facet matches secondary tags only — an exercise never repeats its
 * primary muscle as a tag, and the Browse-by-muscle grid covers the primary. That is
 * why the sheet labels this group "Also hits".
 */
object ExerciseFilter {

    fun apply(exercises: List<Exercise>, state: FilterState): List<Exercise> =
        exercises.filter { matchesFacets(it, state) && matchesQuery(it, state.query) }

    /**
     * Category tile counts. Every muscle is present in the result, including zeroes,
     * so the grid can dim empty categories rather than dropping tiles.
     */
    fun countsByMuscle(exercises: List<Exercise>, state: FilterState): Map<Muscle, Int> {
        val matching = apply(exercises, state)
        return Muscle.entries.associateWith { muscle ->
            matching.count { it.primaryMuscle == muscle }
        }
    }

    /**
     * The categories the grid shows. A muscle with no exercises at all is not a category —
     * it would be a permanently dead tile. This is distinct from a category that the active
     * filter narrows to zero, which stays on screen dimmed and untappable (spec §6.3).
     */
    fun categoriesIn(exercises: List<Exercise>): List<Muscle> =
        Muscle.entries.filter { muscle -> exercises.any { it.primaryMuscle == muscle } }

    /** The category screen: the active filter, narrowed to one home category. */
    fun forMuscle(exercises: List<Exercise>, state: FilterState, muscle: Muscle): List<Exercise> =
        apply(exercises, state).filter { it.primaryMuscle == muscle }

    private fun matchesFacets(exercise: Exercise, state: FilterState): Boolean =
        state.facets.all { (facet, values) ->
            values.isEmpty() || exercise.tags.any { it.facet == facet && it.value in values }
        }

    private fun matchesQuery(exercise: Exercise, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return exercise.name.lowercase().contains(q) ||
            exercise.tags.any { it.value.lowercase().contains(q) }
    }
}

// ---- FilterState transitions ----
//
// The chip row and the Filters sheet edit the same state, so these live here rather than
// in either screen. Extensions on an immutable data class: every call returns a new state,
// which is what a Compose StateFlow wants.

/** Multi-select within a facet — the Filters sheet's chips. */
fun FilterState.toggle(facet: Facet, value: String): FilterState {
    val current = facets[facet].orEmpty()
    val next = if (value in current) current - value else current + value
    // Drop the key rather than leaving an empty set, so activeFacetCount — which drives
    // the Filters badge — never counts a facet the user has fully deselected.
    return copy(facets = if (next.isEmpty()) facets - facet else facets + (facet to next))
}

/**
 * Single-select within a facet — the Library's TYPE chip row. Passing null is the `All`
 * chip: it clears TYPE and nothing else (spec §6.3a).
 */
fun FilterState.selectOnly(facet: Facet, value: String?): FilterState =
    if (value == null) copy(facets = facets - facet)
    else copy(facets = facets + (facet to setOf(value)))

/**
 * `Clear all` in the Filters sheet. The search query is a control outside the sheet and
 * is deliberately left alone.
 */
fun FilterState.clearFacets(): FilterState = copy(facets = emptyMap())

fun FilterState.isSelected(facet: Facet, value: String): Boolean =
    value in facets[facet].orEmpty()
