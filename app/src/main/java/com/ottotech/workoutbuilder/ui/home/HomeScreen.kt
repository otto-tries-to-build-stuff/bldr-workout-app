package com.ottotech.workoutbuilder.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ottotech.workoutbuilder.domain.WorkoutSummary
import com.ottotech.workoutbuilder.ui.components.ConfirmDialog
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.components.InverseButton
import com.ottotech.workoutbuilder.ui.components.SectionLabel
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    onEditWorkout: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onBuild: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Read once per composition rather than per card — every card would otherwise get a
    // slightly different "now", and none of them need second-level accuracy. The zone is
    // read alongside it so "Today" / "Yesterday" line up with the device's calendar day.
    val now = remember { Instant.now() }
    val zone = remember { ZoneId.systemDefault() }
    // Held by id, not by summary, so it survives rotation; resolved against the live list so
    // a workout that vanished meanwhile simply closes the dialog.
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

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
            item { Header(onOpenSettings) }

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
                        zone = zone,
                        onClick = { onStartWorkout(summary.id) },
                        onEdit = { onEditWorkout(summary.id) },
                        onDelete = { pendingDeleteId = summary.id },
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

    pendingDeleteId?.let { id -> state.workouts.firstOrNull { it.id == id } }?.let { target ->
        ConfirmDialog(
            title = "Delete “${target.name}”?",
            body = "This can't be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            onConfirm = {
                viewModel.delete(target.id)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    val colors = LocalAppColors.current
    val overline = remember {
        LocalDate.now()
            .format(DateTimeFormatter.ofPattern("EEE · MMM d", Locale.getDefault()))
            .uppercase(Locale.getDefault())
    }
    Column {
        // The Settings link sits on the date line so the title keeps its full width. Its 48 dp
        // box is taller than the overline; the row absorbs that instead of the title spacing.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(overline, style = AppType.overline, color = colors.textMuted)
            Box(
                modifier = Modifier
                    .size(width = 88.dp, height = Dimens.minTouchTarget)
                    .clip(RoundedCornerShape(Dimens.radiusChip))
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("Settings", style = AppType.chip, color = colors.textMuted)
            }
        }
        Text("YOUR\nWORKOUTS", style = AppType.screenTitle, color = colors.onBackground)
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * One workout, equal in weight to every other. Both rows push their content to the card's two
 * edges, so the card is filled by construction rather than left-heavy with dead space.
 */
@Composable
private fun WorkoutCard(
    summary: WorkoutSummary,
    now: Instant,
    zone: ZoneId,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAppColors.current
    HairlineCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        // The menu spans both text rows, centred, so it adds width but no height. Its 48 dp
        // box stands in for most of the card's right padding.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Dimens.cardPadding, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = Dimens.cardPadding),
            ) {
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
                    Text(
                        summary.shortMeta,
                        style = AppType.cardMeta,
                        color = colors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(summary.lastDoneLine(now, zone), style = AppType.cardMeta, color = colors.textMuted)
                }
            }
            CardMenu(name = summary.name, onEdit = onEdit, onDelete = onDelete)
        }
    }
}

/**
 * Edit and Delete live behind "⋯" rather than on the card itself: tapping the card starts
 * the workout, and a destructive action must not be one mis-tap away from that. The 48 dp
 * box gets its own click, so a tap on it never falls through to the card.
 */
@Composable
private fun CardMenu(name: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalAppColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(Dimens.minTouchTarget)
                .clip(RoundedCornerShape(Dimens.radiusChip))
                .clickable { open = true }
                .semantics { contentDescription = "More options for $name" },
            contentAlignment = Alignment.Center,
        ) {
            Text("⋯", style = AppType.cardTitle, color = colors.onBackground)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = colors.surface,
            // No shadows in this app (spec §5): a hairline edge instead, like the cards.
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            border = BorderStroke(Dimens.hairline, colors.border),
        ) {
            DropdownMenuItem(
                text = { Text("Edit", style = AppType.chip, color = colors.onBackground) },
                onClick = { open = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text("Delete", style = AppType.chip, color = colors.onBackground) },
                onClick = { open = false; onDelete() },
            )
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
