package com.ottotech.workoutbuilder.ui.brand

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ottotech.workoutbuilder.R
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors
import com.ottotech.workoutbuilder.ui.theme.WorkoutTheme

/*
 * BLDR brand assets for Compose. Usage rules live in brand/BRAND.md at the repo root.
 *
 * The mark is the striped B: seven rounded bars ("plates") in five rows. Every brand graphic
 * is single-colour and tinted at the call site, so it follows the mono palette in
 * LocalAppColors in both themes. No literal colours in this file, in line with the rule in
 * ui/theme/Colors.kt.
 */

enum class BldrLogoVariant(@param:DrawableRes val res: Int) {
    /** The striped B on its own. App bars, small spaces. */
    Mark(R.drawable.bldr_mark),

    /** Primary logo: mark and wordmark side by side. */
    Horizontal(R.drawable.bldr_logo_horizontal),

    /** Mark above wordmark. About screen, centred hero spaces. */
    Stacked(R.drawable.bldr_logo_stacked),

    /** BLDR in Archivo Expanded, outlined. Use where the mark is already on screen. */
    Wordmark(R.drawable.bldr_wordmark),
}

/** Draws a BLDR logo at [height], keeping its proportions. */
@Composable
fun BldrLogo(
    height: Dp,
    modifier: Modifier = Modifier,
    variant: BldrLogoVariant = BldrLogoVariant.Horizontal,
    color: Color = LocalAppColors.current.onBackground,
    contentDescription: String? = "BLDR",
) {
    val painter = painterResource(variant.res)
    val ratio = painter.intrinsicSize.width / painter.intrinsicSize.height
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.height(height).aspectRatio(ratio),
        colorFilter = ColorFilter.tint(color),
    )
}

/** Shorthand for the mark alone. */
@Composable
fun BldrMark(
    height: Dp,
    modifier: Modifier = Modifier,
    color: Color = LocalAppColors.current.onBackground,
    contentDescription: String? = "BLDR",
) = BldrLogo(height, modifier, BldrLogoVariant.Mark, color, contentDescription)

// ---- "Build the B" motion -------------------------------------------------------------------
// Geometry matches res/drawable/bldr_mark.xml: a 54 x 70 frame, bars 10 high.
// Bars are listed in build order: bottom row first, and on split rows left before right.

private const val MarkWidth = 54f
private const val MarkHeight = 70f
private const val BarHeight = 10f

private class Bar(val x: Float, val y: Float, val w: Float)

private val Bars = listOf(
    Bar(0f, 60f, 47f),  // bottom
    Bar(0f, 45f, 15f),  // lower bowl, left
    Bar(33f, 45f, 21f), // lower bowl, right
    Bar(0f, 30f, 45f),  // middle
    Bar(0f, 15f, 15f),  // upper bowl, left
    Bar(31f, 15f, 19f), // upper bowl, right
    Bar(0f, 0f, 40f),   // top
)

/** Timings in ms, matching the Motion board on the brand canvas. */
private object BuildTiming {
    const val Step = 120f        // gap between one bar landing and the next starting
    const val Drop = 96f         // fall plus fade-in
    const val Settle = 48f       // small overshoot settling back to rest
    const val DropDistance = 14f // in mark units, about 1.4 bars
    const val Overshoot = 1.5f
    const val Loop = 1600f
    const val FadeStart = 1248f  // full B holds from ~984 ms to here
    const val FadeEnd = 1472f
    val Built = Step * (Bars.size - 1) + Drop + Settle // 984 ms: every bar at rest
}

