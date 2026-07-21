package com.ottotech.workoutbuilder.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ottotech.workoutbuilder.domain.WorkoutSummary
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.components.InverseButton
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    onStartWorkout: (String) -> Unit,
    onBuild: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val upNext = state.upNext

    Column(Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Dimens.screenPadding,
                end = Dimens.screenPadding,
                top = 14.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            item { Header() }

            // While loading, the header stands alone rather than flashing an empty state.
            // In practice this is at most one frame — MainActivity gates on seeding.
            if (state.isEmpty) {
                item { EmptyHero() }
            } else if (upNext != null) {
                item { UpNextHero(upNext, onStart = { onStartWorkout(upNext.id) }) }
                items(state.others, key = { it.id }) { summary ->
                    WorkoutRow(summary, onClick = { onStartWorkout(summary.id) })
                }
            }
        }

        // Pinned above the bottom nav rather than scrolling away — the handoff calls it
        // "sticky-feeling", and it is the screen's only creative action.
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
        Spacer(Modifier.height(6.dp))
        Text("YOUR\nWORKOUTS", style = AppType.screenTitle, color = colors.onBackground)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun UpNextHero(summary: WorkoutSummary, onStart: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusHero))
            .background(colors.surfaceInverse)
            .padding(Dimens.heroPadding),
    ) {
        // Mono accent: on an inverse surface the accent is onSurfaceInverse.
        Text("UP NEXT", style = AppType.heroLabel, color = colors.onSurfaceInverse)
        Spacer(Modifier.height(8.dp))
        Text(summary.name, style = AppType.heroName, color = colors.onSurfaceInverse)
        Spacer(Modifier.height(4.dp))
        Text(summary.metaLine, style = AppType.heroMeta, color = colors.textMuted)
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.primaryButtonHeight)
                .clip(RoundedCornerShape(Dimens.radiusButton))
                .background(colors.onSurfaceInverse)
                .clickable(onClick = onStart),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Start workout ›", style = AppType.primaryButton, color = colors.surfaceInverse)
        }
    }
}

/**
 * Spec §6.1: the empty state uses the hero treatment so a fresh install looks designed
 * rather than broken.
 */
@Composable
private fun EmptyHero() {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusHero))
            .background(colors.surfaceInverse)
            .padding(Dimens.heroPadding),
    ) {
        Text("NOTHING YET", style = AppType.heroLabel, color = colors.onSurfaceInverse)
        Spacer(Modifier.height(8.dp))
        Text(
            "Nothing here yet. Build one from the library, or let the Coach draft one.",
            style = AppType.heroMeta,
            color = colors.onSurfaceInverse,
        )
    }
}

@Composable
private fun WorkoutRow(summary: WorkoutSummary, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    HairlineCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.badgeSize)
                    .clip(RoundedCornerShape(Dimens.radiusChip))
                    .background(colors.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    summary.estimatedMinutes.toString(),
                    style = AppType.cardTitle,
                    color = colors.onSurface,
                )
            }
            Column {
                Text(summary.name, style = AppType.cardTitle, color = colors.onBackground)
                Spacer(Modifier.height(2.dp))
                Text(summary.metaLine, style = AppType.cardMeta, color = colors.textMuted)
            }
        }
    }
}
