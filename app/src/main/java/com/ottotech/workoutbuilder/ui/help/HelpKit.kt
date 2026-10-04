package com.ottotech.workoutbuilder.ui.help

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ottotech.workoutbuilder.ui.brand.rememberSystemAnimationsOn
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.components.ScreenTitle
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The shared kit for the how-to and welcome animations: a clock that plays a [Script], a framed
 * mini-phone that draws a full-size scene scaled down, a fingertip tap hint, and compact
 * stand-ins for the app's controls. Stand-ins rather than the real components: the real ones
 * are clickable and sized for fingers, and a picture must be neither.
 */

/**
 * Plays a [Script] in a loop. With system animations off it never ticks: it holds the end of
 * a step instead, and [show] changes which step is held.
 */
@Stable
class ScriptClock internal constructor(val script: Script, private val animate: Boolean) {

    private var timeMs by mutableLongStateOf(if (animate) 0L else script.endOf(0) - 1L)

    val position: ScenePosition get() = script.positionAt(timeMs)

    /** Jumps to the start of [step], or with animations off to its finished picture. */
    fun show(step: Int) {
        timeMs = if (animate) script.startOf(step).toLong() else script.endOf(step) - 1L
    }

    internal fun advance(ms: Long) {
        timeMs += ms
    }
}

/**
 * A clock for [script] that ticks only while [playing] and while this is composed, so a tour
 * card swiped past or a page left behind schedules no frames.
 */
@Composable
fun rememberScriptClock(script: Script, playing: Boolean = true): ScriptClock {
    val animate = rememberSystemAnimationsOn()
    val clock = remember(script, animate) { ScriptClock(script, animate) }
    if (animate && playing) {
        LaunchedEffect(clock) {
            var last = withFrameMillis { it }
            while (true) {
                withFrameMillis { now ->
                    clock.advance(now - last)
                    last = now
                }
            }
        }
    }
    return clock
}

/** The size a scene is laid out at before the phone scales it down to fit. */
private val MiniWidth = 360.dp
private val MiniHeight = 640.dp
private val Bezel = 10.dp

/**
 * A phone outline holding a scene. The scene is laid out at a real phone's size, then scaled
 * to fit the space given, so its text and spacing keep their true proportions. Font scaling is
 * pinned to 1 inside: at 200% text the picture would overflow, and the steps beside it carry
 * the words anyway. The whole picture is hidden from TalkBack for the same reason.
 */
@Composable
fun MiniPhone(
    modifier: Modifier = Modifier,
    screen: Color = LocalAppColors.current.background,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val unscaledText = remember(density.density) { Density(density.density, fontScale = 1f) }
    Box(modifier.clearAndSetSemantics {}.scaleToFit()) {
        CompositionLocalProvider(LocalDensity provides unscaledText) {
            Box(
                Modifier
                    .size(MiniWidth + Bezel * 2, MiniHeight + Bezel * 2)
                    .clip(RoundedCornerShape(46.dp))
                    .background(colors.surface)
                    .padding(Bezel)
                    .clip(RoundedCornerShape(36.dp))
                    .background(screen),
                content = content,
            )
        }
    }
}

/** Measures the content at its natural size, then draws it scaled down to fit the constraints. */
private fun Modifier.scaleToFit() = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints())
    val byWidth = if (constraints.hasBoundedWidth) constraints.maxWidth / placeable.width.toFloat() else 1f
    val byHeight = if (constraints.hasBoundedHeight) constraints.maxHeight / placeable.height.toFloat() else 1f
    val scale = minOf(byWidth, byHeight, 1f)
    layout((placeable.width * scale).roundToInt(), (placeable.height * scale).roundToInt()) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

/** Standard ease for scene movement, clamped so callers can pass any fraction. */
fun ease(fraction: Float): Float = FastOutSlowInEasing.transform(fraction.coerceIn(0f, 1f))

/** [fraction] of the way from 0 to 1 within the window [from]..[to] of a beat's progress. */
fun window(progress: Float, from: Float, to: Float): Float = ((progress - from) / (to - from)).coerceIn(0f, 1f)

/**
 * The fingertip: a dot that lands on this element, presses with a ring spreading out, then
 * lifts. [progress] is the tap beat's progress; outside 0..1 nothing is drawn.
 */
