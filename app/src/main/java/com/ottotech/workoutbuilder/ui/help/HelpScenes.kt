package com.ottotech.workoutbuilder.ui.help

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ottotech.workoutbuilder.ui.brand.BldrLoader
import com.ottotech.workoutbuilder.ui.brand.BldrMark
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.components.HomeGlyph
import com.ottotech.workoutbuilder.ui.components.LibraryGlyph
import com.ottotech.workoutbuilder.ui.components.ScreenTitle
import com.ottotech.workoutbuilder.ui.components.SearchGlyph
import com.ottotech.workoutbuilder.ui.components.SectionLabel
import com.ottotech.workoutbuilder.ui.execution.MuteGlyph
import com.ottotech.workoutbuilder.ui.execution.PlayGlyph
import com.ottotech.workoutbuilder.ui.execution.TimerRing
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.ExecutionColors
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

/*
 * One scene per how-to topic. A scene reads the clock's position and draws the mini-screens
 * for it; which beat shows what is spelled out here, the timings live in HelpContent.kt.
 */

@Composable
fun TopicScene(topic: HelpTopic, clock: ScriptClock) {
    val pos = clock.position
    when (topic) {
        HelpTopic.BuildYourself -> BuildYourselfScene(pos)
        HelpTopic.AskCoach -> AskCoachScene(pos)
        HelpTopic.RunWorkout -> RunWorkoutScene(pos)
        HelpTopic.FindExercises -> FindExercisesScene(pos)
        HelpTopic.EditOrDelete -> EditOrDeleteScene(pos)
        HelpTopic.SetUpCoach -> SetUpCoachScene(pos)
    }
}

/** A tap's change lands as the fingertip presses, not when the tap beat starts. */
private fun ScenePosition.tapped(key: Enum<*>): Boolean = progressOf(key) > 0.4f

/** A screen pushed over the last one, easing in over the first quarter of [key]. */
private fun ScenePosition.enter(key: Enum<*>): Float = ease(window(progressOf(key), 0f, 0.25f))

/** Typed text: [text] appears letter by letter across the middle of [key]. */
private fun ScenePosition.typed(key: Enum<*>, text: String): String =
    text.take((text.length * window(progressOf(key), 0.15f, 0.75f)).toInt())

/** Shows [under], with [over] sliding in from the right as [enter] goes 0..1. */
@Composable
private fun Push(enter: Float, under: @Composable () -> Unit, over: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (enter < 1f) under()
        if (enter > 0f) Box(Modifier.fillMaxSize().slideX(1f - enter)) { over() }
    }
}

// ---- Build a workout yourself ----------------------------------------------------------------

@Composable
private fun BuildYourselfScene(pos: ScenePosition) {
    val saved = pos.reached(BuildBeat.Saved)
    val builderIn = if (saved) 1f - pos.enter(BuildBeat.Saved) else pos.enter(BuildBeat.Name)
    val added = pos.progressOf(BuildBeat.Added)

    Push(
        enter = builderIn,
        under = {
            MiniHome(
                newWorkout = "Leg day",
                newReveal = if (saved) ease(window(pos.progressOf(BuildBeat.Saved), 0.25f, 0.55f)) else 0f,
                buildTap = pos.during(BuildBeat.TapBuild),
            )
            if (pos.reached(BuildBeat.Sheet) && !pos.reached(BuildBeat.Name)) {
                MiniBuildSheet(rise = ease(pos.progressOf(BuildBeat.Sheet)), yourselfTap = pos.during(BuildBeat.TapYourself))
            }
        },
        over = {
            MiniBuilder(
                title = "BUILD",
                name = pos.typed(BuildBeat.Name, "Leg day"),
                typing = pos.key == BuildBeat.Name,
                rounds = if (pos.tapped(BuildBeat.TapRounds)) "3" else "1",
                restOn = pos.tapped(BuildBeat.TapRest),
                items = if (added > 0f) {
                    listOf(
                        MiniExercise("Goblet squat", pos.tapped(BuildBeat.TapTimed), ease(window(added, 0f, 0.5f))),
                        MiniExercise("Reverse lunge", false, ease(window(added, 0.35f, 0.85f))),
                    )
                } else {
                    emptyList()
                },
                addTap = pos.during(BuildBeat.TapAdd),
                timedTap = pos.during(BuildBeat.TapTimed),
                plusTap = pos.during(BuildBeat.TapRounds),
                restTap = pos.during(BuildBeat.TapRest),
                saveTap = pos.during(BuildBeat.TapSave),
            )
        },
    )
}

// ---- Ask the Coach ---------------------------------------------------------------------------

private const val CoachGoal = "Build strength for skiing"
private const val CoachTweak = "swap lunges for step-ups"

