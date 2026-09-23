package com.ottotech.workoutbuilder.data

import android.content.Context

/**
 * One-shot flags that are not app data.
 *
 * Deliberately SharedPreferences and not a Room table: "have we ever seeded the sample
 * workout" has to survive the user deleting that workout, so it cannot live in the table
 * it describes.
 */
class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("workout-builder", Context.MODE_PRIVATE)

    var sampleWorkoutSeeded: Boolean
        get() = prefs.getBoolean(KEY_SAMPLE_SEEDED, false)
        set(value) = prefs.edit().putBoolean(KEY_SAMPLE_SEEDED, value).apply()

    /**
     * Cue mute. Lives here rather than in Settings because Settings is Phase 2; the Phase 2
     * toggle will read this same key, so nothing has to move.
     */
    var cuesMuted: Boolean
        get() = prefs.getBoolean(KEY_CUES_MUTED, false)
        set(value) = prefs.edit().putBoolean(KEY_CUES_MUTED, value).apply()

    private companion object {
        const val KEY_SAMPLE_SEEDED = "sample_workout_seeded"
        const val KEY_CUES_MUTED = "cues_muted"
    }
}
