package com.ottotech.workoutbuilder.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

/**
 * One category's exercises. Inherits the Library's active filter and search — spec §6.3 —
 * which is why it shares the LibraryViewModel rather than owning one.
 */
@Composable
fun CategoryScreen(
    muscle: Muscle,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current
    val matches = state.exercisesIn(muscle)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = muscle.label, onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(
                start = Dimens.screenPadding,
                end = Dimens.screenPadding,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item {
                Text(
                    text = "${matches.size} ${if (matches.size == 1) "exercise" else "exercises"}" +
                        if (state.filter.isEmpty) "" else " · filtered",
                    style = AppType.cardMeta,
                    color = colors.textMuted,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(matches, key = { it.id }) { exercise ->
                ExerciseRow(exercise) { onOpenExercise(exercise.id) }
            }
            if (matches.isEmpty()) {
                item {
                    Text(
                        "Nothing in this category matches the active filter.",
                        style = AppType.cardMeta,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }
}

/** Back affordance and title for the screens that sit outside the bottom nav. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenPadding)
            .padding(top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // heroName rather than a title style: a 30sp chevron overwhelms the word beside it.
        Text(
            text = "‹",
            style = AppType.heroName,
            color = colors.onBackground,
            modifier = Modifier.clickable(onClick = onBack),
        )
        Text(title.uppercase(), style = AppType.screenTitle, color = colors.onBackground)
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    // The sub-line is the exercise's equipment plus its secondary muscles — the two things
    // that decide whether you can actually do it right now.
    val equipment = exercise.tags.filter { it.facet == Facet.EQUIPMENT }.map { it.value }.sorted()
    val alsoHits = exercise.tags.filter { it.facet == Facet.MUSCLE }.map { it.value }.sorted()
    val sub = (equipment + alsoHits).joinToString(" · ")

    HairlineCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(Dimens.cardPadding)) {
            Text(exercise.name, style = AppType.cardTitle, color = colors.onBackground)
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(sub, style = AppType.cardMeta, color = colors.textMuted)
            }
        }
    }
}