@Composable
private fun AskCoachScene(pos: ScenePosition) {
    val saved = pos.reached(CoachBeat.Saved)
    when {
        // Settings first, then back to Home.
        !pos.reached(CoachBeat.Goal) -> Push(
            enter = if (pos.reached(CoachBeat.TapBuild)) 1f - pos.enter(CoachBeat.TapBuild) else 1f,
            under = {
                MiniHome(buildTap = window(pos.during(CoachBeat.TapBuild), 0.3f, 1f))
                if (pos.reached(CoachBeat.Sheet)) {
                    MiniBuildSheet(rise = ease(pos.progressOf(CoachBeat.Sheet)), coachTap = pos.during(CoachBeat.TapCoach))
                }
            },
            over = { MiniSettingsKey(saved = true) },
        )
        !pos.reached(CoachBeat.Drafting) -> Push(
            enter = pos.enter(CoachBeat.Goal),
            under = { MiniHome() },
            over = {
                MiniCoachForm(
                    goal = pos.typed(CoachBeat.Goal, CoachGoal),
                    typing = pos.key == CoachBeat.Goal,
                    length = pos.tapped(CoachBeat.TapLength),
                    dumbbell = pos.tapped(CoachBeat.TapKit),
                    lengthTap = pos.during(CoachBeat.TapLength),
                    kitTap = pos.during(CoachBeat.TapKit),
                    goTap = pos.during(CoachBeat.TapGo),
                )
            },
        )
        !pos.reached(CoachBeat.Draft) -> MiniCoachLoading()
        else -> Push(
            enter = if (saved) 1f - pos.enter(CoachBeat.Saved) else 1f,
            under = {
                MiniHome(
                    newWorkout = "Ski strength",
                    newReveal = ease(window(pos.progressOf(CoachBeat.Saved), 0.25f, 0.55f)),
                )
            },
            over = {
                val tweaking = pos.key == CoachBeat.Tweak
                MiniCoachDraft(
                    tweak = if (tweaking) pos.typed(CoachBeat.Tweak, CoachTweak) else "",
                    typing = tweaking,
                    swapped = pos.reached(CoachBeat.Tweaked),
                    tweakTap = window(pos.during(CoachBeat.Tweak), 0.72f, 1f),
                    saveTap = pos.during(CoachBeat.TapSave),
                )
            },
        )
    }
}

// ---- Run a workout ---------------------------------------------------------------------------

@Composable
private fun RunWorkoutScene(pos: ScenePosition) {
    when {
        !pos.reached(RunBeat.GetReady) -> Push(
            enter = pos.enter(RunBeat.Overview),
            under = { MiniHome(firstCardTap = pos.during(RunBeat.TapWorkout)) },
            over = { MiniOverview(startTap = pos.during(RunBeat.TapStart)) },
        )
        !pos.reached(RunBeat.Timer) -> MiniLeadIn(count = 3 - (pos.progress * 3).toInt().coerceAtMost(2))
        !pos.reached(RunBeat.Reps) -> {
            val p = pos.progressOf(RunBeat.Timer)
            MiniExecution(name = "Squat jumps", position = 1, remaining = 30f - 4f * p)
        }
        !pos.reached(RunBeat.TapPause) -> MiniExecution(name = "Goblet squat", position = 2, reps = true, doneTap = pos.during(RunBeat.TapDone))
        !pos.reached(RunBeat.Locked) -> {
            val skipped = pos.tapped(RunBeat.TapSkip)
            MiniExecution(
                name = if (skipped) "Skater hops" else "Lateral bounds",
                position = if (skipped) 4 else 3,
                remaining = if (skipped) 30f else 22f,
                paused = pos.tapped(RunBeat.TapPause),
                muted = pos.tapped(RunBeat.TapMute),
                pauseTap = pos.during(RunBeat.TapPause),
                skipTap = pos.during(RunBeat.TapSkip),
                demoTap = pos.during(RunBeat.TapDemo),
                muteTap = pos.during(RunBeat.TapMute),
            )
        }
        else -> {
            val p = pos.progressOf(RunBeat.Locked)
            Box(Modifier.fillMaxSize()) {
                MiniExecution(name = "Skater hops", position = 4, remaining = 30f - 6f * p, muted = true)
                MiniLockScreen(dim = ease(window(p, 0f, 0.2f)), voice = window(p, 0.4f, 0.55f), remaining = 30 - (6f * p).toInt())
            }
        }
    }
}

// ---- Find exercises --------------------------------------------------------------------------

private const val DemoLink = "https://youtu.be/q7Rx2uZ"

@Composable
private fun FindExercisesScene(pos: ScenePosition) {
    when {
        !pos.reached(FindBeat.Library) -> MiniHome(libraryTap = pos.during(FindBeat.TapTab))
        !pos.reached(FindBeat.Category) -> {
            val chipTap = pos.progressOf(FindBeat.TapChip)
            // The sheet rises with Filters and drops away once the chip is picked.
            val rise = if (pos.reached(FindBeat.TapChip)) 1f - ease(window(chipTap, 0.7f, 1f)) else ease(pos.progressOf(FindBeat.Filters))
            Box(Modifier.fillMaxSize()) {
                MiniLibrary(
                    query = pos.typed(FindBeat.Search, "lunge"),
                    typing = pos.key == FindBeat.Search,
                    narrowed = pos.progressOf(FindBeat.Search) > 0.8f,
                    filtered = chipTap > 0.4f,
                    filtersTap = pos.during(FindBeat.TapFilters),
                    legsTap = pos.during(FindBeat.TapTile),
                )
                if (rise > 0f) MiniFiltersSheet(rise = rise, dumbbell = chipTap > 0.4f, chipTap = window(chipTap, 0f, 0.7f))
            }
        }
        !pos.reached(FindBeat.Detail) -> Push(
            enter = pos.enter(FindBeat.Category),
            under = { MiniLibrary(query = "lunge", narrowed = true, filtered = true) },
            over = { MiniCategory(exerciseTap = pos.during(FindBeat.TapExercise)) },
        )
        else -> Push(
            enter = pos.enter(FindBeat.Detail),
            under = { MiniCategory() },
            over = {
                MiniExerciseDetail(
                    link = if (pos.progressOf(FindBeat.Paste) > 0.3f) DemoLink else "",
                    savedLink = pos.reached(FindBeat.LinkSaved),
                    demoTap = pos.during(FindBeat.TapDemo),
                    saveTap = pos.during(FindBeat.TapSaveLink),
                )
            },
        )
    }
}

