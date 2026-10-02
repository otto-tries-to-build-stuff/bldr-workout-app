# BLDR Brand Rollout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The app looks, reads and launches as BLDR: name, icon, a restrained logo presence, the BLDR loader wherever the user waits, and the brand typefaces across every screen.

**Architecture:** No new dependencies and no new screens. Brand graphics and fonts already live in `ui/brand/Brand.kt` and `res/`. This plan wires them in at a handful of call sites and does one pass over `AppType` in `ui/theme/Type.kt`, which every text style in the app goes through. Each task ships on its own, ordered from lowest to highest risk.

**Tech Stack:** Kotlin 2.2, Jetpack Compose (BOM 2026.02.01: `BasicText` `autoSize`, `LineHeightStyle`), Android adaptive icons, JUnit4 + `kotlin.test`.

**Spec:** `brand/BRAND.md` (usage rules, sizes, what was deliberately left undone). It sits alongside the main design spec `docs/superpowers/specs/2026-07-21-workout-builder-android-design.md` §5 (mono, no shadows, type carries hierarchy). Read BRAND.md in full before any task.

## Global Constraints

- Black and white only. No accent hue, gradient or shadow, on the marks or anywhere else.
- No literal hex outside `ui/theme/Colors.kt`. **This includes `@Preview(backgroundColor = 0x…)`**: previews take their background from `WorkoutTheme` + `LocalAppColors`. (Existing XML hex in `res/values*/themes.xml` and `bldr_brand.xml` is the accepted exception, since XML cannot read Kotlin. Add no new XML colours.)
- Never edit `pathData` in any `res/drawable/bldr_*`, `ic_launcher_*` or `ic_stat_bldr` file. If geometry looks wrong, stop and flag it; the user fixes it in the design canvas and re-exports.
- Brand graphics are tinted at the call site. On the always-dark execution and finish screens, pass `ExecutionColors.onBackground`. The default (`LocalAppColors.onBackground`) is near-black in the Light theme and would vanish on the dark execution background.
- Minimum sizes: mark 16 dp tall, horizontal logo 24 dp, stacked logo 56 dp. Clear space is at least the big plate's width (≈ 0.19 × the mark's height) on every side.
- Accessibility: every touch target ≥ 48 dp (`Dimens.minTouchTarget`); decorative logos get `contentDescription = null`, identifying ones get `"BLDR"`; the loader holds still (full mark) when the system has animations turned off; text contrast is unchanged (no colour changes in this plan).
- Leave `namespace`, `applicationId` (`com.ottotech.workoutbuilder`), class names (`WorkoutApp`, `WorkoutTheme`) and the `Theme.WorkoutBuilder` style name alone. They are invisible to users.
- Gradle in Git Bash: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` first.
- After any build that will be tried on a phone, copy `app/build/outputs/apk/debug/app-debug.apk` to `G:\My Drive\BLDR\BLDR_<yyyy-MM-dd_HHmm>.apk`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Largest font size on a 360 dp-wide phone (the Galaxy S22).** Titles, the category header "SHOULDERS" and the timer must shrink to fit. They must never clip, run off-screen or break mid-word. *(Task 6: `ScreenTitle` / `TimerRing` auto-size + device pass at max font size.)*
2. **Animations turned off** (Developer options → Animator duration scale off, or Accessibility → Remove animations). The loader shows the complete, still mark and stops scheduling frames. *(Task 3: "animations off" preview + device check.)*
3. **App set to Light, workout running.** The execution and finish screens stay dark, and the BLDR mark on the finish screen is white, not invisible. *(Task 5: explicit `ExecutionColors.onBackground` + device check in Light.)*
4. **Normal launch versus first launch.** A normal launch never flashes the loader; a fresh install (≈ 10 s of seeding) shows it within a third of a second and keeps it until Home appears. *(Task 4: delay constant + `pm clear` device check.)*
5. **TalkBack.** The loader is announced once as "in progress", decorative marks are silent, and the Settings logo reads "BLDR". *(Tasks 3 and 5: semantics + TalkBack pass.)*

---

## What was checked before writing this plan (2026-10-02)

> Updated 2026-10-03 for the striped-B mark; see `plans/2026-10-02-bldr-striped-b.md`. The icon-geometry bullet below now describes the new mark; the original dumbbell measurements are in git history.

Verified on the current working tree, with nothing changed:

- **Build:** `./gradlew :app:assembleDebug` passes with the brand drop, so `Brand.kt` compiles and every new resource links.
- **Launcher icon (already wired):** `aapt2 dump badging` reports `icon='res/mipmap-anydpi-v26/ic_launcher.xml'` for every density, with `ic_launcher_round` alongside. `minSdk` is 26, so the adaptive icon covers every supported device and **no PNG fallbacks are needed**. Both files declare background, foreground and `<monochrome>` layers. `roundIcon` is only read by Android 7.1 launchers, so it is redundant at minSdk 26 but harmless; keep it.
- **Icon geometry (striped B, no path edits needed):** the launcher foreground and monochrome layers are identical: a 30.9 × 40 dp mark (x 38.57–69.43, y 34.00–74.00 in the 108 dp canvas), centred exactly on (54, 54). Its farthest corner is 25.3 dp from centre, inside the 33 dp safe-zone radius every launcher mask keeps (7.7 dp to spare); offline renders under circle, squircle, rounded-square and teardrop masks, and the Pixel themed icon, all show even margins. `ic_stat_bldr` is 15.4 × 20 dp (x 4.29–19.71, y 2.00–22.00 of 24), centred and inside the 22 dp live area, white on transparent. `Bars` in `Brand.kt` match `bldr_mark.xml`'s seven 10-high pills (radius 5) exactly. **No geometry to flag.**
- **Notification icon (already wired):** `ExecutionNotification` now calls `setSmallIcon(R.drawable.ic_stat_bldr)`, and the drawable is in the APK.
- **Fonts:** all four TTFs are in the APK (≈ 380 KB total). Each embeds its OFL licence text and URL in its name table, which satisfies OFL clause 2, so no licence screen is legally required.
- **Glyph coverage:** Space Grotesk and Archivo cover every character the UI shows except ↺ ⋯ ▸ ✓ ✕. Roboto lacks those five too (it also lacks ↑ ↓, which Space Grotesk has), so they already come from the system fallback font. Nothing gets worse.
- **"Workout Builder" visible to users:** only the two `android:label`s in `AndroidManifest.xml`. No in-app string says it.

### Measured width impact (fontTools, real app strings)

| Text | Style today | Roboto | Archivo Expanded 900 | Room available on S22 |
|---|---|---|---|---|
| WORKOUTS | screenTitle 40 sp | 207 dp | **320 dp** | 320 dp |
| SHOULDERS (category header) | screenTitle 40 sp | 215 dp | **335 dp** | ≈ 288 dp (after the ‹ box) |
| SETTINGS | screenTitle 40 sp | 178 dp | 275 dp | ≈ 288 dp |
| 59:59 (manual-mode elapsed) | timerDigits 46 sp | 113 dp | **169 dp** | 212 dp inside the ring; 220 dp at 1.3× font size |
| 3 (lead-in) | leadInDigits 180 sp | 98 dp | 148 dp | fine, even at the 1.35× pop |

Archivo Expanded is **≈ 1.55× wider** than Roboto ExtraBold at the same size. Space Grotesk is only ≈ 7% wider than Roboto, but its x-height is smaller (0.486 vs 0.528 em) and its natural line box is ≈ 9% taller (1.276 vs 1.172 em). So Archivo sizes must come down, Space Grotesk sizes can stay, and every style needs an explicit line height.

---

## File Structure

| File | Task | Change |
|---|---|---|
| `AndroidManifest.xml` | 2 | `android:label="BLDR"`; drop the duplicate activity label |
| `ui/brand/Brand.kt` | 3, 6 | Loader: animations-off fast path, progress semantics, `@Preview`s. Task 6 removes `BrandType` (folded into `AppType`) |
| `ui/coach/CoachScreen.kt` | 3 | `LinearProgressIndicator` → `BldrLoader` |
| `MainActivity.kt` | 4 | Delayed `BldrLoader` while first-launch seeding runs |
| `ui/home/HomeScreen.kt` | 5, 6 | Mark on the date line; title via `ScreenTitle` |
| `ui/settings/SettingsScreen.kt` | 5 | "About" section: logo, version, font credit |
| `ui/execution/FinishScreen.kt` | 5 | Mark above "WORKOUT COMPLETE" |
| `ui/theme/Type.kt` | 6 | `AppType` on `BrandFonts`, re-fitted sizes and line heights, `navLabel` added, `libraryTitle` removed |
| `ui/theme/Theme.kt` | 6 | Space Grotesk as the default text style (safety net) |
| `ui/components/Controls.kt` | 6 | New `ScreenTitle` (auto-shrinking heading); `ScreenHeader` uses it |
| `ui/library/LibraryScreen.kt` | 6 | Title via `ScreenTitle` |
| `ui/execution/TimerRing.kt` | 6 | Auto-shrinking digits |
| `ui/components/AppScaffold.kt` | 6 | Nav label uses `AppType.navLabel` instead of an inline 12 sp |
| `app/src/test/.../ui/theme/AppTypeTest.kt` | 6 | New: every style on a brand font with an explicit line height |
| `brand/BRAND.md`, `docs/superpowers/STATUS.md` | every task | Prune "Not done yet"; keep the status row current |

All Kotlin paths are relative to `app/src/main/java/com/ottotech/workoutbuilder/` unless they start with `app/`, `brand/` or `docs/`.

---

### Task 0: Save the work in flight, then branch

The working tree currently mixes two unrelated sets of changes: the Workout Overview / lead-in work (modified `domain/` and `ui/` files, `OverviewScreen`, `WorkoutOverviewTest`) and the brand drop (untracked `brand/`, `res/drawable`, `res/font`, `res/mipmap-anydpi-v26`, `bldr_brand.xml`, `ui/brand/`, `CLAUDE.md`, plus the manifest icon lines and the notification icon change). Brand work must not be tangled with them.

- [ ] **Step 1:** Run the unit tests and confirm they are green before committing anything: `./gradlew :app:testDebugUnitTest`.
- [ ] **Step 2:** Commit the overview / lead-in work on `phase-2-ai-coach` as its own commit. Ask the user for a one-line description of it if the diff doesn't make it obvious.
- [ ] **Step 3:** Commit the brand drop as a second commit: `chore: add BLDR brand assets, launcher icon and notification icon`.
- [ ] **Step 4:** `git checkout -b bldr-brand`. Add a row to `docs/superpowers/STATUS.md`: "**BLDR brand rollout** | In progress on `bldr-brand` (branched from `phase-2-ai-coach`). Plan: `plans/2026-10-02-bldr-brand-rollout.md`."

---

### Task 1: App icon — confirm on devices and prepare the store listing

No code changes; the icon is already wired and verified in the build (see "What was checked"). This task proves it on real launchers and lists what the Play listing still needs.

**Files:** none in the app. Possibly `brand/png/` (re-exports by the user, not by Claude).

- [ ] **Step 1: Lint the icon setup.** Run `./gradlew :app:lintDebug` and open `app/build/reports/lint-results-debug.html`. Expected: no `IconLauncherShape`, `MonochromeLauncherIcon`, `IconMissingDensityFolder` or `NotificationIconCompatibility` issues. If any appear, report them; don't edit drawables.
- [ ] **Step 2: Device checks.**
  - Pixel 8 Pro emulator (API 36): home screen icon (circle mask), Settings → Wallpaper & style → Themed icons **on**. The icon redraws in the wallpaper tint, so the monochrome layer works. App info icon. Recents.
  - Galaxy S22 (One UI, squircle mask): home screen, app drawer, Settings → Apps.
  - Start a workout and pull down the shade. The status-bar icon is the BLDR mark in white (not a white square), and the notification header shows the mark.
  - Optional: an API 26 emulator, to confirm the adaptive icon without the monochrome layer.
- [ ] **Step 3: Play Store listing gaps (for the user to re-export; record them in STATUS.md).**
  - `brand/png/bldr-play-store-icon-512.png` is 512 × 512, full-bleed square (correct: Play applies its own corner mask), 10 KB. But it is **24-bit RGB, and Play's spec asks for 32-bit PNG (with alpha)**. Re-export with an alpha channel (fully opaque is fine).
  - **Missing:** feature graphic, 1024 × 500 (required for a listing).
  - **Missing:** at least 2 phone screenshots. Take them after Task 6, so they show the brand type.
  - Listing title can differ from the launcher label (30 characters max). Consider "BLDR: Workout Builder", so the store page keeps the search words while the launcher stays "BLDR". The user decides.

**Risk:** very low; nothing changes in the app. **Check:** lint + on-device.

---

### Task 2: App name → "BLDR"

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `brand/BRAND.md` (remove the "app's label" line from "Not done yet")

- [ ] **Step 1: Change the label once, at application level.** The activity's own label is a duplicate. Without it, the launcher, Recents and the notification header all inherit the application label.

```xml
    <application
        android:name=".WorkoutApp"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="BLDR"
        android:supportsRtl="true"
        android:theme="@style/Theme.WorkoutBuilder">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:theme="@style/Theme.WorkoutBuilder">
