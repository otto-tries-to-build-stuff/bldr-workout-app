package com.ottotech.workoutbuilder.domain

/**
 * Rest between exercises, between rounds, or after a section. `null` means no rest at
 * all — a null rest emits no segment rather than a zero-length one.
 *
 * [Manual] exists because the Court Speed reference workout waits for a tap between
 * sprints; a walk-back cannot be put on a timer.
 */
sealed interface Rest {
    data class Timed(val seconds: Int) : Rest
    data object Manual : Rest
}
