package com.ottotech.workoutbuilder.ui.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ottotech.workoutbuilder.domain.ItemMode
import com.ottotech.workoutbuilder.domain.MoveDirection
import com.ottotech.workoutbuilder.domain.Rest
import com.ottotech.workoutbuilder.domain.toCleanString
import com.ottotech.workoutbuilder.domain.RestSlot
import com.ottotech.workoutbuilder.domain.Section
import com.ottotech.workoutbuilder.domain.TrainingType
import com.ottotech.workoutbuilder.domain.WorkoutItem
import com.ottotech.workoutbuilder.ui.components.AppTextField
import com.ottotech.workoutbuilder.ui.components.Chip
import com.ottotech.workoutbuilder.ui.components.ConfirmDialog
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.components.InverseButton
import com.ottotech.workoutbuilder.ui.components.ScreenHeader
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

@Composable
fun BuilderScreen(
    viewModel: BuilderViewModel,
    onClose: () -> Unit,
    onAddExercise: (sectionId: String) -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current

    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = state.hasUnsavedChanges

    // One exit path for both the header chevron and the system Back gesture, so they cannot
    // diverge. An untouched draft closes straight away — prompting on an empty form is noise.
    val requestClose = { if (dirty) { confirmDiscard = true } else { onClose() } }

    BackHandler(enabled = dirty) { confirmDiscard = true }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = if (state.isEditing) "Edit" else "Build", onBack = requestClose)
        // An edit renders no form until its saved workout arrives (a few ms), so there is never
        // a blank draft on screen to type into and lose.
        if (state.isLoading) return@Column

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Dimens.screenPadding, end = Dimens.screenPadding, bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            item {
                AppTextField(
                    value = state.workout.name,
                    onValueChange = viewModel::onRename,
                    placeholder = "Workout name",
                    textStyle = AppType.cardTitle,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { TypeRow(selected = state.workout.type, onSelect = viewModel::onSetType) }

            items(state.workout.sections, key = { it.id }) { section ->
                SectionCard(
                    state = state,
                    section = section,
                    viewModel = viewModel,
                    onAddExercise = { onAddExercise(section.id) },
                    modifier = Modifier.animateItem(),
                )
            }

            item {
                InverseButton(
                    text = "+ Add section",
                    style = AppType.primaryButton,
                    height = Dimens.primaryButtonHeight,
                    container = colors.surface,
                    content = colors.onSurface,
                    onClick = viewModel::onAddSection,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Undo bar sits above the save button while a snapshot is live, then self-expires.
        if (state.canUndo) {
            LaunchedEffect(Unit) {
                delay(5_000)
                viewModel.onUndoExpired()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(Dimens.radiusButton))
                    .background(colors.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Deleted", style = AppType.cardMeta, color = colors.onSurface)
                Box(
                    modifier = Modifier
                        .defaultMinSize(
                            minWidth = Dimens.minTouchTarget,
                            minHeight = Dimens.minTouchTarget,
                        )
                        .clip(RoundedCornerShape(Dimens.radiusChip))
                        .clickable(onClick = viewModel::onUndo),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text("Undo", style = AppType.chip, color = colors.onSurface)
                }
            }
        }

        if (!state.canSave) {
            Text(
                text = state.saveBlockers.joinToString(" · "),
                style = AppType.cardMeta,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding)
                    .padding(bottom = 6.dp),
            )
        }

        SaveButton(enabled = state.canSave, onClick = { viewModel.save(onSaved) })

        if (confirmDiscard) {
            ConfirmDialog(
                title = if (state.isEditing) "Discard your changes?" else "Discard this workout?",
                body = if (state.isEditing) {
                    "The saved workout stays as it was."
                } else {
                    "It hasn't been saved. Everything you've added will be lost."
                },
                confirmLabel = "Discard",
                dismissLabel = "Keep editing",
                onConfirm = { confirmDiscard = false; onClose() },
                onDismiss = { confirmDiscard = false },
            )
        }
    }
}

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    InverseButton(
        text = "SAVE WORKOUT",
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenPadding)
            .padding(bottom = 12.dp),
    )
}

@Composable
private fun TypeRow(selected: TrainingType, onSelect: (TrainingType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        TrainingType.entries.forEach { type ->
            Chip(text = type.label, selected = type == selected) { onSelect(type) }
        }
    }
}

@Composable
private fun SectionCard(
    state: BuilderUiState,
    section: Section,
    viewModel: BuilderViewModel,
    onAddExercise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    HairlineCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(Dimens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppTextField(
                    value = section.name,
                    onValueChange = { viewModel.onRenameSection(section.id, it) },
                    placeholder = "Section name",
                    textStyle = AppType.cardTitle,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                GlyphButton("↑") { viewModel.onMoveSection(section.id, MoveDirection.UP) }
                GlyphButton("↓") { viewModel.onMoveSection(section.id, MoveDirection.DOWN) }
                GlyphButton("✕") { viewModel.onRemoveSection(section.id) }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rounds", style = AppType.cardMeta, color = colors.textMuted, modifier = Modifier.weight(1f))
                Stepper(value = section.rounds, min = 1, step = 1) { viewModel.onSetRounds(section.id, it) }
            }

            Spacer(Modifier.height(12.dp))
            RestControl("Between exercises", section.restBetweenExercises) {
                viewModel.onSetRest(section.id, RestSlot.BETWEEN_EXERCISES, it)
            }
            RestControl("Between rounds", section.restBetweenRounds) {
                viewModel.onSetRest(section.id, RestSlot.BETWEEN_ROUNDS, it)
            }
            RestControl("After section", section.restAfterSection) {
                viewModel.onSetRest(section.id, RestSlot.AFTER_SECTION, it)
            }

            if (section.items.isNotEmpty()) Spacer(Modifier.height(8.dp))
            section.items.forEach { item ->
                ItemRow(state = state, sectionId = section.id, item = item, viewModel = viewModel)
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = Dimens.minTouchTarget)
                    .clip(RoundedCornerShape(Dimens.radiusChip))
                    .background(colors.surface)
                    .clickable(onClick = onAddExercise)
                    .padding(horizontal = 13.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("+ Add exercise", style = AppType.chip, color = colors.onBackground)
            }
        }
    }
}

