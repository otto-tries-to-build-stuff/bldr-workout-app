package com.ottotech.workoutbuilder.domain

import java.time.Instant

/**
 * A built workout. [lastPerformedAt] drives the Home card's last-done line
 * ("Today" / "Yesterday" / "3 days ago" / …). Nothing writes it until Plan 1D wires up
 * execution, so every workout currently reads "Never done".
 */
data class Workout(
    val id: String,
    val name: String,
    val type: TrainingType,
    val createdAt: Instant,
    val lastPerformedAt: Instant? = null,
    val sections: List<Section> = emptyList(),
)