@Composable
fun Modifier.tapHint(progress: Float): Modifier {
    if (progress <= 0f || progress >= 1f) return this
    val colors = LocalAppColors.current
    return drawWithContent {
        drawContent()
        val land = window(progress, 0f, 0.3f)
        val press = window(progress, 0.3f, 0.55f)
        val opacity = land * (1f - window(progress, 0.6f, 0.9f))
        val radius = 22.dp.toPx() * (0.7f + 0.3f * land) * (1f - 0.15f * sin(press * PI.toFloat()))
        if (press > 0f) {
            drawCircle(
                color = colors.textMuted,
                radius = radius + 28.dp.toPx() * press,
                alpha = 0.6f * (1f - press) * opacity,
                style = Stroke(2.dp.toPx()),
            )
        }
        drawCircle(colors.textMuted, radius, alpha = 0.55f * opacity)
        drawCircle(colors.background, radius, alpha = 0.9f * opacity, style = Stroke(3.dp.toPx()))
    }
}

/** Grows this element's height from 0 to full as [fraction] goes 0..1, fading it in alongside. */
fun Modifier.reveal(fraction: Float): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, (placeable.height * fraction).roundToInt()) { placeable.place(0, 0) }
    }
    .graphicsLayer { alpha = fraction }

/** Slides this element sideways by [fraction] of its own width (0 = in place, 1 = fully right). */
fun Modifier.slideX(fraction: Float): Modifier = graphicsLayer { translationX = size.width * fraction }

/** Slides this element down by [fraction] of its own height (0 = in place, 1 = fully below). */
fun Modifier.slideY(fraction: Float): Modifier = graphicsLayer { translationY = size.height * fraction }

// ---- Stand-ins for the app's controls, sized for a picture ---------------------------------

@Composable
fun MiniHeader(title: String) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", style = AppType.heroName, color = LocalAppColors.current.onBackground)
        Spacer(Modifier.width(18.dp))
        ScreenTitle(title, Modifier.weight(1f))
    }
}

@Composable
fun MiniField(
    text: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    typing: Boolean = false,
    style: TextStyle = AppType.cardTitle,
) {
    val colors = LocalAppColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        when {
            text.isNotEmpty() -> Text(if (typing) "$text|" else text, style = style, color = colors.onSurface, maxLines = 1)
            typing -> Text("|", style = style, color = colors.onSurface)
            else -> Text(placeholder, style = style, color = colors.textMuted, maxLines = 1)
        }
    }
}

/** The app's primary button, or with [filled] false its quieter `surface` version. */
@Composable
fun MiniButton(text: String, modifier: Modifier = Modifier, filled: Boolean = true) {
    val colors = LocalAppColors.current
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(Dimens.radiusButton))
            .background(if (filled) colors.surfaceInverse else colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppType.buildButton, color = if (filled) colors.onSurfaceInverse else colors.onSurface)
    }
}

@Composable
fun MiniChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    container: Color = LocalAppColors.current.surface,
) {
    val colors = LocalAppColors.current
    Box(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.surfaceInverse else container)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppType.chip, color = if (selected) colors.onSurfaceInverse else colors.onSurface)
    }
}

/** `−  value  +`, with the tap hint on the plus. */
@Composable
fun MiniStepper(value: String, plusTap: Float = 0f) {
    val colors = LocalAppColors.current
    @Composable
    fun Glyph(glyph: String, modifier: Modifier = Modifier) = Box(
        modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(colors.surface),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, style = AppType.cardTitle, color = colors.onSurface) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Glyph("−")
        Text(value, style = AppType.cardTitle, color = colors.onBackground)
        Glyph("+", Modifier.tapHint(plusTap))
    }
}

/** A Home workout card: name and minutes over meta and last-done. */
@Composable
fun MiniWorkoutCard(name: String, minutes: String, meta: String, lastDone: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    HairlineCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row {
                Text(name, style = AppType.cardTitle, color = colors.onBackground, modifier = Modifier.weight(1f))
                Text(minutes, style = AppType.cardTitle, color = colors.onBackground)
            }
            Spacer(Modifier.height(4.dp))
            Row {
                Text(meta, style = AppType.cardMeta, color = colors.textMuted, modifier = Modifier.weight(1f))
                Text(lastDone, style = AppType.cardMeta, color = colors.textMuted)
            }
        }
    }
}

/** A tappable choice in a sheet: title over a muted line. */
@Composable
fun MiniOption(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    HairlineCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = AppType.cardTitle, color = colors.onBackground)
            Text(subtitle, style = AppType.cardMeta, color = colors.textMuted)
        }
    }
}
