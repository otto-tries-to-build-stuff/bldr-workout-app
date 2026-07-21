package com.ottotech.workoutbuilder.data

import com.ottotech.workoutbuilder.data.db.ExerciseDao
import com.ottotech.workoutbuilder.data.seed.ExerciseSeed
import com.ottotech.workoutbuilder.domain.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ExerciseRepository(private val dao: ExerciseDao) {

    /** Exercises joined with their tags, re-emitting whenever either table changes. */
    fun observeAll(): Flow<List<Exercise>> =
        combine(dao.observeAll(), dao.observeAllTags()) { exercises, tags ->
            val byExercise = tags.groupBy { it.exerciseId }
            exercises.map { it.toDomain(byExercise[it.id].orEmpty()) }
        }

    /**
     * Inserts any seed row not already present. Idempotent, and additive rather than
     * destructive — a future seed version can add exercises without touching the
     * user's custom ones or resetting demo URLs they have overridden.
     */
    suspend fun seedIfNeeded() {
        val existingIds = dao.getAll().map { it.id }.toSet()
        val missing = ExerciseSeed.exercises.filter { it.id !in existingIds }
        if (missing.isEmpty()) return
        dao.insertAll(
            exercises = missing.map { it.toEntity() },
            tags = missing.flatMap { it.toTagEntities() },
        )
    }

    suspend fun setDemoUrl(id: String, url: String?) = dao.setDemoUrl(id, url)

    suspend fun count(): Int = dao.count()
}
