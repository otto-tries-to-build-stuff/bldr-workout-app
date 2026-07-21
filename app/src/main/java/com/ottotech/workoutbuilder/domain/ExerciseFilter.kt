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
