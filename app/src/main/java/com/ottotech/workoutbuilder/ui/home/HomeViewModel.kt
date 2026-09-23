package com.ottotech.workoutbuilder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ottotech.workoutbuilder.WorkoutApp
import com.ottotech.workoutbuilder.data.ExerciseRepository
import com.ottotech.workoutbuilder.data.WorkoutRepository
import com.ottotech.workoutbuilder.domain.WorkoutSummarizer
import com.ottotech.workoutbuilder.domain.WorkoutSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    val workouts: List<WorkoutSummary> = emptyList(),
) {
    val isEmpty: Boolean get() = !loading && workouts.isEmpty()
}

/**
 * Thin by design. Every decision it appears to make — summarising, ordering the list —
 * is a pure function in `domain/` with its own unit tests; this class only joins two
 * flows and holds the result.
 */
class HomeViewModel(
    workouts: WorkoutRepository,
    exercises: ExerciseRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> =
        combine(workouts.observeAll(), exercises.observeAll()) { workoutList, exerciseList ->
            val library = exerciseList.associateBy { it.id }
            val summaries = WorkoutSummarizer.summarizeAll(workoutList, library)
            HomeUiState(
                loading = false,
                workouts = WorkoutSummarizer.sortedForHome(summaries),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkoutApp
                HomeViewModel(app.workouts, app.exercises)
            }
        }
    }
}
