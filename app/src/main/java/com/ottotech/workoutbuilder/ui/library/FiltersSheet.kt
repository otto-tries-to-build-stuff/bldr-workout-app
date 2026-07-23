package com.ottotech.workoutbuilder.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.TagVocabulary
import com.ottotech.workoutbuilder.domain.isSelected
import com.ottotech.workoutbuilder.ui.components.Chip
import com.ottotech.workoutbuilder.ui.components.InverseButton
import com.ottotech.workoutbuilder.ui.components.SectionLabel
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

/**
 * Spec §6.3a. Changes apply live, so dismissing the sheet keeps them — there is no Cancel.
 * The pinned button is a confirmation of what you already did, not a commit step.
 *
 * TYPE appears here as well as in the Library's chip row; both edit the same FilterState,
 * so `Clear all` genuinely clears everything. The one difference is multiplicity: the chip
 * row single-selects, this sheet multi-selects.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FiltersSheet(viewModel: LibraryViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Dimens.screenPadding)
                .padding(top = 20.dp, bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("Filters")
                Text(
                    text = "Clear all",
                    style = AppType.chip,
                    color = colors.textMuted,
                    modifier = Modifier.clickable(onClick = viewModel::onClearFacets),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                FacetGroup("Type", Facet.TYPE, TagVocabulary.TYPE, viewModel, state)
                FacetGroup("Equipment", Facet.EQUIPMENT, TagVocabulary.EQUIPMENT, viewModel, state)
                FacetGroup("Pattern", Facet.PATTERN, TagVocabulary.PATTERN, viewModel, state)
                // The MUSCLE facet is secondary muscles, so it reads better as "also hits".
                FacetGroup("Also hits", Facet.MUSCLE, TagVocabulary.MUSCLE, viewModel, state)
            }

            Spacer(Modifier.height(16.dp))
            InverseButton(
                text = "SHOW ${state.matchCount} ${if (state.matchCount == 1) "EXERCISE" else "EXERCISES"}",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FacetGroup(
    label: String,
    facet: Facet,
    values: List<String>,
    viewModel: LibraryViewModel,
    state: LibraryUiState,
) {
    Spacer(Modifier.height(20.dp))
    SectionLabel(label)
    Spacer(Modifier.height(10.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        values.forEach { value ->
            Chip(
                text = value,
                selected = state.filter.isSelected(facet, value),
                onClick = { viewModel.onToggle(facet, value) },
            )
        }
    }
}
