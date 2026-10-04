package com.ottotech.workoutbuilder.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** A card that opens another screen: a title, a muted line under it and a ›. */
@Composable
fun LinkCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    HairlineCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.cardPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = AppType.cardTitle, color = colors.onBackground)
                Text(subtitle, style = AppType.cardMeta, color = colors.textMuted)
            }
            Spacer(Modifier.width(12.dp))
            Text("›", style = AppType.heroName, color = colors.onBackground)
        }
    }
}

/**
 * The app's primary action: a filled block on `surfaceInverse` with `onSurfaceInverse`
 * text. Under the mono accent rule this *is* the accent — maximum contrast against the
 * page.
 *
 * [container] and [content] default to that pairing but can be swapped, which is what a
 * button sitting *on* an inverse surface needs: the accent flips back to `onSurfaceInverse`
 * there, so the same component covers both without a second copy of it.
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
    /** Disabled = `surface` with muted text and no ripple, whatever [container] / [content] say. */
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val fill = if (enabled) container else colors.surface
    val ink = if (enabled) content else colors.textMuted
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(Dimens.radiusButton))
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingGlyph != null) {
            Text(leadingGlyph, style = style, color = ink)
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = style, color = ink)
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
    /** Hides the text as dots — for secrets such as the API key. */
    masked: Boolean = false,
    /**
     * Above 1 the field is multi-line: it grows downward from the top, and opens this many lines
     * tall — the text field itself, not just its grey box, so a tap anywhere in the box focuses it.
     */
    minLines: Int = 1,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .heightIn(min = Dimens.searchFieldHeight)
            .clip(RoundedCornerShape(Dimens.radiusButton))
            .background(colors.surface)
            .padding(horizontal = 13.dp, vertical = if (minLines == 1) 0.dp else 12.dp),
        verticalAlignment = if (minLines == 1) Alignment.CenterVertically else Alignment.Top,
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
                singleLine = minLines == 1,
                minLines = minLines,
                visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
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
                .offset(x = -Dimens.chevronInset)
                .size(Dimens.minTouchTarget)
                .clip(RoundedCornerShape(Dimens.radiusChip))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            // heroName rather than a title style: a 40sp chevron overwhelms the word beside it.
            Text(text = "‹", style = AppType.heroName, color = colors.onBackground)
        }
        ScreenTitle(title.uppercase(), Modifier.weight(1f))
    }
}

/**
 * A screen title in Archivo Expanded. The face is wide, so the title shrinks to fit rather than
 * clipping or breaking a word at large font sizes on narrow phones. The 14 sp floor is what
 * "SHOULDERS" needs at the 200% font size on a 360 dp phone; past it, an ellipsis, never a cut.
 */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    val color = LocalAppColors.current.onBackground
    BasicText(
        text = text,
        style = AppType.screenTitle,
        color = { color },
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = AppType.screenTitle.fontSize),
        modifier = modifier.semantics { heading() },
    )
}

/**
 * The app's one confirmation dialog. `surface` rather than `background` for the container:
 * `background` was nearly indistinguishable from the scrim in dark mode (both ~#0B0B0D). Body
 * text stays AA on it (dark textMuted #9A9A9A on #18181C is 6.4:1, light #6B6B6B on #F1F1EF is
 * 4.7:1). Execution passes its own always-dark palette.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    container: Color = LocalAppColors.current.surface,
    content: Color = LocalAppColors.current.onBackground,
    muted: Color = LocalAppColors.current.textMuted,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = container,
        title = { Text(title, style = AppType.cardTitle, color = content) },
        text = { Text(body, style = AppType.cardMeta, color = muted) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, style = AppType.chip, color = content) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel, style = AppType.chip, color = content) }
        },
    )
}

/** A text-only action — a link or a secondary command — with a full 48 dp touch target. */
@Composable
fun TextAction(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = LocalAppColors.current.onBackground,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .defaultMinSize(minWidth = Dimens.minTouchTarget, minHeight = Dimens.minTouchTarget)
            .clip(RoundedCornerShape(Dimens.radiusChip))
            .clickable(enabled = enabled, onClick = onClick),
        // End, not Start: short labels ("Demo") narrower than the 48 dp minimum otherwise sit
        // inset from the right edge, out of line with the text above them.
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(text, style = AppType.chip, color = if (enabled) color else LocalAppColors.current.textMuted)
    }
}
