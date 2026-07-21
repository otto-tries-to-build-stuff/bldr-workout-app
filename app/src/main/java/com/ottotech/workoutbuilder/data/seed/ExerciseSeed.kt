package com.ottotech.workoutbuilder.data.seed

import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.domain.Tag

/**
 * The starter exercise library.
 *
 * Correctness here is enforced by `ExerciseSeedTest` rather than by review: counts per
 * muscle group, slug format, controlled-vocabulary tags, and the eight Court Speed
 * exercises with their verified demo URLs.
 *
 * Tagging is the largest judgement surface — a wrong tag makes an exercise unfindable
 * by filter — so expect to correct these over time.
 */
object ExerciseSeed {

    /** Bump when rows are added so first-run seeding can top up an existing install. */
    const val VERSION = 1

    private fun e(
        id: String,
        name: String,
        muscle: Muscle,
        type: List<String>,
        equipment: List<String>,
        also: List<Muscle> = emptyList(),
        pattern: List<String> = emptyList(),
        url: String? = null,
    ) = Exercise(
        id = id,
        name = name,
        primaryMuscle = muscle,
        tags = buildSet {
            type.forEach { add(Tag(Facet.TYPE, it)) }
            equipment.forEach { add(Tag(Facet.EQUIPMENT, it)) }
            also.forEach { add(Tag(Facet.MUSCLE, it.label)) }
            pattern.forEach { add(Tag(Facet.PATTERN, it)) }
        },
        demoQuery = "$name proper form",
        demoUrl = url,
    )

    private val S = listOf("Strength")
    private val H = listOf("HIIT")
    private val M = listOf("Mobility")
    private val SH = listOf("Strength", "HIIT")

