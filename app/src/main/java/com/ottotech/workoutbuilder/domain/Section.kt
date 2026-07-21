package com.ottotech.workoutbuilder.domain

/**
 * A named block of exercises that repeats for [rounds]. This is what lets the app
 * express a real circuit — "3 rounds of these four moves, 30s on, 15s off".
 *
 * Rest fields are `null` when there is no rest. [restAfterSection] is suppressed after
 * the final section, [restBetweenRounds] after the final round, and
 * [restBetweenExercises] after the final item of a round.
 */
data class Section(
    val id: String,
    val name: String,
    val rounds: Int = 1,
    val restBetweenExercises: Rest? = null,
    val restBetweenRounds: Rest? = null,
    val restAfterSection: Rest? = null,
    val items: List<WorkoutItem> = emptyList(),
)
