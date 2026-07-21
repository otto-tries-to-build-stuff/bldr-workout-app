package com.ottotech.workoutbuilder.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ottotech.workoutbuilder.ui.components.AppTextField
import com.ottotech.workoutbuilder.ui.components.Chip
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.components.InverseButton
import com.ottotech.workoutbuilder.ui.components.ScreenHeader
import com.ottotech.workoutbuilder.ui.components.SectionLabel
import com.ottotech.workoutbuilder.ui.demoIntent
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(
    exerciseId: String,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val exercise = state.exercise(exerciseId)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = exercise?.primaryMuscle?.label ?: "Exercise", onBack = onBack)

        if (exercise == null) {
            // Only reachable if the exercise was deleted while this screen was open.
            Text(
                "That exercise is no longer in the library.",
                style = AppType.cardMeta,
                color = colors.textMuted,
                modifier = Modifier.padding(horizontal = Dimens.screenPadding),
            )
            return@Column
        }

        // Keyed on the id so navigating between exercises resets the field instead of
        // carrying the previous exercise's URL across.
        var urlDraft by remember(exercise.id) { mutableStateOf(exercise.demoUrl.orEmpty()) }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding)
                .padding(bottom = 28.dp),
        ) {
            Text(exercise.name, style = AppType.repsExerciseName, color = colors.onBackground)
            Spacer(Modifier.height(14.dp))

            SectionLabel("Tags")
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Primary muscle first and selected — it is the exercise's home category,
                // not one tag among many. No onClick: these are labels, not controls.
                Chip(exercise.primaryMuscle.label, selected = true)
                // Grouped by facet, alphabetical within each, in one pass.
                exercise.tags
                    .sortedWith(compareBy({ it.facet.ordinal }, { it.value }))
                    .forEach { tag -> Chip(tag.value, selected = false) }
            }

            Spacer(Modifier.height(22.dp))
            InverseButton(
                text = "WATCH DEMO",
                leadingGlyph = "▸",
                onClick = { context.startActivity(demoIntent(exercise)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = exercise.demoUrl?.takeIf { it.isNotBlank() }
                    ?: "Searches YouTube for “${exercise.demoQuery}”",
                style = AppType.cardMeta,
                color = colors.textMuted,
            )

            Spacer(Modifier.height(26.dp))
            SectionLabel("Demo video")
            Spacer(Modifier.height(10.dp))
            HairlineCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(Dimens.cardPadding)) {
                    Text(
                        "Paste a YouTube link to pin a specific demo. Leave it empty to search instead.",
                        style = AppType.cardMeta,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    AppTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        placeholder = "https://youtube.com/…",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    InverseButton(
                        text = "SAVE LINK",
                        height = Dimens.primaryButtonHeight,
                        style = AppType.primaryButton,
                        onClick = { viewModel.onSetDemoUrl(exercise.id, urlDraft) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
