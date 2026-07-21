package com.ottotech.workoutbuilder.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Type scale from the design handoff. Platform system sans (Roboto) throughout —
 * the handoff specifies system fonts with no bundled files.
 *
 * With a mono accent, hierarchy is carried by weight, size and tracking rather than
 * colour, so these values are load-bearing.
 */
object AppType {
    val overline = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em)
    val screenTitle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.035).em, lineHeight = 29.sp)
    val libraryTitle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em)
    val heroLabel = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
    val heroName = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    val heroMeta = TextStyle(fontSize = 12.sp)
    val primaryButton = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val buildButton = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val cardTitle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val cardMeta = TextStyle(fontSize = 11.sp)
    val sectionLabel = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.06.em)
    val chip = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val tileName = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val tileCount = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val moveName = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em, textAlign = TextAlign.Center)
    val muscleTag = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em)
    val repsExerciseName = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
    val setRowLabel = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}
