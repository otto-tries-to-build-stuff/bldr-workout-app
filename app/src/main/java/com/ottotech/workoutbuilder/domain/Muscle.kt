package com.ottotech.workoutbuilder.domain

/**
 * An exercise has exactly one primary muscle — its home in the Browse-by-muscle grid —
 * so category counts stay a clean partition of the library. Additional muscles worked
 * are carried as [Facet.MUSCLE] tags instead.
 */
enum class Muscle(val label: String) {
    CHEST("Chest"),
    BACK("Back"),
    LEGS("Legs"),
    SHOULDERS("Shoulders"),
    ARMS("Arms"),
    CORE("Core"),
    CARDIO("Cardio"),
    MOBILITY("Mobility"),
    FULL_BODY("Full body");

    companion object {
        fun fromLabel(label: String): Muscle? = entries.firstOrNull { it.label == label }
    }
}
