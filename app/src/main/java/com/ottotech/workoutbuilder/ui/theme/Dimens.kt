package com.ottotech.workoutbuilder.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing, radii and fixed sizes. Rescaled 2026-07-26 — see the note in Type.kt and
 * docs/superpowers/plans/2026-07-26-design-correction-pass.md. No shadows anywhere.
 */
object Dimens {
    val screenPadding = 20.dp
    val cardPadding = 18.dp
    val heroPadding = 20.dp
    val gap = 14.dp

    val radiusChip = 12.dp
    val radiusCard = 18.dp
    val radiusTile = 18.dp
    val radiusHero = 22.dp
    val radiusButton = 16.dp
    val radiusCheckbox = 8.dp

    // A hairline is a hairline at any scale — deliberately not rescaled.
    val hairline = 1.dp

    /** Android's platform minimum touch target. Nothing tappable may be smaller. */
    val minTouchTarget = 48.dp

    val buildButtonHeight = 56.dp
    val primaryButtonHeight = 48.dp
    val searchFieldHeight = 52.dp
    val glyphButtonSize = 48.dp
    val categoryTileMinHeight = 80.dp

    /**
     * `ScreenHeader`'s ‹ glyph is narrow inside its 48dp touch box, so centering leaves a
     * visible gap before it — measured on device at ~20dp. This shifts the box left by that
     * much so the glyph lines up with the content edge below it, while the touch target stays
     * 48×48dp (only its position moves, not its size).
     */
    val chevronInset = 20.dp
    val ringSize = 240.dp
    val ringStroke = 14.dp
    val progressBarHeight = 4.dp
    val progressBarGap = 5.dp
}