```

- [ ] **Step 2: Verify in the built file.** Build, then confirm both labels:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleDebug -q
A=$(ls -d /c/Users/ottol/AppData/Local/Android/Sdk/build-tools/*/ | tail -1)
"$A/aapt2.exe" dump badging app/build/outputs/apk/debug/app-debug.apk | grep -E "application:|launchable-activity"
```

Expected: `application: label='BLDR'` and `launchable-activity: … label='BLDR'`.

- [ ] **Step 3: On device.** Install it. The label under the icon reads BLDR on the home screen and in the app drawer, Recents, Settings → Apps and the running-workout notification header. With TalkBack on, touch the launcher icon. TalkBack spells it "B L D R", because there is no separate spoken name for a launcher label. Record that in STATUS.md as a known, accepted behaviour.
- [ ] **Step 4: Confirm nothing else user-visible still says the old name:** `grep -rn "Workout Builder" app/src/main` should return nothing.
- [ ] **Step 5: Commit** `feat: rename the app to BLDR` (manifest + BRAND.md + STATUS.md row).

**Risk:** low. An existing install updates in place, because `applicationId` is unchanged; some launchers cache the old label until the app is updated or the phone restarts. **Check:** aapt2 + on-device.

---

### Task 3: BLDR loader — preview, accessibility, and the Coach

