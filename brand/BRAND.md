# BLDR brand

The app's brand is BLDR (said "builder"). Black and white only: no accent colour, no gradients, no shadows on the marks.

Design source: the "BLDR Brand Kit" canvas in Claude. When the canvas changes, re-export from there rather than editing the path data by hand.

## What's where

| Asset | File | Use it for |
|---|---|---|
| Mark | `res/drawable/bldr_mark.xml` | App bars, small spaces, anywhere under about 32dp tall |
| Primary logo | `res/drawable/bldr_logo_horizontal.xml` | Headers, about screen, anywhere with width to spare |
| Stacked logo | `res/drawable/bldr_logo_stacked.xml` | Splash-style screens, centred hero spaces |
| Wordmark | `res/drawable/bldr_wordmark.xml` | When the mark is already visible nearby |
| Loader base | `res/drawable/bldr_loader_base.xml` | Used by `BldrLoader` only |
| Launcher icon | `res/mipmap-anydpi-v26/ic_launcher.xml` (+ `_round`) | Wired in `AndroidManifest.xml` |
| Launcher layers | `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` | Adaptive icon; monochrome is the Android 13+ themed icon |
| Icon background | `@color/bldr_icon_background` in `res/values/bldr_brand.xml` | Launcher only |
| Notification icon | `res/drawable/ic_stat_bldr.xml` | `setSmallIcon` on every notification |
| Fonts | `res/font/archivo_expanded_black.ttf`, `space_grotesk_*.ttf` | Via `BrandFonts` / `BrandType` |
| Store and web masters | `brand/svg/`, `brand/png/` | Play Store listing (`bldr-play-store-icon-512.png`), web, socials |

## In Compose

All of it lives in `ui/brand/Brand.kt`.

```kotlin
BldrLogo(height = 28.dp)                                    // primary horizontal logo
BldrLogo(height = 96.dp, variant = BldrLogoVariant.Stacked)
BldrMark(height = 24.dp)
BldrLoader(width = 64.dp)                                   // replaces a spinner
Text("Push day", style = BrandType.display)
```

- Every graphic is one colour, tinted at the call site. Defaults to `LocalAppColors.current.onBackground`; pass `onSurfaceInverse` on inverse surfaces. Never hard-code a hex (house rule from `ui/theme/Colors.kt`).
- Don't recolour parts of a logo, outline it, stretch it, rotate it or put it on a photo without a solid backing.
- The counters (holes in the B) are real cut-outs, so the logo works on any solid surface.

## Rules

- Clear space: keep space equal to the big plate's width on every side of the mark or logo.
- Minimum sizes: mark 16dp tall, horizontal logo 24dp tall, stacked logo 56dp tall.
- Headings: Archivo Expanded 900 (`BrandType`). Short lines only; never for body text.
- UI text: Space Grotesk 400, 500, 700 (`BrandFonts.SpaceGrotesk`).
- Notification small icons must stay white on transparent. Android tints them.

## Not done yet (decisions for later)

- `AppType` in `ui/theme/Type.kt` still uses the system font, with sizes tuned for Roboto. Moving to Archivo and Space Grotesk should be done in one pass across `AppType`, re-checking sizes and line heights, not screen by screen.
- No splash theme, by decision (2026-10-02). On Android 12+ the system splash already shows the launcher icon on `windowBackground`. The real wait (first-launch seeding) shows `BldrLoader` instead, after a 300 ms delay so normal launches never flash it (`MainActivity.LaunchLoader`).

## Licences

Archivo and Space Grotesk are under the SIL Open Font License 1.1. The licence texts are in `brand/licenses/` and must ship with the fonts.