// ---- Edit or delete a workout ----------------------------------------------------------------

@Composable
private fun EditOrDeleteScene(pos: ScenePosition) {
    val inBuilder = pos.reached(EditBeat.Builder) && !pos.reached(EditBeat.TapMenuAgain)
    val builderIn = when {
        inBuilder -> pos.enter(EditBeat.Builder)
        pos.reached(EditBeat.TapMenuAgain) && !pos.reached(EditBeat.TapDelete) -> 1f - pos.enter(EditBeat.TapMenuAgain)
        else -> 0f
    }
    val menuOpen = (pos.tapped(EditBeat.TapMenu) && !pos.reached(EditBeat.Builder)) ||
        (window(pos.progressOf(EditBeat.TapMenuAgain), 0.25f, 1f) > 0.4f && !pos.reached(EditBeat.Confirm))
    val confirm = pos.reached(EditBeat.Confirm) && !pos.reached(EditBeat.Gone)

    Push(
        enter = builderIn,
        under = {
            MiniHome(
                firstCardMenuTap = pos.during(EditBeat.TapMenu) + window(pos.during(EditBeat.TapMenuAgain), 0.25f, 1f),
                firstCardGone = ease(window(pos.progressOf(EditBeat.Gone), 0.1f, 0.5f)),
                menu = if (menuOpen) {
                    { MiniMenu(editTap = pos.during(EditBeat.TapEdit), deleteTap = pos.during(EditBeat.TapDelete)) }
                } else {
                    null
                },
            )
            if (confirm) {
                MiniDialog(
                    title = "Delete “Court Speed”?",
                    body = "This can't be undone.",
                    confirm = "Delete",
                    dismiss = "Cancel",
                    appear = ease(window(pos.progressOf(EditBeat.Confirm), 0f, 0.5f)),
                    confirmTap = pos.during(EditBeat.TapConfirm),
                )
            }
        },
        over = {
            MiniBuilder(
                title = "EDIT",
                name = "Court Speed",
                rounds = if (window(pos.progressOf(EditBeat.Builder), 0.3f, 0.9f) > 0.4f) "4" else "3",
                restOn = true,
                items = listOf(MiniExercise("Squat jumps", true), MiniExercise("Lateral bounds", true)),
                plusTap = window(pos.during(EditBeat.Builder), 0.3f, 0.9f),
                saveTap = pos.during(EditBeat.TapSave),
            )
        },
    )
}

// ---- Set up the Coach ------------------------------------------------------------------------

@Composable
private fun SetUpCoachScene(pos: ScenePosition) {
    when {
        !pos.reached(KeyBeat.Billing) -> MiniBrowser {
            Text("Claude Console", style = AppType.heroName, color = LocalAppColors.current.onBackground)
            Spacer(Modifier.height(6.dp))
            Text("Sign in or create an account", style = AppType.cardMeta, color = LocalAppColors.current.textMuted)
            Spacer(Modifier.height(24.dp))
            MiniButton("Continue with email", Modifier.fillMaxWidth().tapHint(window(pos.during(KeyBeat.Console), 0.4f, 1f)))
        }
        !pos.reached(KeyBeat.TapCreate) -> MiniBrowser {
            val colors = LocalAppColors.current
            Text("Billing", style = AppType.heroName, color = colors.onBackground)
            Spacer(Modifier.height(20.dp))
            HairlineCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Credit balance", style = AppType.cardMeta, color = colors.textMuted)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (pos.progress > 0.55f) "\$5.00" else "\$0.00",
                        style = AppType.heroName,
                        color = colors.onBackground,
                    )
                    Spacer(Modifier.height(16.dp))
                    MiniButton("Add credit", Modifier.fillMaxWidth().tapHint(window(pos.progress, 0.1f, 0.6f)))
                }
            }
        }
        !pos.reached(KeyBeat.Settings) -> MiniBrowser {
            val colors = LocalAppColors.current
            Text("API keys", style = AppType.heroName, color = colors.onBackground)
            Spacer(Modifier.height(20.dp))
            if (!pos.tapped(KeyBeat.TapCreate)) {
                Text("No keys yet.", style = AppType.cardMeta, color = colors.textMuted)
                Spacer(Modifier.height(16.dp))
                MiniButton("Create key", Modifier.fillMaxWidth().tapHint(pos.during(KeyBeat.TapCreate)))
            } else {
                HairlineCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("BLDR", style = AppType.cardTitle, color = colors.onBackground)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("sk-ant-api03-••••••", style = AppType.cardMeta, color = colors.onBackground, modifier = Modifier.weight(1f))
                            MiniChip(
                                if (pos.tapped(KeyBeat.TapCopy)) "Copied ✓" else "Copy",
                                selected = false,
                                modifier = Modifier.tapHint(pos.during(KeyBeat.TapCopy)),
                            )
                        }
                    }
                }
            }
        }
        else -> Push(
            enter = pos.enter(KeyBeat.Settings),
            under = { MiniBrowser {} },
            over = {
                MiniSettingsKey(
                    saved = pos.tapped(KeyBeat.TapSaveKey),
                    pasted = pos.progressOf(KeyBeat.Paste) > 0.4f,
                    saveTap = pos.during(KeyBeat.TapSaveKey),
                )
            },
        )
    }
}