@Composable
private fun ItemRow(
    state: BuilderUiState,
    sectionId: String,
    item: WorkoutItem,
    viewModel: BuilderViewModel,
) {
    val colors = LocalAppColors.current

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(Dimens.radiusChip))
            .background(colors.surface)
            .padding(Dimens.cardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.exerciseName(item.exerciseId),
                style = AppType.cardTitle,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            GlyphButton("↑") { viewModel.onMoveItem(sectionId, item.id, MoveDirection.UP) }
            GlyphButton("↓") { viewModel.onMoveItem(sectionId, item.id, MoveDirection.DOWN) }
            GlyphButton("✕") { viewModel.onRemoveItem(sectionId, item.id) }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ItemMode.entries.forEach { mode ->
                Chip(text = mode.chipLabel(), selected = mode == item.mode) {
                    viewModel.onSetItemMode(sectionId, item.id, mode)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        when (item.mode) {
            ItemMode.TIMED -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Duration", style = AppType.cardMeta, color = colors.textMuted, modifier = Modifier.weight(1f))
                Stepper(value = item.durationSec ?: 30, min = 5, step = 5, label = { "${it}s" }) {
                    viewModel.onSetDuration(sectionId, item.id, it)
                }
            }

            ItemMode.REPS -> Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sets", style = AppType.cardMeta, color = colors.textMuted, modifier = Modifier.weight(1f))
                    Stepper(value = item.sets ?: 3, min = 1, step = 1) {
                        viewModel.onSetSetsReps(sectionId, item.id, it, item.reps ?: 10)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reps", style = AppType.cardMeta, color = colors.textMuted, modifier = Modifier.weight(1f))
                    Stepper(value = item.reps ?: 10, min = 1, step = 1) {
                        viewModel.onSetSetsReps(sectionId, item.id, item.sets ?: 3, it)
                    }
                }
                Spacer(Modifier.height(8.dp))
                WeightField(item = item) { viewModel.onSetWeight(sectionId, item.id, it) }
            }

            ItemMode.MANUAL -> Text(
                "Tap Done to advance — no timer.",
                style = AppType.cardMeta,
                color = colors.textMuted,
            )
        }

        Spacer(Modifier.height(10.dp))
        AppTextField(
            value = item.note.orEmpty(),
            onValueChange = { viewModel.onSetNote(sectionId, item.id, it) },
            placeholder = "Note (optional)",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun ItemMode.chipLabel(): String = when (this) {
    ItemMode.TIMED -> "Timed"
    ItemMode.REPS -> "Reps"
    ItemMode.MANUAL -> "Manual"
}

/**
 * Weight is optional and decimal, so it is a text field kept in local state — driving it from
 * the draft would re-parse mid-entry and eat a trailing decimal point. Blank clears it.
 */
@Composable
private fun WeightField(item: WorkoutItem, onChange: (Float?) -> Unit) {
    var text by remember(item.id) {
        mutableStateOf(item.weightKg?.toCleanString() ?: "")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Weight (kg)", style = AppType.cardMeta, color = LocalAppColors.current.textMuted, modifier = Modifier.weight(1f))
        AppTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it.toFloatOrNull())
            },
            placeholder = "—",
            modifier = Modifier.size(width = 96.dp, height = Dimens.searchFieldHeight),
        )
    }
}

/** A `−  value  +` control. Whole-number fields only; the label formats it. */
@Composable
private fun Stepper(
    value: Int,
    min: Int,
    step: Int,
    label: (Int) -> String = { it.toString() },
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlyphButton("−") { onChange((value - step).coerceAtLeast(min)) }
        Text(label(value), style = AppType.cardTitle, color = LocalAppColors.current.onBackground)
        GlyphButton("+") { onChange(value + step) }
    }
}

/** Off / Timed / Manual segmented control; when Timed, a seconds stepper follows. */
@Composable
private fun RestControl(label: String, rest: Rest?, onChange: (Rest?) -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = AppType.cardMeta, color = colors.textMuted)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Chip("Off", selected = rest == null) { onChange(null) }
            Chip("Timed", selected = rest is Rest.Timed) {
                onChange(Rest.Timed((rest as? Rest.Timed)?.seconds ?: 30))
            }
            Chip("Manual", selected = rest is Rest.Manual) { onChange(Rest.Manual) }
        }
        if (rest is Rest.Timed) {
            Spacer(Modifier.height(6.dp))
            Stepper(value = rest.seconds, min = 5, step = 5, label = { "${it}s" }) {
                onChange(Rest.Timed(it))
            }
        }
    }
}

/** A single text glyph in a `surface` square — the app's minus/plus/arrow/close affordance. */
@Composable
private fun GlyphButton(glyph: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(Dimens.glyphButtonSize)
            .clip(RoundedCornerShape(Dimens.radiusChip))
            .background(colors.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = AppType.cardTitle, color = colors.onSurface)
    }
}
