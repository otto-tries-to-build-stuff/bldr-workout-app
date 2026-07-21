package com.ottotech.workoutbuilder.domain

/**
 * One exercise inside a section. Which of the optional fields are meaningful depends
 * on [mode]: [ItemMode.TIMED] uses [durationSec], [ItemMode.REPS] uses [sets] / [reps]
 * / [weightKg], and [ItemMode.MANUAL] uses none of them.
 */
data class WorkoutItem(
    val id: String,
    val exerciseId: String,
    val mode: ItemMode,
    val durationSec: Int? = null,
    val sets: Int? = null,
    val reps: Int? = null,
    val weightKg: Float? = null,
    /** Free-text cue, e.g. "~5m apart · touch ground each end". */
    val note: String? = null,
)
