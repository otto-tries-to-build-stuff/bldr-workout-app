package com.ottotech.workoutbuilder.data

import com.ottotech.workoutbuilder.data.db.WorkoutDao
import com.ottotech.workoutbuilder.domain.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class WorkoutRepository(private val dao: WorkoutDao) {

    /**
     * Headers only — [Workout.sections] is empty. Home only needs names and metadata,
     * and hydrating every section for a list would be wasteful. Use [load] to run one.
     */
    fun observeAll(): Flow<List<Workout>> =
        dao.observeWorkouts().map { rows -> rows.map { it.toDomain(emptyList()) } }

    /** Fully hydrated, ready for SegmentCompiler. */
    suspend fun load(id: String): Workout? {
        val workout = dao.getWorkout(id) ?: return null
        val sections = dao.getSections(id)
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

    suspend fun delete(id: String) = dao.deleteWorkout(id)

    /** Drives "Up next" selection on Home. */
    suspend fun markPerformed(id: String) = dao.markPerformed(id, Instant.now().toEpochMilli())
}
