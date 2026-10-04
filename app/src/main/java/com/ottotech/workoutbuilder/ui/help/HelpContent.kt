package com.ottotech.workoutbuilder.ui.help

/*
 * Every word and every animation timeline of the how-to topics and the welcome tour.
 * Wording edits happen here; the scenes in HelpScenes.kt only draw what a beat names.
 * Steps mark button labels with **double asterisks**, shown in bold.
 */

/** Steps from a button tap to the next beat: the fingertip shows, presses and lifts. */
private const val TAP = 700

enum class BuildBeat { Home, TapBuild, Sheet, TapYourself, Name, TapAdd, Added, TapTimed, TapRounds, TapRest, TapSave, Saved }
enum class CoachBeat { KeySaved, TapBuild, Sheet, TapCoach, Goal, TapLength, TapKit, TapGo, Drafting, Draft, Tweak, Tweaked, TapSave, Saved }
enum class RunBeat { TapWorkout, Overview, TapStart, GetReady, Timer, Reps, TapDone, TapPause, Paused, TapSkip, TapDemo, TapMute, Locked }
enum class FindBeat { TapTab, Library, Search, TapFilters, Filters, TapChip, TapTile, Category, TapExercise, Detail, TapDemo, Paste, TapSaveLink, LinkSaved }
enum class EditBeat { TapMenu, Menu, TapEdit, Builder, TapSave, TapMenuAgain, TapDelete, Confirm, TapConfirm, Gone }

enum class HelpTopic(val title: String, val summary: String, val steps: List<String>, val script: Script) {
    BuildYourself(
        title = "Build a workout yourself",
        summary = "Pick exercises, set reps or timers, rounds and rests.",
        steps = listOf(
            "On Home, tap **BUILD A WORKOUT**, then **Build it yourself**.",
            "Give your workout a name.",
            "Tap **+ Add exercise** and pick from the Library.",
            "For each exercise, choose **Timed**, **Reps** or **Manual**, then set the time, or the sets and reps.",
            "Set **Rounds** to repeat a section, and choose rests between exercises and rounds. Use **+ Add section** for a warm-up or finisher.",
            "Tap **SAVE WORKOUT**.",
        ),
        script = script {
            step(BuildBeat.Home to 900, BuildBeat.TapBuild to TAP, BuildBeat.Sheet to 500, BuildBeat.TapYourself to TAP)
            step(BuildBeat.Name to 1500)
            step(BuildBeat.TapAdd to TAP, BuildBeat.Added to 1000)
            step(BuildBeat.TapTimed to TAP + 500)
            step(BuildBeat.TapRounds to TAP + 300, BuildBeat.TapRest to TAP + 500)
            step(BuildBeat.TapSave to TAP, BuildBeat.Saved to 2200)
        },
    ),
    AskCoach(
        title = "Ask the Coach",
        summary = "Describe your goal and get a workout drafted.",
        steps = listOf(
            "Add your API key in Settings first. Tap **How do I get a key?** there for the steps.",
            "Tap **BUILD A WORKOUT**, then **Ask the Coach**.",
            "Say what you're training for, then pick a length and your equipment.",
            "Tap **BUILD MY WORKOUT** and wait for the draft.",
            "Not quite right? Type a change into **Tweak**, such as \"swap lunges for step-ups\", or tap **Regenerate** for a fresh take.",
            "Tap **SAVE** to add it to your workouts.",
        ),
        script = script {
            step(CoachBeat.KeySaved to 1600)
            step(CoachBeat.TapBuild to TAP + 300, CoachBeat.Sheet to 500, CoachBeat.TapCoach to TAP)
            step(CoachBeat.Goal to 1500, CoachBeat.TapLength to TAP, CoachBeat.TapKit to TAP)
            step(CoachBeat.TapGo to TAP, CoachBeat.Drafting to 1600)
            step(CoachBeat.Draft to 900, CoachBeat.Tweak to 1900, CoachBeat.Tweaked to 900)
            step(CoachBeat.TapSave to TAP, CoachBeat.Saved to 2000)
        },
    ),
    RunWorkout(
        title = "Run a workout",
        summary = "The timer, pausing, skipping and voice cues.",
        steps = listOf(
            "Tap a workout on Home, check the overview, then tap **START**.",
            "A short **Get ready** countdown names the first exercise.",
            "Timed exercises count down on the ring. For reps, tap **Done ›** when you've finished.",
            "Tap **Pause** any time. **Skip ›** moves on, and **↺ Restart** resets the clock.",
            "Tap **Watch demo** to see how an exercise is done. The speaker icon mutes the cues.",
            "Lock your phone if you like. The timer and voice cues keep going.",
        ),
        script = script {
            step(RunBeat.TapWorkout to TAP, RunBeat.Overview to 600, RunBeat.TapStart to TAP)
            step(RunBeat.GetReady to 1800)
            step(RunBeat.Timer to 1600, RunBeat.Reps to 600, RunBeat.TapDone to TAP)
            step(RunBeat.TapPause to TAP, RunBeat.Paused to 600, RunBeat.TapSkip to TAP)
            step(RunBeat.TapDemo to TAP, RunBeat.TapMute to TAP + 300)
            step(RunBeat.Locked to 2600)
        },
    ),
    FindExercises(
        title = "Find exercises",
        summary = "Browse, search and filter the Library.",
        steps = listOf(
            "Open the **Library** tab at the bottom.",
            "Browse by muscle, or search by name.",
            "Tap **Filters** to narrow by type, equipment, movement pattern or extra muscles worked.",
            "Tap an exercise to see its tags, then tap **WATCH DEMO**.",
            "Prefer a different demo? Paste a video link and tap **SAVE LINK**.",
        ),
        script = script {
            step(FindBeat.TapTab to TAP, FindBeat.Library to 600)
            step(FindBeat.Search to 1400)
            step(FindBeat.TapFilters to TAP, FindBeat.Filters to 500, FindBeat.TapChip to TAP + 500)
            step(FindBeat.TapTile to TAP, FindBeat.Category to 400, FindBeat.TapExercise to TAP, FindBeat.Detail to 500, FindBeat.TapDemo to TAP)
            step(FindBeat.Paste to 1200, FindBeat.TapSaveLink to TAP, FindBeat.LinkSaved to 1600)
        },
    ),
    EditOrDelete(
        title = "Edit or delete a workout",
        summary = "Change a saved workout or remove it.",
        steps = listOf(
            "On Home, tap **⋯** next to a workout.",
            "Tap **Edit** to change it, then **SAVE WORKOUT**. You can also tap Edit on a workout's overview.",
            "Or tap **Delete** and confirm. This can't be undone.",
        ),
        script = script {
            step(EditBeat.TapMenu to TAP + 200, EditBeat.Menu to 500)
            step(EditBeat.TapEdit to TAP, EditBeat.Builder to 1300, EditBeat.TapSave to TAP)
            step(EditBeat.TapMenuAgain to TAP + 300, EditBeat.TapDelete to TAP, EditBeat.Confirm to 500, EditBeat.TapConfirm to TAP, EditBeat.Gone to 1800)
        },
    ),
}

