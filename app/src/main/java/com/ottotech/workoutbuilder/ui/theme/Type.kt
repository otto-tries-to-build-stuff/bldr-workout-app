package com.ottotech.workoutbuilder.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ottotech.workoutbuilder.ui.brand.BrandFonts

/**
 * Type scale. Sizes were rescaled 2026-07-26 (the handoff's px were ~62% of true size; do not
 * revert toward them) and re-fitted 2026-10-02 for the BLDR fonts. See
 * docs/superpowers/plans/2026-10-02-bldr-brand-rollout.md, Task 6:
 * - Archivo Expanded 900 (titles, big numbers) runs ~1.55x wider than Roboto ExtraBold, so
 *   titles drop 40 → 32 sp and the timer 46 → 38 sp; ScreenTitle and TimerRing also auto-shrink.
 * - Space Grotesk (everything else) is ~7% wider than Roboto but has a smaller x-height, so body
 *   sizes stay. It ships 400 / 500 / 700 only: SemiBold → Medium, ExtraBold → Bold.
 * Every style sets its own line height; Space Grotesk's natural line box is ~9% taller than
 * Roboto's. AppTypeTest enforces both rules.
 *
 * With a mono accent, hierarchy is carried by weight, size and tracking rather than colour,
 * so these values are load-bearing.
 */
object AppType {
    private val centred = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    private val ui = TextStyle(fontFamily = BrandFonts.SpaceGrotesk, lineHeightStyle = centred)
    private val display = TextStyle(
        fontFamily = BrandFonts.ArchivoExpanded,
        fontWeight = FontWeight.Black,
        lineHeightStyle = centred,
    )

    val screenTitle = display.copy(fontSize = 32.sp, lineHeight = 1.06.em, letterSpacing = 0.01.em)
    val timerDigits = display.copy(
        fontSize = 38.sp,
        lineHeight = 1.1.em,
        fontFeatureSettings = "tnum", // tabular figures: digits must not jitter as they change
    )
    /** The lead-in's "3, 2, 1": the biggest type in the app, meant to be read from the floor. */
    val leadInDigits = timerDigits.copy(fontSize = 180.sp, lineHeight = 200.sp)

    val heroName = ui.copy(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em)
    val moveName = ui.copy(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em, textAlign = TextAlign.Center)
    val repsExerciseName = ui.copy(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
    val cardTitle = ui.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    val tileName = ui.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    val setRowLabel = ui.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val buildButton = ui.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.02.em)
    val primaryButton = ui.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.02.em, textAlign = TextAlign.Center)
    val heroMeta = ui.copy(fontSize = 16.sp, lineHeight = 22.sp)
    val cardMeta = ui.copy(fontSize = 14.sp, lineHeight = 20.sp)
    val chip = ui.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val tileCount = ui.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val overline = ui.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.04.em)
    val sectionLabel = ui.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.06.em)
    val muscleTag = ui.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.04.em)
    val heroLabel = ui.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
    val timerCaption = ui.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
    val navLabel = ui.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
}
