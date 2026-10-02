package com.ottotech.workoutbuilder.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.domain.TagVocabulary
import com.ottotech.workoutbuilder.ui.components.AppTextField
import com.ottotech.workoutbuilder.ui.components.Chip
import com.ottotech.workoutbuilder.ui.components.InverseButton
import com.ottotech.workoutbuilder.ui.components.ScreenTitle
import com.ottotech.workoutbuilder.ui.components.SearchGlyph
import com.ottotech.workoutbuilder.ui.components.SectionLabel
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenCategory: (Muscle) -> Unit,
    onOpenFilters: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current
    val selectedType = state.filter.facets[Facet.TYPE]?.firstOrNull()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = Dimens.screenPadding,
            end = Dimens.screenPadding,
            top = 14.dp,
            bottom = 20.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(Dimens.gap),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                ScreenTitle("LIBRARY")
                Spacer(Modifier.height(12.dp))
                AppTextField(
                    value = state.filter.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = "Search exercises",
                    leading = { SearchGlyph(color = colors.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                // `All` is the absence of a TYPE selection, not a value in the vocabulary.
                Chip("All", selected = selectedType == null) { viewModel.onSelectType(null) }
                TagVocabulary.TYPE.forEach { type ->
                    Chip(type, selected = selectedType == type) { viewModel.onSelectType(type) }
                }
                Spacer(Modifier.padding(start = 2.dp))
                FiltersChip(activeCount = state.filter.activeFacetCount, onClick = onOpenFilters)
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionLabel("Browse by muscle", Modifier.padding(top = 6.dp))
        }

        if (state.allCategoriesEmpty) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyGrid(onClear = viewModel::onClearEverything)
            }
        } else {
            itemsIndexed(state.categories, key = { _, muscle -> muscle.name }) { index, muscle ->
                CategoryTile(
                    muscle = muscle,
                    count = state.counts[muscle] ?: 0,
                    featured = index == 0,
                    onClick = { onOpenCategory(muscle) },
                )
            }
        }
    }
}

@Composable
private fun FiltersChip(activeCount: Int, onClick: () -> Unit) {
    val label = if (activeCount > 0) "Filters · $activeCount" else "Filters"
    Chip(text = label, selected = activeCount > 0, onClick = onClick)
}

/**
 * Spec §6.3. The first tile is featured on `surfaceInverse`; a category the filter has
 * narrowed to zero renders at 40% and is not tappable.
 */
@Composable
private fun CategoryTile(muscle: Muscle, count: Int, featured: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val empty = count == 0
    val alpha = if (empty) 0.4f else 1f
    val background = if (featured) colors.surfaceInverse else colors.surface
    val nameColor = if (featured) colors.onSurfaceInverse else colors.onSurface
    // On the featured tile both name and count would be pure white. Weight alone is not
    // enough separation, so the count drops to 70% — spec §5.
    val countColor = if (featured) colors.onSurfaceInverse.copy(alpha = 0.7f) else colors.textMuted

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.categoryTileMinHeight)
            .clip(RoundedCornerShape(Dimens.radiusTile))
            .background(background.copy(alpha = alpha))
            .clickable(enabled = !empty, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(muscle.label, style = AppType.tileName, color = nameColor.copy(alpha = alpha))
        Text("$count", style = AppType.tileCount, color = countColor.copy(alpha = alpha))
    }
}

@Composable
private fun EmptyGrid(onClear: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Nothing matches those filters.", style = AppType.cardMeta, color = colors.textMuted)
        InverseButton(
            text = "Clear filters",
            height = Dimens.primaryButtonHeight,
            style = AppType.chip,
            onClick = onClear,
            modifier = Modifier.width(160.dp),
        )
    }
}
