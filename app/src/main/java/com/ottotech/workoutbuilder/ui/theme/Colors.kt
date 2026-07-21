package com.ottotech.workoutbuilder.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colour roles. Never reference a literal hex outside this file — everything else
 * reads `LocalAppColors.current`, which is what makes the dark theme nearly free.
 *
 * Mono accent rule: the accent is maximum contrast against its own surface, i.e.
 * [onSurfaceInverse] on an inverse surface and [onBackground] on the page. There is
 * no accent hue, so no per-theme contrast auditing is needed.
 */
@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceInverse: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceInverse: Color,
    val textMuted: Color,
    val textMutedAlt: Color,
    val textDisabled: Color,
    val navInactive: Color,
    val border: Color,
    val borderSubtle: Color,
)

val LightColors = AppColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF1F1EF),
    surfaceInverse = Color(0xFF0B0B0D),
    onBackground = Color(0xFF0A0A0A),
    onSurface = Color(0xFF0B0B0D),
    onSurfaceInverse = Color(0xFFFFFFFF),
    textMuted = Color(0xFF9A9A9A),
    textMutedAlt = Color(0xFF8A8A8A),
    textDisabled = Color(0xFFC2C2C2),
    navInactive = Color(0xFFB3B3B3),
    border = Color(0x21000000),        // rgba(0,0,0,.13)
    borderSubtle = Color(0x14000000),  // rgba(0,0,0,.08)
)

val DarkColors = AppColors(
    background = Color(0xFF0B0B0D),
    surface = Color(0xFF18181C),
    // Slightly off-white so the inverted hero card is not harsh against a black page.
    surfaceInverse = Color(0xFFF4F4F2),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceInverse = Color(0xFF0B0B0D),
    // Mid-greys read correctly on both white and near-black, so they do not vary.
    textMuted = Color(0xFF9A9A9A),
    textMutedAlt = Color(0xFF8A8A8A),
    textDisabled = Color(0xFF5A5A5A),
    navInactive = Color(0xFF5A5A5A),
    border = Color(0x24FFFFFF),        // rgba(255,255,255,.14)
    borderSubtle = Color(0x14FFFFFF),  // rgba(255,255,255,.08)
)

/**
 * The workout execution screen is always dark, in both themes — it is a focus mode.
 * Spec §5.
 */
object ExecutionColors {
    val background = Color(0xFF0B0B0D)
    val card = Color(0xFF18181C)
    val border = Color(0x24FFFFFF)
    val borderStrong = Color(0x33FFFFFF)
    val ringTrack = Color(0x1FFFFFFF)
    val ringProgress = Color(0xFFFFFFFF)
    val onBackground = Color(0xFFFFFFFF)
    val textMuted = Color(0xFF9A9A9A)
}

val LocalAppColors = staticCompositionLocalOf { LightColors }