// ---- Shared mini-screens ---------------------------------------------------------------------

/**
 * Home: two saved workouts, BUILD A WORKOUT and the tab bar. [newWorkout] grows in above the
 * others; [menu] opens beside the first card's ⋯; [firstCardGone] collapses that card away.
 */
@Composable
internal fun MiniHome(
    newWorkout: String? = null,
    newReveal: Float = 0f,
    buildTap: Float = 0f,
    libraryTap: Float = 0f,
    firstCardTap: Float = 0f,
    firstCardMenuTap: Float = 0f,
    firstCardGone: Float = 0f,
    menu: (@Composable () -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.weight(1f).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BldrMark(height = 16.dp, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("MON · OCT 6", style = AppType.overline, color = colors.textMuted, modifier = Modifier.weight(1f))
                Text("Settings", style = AppType.chip, color = colors.textMuted)
            }
            Spacer(Modifier.height(10.dp))
            ScreenTitle("YOUR\nWORKOUTS", maxLines = 2)
            Spacer(Modifier.height(16.dp))
            SectionLabel("Choose a workout")
            Spacer(Modifier.height(12.dp))
            if (newWorkout != null && newReveal > 0f) {
                Column(Modifier.reveal(newReveal)) {
                    MiniWorkoutCard(newWorkout, "30 MIN", "6 exercises · Strength", "Never done")
                    Spacer(Modifier.height(12.dp))
                }
            }
            Box(Modifier.zIndex(1f).then(if (firstCardGone > 0f) Modifier.reveal(1f - firstCardGone) else Modifier)) {
                Column {
                    MiniWorkoutCard(
                        "Court Speed", "22 MIN", "10 exercises · HIIT", "1 week ago",
                        modifier = Modifier.tapHint(firstCardTap),
                        menuTap = firstCardMenuTap,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                // Sized to the card and allowed to overflow it, so the open menu never pushes the list down.
                if (menu != null) {
                    Box(Modifier.matchParentSize()) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 58.dp, end = 10.dp)
                                .wrapContentHeight(Alignment.Top, unbounded = true),
                        ) { menu() }
                    }
                }
            }
            MiniWorkoutCard("Upper body", "35 MIN", "8 exercises · Strength", "Yesterday")
            Spacer(Modifier.weight(1f))
            MiniButton("+  BUILD A WORKOUT", Modifier.fillMaxWidth().tapHint(buildTap))
        }
        MiniTabBar(library = false, libraryTap = libraryTap)
    }
}

@Composable
private fun MiniTabBar(library: Boolean, libraryTap: Float = 0f) {
    val colors = LocalAppColors.current
    @Composable
    fun Tab(label: String, selected: Boolean, modifier: Modifier = Modifier, glyph: @Composable (Color) -> Unit) {
        val tint = if (selected) colors.onBackground else colors.navInactive
        Column(modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            glyph(tint)
            Spacer(Modifier.height(4.dp))
            Text(label, style = AppType.navLabel, color = tint)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(Dimens.hairline).background(colors.borderSubtle))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tab("Home", selected = !library) { HomeGlyph(color = it) }
            Tab("Library", selected = library, Modifier.tapHint(libraryTap)) { LibraryGlyph(color = it) }
        }
    }
}

/** The Build sheet rising over a dimmed Home. */
@Composable
private fun MiniBuildSheet(rise: Float, yourselfTap: Float = 0f, coachTap: Float = 0f) {
    MiniSheet(rise) {
        SectionLabel("Build a workout")
        Spacer(Modifier.height(2.dp))
        MiniOption("Build it yourself", "Pick exercises from the library", Modifier.tapHint(yourselfTap))
        MiniOption("Ask the Coach", "Describe your goal, get a workout drafted", Modifier.tapHint(coachTap))
        Spacer(Modifier.height(8.dp))
    }
}

/** A bottom sheet over a scrim, risen [rise] of the way. */
@Composable
private fun MiniSheet(rise: Float, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Box(Modifier.fillMaxSize()) {
        // The platform's sheet scrim is black in both themes.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f * rise)))
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .slideY(1f - rise)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

/** An exercise in the Builder; [reveal] grows it in. */
internal class MiniExercise(val name: String, val timed: Boolean, val reveal: Float = 1f)

@Composable
internal fun MiniBuilder(
    title: String,
    name: String,
    items: List<MiniExercise>,
    typing: Boolean = false,
    rounds: String = "1",
    restOn: Boolean = false,
    addTap: Float = 0f,
    timedTap: Float = 0f,
    plusTap: Float = 0f,
    restTap: Float = 0f,
    saveTap: Float = 0f,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize().background(colors.background).padding(horizontal = 20.dp)) {
        MiniHeader(title)
        MiniField(name, "Workout name", typing = typing)
        Spacer(Modifier.height(12.dp))
        HairlineCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                MiniField("Main", "Section name")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rounds", style = AppType.cardMeta, color = colors.textMuted, modifier = Modifier.weight(1f))
                    MiniStepper(rounds, plusTap = plusTap)
                }
                Spacer(Modifier.height(10.dp))
                Text("Between rounds", style = AppType.cardMeta, color = colors.textMuted)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniChip("Off", selected = !restOn)
                    MiniChip("Timed", selected = restOn, modifier = Modifier.tapHint(restTap))
                    MiniChip("Manual", selected = false)
                    if (restOn) {
                        Spacer(Modifier.weight(1f))
                        Text("60s", style = AppType.cardTitle, color = colors.onBackground)
                    }
                }
                Spacer(Modifier.height(12.dp))
                items.forEachIndexed { i, item ->
                    MiniItem(item, timedTap = if (i == 0) timedTap else 0f, modifier = Modifier.reveal(item.reveal))
                }
                MiniChip("+ Add exercise", selected = false, modifier = Modifier.tapHint(addTap))
            }
        }
        Spacer(Modifier.weight(1f))
        MiniButton("SAVE WORKOUT", Modifier.fillMaxWidth().tapHint(saveTap))
        Spacer(Modifier.height(14.dp))
    }
}

