package com.ottotech.workoutbuilder.ui.brand

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ottotech.workoutbuilder.R
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

/*
 * BLDR brand assets for Compose. Usage rules live in brand/BRAND.md at the repo root.
 *
 * Every brand graphic is a single-colour vector drawable tinted at the call site, so it follows
 * the mono palette in LocalAppColors in both themes. No literal colours in this file, in line
 * with the rule in ui/theme/Colors.kt.
 */

enum class BldrLogoVariant(@DrawableRes val res: Int) {
    /** The dumbbell B on its own. App bars, small spaces, the loader. */
    Mark(R.drawable.bldr_mark),

    /** Primary logo: mark and wordmark side by side. */
    Horizontal(R.drawable.bldr_logo_horizontal),

    /** Mark above wordmark. Splash, about screen, empty hero spaces. */
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

private const val MarkWidth = 104f
private const val MarkHeight = 72f
private const val MinPlateScale = 0.3f

/** The three animated pieces of the mark, in the drawable's 104 x 72 frame. */
private class Plate(val x: Float, val w: Float, val h: Float, val r: Float)

private val Plates = listOf(
    Plate(x = 0f, w = 6f, h = 12f, r = 3f),   // collar
    Plate(x = 5f, w = 10f, h = 32f, r = 5f),  // small plate
    Plate(x = 18f, w = 14f, h = 72f, r = 7f), // big plate
)
private const val PlateCentreY = 36f

/**
 * Loading indicator: the mark with its plates pulsing outside in, as on the brand canvas.
 * Holds still when the system has animations turned off.
 */
@Composable
fun BldrLoader(
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
    color: Color = LocalAppColors.current.onBackground,
    contentDescription: String = "Loading",
) {
    val description = contentDescription
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val transition = rememberInfiniteTransition(label = "bldrLoader")
    val scales = listOf(
        transition.plateScale(delayMillis = 0),
        transition.plateScale(delayMillis = 150),
        transition.plateScale(delayMillis = 300),
    )

    Box(
        modifier
            .size(width, width * (MarkHeight / MarkWidth))
            .semantics { this.contentDescription = description },
    ) {
        Image(
            painter = painterResource(R.drawable.bldr_loader_base),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(color),
        )
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.width / MarkWidth
            Plates.forEachIndexed { i, plate ->
                val scale = if (animate) scales[i].value else 1f
                val h = plate.h * scale
                val progress = (scale - MinPlateScale) / (1f - MinPlateScale)
                drawRoundRect(
                    color = color,
                    alpha = 0.3f + 0.7f * progress,
                    topLeft = Offset(plate.x * unit, (PlateCentreY - h / 2f) * unit),
                    size = Size(plate.w * unit, h * unit),
                    cornerRadius = CornerRadius(minOf(plate.r, h / 2f) * unit),
                )
            }
        }
    }
}

@Composable
private fun InfiniteTransition.plateScale(delayMillis: Int): State<Float> = animateFloat(
    initialValue = MinPlateScale,
    targetValue = MinPlateScale,
    animationSpec = infiniteRepeatable(
        animation = keyframes {
            durationMillis = 1200
            MinPlateScale at 0
            1f at 420
            1f at 780
            MinPlateScale at 1200
        },
        initialStartOffset = StartOffset(delayMillis),
    ),
    label = "plate$delayMillis",
)

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

/**
 * Brand heading styles. Not wired into AppType yet: switching the app's type is a deliberate,
 * one-pass change (see brand/BRAND.md), because AppType's sizes are tuned for Roboto.
 */
object BrandType {
    val display = TextStyle(
        fontFamily = BrandFonts.ArchivoExpanded,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.01.em,
    )
    val headline = display.copy(fontSize = 26.sp, lineHeight = 30.sp)
    val bigNumber = display.copy(fontSize = 46.sp, lineHeight = 50.sp, fontFeatureSettings = "tnum")
}
