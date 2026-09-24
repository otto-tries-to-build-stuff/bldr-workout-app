package com.ottotech.workoutbuilder

import android.app.Application
import com.ottotech.workoutbuilder.data.ApiKeyStore
import com.ottotech.workoutbuilder.data.AppPrefs
import com.ottotech.workoutbuilder.data.ExerciseRepository
import com.ottotech.workoutbuilder.data.WorkoutRepository
import com.ottotech.workoutbuilder.data.db.AppDatabase

/**
 * Manual dependency wiring. One module, five objects, no injection framework — Hilt would
 * add a plugin, a compiler pass and a set of annotations to save four lines.
 *
 * ViewModels reach this through `APPLICATION_KEY` in their factory, so they still take
 * plain repository constructor parameters and stay independent of it.
 */
class WorkoutApp : Application() {

    private val database by lazy { AppDatabase.get(this) }

    val prefs by lazy { AppPrefs(this) }
    val exercises by lazy { ExerciseRepository(database.exerciseDao()) }
    val workouts by lazy { WorkoutRepository(database.workoutDao()) }
    val apiKeys by lazy { ApiKeyStore(this) }
}
