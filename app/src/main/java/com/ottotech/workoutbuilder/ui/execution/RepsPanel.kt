package com.ottotech.workoutbuilder.ui.execution

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ottotech.workoutbuilder.domain.Segment
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.ExecutionColors

/** How many rows show before the list scrolls internally, rather than growing the screen. */
private const val MAX_VISIBLE_ROWS = 4

/**
 * REPS mode (spec §6.6): a `Chest · Barbell` sub-line, a watch-demo card (rendered by
 * [ExecutionScreen] itself, same as every other WORK segment), a `SETS` label and one
 * checkable row per set.
 *
 * The exercise name itself is deliberately not repeated here: [ExecutionScreen] already
 * renders `segment.exerciseName` (in [AppType.moveName]) plus the muscle tag above the
 * mode-specific content for every segment, TIMED/MANUAL/REPS alike — this panel is that
 * content for REPS, the same role [TimerRing] plays for TIMED/MANUAL, and neither of those
 * repeats the name either. Rendering it again here (as the plan's sample code, written
 * before that shared header existed, did with [AppType.repsExerciseName]) showed the same
 * exercise name twice on screen; confirmed on-device and removed.
 *
 * There is also deliberately no advance control in this panel: `ExecutionControls` already
 * renders one "Done ›" button for REPS segments (spec §6.5), sitting next to Pause where a
 * person's thumb already is on every other segment. A second, identical button here (as the
 * plan's sample code also had) would fire the same [ExecutionViewModel.complete] twice over
 * for no reason — see the Task 5 review carry-over note.
 */
@Composable
fun RepsPanel(segment: Segment) {
    // Which sets are ticked is screen-local: the service and the database have no use for
    // it, and it must reset when the exercise changes, which keying on the segment's index
    // (unique per compiled segment) gives for free.
    val checked = remember(segment.index) {
        mutableStateListOf<Boolean>().apply {
            addAll(List((segment.sets ?: 1).coerceAtLeast(1)) { false })
        }
    }

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val subLine = listOfNotNull(segment.primaryMuscle?.label, segment.equipmentLabel)
            .joinToString(" · ")
        if (subLine.isNotBlank()) {
            Text(
                subLine,
                style = AppType.cardMeta,
                color = ExecutionColors.textMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Text(
            "SETS",
            style = AppType.heroLabel,
            color = ExecutionColors.textMuted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 4.dp),
        )
        // Bounded and independently scrollable, so an unusually large sets value (the
        // Builder's stepper has no maximum) grows this list only up to MAX_VISIBLE_ROWS
        // rows before it scrolls internally, rather than growing the whole screen and
        // pushing the watch-demo/up-next cards and the Pause/Done row off-screen.
        // ExecutionScreen's outer Column is deliberately not scrollable itself — it relies
        // on Spacer(Modifier.weight(1f)), which does not coexist with a scrollable parent.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = Dimens.minTouchTarget * MAX_VISIBLE_ROWS)
                .verticalScroll(rememberScrollState()),
        ) {
            repeat(checked.size) { i ->
                SetRow(
                    label = setRowLabel(segment, i),
                    done = checked[i],
                    onToggle = { checked[i] = !checked[i] },
                )
            }
        }
    }
}

private fun setRowLabel(segment: Segment, i: Int): String {
    val reps = segment.reps?.let { "$it reps" }
    val weight = segment.weightKg?.let { "${it.toCleanString()}kg" }
    val detail = listOfNotNull(reps, weight).joinToString(" · ")
    return if (detail.isBlank()) "Set ${i + 1}" else "Set ${i + 1} · $detail"
}

/** Trims a trailing `.0` off a whole-number weight without dropping a real fraction (e.g. 7.5). */
private fun Float.toCleanString(): String =
    if (this == this.toLong().toFloat()) this.toLong().toString() else this.toString()

@Composable
private fun SetRow(label: String, done: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.minTouchTarget)
            // clip() before clickable() so the ripple follows the row's shape rather than
            // drawing square outside it.
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription = label
                toggleableState = if (done) ToggleableState.On else ToggleableState.Off
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SetCheckbox(checked = done)
            Text(
                label,
                style = AppType.setRowLabel,
                color = if (done) ExecutionColors.textMuted else ExecutionColors.onBackground,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
        }
    }
}

@Composable
private fun SetCheckbox(checked: Boolean) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(shape)
            .then(
                if (checked) {
                    Modifier.background(ExecutionColors.ringProgress)
                } else {
                    Modifier.border(1.dp, ExecutionColors.borderStrong, shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text(
                "✓",
                style = AppType.chip,
                color = ExecutionColors.background,
            )
        }
    }
}