    val exercises: List<Exercise> = listOf(

        // ─────────── CHEST ───────────
        e("barbell-bench-press", "Barbell Bench Press", Muscle.CHEST, S, listOf("Barbell", "Bench"), listOf(Muscle.SHOULDERS, Muscle.ARMS), listOf("Push")),
        e("dumbbell-bench-press", "Dumbbell Bench Press", Muscle.CHEST, S, listOf("Dumbbell", "Bench"), listOf(Muscle.SHOULDERS, Muscle.ARMS), listOf("Push")),
        e("incline-barbell-press", "Incline Barbell Press", Muscle.CHEST, S, listOf("Barbell", "Bench"), listOf(Muscle.SHOULDERS), listOf("Push")),
        e("incline-dumbbell-press", "Incline Dumbbell Press", Muscle.CHEST, S, listOf("Dumbbell", "Bench"), listOf(Muscle.SHOULDERS), listOf("Push")),
        e("push-up", "Push-up", Muscle.CHEST, S, listOf("Bodyweight"), listOf(Muscle.CORE, Muscle.ARMS), listOf("Push")),
        e("wide-push-up", "Wide Push-up", Muscle.CHEST, S, listOf("Bodyweight"), listOf(Muscle.SHOULDERS), listOf("Push")),
        e("decline-push-up", "Decline Push-up", Muscle.CHEST, S, listOf("Bodyweight", "Bench"), listOf(Muscle.SHOULDERS), listOf("Push")),
        e("plyo-push-up", "Plyo Push-up", Muscle.CHEST, SH, listOf("Bodyweight"), listOf(Muscle.ARMS), listOf("Push", "Plyometric")),
        e("dumbbell-fly", "Dumbbell Fly", Muscle.CHEST, S, listOf("Dumbbell", "Bench"), listOf(Muscle.SHOULDERS), listOf("Push")),
        e("cable-crossover", "Cable Crossover", Muscle.CHEST, S, listOf("Machine"), listOf(Muscle.SHOULDERS), listOf("Push")),
        e("chest-dip", "Chest Dip", Muscle.CHEST, S, listOf("Bodyweight"), listOf(Muscle.ARMS, Muscle.SHOULDERS), listOf("Push")),
        e("machine-chest-press", "Machine Chest Press", Muscle.CHEST, S, listOf("Machine"), listOf(Muscle.ARMS), listOf("Push")),

        // ─────────── BACK ───────────
        e("pull-up", "Pull-up", Muscle.BACK, S, listOf("Bodyweight", "Pull-up bar"), listOf(Muscle.ARMS), listOf("Pull")),
        e("chin-up", "Chin-up", Muscle.BACK, S, listOf("Bodyweight", "Pull-up bar"), listOf(Muscle.ARMS), listOf("Pull")),
        e("lat-pulldown", "Lat Pulldown", Muscle.BACK, S, listOf("Machine"), listOf(Muscle.ARMS), listOf("Pull")),
        e("barbell-row", "Barbell Row", Muscle.BACK, S, listOf("Barbell"), listOf(Muscle.ARMS, Muscle.CORE), listOf("Pull")),
        e("dumbbell-row", "Dumbbell Row", Muscle.BACK, S, listOf("Dumbbell", "Bench"), listOf(Muscle.ARMS), listOf("Pull")),
        e("seated-cable-row", "Seated Cable Row", Muscle.BACK, S, listOf("Machine"), listOf(Muscle.ARMS), listOf("Pull")),
        e("t-bar-row", "T-Bar Row", Muscle.BACK, S, listOf("Barbell"), listOf(Muscle.ARMS), listOf("Pull")),
        e("inverted-row", "Inverted Row", Muscle.BACK, S, listOf("Bodyweight", "Pull-up bar"), listOf(Muscle.ARMS, Muscle.CORE), listOf("Pull")),
        e("face-pull", "Face Pull", Muscle.BACK, S, listOf("Bands"), listOf(Muscle.SHOULDERS), listOf("Pull")),
        e("single-arm-lat-pulldown", "Single-Arm Lat Pulldown", Muscle.BACK, S, listOf("Machine"), listOf(Muscle.ARMS, Muscle.CORE), listOf("Pull")),
        e("renegade-row", "Renegade Row", Muscle.BACK, SH, listOf("Dumbbell"), listOf(Muscle.CORE, Muscle.ARMS), listOf("Pull")),
        e("band-pull-apart", "Band Pull-Apart", Muscle.BACK, S, listOf("Bands"), listOf(Muscle.SHOULDERS), listOf("Pull")),
        e("superman", "Superman", Muscle.BACK, S, listOf("Bodyweight"), listOf(Muscle.CORE)),
        e("rack-pull", "Rack Pull", Muscle.BACK, S, listOf("Barbell"), listOf(Muscle.LEGS), listOf("Hinge")),

        // ─────────── LEGS ───────────
        e("back-squat", "Back Squat", Muscle.LEGS, S, listOf("Barbell"), listOf(Muscle.CORE), listOf("Squat")),
        e("front-squat", "Front Squat", Muscle.LEGS, S, listOf("Barbell"), listOf(Muscle.CORE), listOf("Squat")),
        e("goblet-squat", "Goblet Squat", Muscle.LEGS, S, listOf("Kettlebell"), listOf(Muscle.CORE), listOf("Squat")),
        e("air-squat", "Air Squat", Muscle.LEGS, SH, listOf("Bodyweight"), listOf(Muscle.CORE), listOf("Squat")),
        e("bulgarian-split-squat", "Bulgarian Split Squat", Muscle.LEGS, S, listOf("Dumbbell", "Bench"), listOf(Muscle.CORE), listOf("Lunge")),
        e("walking-lunge", "Walking Lunge", Muscle.LEGS, S, listOf("Dumbbell"), listOf(Muscle.CORE), listOf("Lunge")),
        e("reverse-lunge", "Reverse Lunge", Muscle.LEGS, S, listOf("Bodyweight"), listOf(Muscle.CORE), listOf("Lunge")),
        e("romanian-deadlift", "Romanian Deadlift", Muscle.LEGS, S, listOf("Barbell"), listOf(Muscle.BACK), listOf("Hinge")),
        e("deadlift", "Deadlift", Muscle.LEGS, S, listOf("Barbell"), listOf(Muscle.BACK, Muscle.CORE), listOf("Hinge")),
        e("leg-press", "Leg Press", Muscle.LEGS, S, listOf("Machine"), pattern = listOf("Squat")),
        e("leg-curl", "Leg Curl", Muscle.LEGS, S, listOf("Machine")),
        e("leg-extension", "Leg Extension", Muscle.LEGS, S, listOf("Machine")),
        e("calf-raise", "Calf Raise", Muscle.LEGS, S, listOf("Bodyweight")),
        e("kettlebell-swing", "Kettlebell Swing", Muscle.LEGS, SH, listOf("Kettlebell"), listOf(Muscle.BACK, Muscle.CORE), listOf("Hinge")),
        e("lateral-shuffle", "Lateral Shuffle", Muscle.LEGS, H, listOf("Bodyweight"), pattern = listOf("Plyometric"), url = "https://www.youtube.com/shorts/iQjCIghvheI"),
        e("skater-bounds", "Skater Bounds", Muscle.LEGS, H, listOf("Bodyweight"), listOf(Muscle.CORE), listOf("Plyometric"), url = "https://www.youtube.com/shorts/7-Qfb881k7o"),
        e("split-squat-jumps", "Split Squat Jumps", Muscle.LEGS, H, listOf("Bodyweight"), pattern = listOf("Plyometric", "Lunge"), url = "https://www.youtube.com/shorts/e6s05vAhAZo"),
        e("squat-jumps-180", "180° Squat Jumps", Muscle.LEGS, H, listOf("Bodyweight"), listOf(Muscle.CORE), listOf("Plyometric", "Squat"), url = "https://www.youtube.com/watch?v=_-Q5ywo24_s"),
        e("walking-lunge-twist", "Walking Lunge + Twist", Muscle.LEGS, M, listOf("Bodyweight"), listOf(Muscle.CORE), listOf("Lunge"), url = "https://www.youtube.com/shorts/2crIg2iqi8w"),
        e("box-jump", "Box Jump", Muscle.LEGS, SH, listOf("Bodyweight"), listOf(Muscle.CORE), listOf("Plyometric", "Squat")),

        // ─────────── SHOULDERS ───────────
        e("overhead-press", "Overhead Press", Muscle.SHOULDERS, S, listOf("Barbell"), listOf(Muscle.ARMS, Muscle.CORE), listOf("Push")),
        e("dumbbell-shoulder-press", "Dumbbell Shoulder Press", Muscle.SHOULDERS, S, listOf("Dumbbell"), listOf(Muscle.ARMS), listOf("Push")),
        e("arnold-press", "Arnold Press", Muscle.SHOULDERS, S, listOf("Dumbbell"), listOf(Muscle.ARMS), listOf("Push")),
        e("lateral-raise", "Lateral Raise", Muscle.SHOULDERS, S, listOf("Dumbbell")),
        e("front-raise", "Front Raise", Muscle.SHOULDERS, S, listOf("Dumbbell")),
        e("rear-delt-fly", "Rear Delt Fly", Muscle.SHOULDERS, S, listOf("Dumbbell"), listOf(Muscle.BACK), listOf("Pull")),
        e("upright-row", "Upright Row", Muscle.SHOULDERS, S, listOf("Barbell"), listOf(Muscle.ARMS), listOf("Pull")),
        e("pike-push-up", "Pike Push-up", Muscle.SHOULDERS, S, listOf("Bodyweight"), listOf(Muscle.ARMS), listOf("Push")),
        e("handstand-hold", "Handstand Hold", Muscle.SHOULDERS, S, listOf("Bodyweight"), listOf(Muscle.CORE)),
        e("band-shoulder-press", "Band Shoulder Press", Muscle.SHOULDERS, S, listOf("Bands"), listOf(Muscle.ARMS), listOf("Push")),

        // ─────────── ARMS ───────────
        e("barbell-curl", "Barbell Curl", Muscle.ARMS, S, listOf("Barbell"), pattern = listOf("Pull")),
        e("dumbbell-curl", "Dumbbell Curl", Muscle.ARMS, S, listOf("Dumbbell"), pattern = listOf("Pull")),
        e("hammer-curl", "Hammer Curl", Muscle.ARMS, S, listOf("Dumbbell"), pattern = listOf("Pull")),
        e("preacher-curl", "Preacher Curl", Muscle.ARMS, S, listOf("Barbell", "Bench"), pattern = listOf("Pull")),
        e("triceps-pushdown", "Triceps Pushdown", Muscle.ARMS, S, listOf("Machine"), pattern = listOf("Push")),
        e("skull-crusher", "Skull Crusher", Muscle.ARMS, S, listOf("Barbell", "Bench"), pattern = listOf("Push")),
        e("overhead-triceps-extension", "Overhead Triceps Extension", Muscle.ARMS, S, listOf("Dumbbell"), listOf(Muscle.SHOULDERS), listOf("Push")),
        e("close-grip-bench-press", "Close-Grip Bench Press", Muscle.ARMS, S, listOf("Barbell", "Bench"), listOf(Muscle.CHEST), listOf("Push")),
        e("bench-dip", "Bench Dip", Muscle.ARMS, S, listOf("Bodyweight", "Bench"), listOf(Muscle.SHOULDERS), listOf("Push")),

        // ─────────── CORE ───────────
        e("plank", "Plank", Muscle.CORE, S, listOf("Bodyweight"), listOf(Muscle.SHOULDERS)),
        e("side-plank", "Side Plank", Muscle.CORE, S, listOf("Bodyweight"), listOf(Muscle.SHOULDERS)),
        e("dead-bug", "Dead Bug", Muscle.CORE, S, listOf("Bodyweight")),
        e("bird-dog", "Bird Dog", Muscle.CORE, listOf("Strength", "Mobility"), listOf("Bodyweight"), listOf(Muscle.BACK)),
        e("hanging-leg-raise", "Hanging Leg Raise", Muscle.CORE, S, listOf("Bodyweight", "Pull-up bar")),
        e("russian-twist", "Russian Twist", Muscle.CORE, S, listOf("Bodyweight")),
        e("mountain-climbers", "Mountain Climbers", Muscle.CORE, SH, listOf("Bodyweight"), listOf(Muscle.SHOULDERS, Muscle.CARDIO), listOf("Plyometric")),
        e("bicycle-crunch", "Bicycle Crunch", Muscle.CORE, S, listOf("Bodyweight")),
        e("hollow-hold", "Hollow Hold", Muscle.CORE, S, listOf("Bodyweight")),
        e("cable-woodchop", "Cable Woodchop", Muscle.CORE, S, listOf("Machine"), listOf(Muscle.SHOULDERS), listOf("Pull")),
        e("v-up", "V-Up", Muscle.CORE, S, listOf("Bodyweight")),
        e("farmers-carry", "Farmer's Carry", Muscle.CORE, S, listOf("Dumbbell"), listOf(Muscle.BACK, Muscle.ARMS), listOf("Carry")),

        // ─────────── CARDIO ───────────
        e("a-skips", "A-Skips", Muscle.CARDIO, H, listOf("Bodyweight"), listOf(Muscle.LEGS), listOf("Plyometric"), url = "https://www.youtube.com/shorts/fSZUFBbK7CE"),
        e("build-up-strides", "Build-Up Strides", Muscle.CARDIO, H, listOf("Bodyweight"), listOf(Muscle.LEGS), url = "https://www.youtube.com/watch?v=IQrbQMPbTtU"),
        e("burpee", "Burpee", Muscle.CARDIO, H, listOf("Bodyweight"), listOf(Muscle.FULL_BODY), listOf("Plyometric")),
        e("high-knees", "High Knees", Muscle.CARDIO, H, listOf("Bodyweight"), listOf(Muscle.LEGS), listOf("Plyometric")),
        e("jumping-jacks", "Jumping Jacks", Muscle.CARDIO, H, listOf("Bodyweight"), listOf(Muscle.FULL_BODY), listOf("Plyometric")),
        e("sprint", "Sprint", Muscle.CARDIO, H, listOf("Bodyweight"), listOf(Muscle.LEGS)),
        e("shuttle-run", "Shuttle Run", Muscle.CARDIO, H, listOf("Bodyweight"), listOf(Muscle.LEGS), listOf("Plyometric")),
        e("bear-crawl", "Bear Crawl", Muscle.CARDIO, H, listOf("Bodyweight"), listOf(Muscle.CORE, Muscle.SHOULDERS), listOf("Carry")),
        e("flyer", "Flyer", Muscle.CARDIO, H, listOf("Bodyweight"), listOf(Muscle.LEGS)),

        // ─────────── MOBILITY ───────────
        e("leg-swings", "Leg Swings", Muscle.MOBILITY, M, listOf("Bodyweight"), listOf(Muscle.LEGS), url = "https://www.youtube.com/shorts/3l31E2cMGMk"),
        e("cat-cow", "Cat-Cow", Muscle.MOBILITY, M, listOf("Bodyweight"), listOf(Muscle.BACK)),
        e("worlds-greatest-stretch", "World's Greatest Stretch", Muscle.MOBILITY, M, listOf("Bodyweight"), listOf(Muscle.LEGS, Muscle.BACK), listOf("Lunge")),
        e("hip-flexor-stretch", "Hip Flexor Stretch", Muscle.MOBILITY, M, listOf("Bodyweight"), listOf(Muscle.LEGS)),
        e("thoracic-rotation", "Thoracic Rotation", Muscle.MOBILITY, M, listOf("Bodyweight"), listOf(Muscle.BACK)),
        e("ankle-rocks", "Ankle Rocks", Muscle.MOBILITY, M, listOf("Bodyweight"), listOf(Muscle.LEGS)),
        e("shoulder-dislocates", "Shoulder Dislocates", Muscle.MOBILITY, M, listOf("Bands"), listOf(Muscle.SHOULDERS)),
        e("downward-dog", "Downward Dog", Muscle.MOBILITY, M, listOf("Bodyweight"), listOf(Muscle.SHOULDERS, Muscle.LEGS)),
        e("childs-pose", "Child's Pose", Muscle.MOBILITY, M, listOf("Bodyweight"), listOf(Muscle.BACK)),
        e("pigeon-pose", "Pigeon Pose", Muscle.MOBILITY, M, listOf("Bodyweight"), listOf(Muscle.LEGS)),
        e("couch-stretch", "Couch Stretch", Muscle.MOBILITY, M, listOf("Bodyweight"), listOf(Muscle.LEGS)),
        e("wall-slides", "Wall Slides", Muscle.MOBILITY, M, listOf("Bodyweight"), listOf(Muscle.SHOULDERS)),
        e("ninety-ninety-hip-switch", "90/90 Hip Switch", Muscle.MOBILITY, M, listOf("Bodyweight"), listOf(Muscle.LEGS)),
        e("calf-stretch", "Calf Stretch", Muscle.MOBILITY, M, listOf("Bodyweight"), listOf(Muscle.LEGS)),
        e("hamstring-sweep", "Hamstring Sweep", Muscle.MOBILITY, M, listOf("Bodyweight"), listOf(Muscle.LEGS)),
    )
}