> Later change (2026-10-03): the canvas redrew the loader as the striped B's "build the B" motion. `BldrLoader` now draws every bar in code and `bldr_loader_base.xml` is deleted, so the `painterResource(R.drawable.bldr_loader_base)` code below is historical. The Coach wait was also restyled (centred loader with "working words"); see `plans/2026-10-02-bldr-striped-b.md`.

**Every waiting moment in the app, and what happens to each:**

| Where | What the user sees today | Duration | Proposal |
|---|---|---|---|
| First launch: `MainActivity` gates the UI on seeding | Blank page | ≈ 10 s on a fresh install | **BldrLoader**, Task 4 |
| Coach "Drafting your workout…" (`CoachScreen.Loading`) | Thin `LinearProgressIndicator` | 15–30 s | **BldrLoader**, this task |
| Builder in edit mode (`BuilderScreen`, `isLoading`) | Header only | One local database read, a few frames | Leave: a loader would only flash |
| Home before the list arrives (`HomeViewModel.loading`) | Header only | ≤ 1 frame (seeding is gated earlier) | Leave |
| Overview before the workout arrives | Header only | A few frames | Leave |
| Execution before the service publishes its first state (`session.current ?: return`) | Dark page | ≈ 100 ms | Leave |
| Coach client warm-up (≈ 4 s class loading on screen open) | Nothing; it runs in the background | n/a | Covered by the Coach loader if the user taps Generate early |

