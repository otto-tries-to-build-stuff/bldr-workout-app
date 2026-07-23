package com.ottotech.workoutbuilder.domain

import java.time.Instant

/**
 * Everything the Home screen shows about a workout, so the screen never re-walks
 * sections or re-runs the compiler while composing.
 */
data class WorkoutSummary(
    val id: String,
    val name: String,
    val type: TrainingType,
    val exerciseCount: Int,
    val estimatedMinutes: Int,
    val createdAt: Instant,
    val lastPerformedAt: Instant?,
) {
    /** `10 exercises · 22 min · HIIT` — the hero and row meta line, spec §6.1. */
    val metaLine: String
        get() {
            val noun = if (exerciseCount == 1) "exercise" else "exercises"
            return "$exerciseCount $noun · $estimatedMinutes min · ${type.label}"
        }
}

object WorkoutSummarizer {

    fun summarize(workout: Workout, exercises: Map<String, Exercise>): WorkoutSummary {
        // SegmentCompiler throws on an unresolvable exerciseId, which is right for
        // execution — you cannot run a workout with a hole in it. Home is a list, so it
        // drops the dangling item and shows what is left rather than taking the screen
        // down over a deleted custom exercise.
        val resolvable = workout.copy(
            sections = workout.sections.map { section ->
                section.copy(items = section.items.filter { it.exerciseId in exercises })
            },
        )
        val segments = SegmentCompiler.compile(resolvable, exercises)
        return WorkoutSummary(
            id = workout.id,
            name = workout.name,
            type = workout.type,
            exerciseCount = resolvable.sections.sumOf { it.items.size },
            estimatedMinutes = DurationEstimator.estimateMinutes(segments),
            createdAt = workout.createdAt,
            lastPerformedAt = workout.lastPerformedAt,
        )
    }

    fun summarizeAll(workouts: List<Workout>, exercises: Map<String, Exercise>): List<WorkoutSummary> =
        workouts.map { summarize(it, exercises) }
}

object UpNext {

    /**
     * Spec §6.1: the workout performed longest ago.
     *
     * Never-performed workouts sort ahead of every performed one — nothing is more
     * overdue than a workout you have not done at all — and among those the most recently
     * created wins, because the one you just built is the one you meant to do next.
     */
    fun select(summaries: List<WorkoutSummary>): WorkoutSummary? {
        val neverPerformed = summaries.filter { it.lastPerformedAt == null }
        return if (neverPerformed.isNotEmpty()) {
            neverPerformed.maxByOrNull { it.createdAt }
        } else {
            summaries.minByOrNull { it.lastPerformedAt!! }
        }
    }
}
