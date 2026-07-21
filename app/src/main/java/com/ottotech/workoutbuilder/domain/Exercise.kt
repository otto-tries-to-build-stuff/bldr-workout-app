package com.ottotech.workoutbuilder.domain

/**
 * A library item. [tags] replace what would otherwise be separate `type` and
 * `equipment` fields, so an exercise can be both Strength and HIIT and can be
 * filtered by equipment.
 */
data class Exercise(
    val id: String,
    val name: String,
    val primaryMuscle: Muscle,
    val tags: Set<Tag> = emptySet(),
    /** Search string used when [demoUrl] is null, e.g. "barbell bench press proper form". */
    val demoQuery: String,
    /** Optional specific video. When set, opened instead of searching. */
    val demoUrl: String? = null,
    val isCustom: Boolean = false,
) {
    /** First EQUIPMENT tag — used for the reps screen sub-line, e.g. "Chest · Barbell". */
    val equipmentLabel: String?
        get() = tags.firstOrNull { it.facet == Facet.EQUIPMENT }?.value
}