/** An exercise in the Builder: name and its time or sets × reps, over the mode chips. */
@Composable
private fun MiniItem(item: MiniExercise, timedTap: Float, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(modifier.padding(bottom = 8.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .padding(12.dp),
        ) {
            Row {
                Text(item.name, style = AppType.cardTitle, color = colors.onSurface, modifier = Modifier.weight(1f))
                Text(if (item.timed) "30s" else "3 × 10", style = AppType.cardTitle, color = colors.onSurface)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniChip("Timed", selected = item.timed, container = colors.background, modifier = Modifier.tapHint(timedTap))
                MiniChip("Reps", selected = !item.timed, container = colors.background)
                MiniChip("Manual", selected = false, container = colors.background)
            }
        }
    }
}

/** Settings' API key section, empty, pasted into, or saved. */
@Composable
private fun MiniSettingsKey(saved: Boolean, pasted: Boolean = false, saveTap: Float = 0f) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize().background(colors.background).padding(horizontal = 20.dp)) {
        MiniHeader("SETTINGS")
        SectionLabel("Anthropic API key")
        Spacer(Modifier.height(12.dp))
        if (saved) {
            HairlineCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Key saved", style = AppType.cardTitle, color = colors.onBackground)
                        Text("Stored encrypted on this phone", style = AppType.cardMeta, color = colors.textMuted)
                    }
                    Text("Remove", style = AppType.chip, color = colors.onBackground)
                }
            }
        } else {
            MiniField(if (pasted) "••••••••••••••••••" else "", "sk-ant-…", style = AppType.heroMeta)
            Spacer(Modifier.height(12.dp))
            MiniButton("SAVE KEY", Modifier.fillMaxWidth().tapHint(saveTap))
            Spacer(Modifier.height(14.dp))
            Text("How do I get a key?", style = AppType.chip, color = colors.onBackground)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "The Coach sends your goal and the exercise library to Anthropic using this key.",
            style = AppType.cardMeta,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(28.dp))
        SectionLabel("Appearance")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniChip("System", selected = true)
            MiniChip("Light", selected = false)
            MiniChip("Dark", selected = false)
        }
    }
}

@Composable
internal fun MiniCoachForm(
    goal: String,
    typing: Boolean,
    length: Boolean,
    dumbbell: Boolean,
    lengthTap: Float,
    kitTap: Float,
    goTap: Float,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize().background(colors.background).padding(horizontal = 20.dp)) {
        MiniHeader("COACH")
        Box(
            Modifier
                .fillMaxWidth()
                .height(116.dp)
                .clip(RoundedCornerShape(Dimens.radiusButton))
                .background(colors.surface)
                .padding(14.dp),
        ) {
            when {
                goal.isNotEmpty() -> Text(if (typing) "$goal|" else goal, style = AppType.cardTitle, color = colors.onSurface)
                typing -> Text("|", style = AppType.cardTitle, color = colors.onSurface)
                else -> Text("What are you training for?", style = AppType.cardTitle, color = colors.textMuted)
            }
        }
        Spacer(Modifier.height(18.dp))
        SectionLabel("Length")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniChip("15 min", selected = false)
            MiniChip("30 min", selected = length, modifier = Modifier.tapHint(lengthTap))
            MiniChip("45 min", selected = false)
            MiniChip("60 min", selected = false)
        }
        Spacer(Modifier.height(18.dp))
        SectionLabel("Equipment")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniChip("Bodyweight", selected = false)
            MiniChip("Dumbbell", selected = dumbbell, modifier = Modifier.tapHint(kitTap))
            MiniChip("Barbell", selected = false)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniChip("Kettlebell", selected = false)
            MiniChip("Bands", selected = false)
            MiniChip("Bench", selected = false)
        }
        Spacer(Modifier.weight(1f))
        MiniButton("BUILD MY WORKOUT", Modifier.fillMaxWidth().tapHint(goTap))
        Spacer(Modifier.height(14.dp))
    }
}

/** The Coach drafting: the build-the-B loader over one of its working words. */
@Composable
internal fun MiniCoachLoading() {
    val colors = LocalAppColors.current
    Column(
        Modifier.fillMaxSize().background(colors.background).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MiniHeader("COACH")
        Spacer(Modifier.weight(1f))
        BldrLoader(width = 44.dp, contentDescription = "")
        Spacer(Modifier.height(24.dp))
        Text("Selecting optimal exercises…", style = AppType.cardMeta, color = colors.textMuted)
        Spacer(Modifier.weight(1.3f))
    }
}

