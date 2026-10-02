package com.ottotech.workoutbuilder.ui.coach

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ottotech.workoutbuilder.ai.CoachPhase
import com.ottotech.workoutbuilder.domain.Section
import com.ottotech.workoutbuilder.domain.TagVocabulary
import com.ottotech.workoutbuilder.ui.brand.BldrLoader
import com.ottotech.workoutbuilder.ui.brand.rememberSystemAnimationsOn
import com.ottotech.workoutbuilder.ui.components.AppTextField
import com.ottotech.workoutbuilder.ui.components.Chip
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.components.InverseButton
import com.ottotech.workoutbuilder.ui.components.ScreenHeader
import com.ottotech.workoutbuilder.ui.components.SectionLabel
import com.ottotech.workoutbuilder.ui.components.TextAction
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

private val LENGTHS = listOf(15, 30, 45, 60)

/**
 * Spec §6.6: describe a goal → wait → preview → Save / Regenerate / Tweak. The screen holds no
 * state of its own beyond what [CoachViewModel] exposes, so a rotation mid-request is safe.
 */
@Composable
fun CoachScreen(
    viewModel: CoachViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current

    // Coming back from Settings with a new key unlocks the form without leaving the screen.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshKey()
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ScreenHeader(title = "Coach", onBack = onBack)

        if (!state.hasKey) {
            NoKey(onOpenSettings)
            return@Column
        }

        when (val phase = state.phase) {
            CoachPhase.Compose -> ComposeGoal(state, viewModel)
            CoachPhase.Loading -> Loading()
            is CoachPhase.Preview -> Preview(state, phase, viewModel, onSaved)
            is CoachPhase.Problem -> Problem(phase, viewModel, onOpenSettings)
        }
    }
}

@Composable
private fun NoKey(onOpenSettings: () -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.padding(horizontal = Dimens.screenPadding), verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
        HairlineCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Dimens.cardPadding)) {
                Text("Add your API key first", style = AppType.cardTitle, color = colors.onBackground)
                Spacer(Modifier.height(6.dp))
                Text(
                    "The Coach uses your own Anthropic key. It's stored encrypted on this phone.",
                    style = AppType.cardMeta,
                    color = colors.textMuted,
                )
            }
        }
        PrimaryButton("OPEN SETTINGS", onClick = onOpenSettings)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ComposeGoal(state: CoachUiState, viewModel: CoachViewModel) {
    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        item {
            AppTextField(
                value = state.goal,
                onValueChange = viewModel::onGoal,
                placeholder = "What are you training for?",
                textStyle = AppType.cardTitle,
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { SectionLabel("Length") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LENGTHS.forEach { m ->
                    Chip("$m min", selected = state.minutes == m, onClick = { viewModel.onMinutes(m) })
                }
            }
        }
        item { SectionLabel("Equipment") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TagVocabulary.EQUIPMENT.forEach { e ->
                    Chip(e, selected = e in state.equipment, onClick = { viewModel.onEquipment(e) })
                }
            }
        }
    }
    PrimaryButton(
        "BUILD MY WORKOUT",
        enabled = state.goal.isNotBlank(),
        onClick = viewModel::generate,
        modifier = Modifier.padding(horizontal = Dimens.screenPadding).padding(bottom = 12.dp),
    )
}

/** Shown while the Coach drafts. Visual reassurance only: the request is one call with no real steps to report. */
private val WorkingWords = listOf(
    "Analyzing fitness objectives…",
    "Evaluating movement patterns…",
    "Selecting optimal exercises…",
    "Calibrating volume and intensity…",
    "Optimizing rest intervals…",
    "Finalizing workout structure…",
)
private const val WorkingWordMillis = 3_000L

