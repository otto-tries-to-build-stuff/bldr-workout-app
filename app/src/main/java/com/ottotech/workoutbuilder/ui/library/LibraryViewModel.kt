package com.ottotech.workoutbuilder.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ottotech.workoutbuilder.WorkoutApp
import com.ottotech.workoutbuilder.data.ExerciseRepository
import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.ExerciseFilter
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.FilterState
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.domain.clearFacets
import com.ottotech.workoutbuilder.domain.selectOnly
import com.ottotech.workoutbuilder.domain.toggle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    /** The whole library, unfiltered — the category and detail screens filter it themselves. */
    val exercises: List<Exercise> = emptyList(),
    val filter: FilterState = FilterState(),
    /** Muscles the library has at all, in enum order. */
    val categories: List<Muscle> = emptyList(),
    /** Per-category counts **after** the active filter — spec §6.3. */
    val counts: Map<Muscle, Int> = emptyMap(),
    /** Total exercises matching the filter, for the sheet's `SHOW N EXERCISES` button. */
    val matchCount: Int = 0,
) {
    /** Every category filtered down to nothing — the grid is replaced by an empty state. */
    val allCategoriesEmpty: Boolean
        get() = categories.isNotEmpty() && categories.all { (counts[it] ?: 0) == 0 }

    fun exercisesIn(muscle: Muscle): List<Exercise> =
        ExerciseFilter.forMuscle(exercises, filter, muscle)

    fun exercise(id: String): Exercise? = exercises.firstOrNull { it.id == id }
}

/**
 * Shared by the Library grid, a category's exercise list and the exercise detail screen,
 * so the filter the user set on the grid is still in force when they drill in (spec §6.3).
 */
class LibraryViewModel(
    private val exercises: ExerciseRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(FilterState())

    val state: StateFlow<LibraryUiState> =
        combine(exercises.observeAll(), filter) { library, activeFilter ->
            LibraryUiState(
                exercises = library,
                filter = activeFilter,
                categories = ExerciseFilter.categoriesIn(library),
                counts = ExerciseFilter.countsByMuscle(library, activeFilter),
                matchCount = ExerciseFilter.apply(library, activeFilter).size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onQueryChange(query: String) {
        filter.value = filter.value.copy(query = query)
    }

    /** The TYPE chip row. Null is the `All` chip. */
    fun onSelectType(value: String?) {
        filter.value = filter.value.selectOnly(Facet.TYPE, value)
    }

    /** The Filters sheet's chips. */
    fun onToggle(facet: Facet, value: String) {
        filter.value = filter.value.toggle(facet, value)
    }

    /** `Clear all` in the Filters sheet. Search is a control outside the sheet, so it stays. */
    fun onClearFacets() {
        filter.value = filter.value.clearFacets()
    }

    /**
     * The empty grid's escape hatch. Unlike [onClearFacets] this also clears the search,
     * because the dead end it undoes can be caused by either one — and a button that
     * leaves the screen just as empty is worse than no button.
     */
    fun onClearEverything() {
        filter.value = FilterState()
    }

    /** Exercise detail: paste a specific demo URL, or clear it back to search. */
    fun onSetDemoUrl(exerciseId: String, url: String?) {
        viewModelScope.launch {
            exercises.setDemoUrl(exerciseId, url?.takeIf { it.isNotBlank() })
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkoutApp
                LibraryViewModel(app.exercises)
            }
        }
    }
}
