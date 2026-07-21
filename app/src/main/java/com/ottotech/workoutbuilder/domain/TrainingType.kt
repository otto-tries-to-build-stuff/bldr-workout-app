package com.ottotech.workoutbuilder.domain

/**
 * A workout has exactly one type, shown on its Home card.
 *
 * Exercises use the same value set via [Facet.TYPE], but multi-valued — a Kettlebell
 * Swing is both Strength and HIIT.
 */
enum class TrainingType(val label: String) {
    STRENGTH("Strength"),
    HIIT("HIIT"),
    MOBILITY("Mobility"),
}
