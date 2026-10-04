package com.ottotech.workoutbuilder.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ottotech.workoutbuilder.ui.brand.BldrMark
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.components.ScreenTitle
import com.ottotech.workoutbuilder.ui.components.SectionLabel
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

/*
 * One scene per how-to topic. A scene reads the clock's position and draws the mini-screens
 * for it; which beat shows what is spelled out here, the timings live in HelpContent.kt.
 */

@Composable
fun TopicScene(topic: HelpTopic, clock: ScriptClock) {
    when (topic) {
        HelpTopic.BuildYourself -> BuildYourselfScene(clock)
        else -> ComingSoonScene()
    }
}

// ---- Build a workout yourself ----------------------------------------------------------------

@Composable
private fun BuildYourselfScene(clock: ScriptClock) {
    val pos = clock.position
    val saved = pos.reached(BuildBeat.Saved)
    // The Builder slides in over Home as naming starts, and back out once saved.
    val builderIn = if (saved) 1f - ease(window(pos.progressOf(BuildBeat.Saved), 0f, 0.25f))
    else ease(window(pos.progressOf(BuildBeat.Name), 0f, 0.25f))

    Box(Modifier.fillMaxSize()) {
        MiniHome(
            newWorkout = if (saved) ease(window(pos.progressOf(BuildBeat.Saved), 0.25f, 0.55f)) else 0f,
            buildTap = pos.during(BuildBeat.TapBuild),
        )
        if (pos.reached(BuildBeat.Sheet) && !pos.reached(BuildBeat.Name)) {
            MiniBuildSheet(rise = ease(pos.progressOf(BuildBeat.Sheet)), tapYourself = pos.during(BuildBeat.TapYourself))
        }
        if (builderIn > 0f) MiniBuilder(pos, Modifier.slideX(1f - builderIn))
    }
}

/** Home with two saved workouts; [newWorkout] grows "Leg day" in above them. */
@Composable
private fun MiniHome(newWorkout: Float, buildTap: Float) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize().background(colors.background).padding(20.dp)) {
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
        if (newWorkout > 0f) {
            Column(Modifier.reveal(newWorkout)) {
                MiniWorkoutCard("Leg day", "24 MIN", "2 exercises · Strength", "Never done")
                Spacer(Modifier.height(12.dp))
            }
        }
        MiniWorkoutCard("Court Speed", "22 MIN", "10 exercises · HIIT", "1 week ago")
        Spacer(Modifier.height(12.dp))
        MiniWorkoutCard("Upper body", "35 MIN", "8 exercises · Strength", "Yesterday")
        Spacer(Modifier.weight(1f))
        MiniButton("+  BUILD A WORKOUT", Modifier.fillMaxWidth().tapHint(buildTap))
    }
}

/** The Build sheet rising over a dimmed Home. */
@Composable
private fun MiniBuildSheet(rise: Float, tapYourself: Float) {
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
        ) {
            SectionLabel("Build a workout")
            Spacer(Modifier.height(2.dp))
            MiniOption("Build it yourself", "Pick exercises from the library", Modifier.tapHint(tapYourself))
            MiniOption("Ask the Coach", "Describe your goal, get a workout drafted")
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MiniBuilder(pos: ScenePosition, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val name = "Leg day"
    val typed = (name.length * window(pos.progressOf(BuildBeat.Name), 0.3f, 0.85f)).toInt()
    // A tap's change lands as the fingertip presses, not when the beat starts.
    val timed = pos.progressOf(BuildBeat.TapTimed) > 0.4f
    val rounds = if (pos.progressOf(BuildBeat.TapRounds) > 0.4f) "3" else "1"
    val restOn = pos.progressOf(BuildBeat.TapRest) > 0.4f
    val added = pos.progressOf(BuildBeat.Added)

    Column(modifier.fillMaxSize().background(colors.background).padding(horizontal = 20.dp)) {
        MiniHeader("BUILD")
        MiniField(name.take(typed), "Workout name", typing = pos.key == BuildBeat.Name)
        Spacer(Modifier.height(12.dp))
        HairlineCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                MiniField("Main", "Section name")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rounds", style = AppType.cardMeta, color = colors.textMuted, modifier = Modifier.weight(1f))
                    MiniStepper(rounds, plusTap = pos.during(BuildBeat.TapRounds))
                }
                Spacer(Modifier.height(10.dp))
                Text("Between rounds", style = AppType.cardMeta, color = colors.textMuted)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniChip("Off", selected = !restOn)
                    MiniChip("Timed", selected = restOn, modifier = Modifier.tapHint(pos.during(BuildBeat.TapRest)))
                    MiniChip("Manual", selected = false)
                    if (restOn) {
                        Spacer(Modifier.weight(1f))
                        Text("60s", style = AppType.cardTitle, color = colors.onBackground)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (added > 0f) {
                    MiniItem(
                        "Goblet squat",
                        timed = timed,
                        timedTap = pos.during(BuildBeat.TapTimed),
                        modifier = Modifier.reveal(ease(window(added, 0f, 0.5f))),
                    )
                    MiniItem("Reverse lunge", timed = false, modifier = Modifier.reveal(ease(window(added, 0.35f, 0.85f))))
                }
                MiniChip("+ Add exercise", selected = false, modifier = Modifier.tapHint(pos.during(BuildBeat.TapAdd)))
            }
        }
        Spacer(Modifier.weight(1f))
        MiniButton("SAVE WORKOUT", Modifier.fillMaxWidth().tapHint(pos.during(BuildBeat.TapSave)))
        Spacer(Modifier.height(14.dp))
    }
}

/** An exercise in the Builder: name and its time or sets × reps, over the mode chips. */
@Composable
private fun MiniItem(name: String, timed: Boolean, modifier: Modifier = Modifier, timedTap: Float = 0f) {
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
                Text(name, style = AppType.cardTitle, color = colors.onSurface, modifier = Modifier.weight(1f))
                Text(if (timed) "30s" else "3 × 10", style = AppType.cardTitle, color = colors.onSurface)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniChip("Timed", selected = timed, container = colors.background, modifier = Modifier.tapHint(timedTap))
                MiniChip("Reps", selected = !timed, container = colors.background)
                MiniChip("Manual", selected = false, container = colors.background)
            }
        }
    }
}

// ---- Placeholder until each topic's scene is drawn -------------------------------------------

@Composable
private fun ComingSoonScene() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BldrMark(height = 64.dp, contentDescription = null)
    }
}
