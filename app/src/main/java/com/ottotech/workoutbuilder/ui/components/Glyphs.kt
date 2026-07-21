package com.ottotech.workoutbuilder.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The handoff's icons are geometric placeholders, described exactly as drawn here.
 * Keeping them as Canvas drawings means no icon-font or icon-library dependency, and
 * they inherit the mono palette by taking a colour parameter.
 */

private val StrokeWidth = 1.6.dp

/** Bottom nav, Home tab: a rounded-square outline. */
@Composable
fun HomeGlyph(size: Dp = 16.dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = StrokeWidth.toPx()
        val inset = w / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(this.size.width - w, this.size.height - w),
            cornerRadius = CornerRadius(this.size.width * 0.28f),
            style = Stroke(width = w),
        )
    }
}

/** Bottom nav, Library tab: three stacked bars. */
@Composable
fun LibraryGlyph(size: Dp = 16.dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val barHeight = this.size.height * 0.16f
        val gap = (this.size.height - barHeight * 3f) / 2f
        repeat(3) { i ->
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, i * (barHeight + gap)),
                size = Size(this.size.width, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f),
            )
        }
    }
}

/** Search field leading icon: a circle outline. */
@Composable
fun SearchGlyph(size: Dp = 13.dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = StrokeWidth.toPx()
        drawCircle(color = color, radius = (this.size.minDimension - w) / 2f, style = Stroke(width = w))
    }
}
