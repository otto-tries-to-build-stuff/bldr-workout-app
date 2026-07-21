package com.ottotech.workoutbuilder.fixtures

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
 * The Court Speed workout from `web-app-reference/HIIT Workout.html`, rebuilt in the
 * data model. Mirrors that file's `buildSegments()`.
 *
 * This is the acceptance fixture: if the compiler turns this into the same segment
 * sequence the web app produces, the model can express a real workout.
 */
object CourtSpeed {

    private fun ex(id: String, name: String, muscle: Muscle, url: String? = null) = Exercise(
        id = id,
        name = name,
        primaryMuscle = muscle,
        tags = setOf(Tag(Facet.TYPE, "HIIT"), Tag(Facet.EQUIPMENT, "Bodyweight")),
        demoQuery = "${name.lowercase()} proper form",
        demoUrl = url,
    )

    val exercises: Map<String, Exercise> = listOf(
        ex("leg-swings", "Leg Swings", Muscle.MOBILITY, "https://www.youtube.com/shorts/3l31E2cMGMk"),
        ex("walking-lunge-twist", "Walking Lunge + Twist", Muscle.LEGS, "https://www.youtube.com/shorts/2crIg2iqi8w"),
        ex("a-skips", "A-Skips", Muscle.CARDIO, "https://www.youtube.com/shorts/fSZUFBbK7CE"),
        ex("build-up-strides", "Build-Up Strides", Muscle.CARDIO, "https://www.youtube.com/watch?v=IQrbQMPbTtU"),
        ex("lateral-shuffle", "Lateral Shuffle", Muscle.LEGS, "https://www.youtube.com/shorts/iQjCIghvheI"),
        ex("skater-bounds", "Skater Bounds", Muscle.LEGS, "https://www.youtube.com/shorts/7-Qfb881k7o"),
        ex("split-squat-jumps", "Split Squat Jumps", Muscle.LEGS, "https://www.youtube.com/shorts/e6s05vAhAZo"),
        ex("squat-jumps-180", "180° Squat Jumps", Muscle.LEGS, "https://www.youtube.com/watch?v=_-Q5ywo24_s"),
        ex("sprint", "Sprint", Muscle.CARDIO),
        ex("flyer", "Flyer", Muscle.CARDIO),
    ).associateBy { it.id }

    private fun timed(id: String, exId: String, sec: Int, note: String? = null) =
        WorkoutItem(id = id, exerciseId = exId, mode = ItemMode.TIMED, durationSec = sec, note = note)

    val workout = Workout(
        id = "court-speed",
        name = "Court Speed",
        type = TrainingType.HIIT,
        createdAt = Instant.EPOCH,
        sections = listOf(
            Section(
                id = "warm-prep",
                name = "Warm Prep",
                items = listOf(
                    timed("wp1", "leg-swings", 45, "10 each direction · per leg"),
                    timed("wp2", "walking-lunge-twist", 45, "8 each side"),
                    timed("wp3", "a-skips", 45, "20m × 2"),
                    timed("wp4", "build-up-strides", 45, "2 × ~70% pace"),
                ),
            ),
            Section(
                id = "block-a",
                name = "Block A · Tennis Circuit",
                rounds = 3,
                restBetweenExercises = Rest.Timed(15),
                restBetweenRounds = Rest.Timed(60),
                // The web app's "Reset · walk to sprint start" transition.
                restAfterSection = Rest.Timed(60),
                items = listOf(
                    timed("ba1", "lateral-shuffle", 30, "~5m apart · touch ground each end"),
                    timed("ba2", "skater-bounds", 30, "Explosive · stick each landing"),
                    timed("ba3", "split-squat-jumps", 30, "Alternating legs"),
                    timed("ba4", "squat-jumps-180", 30, "Alternate rotation direction"),
                ),
            ),
            Section(
                id = "block-b",
                name = "Block B · Sprints",
                rounds = 6,
                // Walk-back recovery: tap when ready, not a timer.
                restBetweenRounds = Rest.Manual,
                items = listOf(
                    WorkoutItem(
                        "bb1", "sprint", ItemMode.MANUAL,
                        note = "60–80m @ 90% · drive knees, stay tall",
                    ),
                ),
            ),
            Section(
                id = "bonus",
                name = "Bonus · Optional",
                items = listOf(
                    WorkoutItem(
                        "bo1", "flyer", ItemMode.MANUAL,
                        note = "40m all-out · if you have it",
                    ),
                ),
            ),
        ),
    )
}