**Files:**
- Modify: `ui/brand/Brand.kt`
- Modify: `ui/coach/CoachScreen.kt`

**Interfaces:**
- Produces: `BldrLoader(modifier, width = 64.dp, color, contentDescription = "Loading", animate = rememberSystemAnimationsOn())`. The new trailing `animate` parameter is used by previews; callers leave it at its default.

- [ ] **Step 1: Make the still path truly still, and announce progress.** Replace the body of `BldrLoader` and add the helper. When animations are off, the infinite transition is never created, so no frames are scheduled. `progressBarRangeInfo` makes TalkBack announce it as "in progress".

```kotlin
@Composable
fun BldrLoader(
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
    color: Color = LocalAppColors.current.onBackground,
    contentDescription: String = "Loading",
    /** Exposed for previews. Callers keep the default, which follows the system setting. */
    animate: Boolean = rememberSystemAnimationsOn(),
) {
    val description = contentDescription
    val scales: List<State<Float>>? = if (animate) {
        val transition = rememberInfiniteTransition(label = "bldrLoader")
        listOf(
            transition.plateScale(delayMillis = 0),
            transition.plateScale(delayMillis = 150),
            transition.plateScale(delayMillis = 300),
        )
    } else null

    Box(
        modifier
            .size(width, width * (MarkHeight / MarkWidth))
            .semantics {
                this.contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
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
                val scale = scales?.get(i)?.value ?: 1f
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

/** False when the user has turned animations off (Developer options, or Accessibility → Remove animations). */
@Composable
private fun rememberSystemAnimationsOn(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}
```

New imports: `androidx.compose.ui.semantics.ProgressBarRangeInfo`, `androidx.compose.ui.semantics.progressBarRangeInfo`.

- [ ] **Step 2: Add the previews** at the bottom of `Brand.kt`. They use the theme for the background, never a hex `backgroundColor`.

```kotlin
@Preview(name = "Loader · light")
@Composable
private fun BldrLoaderLightPreview() = LoaderPreview(dark = false, animate = true)

@Preview(name = "Loader · dark")
@Composable
private fun BldrLoaderDarkPreview() = LoaderPreview(dark = true, animate = true)

@Preview(name = "Loader · animations off")
@Composable
private fun BldrLoaderStillPreview() = LoaderPreview(dark = false, animate = false)

@Composable
private fun LoaderPreview(dark: Boolean, animate: Boolean) {
    WorkoutTheme(dark = dark) {
        Box(Modifier.background(LocalAppColors.current.background).padding(24.dp)) {
            BldrLoader(animate = animate)
        }
    }
}
```

New imports: `androidx.compose.ui.tooling.preview.Preview`, `androidx.compose.foundation.background`, `androidx.compose.foundation.layout.padding`, `com.ottotech.workoutbuilder.ui.theme.WorkoutTheme`. (`ui-tooling-preview` is already an `implementation` dependency; `ui-tooling` is `debugImplementation`.)

- [ ] **Step 3: Check the previews in Android Studio.** Open `Brand.kt` → Split view. Expected: three previews. The still one shows the complete mark at full strength. The static light/dark ones show the plates at their smallest (the first animation frame). Switch the light one to **Interactive** mode to watch the pulse run outside-in: collar first, big plate last.
- [ ] **Step 4: Swap the Coach's progress bar.** In `CoachScreen.kt`, `Loading()` becomes:

```kotlin
@Composable
private fun Loading() {
    val colors = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BldrLoader(width = 64.dp)
        Spacer(Modifier.height(8.dp))
        Text("Drafting your workout…", style = AppType.cardTitle, color = colors.onBackground)
        Text("This takes 15–30 seconds.", style = AppType.cardMeta, color = colors.textMuted)
    }
}
```

Remove the `LinearProgressIndicator` import; add `com.ottotech.workoutbuilder.ui.brand.BldrLoader`.

- [ ] **Step 5: Build, run the unit tests and install.** `./gradlew :app:testDebugUnitTest :app:installDebug`. Expected: green, with no test count change.
- [ ] **Step 6: On device.** Settings → paste a dummy key that passes the format check → Coach → type a goal → BUILD MY WORKOUT. The loader pulses until the 401 comes back, a second or two (or 15–30 s with a real key). Then run `adb shell settings put global animator_duration_scale 0`, repeat, and expect the complete still mark. Restore with `… 1`. With TalkBack on, the loader reads "Loading, in progress" once.
- [ ] **Step 7: Commit** `feat: BLDR loader in the Coach, with previews and a still state when animations are off`.

**Risk:** low. One screen changes, and the loader component already existed. **Check:** preview + on-device (animations on and off, TalkBack).

---

### Task 4: Launch experience

**Recommendation: rely on the default system splash, add no splash theme and no `core-splashscreen` dependency, and fix the real gap: the blank screen during first-launch seeding.**

Why: on Android 12+ (both test devices), the system splash already shows the adaptive launcher icon (the white mark on its black disc) on `windowBackground`, which matches the app's background in both themes. A custom splash theme would duplicate that. It also cannot help the actual problem. The system splash leaves as soon as the first frame draws, and `MainActivity` draws an empty `Box` straight away and then waits ≈ 10 s for seeding on a fresh install. Holding the splash for those 10 s (with `setKeepOnScreenCondition`) would look frozen, and it would need a new dependency to behave the same on Android 8–11.