/** Offset (mark units) and alpha for bar [index] at [t] ms into the build. */
private fun barState(index: Int, t: Float, fadeOut: Boolean): Pair<Float, Float> {
    val local = t - index * BuildTiming.Step
    var dy: Float
    var alpha: Float
    when {
        local <= 0f -> { dy = -BuildTiming.DropDistance; alpha = 0f }
        local < BuildTiming.Drop -> {
            val p = local / BuildTiming.Drop
            val eased = 1f - (1f - p) * (1f - p) // ease out
            dy = -BuildTiming.DropDistance + (BuildTiming.DropDistance + BuildTiming.Overshoot) * eased
            alpha = p
        }
        local < BuildTiming.Drop + BuildTiming.Settle -> {
            val p = (local - BuildTiming.Drop) / BuildTiming.Settle
            dy = BuildTiming.Overshoot * (1f - p)
            alpha = 1f
        }
        else -> { dy = 0f; alpha = 1f }
    }
    if (fadeOut && t > BuildTiming.FadeStart) {
        val p = ((t - BuildTiming.FadeStart) / (BuildTiming.FadeEnd - BuildTiming.FadeStart)).coerceIn(0f, 1f)
        alpha *= 1f - p
    }
    return dy to alpha
}

private fun DrawScope.drawBars(color: Color, state: (Int) -> Pair<Float, Float>, only: IntRange = Bars.indices) {
    val unit = size.width / MarkWidth
    for (i in only) {
        val bar = Bars[i]
        val (dy, alpha) = state(i)
        if (alpha <= 0f) continue
        drawRoundRect(
            color = color,
            alpha = alpha,
            topLeft = Offset(bar.x * unit, (bar.y + dy) * unit),
            size = Size(bar.w * unit, BarHeight * unit),
            cornerRadius = CornerRadius(BarHeight / 2f * unit),
        )
    }
}

/** False when the user has turned animations off (Developer options, or Accessibility → Remove animations). */
@Composable
internal fun rememberSystemAnimationsOn(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/**
 * Loading indicator: the striped B builds bar by bar from the bottom up, holds, fades and
 * repeats every 1.6 s. With system animations off it shows the finished B.
 *
 * Bars drop in from above the mark, so leave a little headroom (about a fifth of [width])
 * above it; the canvas does not clip.
 */
@Composable
fun BldrLoader(
    modifier: Modifier = Modifier,
    width: Dp = 40.dp,
    color: Color = LocalAppColors.current.onBackground,
    contentDescription: String = "Loading",
    /** Exposed for previews. Callers keep the default, which follows the system setting. */
    animate: Boolean = rememberSystemAnimationsOn(),
) {
    val description = contentDescription
    // No transition at all when still, so a held loader schedules no frames.
    val time: State<Float>? = if (animate) {
        rememberInfiniteTransition(label = "bldrLoader").animateFloat(
            initialValue = 0f,
            targetValue = BuildTiming.Loop,
            animationSpec = infiniteRepeatable(tween(BuildTiming.Loop.toInt(), easing = LinearEasing)),
            label = "bldrLoaderTime",
        )
    } else null
    Canvas(
        modifier
            .size(width, width * (MarkHeight / MarkWidth))
            .semantics {
                this.contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
    ) {
        val t = time?.value
        if (t != null) {
            drawBars(color, { i -> barState(i, t, fadeOut = true) })
        } else {
            drawBars(color, { 0f to 1f })
        }
    }
}

/**
 * Splash version: builds the B once and holds it, then calls [onBuilt] (about 1 s in) so the
 * caller can move on to the first screen. Calls [onBuilt] straight away with animations off.
 */
@Composable
fun BldrSplashBuild(
    modifier: Modifier = Modifier,
    width: Dp = 72.dp,
    color: Color = LocalAppColors.current.onBackground,
    holdMillis: Long = 400,
    onBuilt: () -> Unit = {},
    /** Exposed for previews. Callers keep the default, which follows the system setting. */
    animate: Boolean = rememberSystemAnimationsOn(),
) {
    val latestOnBuilt = rememberUpdatedState(onBuilt)
    val time = remember { Animatable(if (animate) 0f else BuildTiming.Built) }
    LaunchedEffect(animate) {
        if (animate) {
            time.animateTo(BuildTiming.Built, tween(BuildTiming.Built.toInt(), easing = LinearEasing))
            kotlinx.coroutines.delay(holdMillis)
        }
        latestOnBuilt.value()
    }
    Canvas(
        modifier
            .size(width, width * (MarkHeight / MarkWidth))
            .semantics { this.contentDescription = "BLDR" },
    ) {
        val t = time.value
        drawBars(color, { i -> barState(i, t, fadeOut = false) })
    }
}

/**
 * Small indicator for pull to refresh and inline waits: the bottom three bars of the B
 * pulse in sequence. Holds still with animations off.
 */
@Composable
fun BldrRefreshIndicator(
    modifier: Modifier = Modifier,
    width: Dp = 28.dp,
    color: Color = LocalAppColors.current.onBackground,
    contentDescription: String = "Refreshing",
    /** Exposed for previews. Callers keep the default, which follows the system setting. */
    animate: Boolean = rememberSystemAnimationsOn(),
) {
    val description = contentDescription
    val pulses: List<State<Float>>? = if (animate) {
        val transition = rememberInfiniteTransition(label = "bldrRefresh")
        List(3) { i ->
            transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 150),
                ),
                label = "bldrPulse$i",
            )
        }
    } else null
    // Only the bottom three bars: rows y 45..70 of the mark.
    val rowsHeight = 25f
    Canvas(
        modifier
            .size(width, width * (rowsHeight / MarkWidth))
            .semantics {
                this.contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
    ) {
        val unit = size.width / MarkWidth
        for (i in 0..2) {
            val bar = Bars[i]
            drawRoundRect(
                color = color,
                alpha = pulses?.get(i)?.value ?: 1f,
                topLeft = Offset(bar.x * unit, (bar.y - 45f) * unit),
                size = Size(bar.w * unit, BarHeight * unit),
                cornerRadius = CornerRadius(BarHeight / 2f * unit),
            )
        }
    }
}

