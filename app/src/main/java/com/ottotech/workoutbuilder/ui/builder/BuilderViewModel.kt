package com.ottotech.workoutbuilder.ui.builder

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
import com.ottotech.workoutbuilder.domain.ItemMode
import com.ottotech.workoutbuilder.domain.MoveDirection
import com.ottotech.workoutbuilder.domain.Rest
import com.ottotech.workoutbuilder.domain.RestSlot
import com.ottotech.workoutbuilder.domain.TrainingType
import com.ottotech.workoutbuilder.domain.Workout
import com.ottotech.workoutbuilder.domain.WorkoutDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

data class BuilderUiState(
    val workout: Workout,
    val exercises: Map<String, Exercise> = emptyMap(),
    val saveBlockers: List<String> = emptyList(),
    val canUndo: Boolean = false,
    /** Editing a saved workout rather than building a new one. */
    val isEditing: Boolean = false,
    /** An edit whose saved workout has not arrived yet; the screen shows no form until it has. */
    val isLoading: Boolean = false,
    /** The saved workout as loaded, null for a new one. */
    val original: Workout? = null,
) {
    val canSave: Boolean get() = saveBlockers.isEmpty()
    val hasUnsavedChanges: Boolean get() = WorkoutDraft.hasUnsavedChanges(original, workout)

    /** Name for an item's exercise, tolerant of a since-deleted custom exercise. */
    fun exerciseName(id: String): String = exercises[id]?.name ?: "Removed exercise"
}

/**
 * Holds one workout draft and persists it. Thin by design: every edit is a `WorkoutDraft`
 * call, and the draft is the single source of truth the screen renders.
 *
 * Undo is a whole-draft snapshot rather than positional bookkeeping — drafts are small
 * immutable objects, so stashing the previous one and restoring it is both simpler and
 * exactly correct. Any non-delete edit clears the snapshot so Undo never resurrects stale
 * state after further changes.
 *
 * With an [editingId] it loads that saved workout into the draft instead. Saving upserts by
 * id, so the edit replaces the saved workout in place and keeps its history.
 */