**Files:**
- Modify: `MainActivity.kt`
- Modify: `brand/BRAND.md` (the "No splash theme is set" line becomes the decision, with the reason)

- [ ] **Step 1: Show the loader while seeding, after a short delay.** In `setContent`:

```kotlin
            WorkoutTheme(dark = dark) {
                Box(Modifier.fillMaxSize().background(LocalAppColors.current.background)) {
                    if (ready) AppNav() else LaunchLoader()
                }
            }
```

and at the bottom of the file:

```kotlin
/**
 * Shown while [MainActivity] waits on seeding: ~10 s on a fresh install, a few frames otherwise.
 * It waits [LaunchLoaderDelayMs] before appearing, so a normal launch never flashes it.
 */
@Composable
private fun LaunchLoader() {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LaunchLoaderDelayMs)
        show = true
    }
    if (!show) return
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BldrLoader(width = 72.dp)
        Spacer(Modifier.height(16.dp))
        Text("Getting ready", style = AppType.cardMeta, color = LocalAppColors.current.textMuted)
    }
}

private const val LaunchLoaderDelayMs = 300L
```

- [ ] **Step 2: Fresh-install check.** Run `MSYS_NO_PATHCONV=1 adb shell pm clear com.ottotech.workoutbuilder`, then launch. Expected on the S22: system splash (mark), then the pulsing loader with "Getting ready" for ≈ 10 s, then Home with Court Speed. The loader sits roughly where the splash icon was, so the hand-off reads as one moment.
- [ ] **Step 3: Normal-launch check.** Force-stop, then launch again. Expected: splash, then Home with no loader flash. If a flash is seen on the S22, raise `LaunchLoaderDelayMs` in 100 ms steps and note the value in STATUS.md.
- [ ] **Step 4: Animations off + TalkBack.** Same fresh-install run with `animator_duration_scale 0`: still mark. With TalkBack: "Loading, in progress", then "Getting ready".
- [ ] **Step 5: Commit** `feat: BLDR loader during first-launch setup; keep the system splash`.

**Known and left alone (existing behaviour):** the launch window's background follows the phone's dark mode, not the app's Appearance setting. Someone who picked Light on a dark phone sees a near-black splash for a moment. Fixing it needs per-launch theme switching before `onCreate`, which is out of scope.

**Risk:** low to medium. It touches the app's entry point, but only the not-ready branch. **Check:** on-device (fresh install, normal launch, animations off).

---

### Task 5: Logo presence inside the app

Restrained by design: three placements, each at a natural brand moment. The type-led screen headers stay logo-free, because a logo on every header is noise in a mono, type-led app. Empty states stay text-only.

| Place | Asset | Size | Colour | Description |
|---|---|---|---|---|
| Home header, start of the date line | `BldrMark` | 18 dp tall | `onBackground` | `null` (decorative) |
| Settings → new "About" section, last | `BldrLogo` (horizontal) | 24 dp (its minimum) | `onBackground` | `"BLDR"` |
| Finish screen, above "WORKOUT COMPLETE" | `BldrMark` | 40 dp | **`ExecutionColors.onBackground`** | `null` |

Considered and declined: the logo in `ScreenHeader` (every pushed screen), in the Home empty card, in the Library "Nothing matches" state and on the execution screen (focus mode; the mark there would compete with the timer).

**Files:**
- Modify: `ui/home/HomeScreen.kt` (`Header`)
- Modify: `ui/settings/SettingsScreen.kt`
- Modify: `ui/execution/FinishScreen.kt`

- [ ] **Step 1: Home.** In `Header`, the date row becomes:

```kotlin
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 18 dp clears the mark's 16 dp minimum; 10 dp is well over its clear space (~3.4 dp).
            BldrMark(height = 18.dp, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(overline, style = AppType.overline, color = colors.textMuted, modifier = Modifier.weight(1f))
            TextAction("Settings", color = colors.textMuted, onClick = onOpenSettings)
        }
```

- [ ] **Step 2: Settings "About".** Append after the Sound items, inside the `LazyColumn`. Read the version from the package manager, because `BuildConfig` generation is off by default in this AGP and isn't worth enabling for one line:

```kotlin
            item { Spacer(Modifier.height(8.dp)) }
            item { SectionLabel("About") }
            item {
                val version = remember {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BldrLogo(height = 24.dp)
                    Text("Version $version", style = AppType.cardMeta, color = colors.textMuted)
                    Text(
                        "Set in Archivo and Space Grotesk, used under the SIL Open Font License.",
                        style = AppType.cardMeta,
                        color = colors.textMuted,
                    )
                }
            }
```

- [ ] **Step 3: Finish screen.** First child of the centred `Column`:

```kotlin
            // Execution is always dark: the default tint (LocalAppColors) would be near-black in Light.
            BldrMark(height = 40.dp, color = ExecutionColors.onBackground, contentDescription = null)
            Spacer(Modifier.height(8.dp))
```

