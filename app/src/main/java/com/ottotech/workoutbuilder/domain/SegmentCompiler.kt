package com.ottotech.workoutbuilder.domain

/**
 * Flattens a [Workout] into the ordered list of steps the execution screen runs:
 * rounds expanded, rests inserted, exercise metadata denormalised onto each segment.
 *
 * Mirrors `buildSegments()` in the Court Speed reference web app. This is the single
 * most correctness-critical piece of the app — a bug here silently corrupts every
 * workout — which is why it is pure, Android-free, and covered test-first.
 *
 * Rest suppression rules (spec §4):
 * - no inter-exercise rest after the final item of a round
 * - no round rest after the final round of a section
 * - no section rest after the final section
 * - a null rest emits nothing, not a zero-length segment
 * - an empty section contributes nothing at all, including its rests
 */
object SegmentCompiler {

    fun compile(workout: Workout, exercises: Map<String, Exercise>): List<Segment> {
        val out = mutableListOf<Segment>()

        workout.sections.forEachIndexed { sectionIndex, section ->
            if (section.items.isEmpty()) return@forEachIndexed

            val rounds = section.rounds.coerceAtLeast(1)
            val isLastSection = sectionIndex == workout.sections.lastIndex

            repeat(rounds) { roundIndex ->
                val round = roundIndex + 1
                val isLastRound = round == rounds
                val label = labelFor(section, round, rounds)

                section.items.forEachIndexed { itemIndex, item ->
                    val exercise = requireNotNull(exercises[item.exerciseId]) {
                        "Unknown exerciseId '${item.exerciseId}' in section '${section.name}'"
                    }
                    out += workSegment(item, exercise, label)

                    if (itemIndex != section.items.lastIndex) {
                        section.restBetweenExercises?.let { out += restSegment(it, label) }
                    }
                }

                if (!isLastRound) {
                    section.restBetweenRounds?.let { out += restSegment(it, label) }
                }
            }

            if (!isLastSection) {
                section.restAfterSection?.let {
                    out += restSegment(it, labelFor(section, rounds, rounds))
                }
            }
        }

        return out.mapIndexed { i, segment -> segment.copy(index = i, total = out.size) }
    }

    private fun labelFor(section: Section, round: Int, rounds: Int): String {
        val name = section.name.uppercase()
        return if (rounds > 1) "$name · ROUND $round/$rounds" else name
    }

    private fun workSegment(item: WorkoutItem, exercise: Exercise, label: String) = Segment(
        kind = SegmentKind.WORK,
        mode = item.mode,
        exerciseName = exercise.name,
        label = label,
        primaryMuscle = exercise.primaryMuscle,
        equipmentLabel = exercise.equipmentLabel,
        note = item.note,
        demoQuery = exercise.demoQuery,
        demoUrl = exercise.demoUrl,
        durationSec = if (item.mode == ItemMode.TIMED) item.durationSec else null,
        sets = item.sets,
        reps = item.reps,
        weightKg = item.weightKg,
    )

    private fun restSegment(rest: Rest, label: String) = Segment(
        kind = SegmentKind.REST,
        mode = if (rest is Rest.Timed) ItemMode.TIMED else ItemMode.MANUAL,
        exerciseName = "Rest",
        label = label,
        durationSec = (rest as? Rest.Timed)?.seconds,
    )
}
