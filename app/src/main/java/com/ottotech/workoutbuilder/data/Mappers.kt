package com.ottotech.workoutbuilder.data

import com.ottotech.workoutbuilder.data.db.ExerciseEntity
import com.ottotech.workoutbuilder.data.db.ExerciseTagEntity
import com.ottotech.workoutbuilder.data.db.SectionEntity
import com.ottotech.workoutbuilder.data.db.WorkoutEntity
import com.ottotech.workoutbuilder.data.db.WorkoutItemEntity
import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.ItemMode
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.domain.Rest
import com.ottotech.workoutbuilder.domain.Section
import com.ottotech.workoutbuilder.domain.Tag
import com.ottotech.workoutbuilder.domain.TrainingType
import com.ottotech.workoutbuilder.domain.Workout
import com.ottotech.workoutbuilder.domain.WorkoutItem
import java.time.Instant

/**
 * Entity ↔ domain translation. Kept out of both layers so the domain model has no
 * persistence concerns and the entities have no behaviour.
 */

fun Exercise.toEntity() = ExerciseEntity(
    id = id,
    name = name,
    primaryMuscle = primaryMuscle.name,
    demoQuery = demoQuery,
    demoUrl = demoUrl,
    isCustom = isCustom,
)

fun Exercise.toTagEntities(): List<ExerciseTagEntity> =
    tags.map { ExerciseTagEntity(exerciseId = id, facet = it.facet.name, value = it.value) }

fun ExerciseEntity.toDomain(tags: List<ExerciseTagEntity>) = Exercise(
    id = id,
    name = name,
    primaryMuscle = Muscle.valueOf(primaryMuscle),
    tags = tags.map { Tag(Facet.valueOf(it.facet), it.value) }.toSet(),
    demoQuery = demoQuery,
    demoUrl = demoUrl,
    isCustom = isCustom,
)

// ─── Rest is stored as a mode string plus optional seconds ───

fun Rest?.mode(): String? = when (this) {
    is Rest.Timed -> "TIMED"
    Rest.Manual -> "MANUAL"
    null -> null
}

fun Rest?.seconds(): Int? = (this as? Rest.Timed)?.seconds

fun restFrom(mode: String?, seconds: Int?): Rest? = when (mode) {
    "TIMED" -> Rest.Timed(seconds ?: 0)
    "MANUAL" -> Rest.Manual
    else -> null
}

// ─── Workout ───

fun Workout.toEntity() = WorkoutEntity(
    id = id,
    name = name,
    type = type.name,
    createdAt = createdAt.toEpochMilli(),
    lastPerformedAt = lastPerformedAt?.toEpochMilli(),
)

fun WorkoutEntity.toDomain(sections: List<Section>) = Workout(
    id = id,
    name = name,
    type = TrainingType.valueOf(type),
    createdAt = Instant.ofEpochMilli(createdAt),
    lastPerformedAt = lastPerformedAt?.let { Instant.ofEpochMilli(it) },
    sections = sections,
)

fun Section.toEntity(workoutId: String, orderIndex: Int) = SectionEntity(
    id = id,
    workoutId = workoutId,
    orderIndex = orderIndex,
    name = name,
    rounds = rounds,
    restBetweenExercisesMode = restBetweenExercises.mode(),
    restBetweenExercisesSec = restBetweenExercises.seconds(),
    restBetweenRoundsMode = restBetweenRounds.mode(),
    restBetweenRoundsSec = restBetweenRounds.seconds(),
    restAfterSectionMode = restAfterSection.mode(),
    restAfterSectionSec = restAfterSection.seconds(),
)

fun SectionEntity.toDomain(items: List<WorkoutItem>) = Section(
    id = id,
    name = name,
    rounds = rounds,
    restBetweenExercises = restFrom(restBetweenExercisesMode, restBetweenExercisesSec),
    restBetweenRounds = restFrom(restBetweenRoundsMode, restBetweenRoundsSec),
    restAfterSection = restFrom(restAfterSectionMode, restAfterSectionSec),
    items = items,
)

fun WorkoutItem.toEntity(sectionId: String, orderIndex: Int) = WorkoutItemEntity(
    id = id,
    sectionId = sectionId,
    orderIndex = orderIndex,
    exerciseId = exerciseId,
    mode = mode.name,
    durationSec = durationSec,
    sets = sets,
    reps = reps,
    weightKg = weightKg,
    note = note,
)

fun WorkoutItemEntity.toDomain() = WorkoutItem(
    id = id,
    exerciseId = exerciseId,
    mode = ItemMode.valueOf(mode),
    durationSec = durationSec,
    sets = sets,
    reps = reps,
    weightKg = weightKg,
    note = note,
)
