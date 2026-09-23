package com.ottotech.workoutbuilder.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Type scale, corrected 2026-07-26. The design handoff's px values were measured inside a
 * 264px-wide mock phone frame — a thumbnail, not a device — so they landed in the app at ~62%
 * of intended size. These are those values scaled ~1.6x and snapped to sensible steps. See
 * docs/superpowers/plans/2026-07-26-design-correction-pass.md. Do not revert toward the
 * handoff numbers.
 *
 * Platform system sans (Roboto) throughout — the handoff specifies system fonts with no
 * bundled files.
 *
 * With a mono accent, hierarchy is carried by weight, size and tracking rather than colour,
 * so these values are load-bearing.
 */
object AppType {
    val overline = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em)
    val screenTitle = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.035).em, lineHeight = 42.sp)
    val libraryTitle = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em)
    val heroLabel = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
    val heroName = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
    val heroMeta = TextStyle(fontSize = 16.sp)
    val primaryButton = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val buildButton = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold)
    val cardTitle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val cardMeta = TextStyle(fontSize = 14.sp)
    val sectionLabel = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.06.em)
    val chip = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    val tileName = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val tileCount = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    val moveName = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em, textAlign = TextAlign.Center)
    val muscleTag = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em)
    val timerDigits = TextStyle(
        fontSize = 46.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.03).em,
        fontFeatureSettings = "tnum", // tabular figures: digits must not jitter as they change
    )
    val timerCaption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
    val repsExerciseName = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
    val setRowLabel = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}