@Composable
internal fun MiniCoachDraft(
    tweak: String = "",
    typing: Boolean = false,
    swapped: Boolean = false,
    tweakTap: Float = 0f,
    saveTap: Float = 0f,
    name: String = "Ski strength",
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize().background(colors.background).padding(horizontal = 20.dp)) {
        MiniHeader("COACH")
        Text(name, style = AppType.heroName, color = colors.onBackground)
        Text("30 MIN · 6 exercises · Strength", style = AppType.cardMeta, color = colors.textMuted)
        Spacer(Modifier.height(14.dp))
        MiniSectionCard("Warm-up", null, listOf("Jumping jacks", "Hip circles"))
        Spacer(Modifier.height(12.dp))
        MiniSectionCard(
            "Main",
            "× 3 rounds",
            listOf("Goblet squat", if (swapped) "Step-ups" else "Reverse lunge", "Lateral bounds", "Plank"),
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniField(tweak, "Tweak… e.g. swap lunges for step-ups", Modifier.weight(1f), typing = typing, style = AppType.cardMeta)
            Spacer(Modifier.width(12.dp))
            Text("Tweak", style = AppType.chip, color = colors.onBackground, modifier = Modifier.tapHint(tweakTap))
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            MiniButton("Regenerate", Modifier.weight(1f), filled = false)
            MiniButton("SAVE", Modifier.weight(1f).tapHint(saveTap))
        }
        Spacer(Modifier.height(14.dp))
    }
}

/** A read-only section: name and rounds over its exercise names. */
@Composable
private fun MiniSectionCard(name: String, rounds: String?, exercises: List<String>) {
    val colors = LocalAppColors.current
    HairlineCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(name, style = AppType.cardTitle, color = colors.onBackground, modifier = Modifier.weight(1f))
                if (rounds != null) Text(rounds, style = AppType.cardMeta, color = colors.textMuted)
            }
            exercises.forEach { Text(it, style = AppType.cardMeta, color = colors.onBackground) }
        }
    }
}

@Composable
private fun MiniOverview(startTap: Float) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize().background(colors.background).padding(horizontal = 20.dp)) {
        MiniHeader("WORKOUT")
        Text("Court Speed", style = AppType.heroName, color = colors.onBackground)
        Text("22 MIN · 10 exercises · HIIT", style = AppType.cardMeta, color = colors.textMuted)
        Spacer(Modifier.height(14.dp))
        HairlineCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row {
                    Text("Main", style = AppType.cardTitle, color = colors.onBackground, modifier = Modifier.weight(1f))
                    Text("× 3 rounds", style = AppType.cardMeta, color = colors.textMuted)
                }
                Text("20s rest between exercises", style = AppType.cardMeta, color = colors.textMuted)
                Spacer(Modifier.height(6.dp))
                listOf("Squat jumps", "Lateral bounds", "Skater hops", "Sprint in place").forEach { name ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = AppType.setRowLabel, color = colors.onBackground)
                            Text("30s", style = AppType.cardMeta, color = colors.textMuted)
                        }
                        Text("Demo", style = AppType.chip, color = colors.onBackground)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            MiniButton("Edit", Modifier.weight(1f), filled = false)
            MiniButton("START", Modifier.weight(1f).tapHint(startTap))
        }
        Spacer(Modifier.height(14.dp))
    }
}

/** The always-dark "GET READY" countdown before the first exercise. */
@Composable
private fun MiniLeadIn(count: Int) {
    Column(
        Modifier.fillMaxSize().background(ExecutionColors.background).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text("GET READY", style = AppType.timerCaption, color = ExecutionColors.textMuted)
        Text("$count", style = AppType.leadInDigits, color = ExecutionColors.onBackground)
        Text("FIRST UP", style = AppType.timerCaption, color = ExecutionColors.textMuted)
        Spacer(Modifier.height(6.dp))
        Text("Squat jumps", style = AppType.cardTitle, color = ExecutionColors.onBackground)
        Spacer(Modifier.weight(1.3f))
    }
}