- [ ] **Step 4: Build, test, install.** `./gradlew :app:testDebugUnitTest :app:installDebug`.
- [ ] **Step 5: On device, in Light and in Dark** (Settings → Appearance): the Home mark lines up vertically with the date text, and Settings still has its 48 dp tap height. In Settings, About shows the logo, "Version 0.1" and the font line. Run a short workout to the end in **Light**: the mark on the finish screen is white on dark. TalkBack: the Home mark is skipped; the About logo reads "BLDR".
- [ ] **Step 6: Commit** `feat: BLDR mark on Home and the finish screen; About section in Settings`.

**Risk:** low. The one real trap (an invisible mark on the finish screen in Light) is called out in the code comment and the device check. **Check:** on-device, both themes, TalkBack.

---

### Task 6: Typography — one pass from Roboto to the BLDR fonts

**The mapping.** Archivo Expanded 900 is used for screen titles and big numbers only; Space Grotesk for everything else. Space Grotesk ships in 400 / 500 / 700, so **SemiBold (600) → Medium (500)** and **ExtraBold (800) → Bold (700)**.

| Style | Today (Roboto) | New | Line height | Tracking | Why |
|---|---|---|---|---|---|
| `screenTitle` | 40 ExtraBold, lh 42 | **Archivo 32** | 1.06 em | 0.01 em | 1.55× wider; "SHOULDERS" must fit 288 dp. Auto-shrinks to 22 sp |
| `libraryTitle` | 40 ExtraBold | **removed**; Library uses `screenTitle` | | | Duplicate of `screenTitle` |
| `timerDigits` | 46 ExtraBold, tnum | **Archivo 38**, tnum | 1.1 em | 0 | "59:59" = 140 dp in a 212 dp ring. Auto-shrinks to 24 sp |
| `leadInDigits` | 180, lh 200 | Archivo 180, lh 200, tnum | 200 sp | 0 | "3" = 148 dp; cap height matches Roboto's (123 vs 128 dp) |
| `heroName` | 26 ExtraBold | SG Bold 26 | 30 sp | −0.01 em | |
| `moveName` | 34 ExtraBold, centred | SG Bold **32**, centred | 36 sp | −0.02 em | Long names stay at 2 lines at 1.3× font size |
| `repsExerciseName` | 38 Bold | SG Bold **34** | 38 sp | −0.02 em | Same reason; exercise detail title |
| `cardTitle` | 18 Bold | SG Bold 18 | 24 sp | 0 | |
| `tileName` | 18 Bold | SG Bold 18 | 24 sp | 0 | |
| `setRowLabel` | 17 SemiBold | SG Medium 17 | 22 sp | 0 | |
| `buildButton` | 17 Bold | SG Bold 17 | 22 sp | 0.02 em | All-caps label |
| `primaryButton` | 16 Bold, centred | SG Bold 16, centred | 20 sp | 0.02 em | All-caps label |
| `heroMeta` | 16 | SG Regular 16 | 22 sp | 0 | |
| `cardMeta` | 14 | SG Regular 14 | 20 sp | 0 | |
| `chip` | 14 SemiBold | SG Medium 14 | 18 sp | 0 | |
| `tileCount` | 14 SemiBold | SG Medium 14 | 18 sp | 0 | |
| `overline` | 13 SemiBold, 0.02 em | SG Medium 13 | 16 sp | 0.04 em | |
| `sectionLabel` | 13 ExtraBold, 0.06 em | SG Bold 13 | 16 sp | 0.06 em | |
| `muscleTag` | 13 SemiBold, 0.04 em | SG Medium 13 | 16 sp | 0.04 em | |
| `heroLabel` | 12 Bold, 0.08 em | SG Bold 12 | 16 sp | 0.08 em | |
| `timerCaption` | 12 Bold, 0.08 em | SG Bold 12 | 16 sp | 0.08 em | |
| `navLabel` (**new**) | inline 12 sp in `AppScaffold` | SG Medium 12 (Bold when active) | 16 sp | 0 | Brings the one ad-hoc style into `AppType` |

Body sizes stay put: Space Grotesk's smaller x-height offsets its extra width, so the same sp reads at about the same size. All Roboto-tuned negative tracking on titles goes, because Archivo Expanded is drawn for neutral or slightly open spacing.

