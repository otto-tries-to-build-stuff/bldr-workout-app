# BLDR brand

The app's brand is BLDR (said "builder"). The mark is the striped B: seven rounded bars, like weight plates, stacked in five rows to form a B. Black and white only: no accent colour, no gradients, no shadows on the marks.

Design source: the "BLDR Brand Kit" canvas in Claude. When the canvas changes, re-export from there rather than editing the path data by hand.

## What's where

| Asset | File | Use it for |
|---|---|---|
| Mark | `res/drawable/bldr_mark.xml` | App bars, small spaces, anywhere under about 32dp tall |
| Primary logo | `res/drawable/bldr_logo_horizontal.xml` | Headers, about screen, anywhere with width to spare |
| Stacked logo | `res/drawable/bldr_logo_stacked.xml` | Splash-style screens, centred hero spaces |
| Wordmark | `res/drawable/bldr_wordmark.xml` | When the mark is already visible nearby |
| Launcher icon | `res/mipmap-anydpi-v26/ic_launcher.xml` (+ `_round`) | Wired in `AndroidManifest.xml` |
| Launcher layers | `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` | Adaptive icon; monochrome is the Android 13+ themed icon |
| Icon background | `@color/bldr_icon_background` in `res/values/bldr_brand.xml` | Launcher only |
| Notification icon | `res/drawable/ic_stat_bldr.xml` | `setSmallIcon` on every notification |
| Fonts | `res/font/archivo_expanded_black.ttf`, `space_grotesk_*.ttf` | Via `BrandFonts`, wired into `AppType` (`ui/theme/Type.kt`) |
| Store and web masters | `brand/svg/`, `brand/png/` | Play Store listing (`bldr-play-store-icon-512.png`), web, socials |

## In Compose

All of it lives in `ui/brand/Brand.kt`.

```kotlin
BldrLogo(height = 28.dp)                                    // primary horizontal logo
BldrLogo(height = 96.dp, variant = BldrLogoVariant.Stacked)
BldrMark(height = 24.dp)
BldrLoader(width = 40.dp)                                   // full-screen and section loading
BldrRefreshIndicator()                                      // pull to refresh, small inline waits
BldrSplashBuild(onBuilt = { /* go to first screen */ })     // builds once, if a branded launch is ever wanted
Text("Push day", style = AppType.heroName)                  // every app style lives in AppType
ScreenTitle("LIBRARY")                                      // Archivo title that shrinks to fit
```

- Every graphic is one colour, tinted at the call site. Defaults to `LocalAppColors.current.onBackground`; pass `onSurfaceInverse` on inverse surfaces. Never hard-code a hex (house rule from `ui/theme/Colors.kt`).
- Don't recolour parts of a logo, outline it, stretch it, rotate it or put it on a photo without a solid backing.
- The gaps between bars are real gaps, so the logo works on any solid surface.

## Motion: build the B

- Bars drop in from the bottom row up. On rows with two bars, the left one lands first. 120 ms between bars, a 96 ms drop with a small overshoot, then a 48 ms settle.
- `BldrLoader` loops every 1.6 s: build (about 1 s), hold the full B, fade, repeat.
- `BldrSplashBuild` builds once, holds 400 ms, then calls `onBuilt`.
- `BldrRefreshIndicator` pulses only the bottom three bars, for small sizes.
- All three show the still, complete B and schedule no frames when the system has animations turned off. Don't remove that.
- The loader's `width` is the mark's true width; it is 1.3x taller than wide. Bars drop in from above, so leave about a fifth of the width as headroom above it (the canvas doesn't clip).
- Timings are the source of truth in the Motion board on the brand canvas; in code they live in `BuildTiming` in `Brand.kt`.

## Rules

- Clear space: keep space equal to two bar heights on every side of the mark or logo.
- Minimum sizes: mark 16dp tall (the gaps close up below that), horizontal logo 24dp tall, stacked logo 56dp tall.
- Headings and big numbers: Archivo Expanded 900, only via `AppType.screenTitle`, `timerDigits` and `leadInDigits`. Short lines only; never for body text. It runs ~1.55x wider than Roboto, so titles go through `ScreenTitle` (auto-shrinks).
- UI text: Space Grotesk 400, 500, 700 (`BrandFonts.SpaceGrotesk`).
- Notification small icons must stay white on transparent. Android tints them.

## Decisions

- No splash theme, by decision (2026-10-02). On Android 12+ the system splash already shows the launcher icon on `windowBackground`. The real wait (first-launch seeding) shows `BldrLoader` instead, after a 300 ms delay so normal launches never flash it (`MainActivity.LaunchLoader`).
- `BldrSplashBuild` and `BldrRefreshIndicator` are available but unused (2026-10-02). As a splash, the one-shot build would add ~1.4 s to every launch; the only real launch wait (first-launch seeding) has an unknown length, which the looping `BldrLoader` fits. The app has no pull-to-refresh or small inline waits for the refresh indicator.
- The Coach's wait centres `BldrLoader` and steps through six "working words" (3 s each, holding on the last). They are reassurance, not real progress: the request is a single call.

## Licences

Archivo and Space Grotesk are under the SIL Open Font License 1.1. The licence texts are in `brand/licenses/` and must ship with the fonts.