/** The always-dark workout screen: a timed exercise on the ring, or a reps exercise's sets. */
@Composable
internal fun MiniExecution(
    name: String,
    position: Int,
    remaining: Float = 30f,
    reps: Boolean = false,
    paused: Boolean = false,
    muted: Boolean = false,
    pauseTap: Float = 0f,
    skipTap: Float = 0f,
    doneTap: Float = 0f,
    demoTap: Float = 0f,
    muteTap: Float = 0f,
) {
    val shape = RoundedCornerShape(13.dp)
    Column(
        Modifier.fillMaxSize().background(ExecutionColors.background).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("COURT SPEED · $position/10", style = AppType.sectionLabel, color = ExecutionColors.textMuted, modifier = Modifier.weight(1f))
            Box(Modifier.size(40.dp).tapHint(muteTap), contentAlignment = Alignment.Center) {
                MuteGlyph(muted = muted, color = ExecutionColors.onBackground)
            }
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Text("✕", style = AppType.cardTitle, color = ExecutionColors.onBackground)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(Dimens.progressBarHeight), horizontalArrangement = Arrangement.spacedBy(Dimens.progressBarGap)) {
            repeat(10) { i ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp))
                        .background(if (i < position - 1) ExecutionColors.ringProgress else ExecutionColors.ringTrack),
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(name, style = AppType.moveName, color = ExecutionColors.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        if (reps) {
            Text("Legs · Dumbbell", style = AppType.cardMeta, color = ExecutionColors.textMuted)
            Spacer(Modifier.height(16.dp))
            Text("SETS", style = AppType.heroLabel, color = ExecutionColors.textMuted)
            Spacer(Modifier.height(8.dp))
            (1..3).forEach { set ->
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape)
                        .border(BorderStroke(Dimens.hairline, ExecutionColors.border), shape)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text("Set $set · 10 reps", style = AppType.setRowLabel, color = ExecutionColors.onBackground)
                }
            }
            Spacer(Modifier.weight(1f))
        } else {
            val seconds = remaining.toInt().coerceAtLeast(0)
            TimerRing(progress = remaining / 30f, centreTop = "$seconds", centreBottom = "REMAINING", modifier = Modifier.size(196.dp))
            Spacer(Modifier.height(4.dp))
            Text("↺  Restart", style = AppType.chip, color = ExecutionColors.textMuted, modifier = Modifier.padding(vertical = 10.dp))
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().clip(shape).background(ExecutionColors.card).tapHint(demoTap).padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PlayGlyph(color = ExecutionColors.onBackground)
                Text("Watch demo", style = AppType.cardTitle, color = ExecutionColors.onBackground)
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExecutionButton(if (paused) "Resume" else "Pause", filled = false, Modifier.weight(1f).tapHint(pauseTap))
            ExecutionButton(if (reps) "Done ›" else "Skip ›", filled = true, Modifier.weight(1f).tapHint(if (reps) doneTap else skipTap))
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ExecutionButton(text: String, filled: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(Dimens.radiusButton)
    Box(
        modifier
            .height(52.dp)
            .clip(shape)
            .then(
                if (filled) Modifier.background(ExecutionColors.onBackground)
                else Modifier.border(BorderStroke(Dimens.hairline, ExecutionColors.borderStrong), shape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppType.primaryButton, color = if (filled) ExecutionColors.background else ExecutionColors.onBackground)
    }
}

/** The screen going dark in a pocket, with the workout carrying on in its notification and voice. */
@Composable
internal fun MiniLockScreen(dim: Float, voice: Float, remaining: Int, exercise: String = "Skater hops") {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(ExecutionColors.background.copy(alpha = 0.97f * dim)))
        Column(Modifier.fillMaxWidth().padding(20.dp).padding(top = 30.dp).slideY(-(1f - dim))) {
            Text("6:42", style = AppType.screenTitle, color = ExecutionColors.onBackground.copy(alpha = dim), modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ExecutionColors.card.copy(alpha = dim)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BldrMark(height = 18.dp, color = ExecutionColors.onBackground.copy(alpha = dim), contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Court Speed", style = AppType.cardTitle, color = ExecutionColors.onBackground.copy(alpha = dim))
                    Text("$exercise · ${remaining}s", style = AppType.cardMeta, color = ExecutionColors.textMuted.copy(alpha = dim))
                }
            }
        }
        if (voice > 0f) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .padding(top = 140.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ExecutionColors.onBackground.copy(alpha = voice))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text("“Rest”", style = AppType.cardTitle, color = ExecutionColors.background.copy(alpha = voice))
            }
        }
    }
}

@Composable
private fun MiniLibrary(
    query: String,
    typing: Boolean = false,
    narrowed: Boolean,
    filtered: Boolean,
    filtersTap: Float = 0f,
    legsTap: Float = 0f,
) {
    val colors = LocalAppColors.current
    // Counts before and after searching "lunge" (and filtering to dumbbells).
    val tiles = listOf(
        Triple("Chest", "14", "0"), Triple("Back", "12", "0"), Triple("Legs", "16", if (filtered) "4" else "6"),
        Triple("Shoulders", "10", "0"), Triple("Arms", "12", "0"), Triple("Core", "11", "0"),
    )
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.weight(1f).padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
            ScreenTitle("LIBRARY")
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchGlyph(color = colors.textMuted)
                Spacer(Modifier.width(10.dp))
                when {
                    query.isNotEmpty() -> Text(if (typing) "$query|" else query, style = AppType.heroMeta, color = colors.onSurface)
                    typing -> Text("|", style = AppType.heroMeta, color = colors.onSurface)
                    else -> Text("Search exercises", style = AppType.heroMeta, color = colors.textMuted)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniChip("All", selected = true)
                MiniChip("Strength", selected = false)
                MiniChip("HIIT", selected = false)
                MiniChip(if (filtered) "Filters · 1" else "Filters", selected = filtered, modifier = Modifier.tapHint(filtersTap))
            }
            Spacer(Modifier.height(18.dp))
            SectionLabel("Browse by muscle")
            Spacer(Modifier.height(12.dp))
            tiles.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEachIndexed { col, (label, all, matching) ->
                        val featured = row == 0 && col == 0
                        val count = if (narrowed) matching else all
                        val empty = count == "0"
                        val alpha = if (empty) 0.4f else 1f
                        val ink = if (featured) colors.onSurfaceInverse else colors.onSurface
                        Column(
                            Modifier
                                .weight(1f)
                                .height(74.dp)
                                .clip(RoundedCornerShape(Dimens.radiusTile))
                                .background((if (featured) colors.surfaceInverse else colors.surface).copy(alpha = alpha))
                                .then(if (label == "Legs") Modifier.tapHint(legsTap) else Modifier)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(label, style = AppType.tileName, color = ink.copy(alpha = alpha))
                            Text(count, style = AppType.tileCount, color = (if (featured) ink.copy(alpha = 0.7f) else colors.textMuted).copy(alpha = alpha))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        MiniTabBar(library = true)
    }
}

@Composable
private fun MiniFiltersSheet(rise: Float, dumbbell: Boolean, chipTap: Float) {
    val colors = LocalAppColors.current
    MiniSheet(rise) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Filters", Modifier.weight(1f))
            Text("Clear all", style = AppType.chip, color = colors.textMuted)
        }
        Text("Equipment", style = AppType.cardMeta, color = colors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniChip("Bodyweight", selected = false)
            MiniChip("Dumbbell", selected = dumbbell, modifier = Modifier.tapHint(chipTap))
            MiniChip("Barbell", selected = false)
        }
        Text("Pattern", style = AppType.cardMeta, color = colors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniChip("Squat", selected = false)
            MiniChip("Lunge", selected = false)
            MiniChip("Hinge", selected = false)
        }
        Spacer(Modifier.height(4.dp))
        MiniButton(if (dumbbell) "SHOW 4 EXERCISES" else "SHOW 6 EXERCISES", Modifier.fillMaxWidth())
    }
}

