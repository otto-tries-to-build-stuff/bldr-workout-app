package com.ottotech.workoutbuilder.domain

import java.time.Instant

/**
 * A built workout. [lastPerformedAt] drives the "Up next" hero card on Home: the
 * workout gone longest without being done, or the newest if none has been performed.
 */
data class Workout(
    val id: String,
    val name: String,
    val type: TrainingType,
    val createdAt: Instant,
    val lastPerformedAt: Instant? = null,
    val sections: List<Section> = emptyList(),
)