@Composable
private fun ColumnScope.Loading() {
    val colors = LocalAppColors.current
    val animate = rememberSystemAnimationsOn()
    // Steps through the words once and holds on the last: a loop would jump from "Finalizing" back to the start.
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(WorkingWords.lastIndex) {
            delay(WorkingWordMillis)
            index++
        }
    }
    Column(
        Modifier.fillMaxWidth().weight(1f).padding(horizontal = Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BldrLoader(contentDescription = "Drafting your workout")
        Spacer(Modifier.height(24.dp))
        // Sized by invisible copies of every phrase, so a wrapping phrase never shifts the loader.
        Box(contentAlignment = Alignment.TopCenter) {
            WorkingWords.forEach {
                Text(it, style = AppType.cardTitle, textAlign = TextAlign.Center, modifier = Modifier.alpha(0f).clearAndSetSemantics {})
            }
            AnimatedContent(
                targetState = WorkingWords[index],
                transitionSpec = {
                    if (animate) fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    else EnterTransition.None togetherWith ExitTransition.None
                },
                label = "workingWords",
            ) { words ->
                Text(words, style = AppType.cardTitle, color = colors.onBackground, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("This takes 15–30 seconds.", style = AppType.cardMeta, color = colors.textMuted)
    }
}

@Composable
private fun ColumnScope.Preview(
    state: CoachUiState,
    phase: CoachPhase.Preview,
    viewModel: CoachViewModel,
    onSaved: () -> Unit,
) {
    val colors = LocalAppColors.current
    val workout = phase.result.workout
    val newIds = phase.result.newExercises.map { it.id }.toSet()

    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        item {
            Column {
                Text(workout.name, style = AppType.heroName, color = colors.onBackground)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${phase.summary.minutesLabel} · ${phase.summary.shortMeta}",
                    style = AppType.cardMeta,
                    color = colors.textMuted,
                )
            }
        }
        phase.notice?.let { notice ->
            // A failed Tweak or Regenerate: the draft below is still the last good one.
            item {
                HairlineCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Dimens.cardPadding)) {
                        Text("That didn't work — your draft is unchanged.", style = AppType.cardTitle, color = colors.onBackground)
                        Spacer(Modifier.height(4.dp))
                        Text(notice, style = AppType.cardMeta, color = colors.textMuted)
                    }
                }
            }
        }
        items(workout.sections, key = { it.id }) { section ->
            SectionCard(section, phase.exerciseNames, newIds)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppTextField(
                    value = state.tweak,
                    onValueChange = viewModel::onTweakText,
                    placeholder = "Tweak… e.g. swap lunges for step-ups",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextAction("Tweak", enabled = state.tweak.isNotBlank(), onClick = viewModel::applyTweak)
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding).padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        InverseButton(
            text = "Regenerate",
            height = Dimens.primaryButtonHeight,
            style = AppType.primaryButton,
            container = colors.surface,
            content = colors.onSurface,
            onClick = viewModel::regenerate,
            modifier = Modifier.weight(1f),
        )
        InverseButton(
            text = "SAVE",
            height = Dimens.primaryButtonHeight,
            style = AppType.primaryButton,
            onClick = { viewModel.save(onSaved) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** Collapsed: names only, no editing — edits happen in the Builder after saving. */
@Composable
private fun SectionCard(section: Section, names: Map<String, String>, newIds: Set<String>) {
    val colors = LocalAppColors.current
    HairlineCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(section.name, style = AppType.cardTitle, color = colors.onBackground, modifier = Modifier.weight(1f))
                if (section.rounds > 1) {
                    Text("× ${section.rounds} rounds", style = AppType.cardMeta, color = colors.textMuted)
                }
            }
            section.items.forEach { item ->
                Row {
                    Text(names[item.exerciseId] ?: "Exercise", style = AppType.cardMeta, color = colors.onBackground)
                    if (item.exerciseId in newIds) {
                        Text("  new", style = AppType.cardMeta, color = colors.textMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Problem(phase: CoachPhase.Problem, viewModel: CoachViewModel, onOpenSettings: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        Modifier.padding(horizontal = Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        Text(phase.message, style = AppType.cardTitle, color = colors.onBackground)
        if (phase.canRetry) PrimaryButton("TRY AGAIN", onClick = viewModel::retry)
        if (phase.keyProblem) PrimaryButton("OPEN SETTINGS", onClick = onOpenSettings)
        TextAction("Back to my goal", onClick = viewModel::backToCompose)
    }
}

/** The screen's full-width primary action, at the primary-button size. */
@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    InverseButton(
        text = text,
        height = Dimens.primaryButtonHeight,
        style = AppType.primaryButton,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    )
}
