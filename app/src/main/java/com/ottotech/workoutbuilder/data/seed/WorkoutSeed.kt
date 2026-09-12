package com.ottotech.workoutbuilder.data.seed

import com.ottotech.workoutbuilder.domain.ItemMode
import com.ottotech.workoutbuilder.domain.Rest
import com.ottotech.workoutbuilder.domain.Section
import com.ottotech.workoutbuilder.domain.TrainingType
import com.ottotech.workoutbuilder.domain.Workout
import com.ottotech.workoutbuilder.domain.WorkoutItem
import java.time.Instant

/**
 * The workouts a fresh install starts with.
 *
 * Court Speed is rebuilt from `web-app-reference/HIIT Workout.html` and mirrors that file's
 * `buildSegments()`. It is both the app's sample content and its acceptance fixture: if the
 * compiler turns this into the same 40-segment sequence the web app produces, the data model
 * can express a real workout. See `WorkoutSeedTest`.
 *
 * Every `exerciseId` below must be a row in [ExerciseSeed] — the test enforces that too.
 *
 * `createdAt` is EPOCH rather than the install time so the object stays a constant and the
 * test stays deterministic. Nothing renders a workout's creation date directly, but Home
 * orders its list newest-created first via `WorkoutSummarizer.sortedForHome`, so anything
 * the user builds later correctly appears above this seed.
 */
object WorkoutSeed {

    const val COURT_SPEED_ID = "court-speed"

    private fun timed(id: String, exId: String, sec: Int, note: String? = null) =
        WorkoutItem(id = id, exerciseId = exId, mode = ItemMode.TIMED, durationSec = sec, note = note)

    val courtSpeed = Workout(
        id = COURT_SPEED_ID,
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

    val workouts: List<Workout> = listOf(courtSpeed)
}
