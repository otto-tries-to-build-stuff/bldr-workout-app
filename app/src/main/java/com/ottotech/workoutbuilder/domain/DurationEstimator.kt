package com.ottotech.workoutbuilder.domain

import kotlin.math.roundToInt

/**
 * Estimates how long a compiled workout takes, for the Home card meta line
 * ("45 min") and the Coach preview.
 *
 * Only timed segments have a real duration; reps and manual segments are estimated.
 * The constants are tunable in one place — the Court Speed test asserts a band rather
 * than an exact figure so adjusting them does not break it.
 */
object DurationEstimator {

    /** Work plus inter-set rest for one set. */
    const val REPS_SECONDS_PER_SET = 45

    /** A sprint, a lift, or whatever the user taps Done on. */
    const val MANUAL_WORK_SECONDS = 20

    /** A walk-back or self-paced recovery. */
    const val MANUAL_REST_SECONDS = 60

    fun estimateSeconds(segments: List<Segment>): Int = segments.sumOf { segment ->
        when (segment.mode) {
            ItemMode.TIMED -> segment.durationSec ?: 0
            ItemMode.REPS -> (segment.sets ?: 1) * REPS_SECONDS_PER_SET
            ItemMode.MANUAL ->
                if (segment.kind == SegmentKind.WORK) MANUAL_WORK_SECONDS else MANUAL_REST_SECONDS
        }
    }

    fun estimateMinutes(segments: List<Segment>): Int =
        (estimateSeconds(segments) / 60.0).roundToInt()
}