@Composable
private fun MiniCategory(exerciseTap: Float = 0f) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize().background(colors.background).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MiniHeader("LEGS")
        listOf("Reverse lunge", "Walking lunge", "Split squat", "Lateral lunge").forEachIndexed { i, name ->
            HairlineCard(Modifier.fillMaxWidth().then(if (i == 0) Modifier.tapHint(exerciseTap) else Modifier)) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = AppType.cardTitle, color = colors.onBackground)
                        Text("Strength · Dumbbell", style = AppType.cardMeta, color = colors.textMuted)
                    }
                    Text("+", style = AppType.heroName, color = colors.onBackground)
                }
            }
        }
    }
}

@Composable
private fun MiniExerciseDetail(link: String, savedLink: Boolean, demoTap: Float, saveTap: Float) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize().background(colors.background).padding(horizontal = 20.dp)) {
        MiniHeader("EXERCISE")
        Text("Reverse lunge", style = AppType.repsExerciseName, color = colors.onBackground)
        Spacer(Modifier.height(14.dp))
        SectionLabel("Tags")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniChip("Legs", selected = true)
            MiniChip("Strength", selected = false)
            MiniChip("Dumbbell", selected = false)
            MiniChip("Lunge", selected = false)
        }
        Spacer(Modifier.height(20.dp))
        MiniButton("▸  WATCH DEMO", Modifier.fillMaxWidth().tapHint(demoTap))
        Spacer(Modifier.height(8.dp))
        Text(
            if (savedLink) DemoLink else "Searches YouTube for “reverse lunge”",
            style = AppType.cardMeta,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(22.dp))
        SectionLabel("Demo video")
        Spacer(Modifier.height(10.dp))
        HairlineCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Paste a YouTube link to pin a specific demo.", style = AppType.cardMeta, color = colors.textMuted)
                Spacer(Modifier.height(10.dp))
                MiniField(link, "https://youtube.com/…", style = AppType.cardMeta)
                Spacer(Modifier.height(10.dp))
                MiniButton("SAVE LINK", Modifier.fillMaxWidth().tapHint(saveTap))
            }
        }
    }
}

/** The ⋯ menu on a Home card. */
@Composable
private fun MiniMenu(editTap: Float, deleteTap: Float) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(vertical = 6.dp),
    ) {
        Text("Edit", style = AppType.cardTitle, color = colors.onSurface, modifier = Modifier.fillMaxWidth().tapHint(editTap).padding(horizontal = 18.dp, vertical = 12.dp))
        Text("Delete", style = AppType.cardTitle, color = colors.onSurface, modifier = Modifier.fillMaxWidth().tapHint(deleteTap).padding(horizontal = 18.dp, vertical = 12.dp))
    }
}

@Composable
private fun MiniDialog(title: String, body: String, confirm: String, dismiss: String, appear: Float, confirmTap: Float) {
    val colors = LocalAppColors.current
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f * appear)))
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.surface.copy(alpha = appear))
                .padding(24.dp),
        ) {
            Text(title, style = AppType.cardTitle, color = colors.onBackground.copy(alpha = appear))
            Spacer(Modifier.height(10.dp))
            Text(body, style = AppType.cardMeta, color = colors.textMuted.copy(alpha = appear))
            Spacer(Modifier.height(20.dp))
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(dismiss, style = AppType.chip, color = colors.onBackground.copy(alpha = appear))
                Text(confirm, style = AppType.chip, color = colors.onBackground.copy(alpha = appear), modifier = Modifier.tapHint(confirmTap))
            }
        }
    }
}

/** A plain browser window: an address bar over a web page. */
@Composable
private fun MiniBrowser(page: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Box(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.background).padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Text("platform.claude.com", style = AppType.cardMeta, color = colors.textMuted)
            }
        }
        Column(Modifier.padding(24.dp)) { page() }
    }
}