class BuilderViewModel(
    private val workouts: WorkoutRepository,
    exercises: ExerciseRepository,
    private val editingId: String? = null,
) : ViewModel() {

    // Seed with one empty section so the screen opens on something to fill in rather than a
    // blank page. The id is generated here, keeping WorkoutDraft.addSection pure.
    private val draft = MutableStateFlow(
        WorkoutDraft.addSection(
            WorkoutDraft.newWorkout(UUID.randomUUID().toString(), Instant.now()),
            UUID.randomUUID().toString(),
        ),
    )
    private val undo = MutableStateFlow<Workout?>(null)

    /**
     * The saved workout being edited and whether it has loaded. One flow, so the loaded
     * workout, the draft and the end of loading always change together.
     */
    private val loaded = MutableStateFlow(Loaded(original = null, done = editingId == null))

    private data class Loaded(val original: Workout?, val done: Boolean)

    init {
        if (editingId != null) {
            viewModelScope.launch {
                // A workout deleted meanwhile falls back to the blank draft rather than a form
                // that never appears.
                val saved = workouts.load(editingId)
                if (saved != null) draft.value = saved
                loaded.value = Loaded(original = saved, done = true)
            }
        }
    }

    /** The section awaiting an exercise while the Library pick flow is open. */
    private var pendingSectionId: String? = null

    val state: StateFlow<BuilderUiState> =
        combine(draft, exercises.observeAll(), undo, loaded) { workout, library, undoSnapshot, load ->
            uiState(workout, library.associateBy { it.id }, undoSnapshot != null, load)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            uiState(draft.value, emptyMap(), canUndo = false, loaded.value),
        )

    private fun uiState(workout: Workout, exercises: Map<String, Exercise>, canUndo: Boolean, load: Loaded) =
        BuilderUiState(
            workout = workout,
            exercises = exercises,
            saveBlockers = WorkoutDraft.saveBlockers(workout),
            canUndo = canUndo,
            isEditing = editingId != null,
            isLoading = !load.done,
            original = load.original,
        )

    // Non-delete edits clear the undo snapshot; delete edits set it.
    private fun edit(transform: (Workout) -> Workout) {
        undo.value = null
        draft.value = transform(draft.value)
    }

    private fun editWithUndo(transform: (Workout) -> Workout) {
        val before = draft.value
        draft.value = transform(before)
        undo.value = before
    }

    fun onRename(name: String) = edit { WorkoutDraft.rename(it, name) }
    fun onSetType(type: TrainingType) = edit { WorkoutDraft.setType(it, type) }

    fun onAddSection() = edit { WorkoutDraft.addSection(it, UUID.randomUUID().toString()) }
    fun onRemoveSection(sectionId: String) = editWithUndo { WorkoutDraft.removeSection(it, sectionId) }
    fun onMoveSection(sectionId: String, direction: MoveDirection) =
        edit { WorkoutDraft.moveSection(it, sectionId, direction) }
    fun onRenameSection(sectionId: String, name: String) =
        edit { WorkoutDraft.renameSection(it, sectionId, name) }
    fun onSetRounds(sectionId: String, rounds: Int) = edit { WorkoutDraft.setRounds(it, sectionId, rounds) }
    fun onSetRest(sectionId: String, slot: RestSlot, rest: Rest?) =
        edit { WorkoutDraft.setRest(it, sectionId, slot, rest) }

    /** Called by the section's "+ Add exercise"; the picked id comes back to [onExercisePicked]. */
    fun beginPick(sectionId: String) {
        pendingSectionId = sectionId
    }

    fun onExercisePicked(exerciseId: String) {
        val sectionId = pendingSectionId ?: return
        edit { WorkoutDraft.addItem(it, sectionId, UUID.randomUUID().toString(), exerciseId) }
        pendingSectionId = null
    }

    fun onRemoveItem(sectionId: String, itemId: String) =
        editWithUndo { WorkoutDraft.removeItem(it, sectionId, itemId) }
    fun onMoveItem(sectionId: String, itemId: String, direction: MoveDirection) =
        edit { WorkoutDraft.moveItem(it, sectionId, itemId, direction) }
    fun onSetItemMode(sectionId: String, itemId: String, mode: ItemMode) =
        edit { WorkoutDraft.setItemMode(it, sectionId, itemId, mode) }
    fun onSetDuration(sectionId: String, itemId: String, durationSec: Int) =
        edit { WorkoutDraft.setDuration(it, sectionId, itemId, durationSec) }
    fun onSetSetsReps(sectionId: String, itemId: String, sets: Int, reps: Int) =
        edit { WorkoutDraft.setSetsReps(it, sectionId, itemId, sets, reps) }
    fun onSetWeight(sectionId: String, itemId: String, weightKg: Float?) =
        edit { WorkoutDraft.setWeight(it, sectionId, itemId, weightKg) }
    fun onSetNote(sectionId: String, itemId: String, note: String?) =
        edit { WorkoutDraft.setNote(it, sectionId, itemId, note) }

    fun onUndo() {
        undo.value?.let { draft.value = it }
        undo.value = null
    }

    fun onUndoExpired() {
        undo.value = null
    }

    /** Persists the draft and calls [onSaved] on the main thread once written. */
    fun save(onSaved: () -> Unit) {
        val workout = draft.value
        if (!WorkoutDraft.canSave(workout)) return
        viewModelScope.launch {
            workouts.save(workout)
            onSaved()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkoutApp
                // The Builder route's optional nav argument arrives through SavedStateHandle.
                BuilderViewModel(app.workouts, app.exercises, createSavedStateHandle().get<String>("workoutId"))
            }
        }
    }
}
