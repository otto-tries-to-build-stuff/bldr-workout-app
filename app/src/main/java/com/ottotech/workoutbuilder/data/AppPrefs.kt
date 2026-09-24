package com.ottotech.workoutbuilder.data

import android.content.Context
import com.ottotech.workoutbuilder.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-shot flags and user settings that are not app data.
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

    /** Cue mute — written by both the in-workout mute button and the Settings sound switch. */
    var cuesMuted: Boolean
        get() = prefs.getBoolean(KEY_CUES_MUTED, false)
        set(value) = prefs.edit().putBoolean(KEY_CUES_MUTED, value).apply()

    private val _themeMode = MutableStateFlow(ThemeMode.fromStored(prefs.getString(KEY_THEME, null)))

    /** Observed by MainActivity, so a change in Settings re-themes the whole app at once. */
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    private companion object {
        const val KEY_SAMPLE_SEEDED = "sample_workout_seeded"
        const val KEY_CUES_MUTED = "cues_muted"
        const val KEY_THEME = "theme_mode"
    }
}