/** Bundled brand typefaces (SIL Open Font License, see brand/licenses). */
object BrandFonts {
    /** Archivo at width 125, weight 900. Headings and big numbers only. */
    val ArchivoExpanded = FontFamily(Font(R.font.archivo_expanded_black, FontWeight.Black))

    /** UI text: labels, buttons, body copy, set lists. */
    val SpaceGrotesk = FontFamily(
        Font(R.font.space_grotesk_regular, FontWeight.Normal),
        Font(R.font.space_grotesk_medium, FontWeight.Medium),
        Font(R.font.space_grotesk_bold, FontWeight.Bold),
    )
}

@Preview(name = "Loader · light")
@Composable
private fun BldrLoaderLightPreview() = LoaderPreview(dark = false, animate = true)

@Preview(name = "Loader · dark")
@Composable
private fun BldrLoaderDarkPreview() = LoaderPreview(dark = true, animate = true)

@Preview(name = "Loader · animations off")
@Composable
private fun BldrLoaderStillPreview() = LoaderPreview(dark = false, animate = false)

// Static preview panes render frame 0 of the build, where no bar has landed yet, so the
// animated loader and splash previews look empty there. Use Interactive mode to watch them;
// the "animations off" previews show the finished B.
@Preview(name = "Splash build and refresh")
@Composable
private fun BldrSplashAndRefreshPreview() = SplashAndRefreshPreview(animate = true)

@Preview(name = "Splash build and refresh · animations off")
@Composable
private fun BldrSplashAndRefreshStillPreview() = SplashAndRefreshPreview(animate = false)

@Composable
private fun SplashAndRefreshPreview(animate: Boolean) {
    WorkoutTheme(dark = false) {
        Row(
            Modifier.background(LocalAppColors.current.background).padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            BldrSplashBuild(animate = animate)
            BldrRefreshIndicator(animate = animate)
        }
    }
}

@Composable
private fun LoaderPreview(dark: Boolean, animate: Boolean) {
    WorkoutTheme(dark = dark) {
        // Top padding leaves headroom for bars dropping in from above the mark.
        Box(Modifier.background(LocalAppColors.current.background).padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 24.dp)) {
            BldrLoader(animate = animate)
        }
    }
}
