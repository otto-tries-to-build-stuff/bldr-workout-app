package com.ottotech.workoutbuilder.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.Section
import com.ottotech.workoutbuilder.domain.WorkoutItem
import com.ottotech.workoutbuilder.domain.WorkoutOverview
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.components.InverseButton
import com.ottotech.workoutbuilder.ui.components.ScreenHeader
import com.ottotech.workoutbuilder.ui.components.TextAction
import com.ottotech.workoutbuilder.ui.demoIntent
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

/**
 * The stop between Home and the timer: the whole workout at a glance, a demo link per
 * exercise, and Edit / Start. Read-only — every change goes through the Builder.
 */
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onStart: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current

    // Nothing on this screen deletes, but a workout can't be shown once it's gone.
    if (!state.loading && state.workout == null) {
        LaunchedEffect(Unit) { onBack() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ScreenHeader(title = "Workout", onBack = onBack)

        val workout = state.workout ?: return@Column
        val summary = state.summary ?: return@Column

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            item {
                Column {
                    Text(workout.name, style = AppType.heroName, color = colors.onBackground)
                    Spacer(Modifier.height(4.dp))
                    Text("${summary.minutesLabel} · ${summary.shortMeta}", style = AppType.cardMeta, color = colors.textMuted)
                }
            }
            items(workout.sections, key = { it.id }) { section ->
                SectionCard(section, state.exercises)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            InverseButton(
                text = "Edit",
                height = Dimens.primaryButtonHeight,
                style = AppType.primaryButton,
                container = colors.surface,
                content = colors.onSurface,
                onClick = onEdit,
                modifier = Modifier.weight(1f),
            )
            InverseButton(
                text = "START",
                height = Dimens.primaryButtonHeight,
                style = AppType.primaryButton,
                // An empty workout has nothing to run; the timer would finish it instantly.
                enabled = summary.exerciseCount > 0,
                onClick = onStart,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SectionCard(section: Section, exercises: Map<String, Exercise>) {
    val colors = LocalAppColors.current
    HairlineCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = Dimens.cardPadding, end = Dimens.cardPadding, top = Dimens.cardPadding, bottom = 10.dp)) {
            Row {
                Text(section.name, style = AppType.cardTitle, color = colors.onBackground, modifier = Modifier.weight(1f))
                if (section.rounds > 1) {
                    Text("× ${section.rounds} rounds", style = AppType.cardMeta, color = colors.textMuted)
                }
            }
            WorkoutOverview.restLine(section)?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = AppType.cardMeta, color = colors.textMuted)
            }
            Spacer(Modifier.height(6.dp))
            section.items.forEach { item -> ItemRow(item, exercises[item.exerciseId]) }
        }
    }
}

/** Name and amount on the left, Demo on the right. A since-deleted exercise has no demo to offer. */
@Composable
private fun ItemRow(item: WorkoutItem, exercise: Exercise?) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(exercise?.name ?: "Removed exercise", style = AppType.setRowLabel, color = colors.onBackground)
            Text(WorkoutOverview.itemDetail(item), style = AppType.cardMeta, color = colors.textMuted)
            item.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = AppType.cardMeta, color = colors.textMuted)
            }
        }
        if (exercise != null) {
            Spacer(Modifier.width(8.dp))
            TextAction(
                "Demo",
                onClick = { context.startActivity(demoIntent(exercise)) },
                modifier = Modifier.semantics { contentDescription = "Watch a demo of ${exercise.name}" },
            )
        }
    }
}
