package com.ottotech.workoutbuilder.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Storage shape. Enums and [java.time.Instant] are flattened to strings and longs here;
 * conversion lives in the mappers rather than in Room type converters, so the domain
 * model stays free of persistence concerns.
 */

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val primaryMuscle: String,
    val demoQuery: String,
    val demoUrl: String?,
    val isCustom: Boolean,
)

/**
 * Join table for faceted tags. Indexed on (facet, value) so Library filtering stays
 * fast without loading the whole library into memory.
 */
@Entity(
    tableName = "exercise_tags",
    primaryKeys = ["exerciseId", "facet", "value"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("facet", "value"), Index("exerciseId")],
)
data class ExerciseTagEntity(
    val exerciseId: String,
    val facet: String,
    val value: String,
)

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val createdAt: Long,
    val lastPerformedAt: Long?,
)

@Entity(
    tableName = "sections",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutId")],
)
data class SectionEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    val orderIndex: Int,
    val name: String,
    val rounds: Int,
    // Each Rest? is stored as a mode string plus optional seconds; null mode means no rest.
    val restBetweenExercisesMode: String?,
    val restBetweenExercisesSec: Int?,
    val restBetweenRoundsMode: String?,
    val restBetweenRoundsSec: Int?,
    val restAfterSectionMode: String?,
    val restAfterSectionSec: Int?,
)

@Entity(
    tableName = "workout_items",
    foreignKeys = [
        ForeignKey(
            entity = SectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sectionId")],
)
data class WorkoutItemEntity(
    @PrimaryKey val id: String,
    val sectionId: String,
    val orderIndex: Int,
    val exerciseId: String,
    val mode: String,
    val durationSec: Int?,
    val sets: Int?,
    val reps: Int?,
    val weightKg: Float?,
    val note: String?,
)
