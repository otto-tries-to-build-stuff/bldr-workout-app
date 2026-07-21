package com.ottotech.workoutbuilder.domain

/** The dimension a [Tag] belongs to. Filters combine OR within a facet, AND across facets. */
enum class Facet { TYPE, EQUIPMENT, MUSCLE, PATTERN }

data class Tag(val facet: Facet, val value: String)

/**
 * Values are a controlled vocabulary rather than free text, so filtering stays
 * consistent and drift like "dumbbell" vs "Dumbbells" is impossible. Custom exercises
 * — user-created or Coach-invented — pick from the same vocabulary; it is not
 * user-extensible.
 */
object TagVocabulary {

    val TYPE = listOf("Strength", "HIIT", "Mobility")

    val EQUIPMENT = listOf(
        "Bodyweight", "Dumbbell", "Barbell", "Kettlebell",
        "Bands", "Machine", "Bench", "Pull-up bar",
    )

    /** Secondary muscles worked. An exercise never repeats its primary muscle here. */
    val MUSCLE = Muscle.entries.map { it.label }

    val PATTERN = listOf("Push", "Pull", "Hinge", "Squat", "Lunge", "Carry", "Plyometric")

    fun valuesFor(facet: Facet): List<String> = when (facet) {
        Facet.TYPE -> TYPE
        Facet.EQUIPMENT -> EQUIPMENT
        Facet.MUSCLE -> MUSCLE
        Facet.PATTERN -> PATTERN
    }

    fun isValid(tag: Tag): Boolean = tag.value in valuesFor(tag.facet)
}
