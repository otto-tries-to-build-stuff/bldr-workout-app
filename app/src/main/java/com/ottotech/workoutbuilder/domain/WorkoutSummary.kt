package com.ottotech.workoutbuilder.domain

import java.time.Instant
import java.time.temporal.ChronoUnit

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
    /** `10 exercises · HIIT` — the Home card's second line. Minutes get their own slot. */
    val shortMeta: String
        get() {
            val noun = if (exerciseCount == 1) "exercise" else "exercises"
            return "$exerciseCount $noun · ${type.label}"
        }

    /** `37 MIN` — right-aligned on the Home card. Carries its unit; the old bare badge did not. */
    val minutesLabel: String get() = "$estimatedMinutes MIN"

    /**
     * `Never done` / `Today` / `Yesterday` / `4 days ago` / `3 weeks ago` / `2 months ago`.
     *
     * [now] is a parameter rather than `Instant.now()` so this stays pure and testable —
     * the same reason `WorkoutDraft` takes its ids from the caller.
     *
     * Until Plan 1D calls `WorkoutRepository.markPerformed`, every workout returns
     * "Never done". That is accurate, not a placeholder.
     */
    fun lastDoneLine(now: Instant): String {
        val performed = lastPerformedAt ?: return "Never done"
        val days = ChronoUnit.DAYS.between(performed, now)
        return when {
            days <= 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7L -> "$days days ago"
            days < 60L -> {
                val weeks = days / 7L
                if (weeks == 1L) "1 week ago" else "$weeks weeks ago"
            }
            else -> {
                val months = days / 30L
                if (months == 1L) "1 month ago" else "$months months ago"
            }
        }
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

    /**
     * Home's card order: newest created first, so the workout you just built is at the top.
     *
     * This replaces the recommendation logic deleted 2026-07-26, which ranked by
     * `lastPerformedAt`. Nothing ever writes that field — `markPerformed` has no caller until
     * Plan 1D — so the old rule always fell through to its "newest created" tiebreak while
     * presenting itself as a recommendation. Home now offers two plain options, choose or
     * build, and does not rank.
     */
    fun sortedForHome(summaries: List<WorkoutSummary>): List<WorkoutSummary> =
        summaries.sortedByDescending { it.createdAt }
}
