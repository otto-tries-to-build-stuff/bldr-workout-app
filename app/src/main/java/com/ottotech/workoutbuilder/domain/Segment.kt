package com.ottotech.workoutbuilder.domain

/** [LEAD_IN] is the "3, 2, 1" before a workout's first segment — see [WorkoutSession.start]. */
enum class SegmentKind { WORK, REST, LEAD_IN }

/**
 * One step of a running workout, produced by [SegmentCompiler] flattening a [Workout]:
 * rounds expanded, rests inserted, everything the execution screen needs denormalised
 * onto it so rendering never needs a database lookup mid-workout.
 *
 * [kind] and [mode] are independent: a rest can be TIMED (counts down) or MANUAL
 * (waits for a tap). REST + REPS is invalid and is never produced.
 */
data class Segment(
    val kind: SegmentKind,
    val mode: ItemMode,
    /** "Rest" for rest segments. */
    val exerciseName: String,
    /** Header line, e.g. "BLOCK A · CIRCUIT · ROUND 2/3". */
    val label: String,
    val primaryMuscle: Muscle? = null,
    /** First EQUIPMENT tag, denormalised for the reps sub-line. Null for rests. */
    val equipmentLabel: String? = null,
    val note: String? = null,
    val demoQuery: String? = null,
    val demoUrl: String? = null,
    /** Null when [mode] is MANUAL or REPS. */
    val durationSec: Int? = null,
    val sets: Int? = null,
    val reps: Int? = null,
    val weightKg: Float? = null,
    /** Position in the compiled list; assigned after the full list is built. */
    val index: Int = 0,
    val total: Int = 0,
)
