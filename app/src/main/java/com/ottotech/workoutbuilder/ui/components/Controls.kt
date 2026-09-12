package com.ottotech.workoutbuilder.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

/** A flat card with a hairline border. No shadows anywhere in this app — spec §5. */
@Composable
fun HairlineCard(
    modifier: Modifier = Modifier,
    radius: Dp = Dimens.radiusCard,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(Dimens.hairline, colors.border), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

/**
 * The app's primary action: a filled block on `surfaceInverse` with `onSurfaceInverse`
 * text. Under the mono accent rule this *is* the accent — maximum contrast against the
 * page.
 *
 * [container] and [content] default to that pairing but can be swapped, which is what a
 * button sitting *on* an inverse surface needs: inside the hero card the accent flips back
 * to `onSurfaceInverse`, so the same component covers both without a second copy of it.
 */
@Composable
fun InverseButton(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = Dimens.buildButtonHeight,
    leadingGlyph: String? = null,
    style: TextStyle = AppType.buildButton,
    container: Color = LocalAppColors.current.surfaceInverse,
    content: Color = LocalAppColors.current.onSurfaceInverse,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(Dimens.radiusButton))
            .background(container)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingGlyph != null) {
            Text(leadingGlyph, style = style, color = content)
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = style, color = content)
    }
}

/** `BROWSE BY MUSCLE`, `SETS`, `FILTERS` — spec §5 section label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = AppType.sectionLabel,
        color = LocalAppColors.current.onBackground,
        modifier = modifier,
    )
}

/**
 * Filter and tag chip. Selected chips invert — the mono accent rule again.
 *
 * [onClick] is nullable because the exercise detail screen displays tags as chips with no
 * interaction; a chip that ripples and does nothing is a small lie about what it does.
 */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val background = if (selected) colors.surfaceInverse else colors.surface
    val foreground = if (selected) colors.onSurfaceInverse else colors.onSurface
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = Dimens.minTouchTarget)
            .clip(RoundedCornerShape(Dimens.radiusChip))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppType.chip, color = foreground)
    }
}

/**
 * Text input styled as a `surface` block rather than a Material text field, which would
 * bring its own container, label slot and indicator line to fight back.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    textStyle: TextStyle = AppType.heroMeta,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .heightIn(min = Dimens.searchFieldHeight)
            .clip(RoundedCornerShape(Dimens.radiusButton))
            .background(colors.surface)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = textStyle, color = colors.textMuted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = textStyle.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.onSurface),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Back affordance and title for the screens that sit outside the bottom nav.
 *
 * Shared by the category list and the exercise detail, so it lives here rather than in
 * whichever screen happened to need it first.
 */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenPadding)
            .padding(top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.minTouchTarget)
                .clip(RoundedCornerShape(Dimens.radiusChip))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            // heroName rather than a title style: a 40sp chevron overwhelms the word beside it.
            Text(text = "‹", style = AppType.heroName, color = colors.onBackground)
        }
        Text(title.uppercase(), style = AppType.screenTitle, color = colors.onBackground)
    }
}
