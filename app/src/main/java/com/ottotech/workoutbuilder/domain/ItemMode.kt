package com.ottotech.workoutbuilder.domain

/**
 * How a segment is executed and what advances it.
 *
 * - [TIMED]  countdown ring, advances automatically at zero
 * - [REPS]   set checklist, advances when the user taps Next exercise
 * - [MANUAL] count-up timer, advances when the user taps Done
 *
 * A single workout may mix all three.
 */
enum class ItemMode { TIMED, REPS, MANUAL }