enum class TourBuildBeat { RowsIn, Flip, Rounds, Hold }
enum class TourCoachBeat { Type, Think, Card, Hold }
enum class TourTrainBeat { Count, Voice, Dim, Hold }
enum class TourPrivateBeat { Slide, Lock, Hold }

/** The welcome tour. The first card has no script: it shows the brand's own build-the-B loader. */
enum class WelcomeCard(val title: String, val body: String, val script: Script?) {
    Welcome(
        title = "WELCOME TO BLDR",
        body = "Build workouts that fit you, then train with a timer that coaches you through them.",
        script = null,
    ),
    Build(
        title = "BUILD IT YOUR WAY",
        body = "Pick from 100 exercises. Set reps or timers, rounds and rests.",
        script = script { step(TourBuildBeat.RowsIn to 1300, TourBuildBeat.Flip to 800, TourBuildBeat.Rounds to 800, TourBuildBeat.Hold to 1800) },
    ),
    Coach(
        title = "OR ASK THE COACH",
        body = "Describe your goal and get a workout drafted for you. Tweak it until it's right.",
        script = script { step(TourCoachBeat.Type to 1500, TourCoachBeat.Think to 1600, TourCoachBeat.Card to 700, TourCoachBeat.Hold to 1800) },
    ),
    Train(
        title = "TRAIN HANDS-FREE",
        body = "A timer, voice and sound cues that keep going, even when your phone is locked.",
        script = script { step(TourTrainBeat.Count to 1400, TourTrainBeat.Voice to 1400, TourTrainBeat.Dim to 1800, TourTrainBeat.Hold to 600) },
    ),
    Private(
        title = "PRIVATE BY DESIGN",
        body = "Your workouts stay on your phone. No accounts, no ads, no tracking.",
        script = script { step(TourPrivateBeat.Slide to 1000, TourPrivateBeat.Lock to 600, TourPrivateBeat.Hold to 2000) },
    ),
}

/** Splits a step on **markers** into (text, isBold) runs. Unpaired markers are left as text. */
fun boldRuns(step: String): List<Pair<String, Boolean>> {
    val parts = step.split("**")
    if (parts.size % 2 == 0) return listOf(step to false)
    return parts.mapIndexedNotNull { i, part -> if (part.isEmpty()) null else part to (i % 2 == 1) }
}
