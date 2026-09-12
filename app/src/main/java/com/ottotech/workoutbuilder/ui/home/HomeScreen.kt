package com.ottotech.workoutbuilder.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ottotech.workoutbuilder.domain.WorkoutSummary
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.components.InverseButton
import com.ottotech.workoutbuilder.ui.components.SectionLabel
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home offers two options and does not rank: choose a workout, or build one.
 *
 * The old "Up next" hero was removed 2026-07-26. It presented itself as a recommendation but
 * could only ever resolve to "the last workout you created" — nothing writes `lastPerformedAt`
 * until Plan 1D — and the user does not train in a fixed rotation, so even a working version
 * would have promised a schedule the app does not have. See
 * docs/superpowers/plans/2026-07-26-design-correction-pass.md.
 */
@Composable
fun HomeScreen(
    onStartWorkout: (String) -> Unit,
    onBuild: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Read once per composition rather than per card — every card would otherwise get a
    // slightly different "now", and none of them need second-level accuracy.
    val now = remember { Instant.now() }

    Column(Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Dimens.screenPadding,
                end = Dimens.screenPadding,
                top = 16.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            item { Header() }

            // While loading, the header stands alone rather than flashing an empty state.
            // In practice this is at most one frame — MainActivity gates on seeding.
            if (state.isEmpty) {
                item { EmptyCard() }
            } else if (state.workouts.isNotEmpty()) {
                item { SectionLabel("Choose a workout") }
                items(state.workouts, key = { it.id }) { summary ->
                    WorkoutCard(
                        summary = summary,
                        now = now,
                        onClick = { onStartWorkout(summary.id) },
                    )
                }
            }
        }

        // The second of Home's two options, pinned above the bottom nav rather than scrolling
        // away — the handoff calls it "sticky-feeling".
        InverseButton(
            text = "BUILD A WORKOUT",
            leadingGlyph = "+",
            onClick = onBuild,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenPadding)
                .padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun Header() {
    val colors = LocalAppColors.current
    val overline = remember {
        LocalDate.now()
            .format(DateTimeFormatter.ofPattern("EEE · MMM d", Locale.getDefault()))
            .uppercase(Locale.getDefault())
    }
    Column {
        Text(overline, style = AppType.overline, color = colors.textMuted)
        Spacer(Modifier.height(8.dp))
        Text("YOUR\nWORKOUTS", style = AppType.screenTitle, color = colors.onBackground)
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * One workout, equal in weight to every other. Both rows push their content to the card's two
 * edges, so the card is filled by construction rather than left-heavy with dead space.
 */
@Composable
private fun WorkoutCard(summary: WorkoutSummary, now: Instant, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    HairlineCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(Dimens.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    summary.name,
                    style = AppType.cardTitle,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(summary.minutesLabel, style = AppType.cardTitle, color = colors.onBackground)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(summary.shortMeta, style = AppType.cardMeta, color = colors.textMuted)
                Text(summary.lastDoneLine(now), style = AppType.cardMeta, color = colors.textMuted)
            }
        }
    }
}

/**
 * Spec §6.1 gives the empty state a designed treatment so a fresh install looks intentional
 * rather than broken. It names the one option that is still available.
 */
@Composable
private fun EmptyCard() {
    val colors = LocalAppColors.current
    HairlineCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(Dimens.cardPadding)) {
            Text("No workouts yet", style = AppType.cardTitle, color = colors.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(
                "Build one from the library and it will appear here.",
                style = AppType.cardMeta,
                color = colors.textMuted,
            )
        }
    }
}
