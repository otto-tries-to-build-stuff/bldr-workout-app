package com.ottotech.workoutbuilder.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ottotech.workoutbuilder.WorkoutApp
import com.ottotech.workoutbuilder.data.ExerciseRepository
import com.ottotech.workoutbuilder.data.WorkoutRepository
import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.Workout
import com.ottotech.workoutbuilder.domain.WorkoutSummarizer
import com.ottotech.workoutbuilder.domain.WorkoutSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class OverviewUiState(
    val loading: Boolean = true,
    /** Null once loaded means the workout no longer exists. */
    val workout: Workout? = null,
    val summary: WorkoutSummary? = null,
    val exercises: Map<String, Exercise> = emptyMap(),
)

/**
 * Observes rather than loads once, so returning from an Edit shows the saved changes
 * without the screen having to ask.
 */
class OverviewViewModel(
    workouts: WorkoutRepository,
    exercises: ExerciseRepository,
    workoutId: String,
) : ViewModel() {

    val state: StateFlow<OverviewUiState> =
        combine(workouts.observeAll(), exercises.observeAll()) { workoutList, exerciseList ->
            val library = exerciseList.associateBy { it.id }
            val workout = workoutList.find { it.id == workoutId }
            OverviewUiState(
                loading = false,
                workout = workout,
                summary = workout?.let { WorkoutSummarizer.summarize(it, library) },
                exercises = library,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkoutApp
                OverviewViewModel(app.workouts, app.exercises, createSavedStateHandle().get<String>("workoutId").orEmpty())
            }
        }
    }
}