**Screens at risk of overflow, and what handles each:**
- **Category header "SHOULDERS"** (335 dp at 40 sp vs ≈ 288 dp available): 32 sp → 268 dp, and `ScreenTitle` auto-shrinks at larger font sizes.
- **Home "YOUR / WORKOUTS"** (320 dp at 40 sp = the S22's full content width): 32 sp → 256 dp, and auto-shrinks on 2 lines.
- **Timer in manual mode** (elapsed "59:59" at 1.3× font size would be 220 dp in a 212 dp ring): 38 sp + auto-shrink.
- **Long exercise names** ("Overhead Triceps Extension", the longest at 441 dp at 34 sp in Space Grotesk): wrap to 2 lines on the execution screen, the finish screen and exercise detail. Device check at max font size; this is the one place a 3rd line might appear.
- **Lead-in digit:** safe, no change needed.
- **Fixed-height buttons (48 / 56 dp) and 48 dp chips:** explicit line heights ≤ 22 sp keep the text centred in them; `LineHeightStyle(Center, Trim.None)` stops Space Grotesk's tall ascender from pushing labels low.

**Files:**
- Create: `app/src/test/java/com/ottotech/workoutbuilder/ui/theme/AppTypeTest.kt`
- Modify: `ui/theme/Type.kt`, `ui/theme/Theme.kt`, `ui/components/Controls.kt`, `ui/components/AppScaffold.kt`, `ui/home/HomeScreen.kt`, `ui/library/LibraryScreen.kt`, `ui/execution/TimerRing.kt`, `ui/brand/Brand.kt`, `brand/BRAND.md`

**Interfaces:**
- Consumes: `BrandFonts.ArchivoExpanded`, `BrandFonts.SpaceGrotesk` (`ui/brand/Brand.kt`).
- Produces: `AppType.navLabel`; `@Composable fun ScreenTitle(text: String, modifier: Modifier = Modifier, maxLines: Int = 1)` in `ui/components/Controls.kt`. Removes `AppType.libraryTitle` and `BrandType`.

- [ ] **Step 1: Write the failing test.**

```kotlin
package com.ottotech.workoutbuilder.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import com.ottotech.workoutbuilder.ui.brand.BrandFonts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards the one-pass font migration: no style may fall back to the system font. */
class AppTypeTest {
    private val styles: Map<String, TextStyle> = AppType::class.java.declaredMethods
        .filter { it.parameterCount == 0 && it.returnType == TextStyle::class.java }
        .associate { m -> m.name.removePrefix("get").replaceFirstChar { it.lowercase() } to m.invoke(AppType) as TextStyle }

    @Test
    fun `every style is on a brand font`() {
        val brand = setOf(BrandFonts.ArchivoExpanded, BrandFonts.SpaceGrotesk)
        assertTrue(styles.size >= 20, "Reflection found only ${styles.keys}")
        val off = styles.filterValues { it.fontFamily !in brand }.keys
        assertTrue(off.isEmpty(), "Not on a brand font: $off")
    }

    @Test
    fun `every style sets its own line height`() {
        val unset = styles.filterValues { !it.lineHeight.isSpecified }.keys
        assertTrue(unset.isEmpty(), "No explicit lineHeight: $unset")
    }

    @Test
    fun `archivo is only for titles and big numbers`() {
        val archivo = styles.filterValues { it.fontFamily == BrandFonts.ArchivoExpanded }.keys
        assertEquals(setOf("screenTitle", "timerDigits", "leadInDigits"), archivo)
    }
}
```

- [ ] **Step 2: Run it and watch it fail.** `./gradlew :app:testDebugUnitTest --tests "*AppTypeTest*"`. Expected: FAIL. "Not on a brand font" lists every style, and the line-height test lists the styles without one. (If it errors on class loading rather than failing on the assertions, the Compose text classes aren't usable on the JVM here. Delete the test, note it in STATUS.md, and rely on Step 9's device pass.)
- [ ] **Step 3: Rewrite `AppType`.**

```kotlin
package com.ottotech.workoutbuilder.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ottotech.workoutbuilder.ui.brand.BrandFonts

/**
 * Type scale. Sizes were rescaled 2026-07-26 (the handoff's px were ~62% of true size; do not
 * revert toward them) and re-fitted 2026-10-02 for the BLDR fonts. See
 * docs/superpowers/plans/2026-10-02-bldr-brand-rollout.md, Task 6:
 * - Archivo Expanded 900 (titles, big numbers) runs ~1.55x wider than Roboto ExtraBold, so
 *   titles drop 40 → 32 sp and the timer 46 → 38 sp; ScreenTitle and TimerRing also auto-shrink.
 * - Space Grotesk (everything else) is ~7% wider than Roboto but has a smaller x-height, so body
 *   sizes stay. It ships 400 / 500 / 700 only: SemiBold → Medium, ExtraBold → Bold.
 * Every style sets its own line height; Space Grotesk's natural line box is ~9% taller than
 * Roboto's. AppTypeTest enforces both rules.
 *
 * With a mono accent, hierarchy is carried by weight, size and tracking rather than colour,
 * so these values are load-bearing.
 */
object AppType {
    private val centred = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    private val ui = TextStyle(fontFamily = BrandFonts.SpaceGrotesk, lineHeightStyle = centred)
    private val display = TextStyle(
        fontFamily = BrandFonts.ArchivoExpanded,
        fontWeight = FontWeight.Black,
        lineHeightStyle = centred,
    )

    val screenTitle = display.copy(fontSize = 32.sp, lineHeight = 1.06.em, letterSpacing = 0.01.em)
    val timerDigits = display.copy(
        fontSize = 38.sp,
        lineHeight = 1.1.em,
        fontFeatureSettings = "tnum", // tabular figures: digits must not jitter as they change
    )
    /** The lead-in's "3, 2, 1": the biggest type in the app, meant to be read from the floor. */
    val leadInDigits = timerDigits.copy(fontSize = 180.sp, lineHeight = 200.sp)

    val heroName = ui.copy(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em)
    val moveName = ui.copy(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em, textAlign = TextAlign.Center)
    val repsExerciseName = ui.copy(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
    val cardTitle = ui.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    val tileName = ui.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    val setRowLabel = ui.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val buildButton = ui.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.02.em)
    val primaryButton = ui.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.02.em, textAlign = TextAlign.Center)
    val heroMeta = ui.copy(fontSize = 16.sp, lineHeight = 22.sp)
    val cardMeta = ui.copy(fontSize = 14.sp, lineHeight = 20.sp)
    val chip = ui.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val tileCount = ui.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val overline = ui.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.04.em)
    val sectionLabel = ui.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.06.em)
    val muscleTag = ui.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.04.em)
    val heroLabel = ui.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
    val timerCaption = ui.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
    val navLabel = ui.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
}
```

- [ ] **Step 4: Run the test again; expect PASS (3 tests).**
- [ ] **Step 5: Add `ScreenTitle` and use it in all three title sites.** In `ui/components/Controls.kt`:

```kotlin
/**
 * A screen title in Archivo Expanded. The face is wide, so the title shrinks to fit (down to
 * 22 sp) rather than clipping or breaking a word at large font sizes on narrow phones.
 */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    val color = LocalAppColors.current.onBackground
    BasicText(
        text = text,
        style = AppType.screenTitle,
        color = { color },
        maxLines = maxLines,
        autoSize = TextAutoSize.StepBased(minFontSize = 22.sp, maxFontSize = AppType.screenTitle.fontSize),
        modifier = modifier.semantics { heading() },
    )
}
```

Then:
- `ScreenHeader`: replace `Text(title.uppercase(), style = AppType.screenTitle, …)` with `ScreenTitle(title.uppercase(), Modifier.weight(1f))`.
- `HomeScreen.Header`: `ScreenTitle("YOUR\nWORKOUTS", maxLines = 2)`.
- `LibraryScreen`: `ScreenTitle("LIBRARY")` (this was `AppType.libraryTitle`).

Imports: `androidx.compose.foundation.text.BasicText`, `androidx.compose.foundation.text.TextAutoSize`, `androidx.compose.ui.semantics.heading`, `androidx.compose.ui.semantics.semantics`, `androidx.compose.ui.unit.sp`. Screen titles become TalkBack headings, a small accessibility gain.

- [ ] **Step 6: Auto-shrink the timer.** In `TimerRing.kt`, the centre digits:

```kotlin
            // Archivo digits are wide: shrink rather than touch the ring at large font sizes.
            BasicText(
                centreTop,
                style = AppType.timerDigits,
                color = { ExecutionColors.onBackground },
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = AppType.timerDigits.fontSize),
                modifier = Modifier.widthIn(max = Dimens.ringSize - Dimens.ringStroke * 2 - 24.dp),
            )
```

- [ ] **Step 7: Nav label and the safety net.**
  - `AppScaffold.kt`: replace the inline `fontSize = 12.sp, fontWeight = …` with `style = if (active) AppType.navLabel.copy(fontWeight = FontWeight.Bold) else AppType.navLabel`.
  - `Theme.kt`: any `Text` without an `AppType` style should still land on Space Grotesk, not Roboto:

```kotlin
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme {
            // Safety net: Text with no AppType style still renders in Space Grotesk, not Roboto.
            ProvideTextStyle(AppType.cardMeta, content)
        }
    }
```

- [ ] **Step 8: Remove `BrandType`.** It becomes a second, unused type scale that disagrees with `AppType` (its 40 / 46 sp sizes overflow, per the table above). Delete the `BrandType` object from `Brand.kt`. In `brand/BRAND.md`, change the example line to `Text("Push day", style = AppType.screenTitle)`, change the Headings rule to point at `AppType.screenTitle` / `timerDigits` / `leadInDigits`, and delete the `AppType` bullet from "Not done yet". (This needs the user's OK; see the decision in the hand-off message.)
- [ ] **Step 9: Build, run the full test suite, install, and do the device pass.** `./gradlew :app:testDebugUnitTest :app:installDebug`, then copy the APK to the Drive folder. On the **Galaxy S22** at the default font size **and** at the largest (Settings → Display → Font size and style → max), in Light and Dark, screenshot before/after:
  - Home (title, mark, cards, the BUILD button label centred in 56 dp)
  - Library → Shoulders (header must fit on one line, shrunk if needed) → "Overhead Triceps Extension" detail
  - Builder (Off / Timed / Manual chips plus the rest stepper; the stepper is on its own line by design)
  - Coach (compose form; loader; preview), Settings (Appearance chips in one row; About)
  - Overview → start: lead-in "3"; a timed ring; a manual item left running past 1:00 (two-part clock); the reps panel; the finish screen
  - Any confirmation dialog (Home ⋯ → Delete → Cancel)

  Expected: nothing clipped, overlapping or broken mid-word; text vertically centred in buttons and chips; every touch target still 48 dp. Then repeat Home, Category and the execution ring on the Pixel 8 Pro emulator (wider, for balance).
- [ ] **Step 10: Commit** `feat: BLDR typography — Archivo Expanded titles and numbers, Space Grotesk everywhere else`. Update STATUS.md: rollout complete; the new facts (sizes re-fitted for Archivo; `ScreenTitle` auto-shrinks; SemiBold maps to Medium).

**Risk:** the highest in this plan. Every screen changes at once, which is what BRAND.md asks for, and it is easy to revert as one commit. The specific risks are the overflows listed above, the slightly lighter chips and tags (600 → 500), and line-height drift in fixed-height controls. **Check:** unit test + full on-device pass at default and max font size, both themes, two devices.

---

## Out of scope, deliberately

- A custom splash theme or `core-splashscreen` (Task 4 explains why).
- Logos in screen headers, empty states or the execution screen (Task 5).
- Previews for whole screens. The project has none, and adding them is a separate piece of work.
- Making the launch window follow the in-app Appearance setting (Task 4, "Known and left alone").
- Any edit to vector path data.
