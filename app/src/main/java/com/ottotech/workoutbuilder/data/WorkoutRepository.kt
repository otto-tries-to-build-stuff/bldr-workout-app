package com.ottotech.workoutbuilder.data

import com.ottotech.workoutbuilder.data.db.WorkoutDao
import com.ottotech.workoutbuilder.data.db.WorkoutEntity
import com.ottotech.workoutbuilder.data.seed.WorkoutSeed
import com.ottotech.workoutbuilder.domain.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class WorkoutRepository(private val dao: WorkoutDao) {

    /**
     * Fully hydrated workouts, sections and items.
     *
     * Home needs section contents to count exercises and estimate duration, so headers
     * alone are not enough. Re-loading every workout on each emission is cheap at this
     * scale — a personal app holds a handful of workouts, not thousands.
     *
     * Note the invalidation boundary: Room re-emits when the `workouts` table changes, not
     * when `sections` or `workout_items` change on their own. That is correct here because
     * [save] always writes the workout row too, so every edit that matters trips it.
     */
    fun observeAll(): Flow<List<Workout>> =
        // hydrate, not load: observeWorkouts already handed us the rows, so re-querying
        // each one by id would be a wasted round trip per workout per emission.
        dao.observeWorkouts().map { rows -> rows.map { hydrate(it) } }

    /** Fully hydrated, ready for SegmentCompiler. */
    suspend fun load(id: String): Workout? =
        dao.getWorkout(id)?.let { hydrate(it) }

    private suspend fun hydrate(workout: WorkoutEntity): Workout {
        val sections = dao.getSections(workout.id)
        val itemsBySection = dao.getItems(sections.map { it.id }).groupBy { it.sectionId }
        return workout.toDomain(
            sections.map { section ->
                section.toDomain(itemsBySection[section.id].orEmpty().map { it.toDomain() })
            },
        )
    }

    suspend fun save(workout: Workout) {
        val sections = workout.sections.mapIndexed { i, section -> section.toEntity(workout.id, i) }
        val items = workout.sections.flatMap { section ->
            section.items.mapIndexed { i, item -> item.toEntity(section.id, i) }
        }
        dao.upsertFull(workout.toEntity(), sections, items)
    }

    /**
     * Writes the sample workouts on first launch only.
     *
     * Gated on a preference rather than on "is the table empty" so that deleting the sample
     * workout keeps it deleted instead of resurrecting it on the next cold start.
     */
    suspend fun seedSampleIfNeeded(prefs: AppPrefs) {
        if (prefs.sampleWorkoutSeeded) return
        WorkoutSeed.workouts.forEach { save(it) }
        prefs.sampleWorkoutSeeded = true
    }

    suspend fun delete(id: String) = dao.deleteWorkout(id)

    /** Records when a workout was done, for the Home card's last-done line. Callerless until Plan 1D wires up execution. */
    suspend fun markPerformed(id: String) = dao.markPerformed(id, Instant.now().toEpochMilli())
}
