package com.ottotech.workoutbuilder.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
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
    // AA against both #FFFFFF (5.3:1) and #F1F1EF (4.7:1). The old #9A9A9A was 2.8:1 / 2.5:1.
    textMuted = Color(0xFF6B6B6B),
    textMutedAlt = Color(0xFF5F5F5F),   // 6.4:1 on white
    textDisabled = Color(0xFFC2C2C2),   // disabled text is WCAG-exempt — deliberately unchanged
    navInactive = Color(0xFF767676),    // 4.5:1 on white
    border = Color(0x21000000),        // rgba(0,0,0,.13)
    borderSubtle = Color(0x14000000),  // rgba(0,0,0,.08)
)

val DarkColors = AppColors(
    background = Color(0xFF0B0B0D),
    surface = Color(0xFF18181C),
    // Slightly off-white so inverse surfaces are not harsh against a black page.
    surfaceInverse = Color(0xFFF4F4F2),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceInverse = Color(0xFF0B0B0D),
    // Dark-mode muted greys are lighter than light-mode's because they sit on near-black:
    // #9A9A9A is 6.8:1 on #0B0B0D and 6.4:1 on #18181C. They are NOT the same value as
    // LightColors' — that symmetry was the original contrast bug.
    textMuted = Color(0xFF9A9A9A),
    textMutedAlt = Color(0xFF8A8A8A),
    textDisabled = Color(0xFF5A5A5A),
    // ≈5.7:1 on #0B0B0D. The original pass only audited light mode; the final review found
    // dark navInactive at the old #5A5A5A (≈2.85:1, an AA fail) and raised it here.
    navInactive = Color(0xFF8A8A8A),
    border = Color(0x24FFFFFF),        // rgba(255,255,255,.14)
    borderSubtle = Color(0x14FFFFFF),  // rgba(255,255,255,.08)
)

/**
 * The workout execution screen's colours. Originally always dark (spec §5, "focus mode");
 * since 2026-10 it follows the app theme like every other screen, so Light is white.
 */
@Immutable
data class ExecutionPalette(
    val background: Color,
    val card: Color,
    val border: Color,
    val borderStrong: Color,
    val ringTrack: Color,
    val ringProgress: Color,
    val onBackground: Color,
    val textMuted: Color,
)

val LightExecutionColors = ExecutionPalette(
    background = Color(0xFFFFFFFF),
    card = Color(0xFFF1F1EF),
    border = Color(0x21000000),
    borderStrong = Color(0x33000000),
    ringTrack = Color(0x1F000000),
    ringProgress = Color(0xFF0B0B0D),
    onBackground = Color(0xFF0A0A0A),
    textMuted = Color(0xFF6B6B6B),     // AA on white and on the card, as LightColors.textMuted
)

val DarkExecutionColors = ExecutionPalette(
    background = Color(0xFF0B0B0D),
    card = Color(0xFF18181C),
    border = Color(0x24FFFFFF),
    borderStrong = Color(0x33FFFFFF),
    ringTrack = Color(0x1FFFFFFF),
    ringProgress = Color(0xFFFFFFFF),
    onBackground = Color(0xFFFFFFFF),
    textMuted = Color(0xFF9A9A9A),
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

val LocalExecutionColors = staticCompositionLocalOf { LightExecutionColors }

/** The current theme's [ExecutionPalette]; reads like a plain object at call sites. */
val ExecutionColors: ExecutionPalette
    @Composable @ReadOnlyComposable get() = LocalExecutionColors.current
