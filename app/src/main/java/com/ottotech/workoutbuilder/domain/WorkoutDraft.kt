package com.ottotech.workoutbuilder.domain

import java.time.Instant

/** Which of a section's three rest slots an edit targets. */
enum class RestSlot { BETWEEN_EXERCISES, BETWEEN_ROUNDS, AFTER_SECTION }

/** Reorder direction for the Builder's move controls. */
enum class MoveDirection { UP, DOWN }

/**
 * Pure edits on a workout draft. The Builder's ViewModel holds a `MutableStateFlow<Workout>`
 * and does nothing but call these; every rule that could be wrong — round clamping, rest
 * placement, mode defaults, save validation — lives here where a JVM test can pin it in
 * milliseconds, exactly like `ExerciseFilter`'s FilterState transitions.
 *
 * IDs for new sections and items are passed in, not generated here, so these functions are
 * deterministic and testable. The ViewModel supplies `UUID.randomUUID().toString()`.
 */
object WorkoutDraft {

    private const val DEFAULT_DURATION_SEC = 30
    private const val DEFAULT_SETS = 3
    private const val DEFAULT_REPS = 10

    fun newWorkout(id: String, createdAt: Instant): Workout =
        Workout(id = id, name = "", type = TrainingType.STRENGTH, createdAt = createdAt)

    fun rename(workout: Workout, name: String): Workout = workout.copy(name = name)

    fun setType(workout: Workout, type: TrainingType): Workout = workout.copy(type = type)

    // ---- sections ----

    fun addSection(workout: Workout, sectionId: String): Workout =
        workout.copy(
            sections = workout.sections + Section(id = sectionId, name = "Section ${workout.sections.size + 1}"),
        )

    fun removeSection(workout: Workout, sectionId: String): Workout =
        workout.copy(sections = workout.sections.filterNot { it.id == sectionId })

    fun moveSection(workout: Workout, sectionId: String, direction: MoveDirection): Workout {
        val index = workout.sections.indexOfFirst { it.id == sectionId }
        return workout.copy(sections = workout.sections.movedOrSame(index, direction))
    }

    fun renameSection(workout: Workout, sectionId: String, name: String): Workout =
        mapSection(workout, sectionId) { it.copy(name = name) }

    fun setRounds(workout: Workout, sectionId: String, rounds: Int): Workout =
        mapSection(workout, sectionId) { it.copy(rounds = rounds.coerceAtLeast(1)) }

    fun setRest(workout: Workout, sectionId: String, slot: RestSlot, rest: Rest?): Workout =
        mapSection(workout, sectionId) { section ->
            when (slot) {
                RestSlot.BETWEEN_EXERCISES -> section.copy(restBetweenExercises = rest)
                RestSlot.BETWEEN_ROUNDS -> section.copy(restBetweenRounds = rest)
                RestSlot.AFTER_SECTION -> section.copy(restAfterSection = rest)
            }
        }

    // ---- items ----

    fun addItem(workout: Workout, sectionId: String, itemId: String, exerciseId: String): Workout =
        mapSection(workout, sectionId) { section ->
            section.copy(
                items = section.items + WorkoutItem(
                    id = itemId, exerciseId = exerciseId,
                    mode = ItemMode.TIMED, durationSec = DEFAULT_DURATION_SEC,
                ),
            )
        }

    fun removeItem(workout: Workout, sectionId: String, itemId: String): Workout =
        mapSection(workout, sectionId) { it.copy(items = it.items.filterNot { item -> item.id == itemId }) }

    fun moveItem(workout: Workout, sectionId: String, itemId: String, direction: MoveDirection): Workout =
        mapSection(workout, sectionId) { section ->
            val index = section.items.indexOfFirst { it.id == itemId }
            section.copy(items = section.items.movedOrSame(index, direction))
        }

    fun setItemMode(workout: Workout, sectionId: String, itemId: String, mode: ItemMode): Workout =
        mapItem(workout, sectionId, itemId) { item ->
            when (mode) {
                // Fill in the fields the new mode requires so a mode switch never leaves the
                // item in a state saveBlockers would reject.
                ItemMode.TIMED -> item.copy(mode = mode, durationSec = item.durationSec ?: DEFAULT_DURATION_SEC)
                ItemMode.REPS -> item.copy(
                    mode = mode,
                    sets = item.sets ?: DEFAULT_SETS,
                    reps = item.reps ?: DEFAULT_REPS,
                )
                ItemMode.MANUAL -> item.copy(mode = mode)
            }
        }

    fun setDuration(workout: Workout, sectionId: String, itemId: String, durationSec: Int): Workout =
        mapItem(workout, sectionId, itemId) { it.copy(durationSec = durationSec) }

    fun setSetsReps(workout: Workout, sectionId: String, itemId: String, sets: Int, reps: Int): Workout =
        mapItem(workout, sectionId, itemId) { it.copy(sets = sets, reps = reps) }

    fun setWeight(workout: Workout, sectionId: String, itemId: String, weightKg: Float?): Workout =
        mapItem(workout, sectionId, itemId) { it.copy(weightKg = weightKg) }

    fun setNote(workout: Workout, sectionId: String, itemId: String, note: String?): Workout =
        mapItem(workout, sectionId, itemId) { it.copy(note = note?.takeIf { text -> text.isNotBlank() }) }

    // ---- validation ----

    /**
     * Human-readable reasons the workout cannot be saved yet, empty when it is ready. The
     * Builder disables SAVE and shows the first blocker while this is non-empty.
     */
    fun saveBlockers(workout: Workout): List<String> = buildList {
        if (workout.name.isBlank()) add("Name your workout")
        val items = workout.sections.flatMap { it.items }
        if (items.isEmpty()) add("Add at least one exercise")
        if (items.any { it.mode == ItemMode.TIMED && (it.durationSec == null || it.durationSec <= 0) }) {
            add("Set a duration for every timed exercise")
        }
        if (items.any { it.mode == ItemMode.REPS && (it.sets == null || it.sets <= 0 || it.reps == null || it.reps <= 0) }) {
            add("Set sets and reps for every rep exercise")
        }
    }

    fun canSave(workout: Workout): Boolean = saveBlockers(workout).isEmpty()

    // ---- helpers ----

    private fun mapSection(workout: Workout, sectionId: String, transform: (Section) -> Section): Workout =
        workout.copy(sections = workout.sections.map { if (it.id == sectionId) transform(it) else it })

    private fun mapItem(
        workout: Workout,
        sectionId: String,
        itemId: String,
        transform: (WorkoutItem) -> WorkoutItem,
    ): Workout = mapSection(workout, sectionId) { section ->
        section.copy(items = section.items.map { if (it.id == itemId) transform(it) else it })
    }

    /** Swap the element at [index] one step in [direction], or return the list unchanged. */
    private fun <T> List<T>.movedOrSame(index: Int, direction: MoveDirection): List<T> {
        if (index < 0) return this
        val target = when (direction) {
            MoveDirection.UP -> index - 1
            MoveDirection.DOWN -> index + 1
        }
        if (target !in indices) return this
        return toMutableList().apply {
            val moved = removeAt(index)
            add(target, moved)
        }
    }
}
