# BLDR Striped-B Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Carry the new "striped B" mark and its "build the B" motion through the app: loader sizes, logo placements, preview health, icon checks and docs, without touching anything the design canvas owns.

**Architecture:** The canvas export is already in the working tree (all drawables regenerated, `bldr_loader_base.xml` deleted, `Brand.kt` merged with the new `BldrLoader` / `BldrSplashBuild` / `BldrRefreshIndicator`). The app-side work is small: two loader call sites change size, a few comments and previews are corrected, and the docs that describe the old dumbbell mark are rewritten. No new screens, no new loading states, no dependencies.

**Tech Stack:** Kotlin 2.2, Jetpack Compose (BOM 2026.02.01), Android adaptive icons, JUnit4.

**Spec:** `brand/BRAND.md` (updated from the canvas), especially "Motion: build the B" and "Rules". Builds on `plans/2026-10-02-bldr-brand-rollout.md` (complete on branch `bldr-brand`).

## Global Constraints

- Black and white only. No literal hex outside `ui/theme/Colors.kt`, including `@Preview(backgroundColor = …)`.
- Never edit `pathData` in any drawable, or `Bars`, `BuildTiming`, `MarkWidth`/`MarkHeight`/`BarHeight` or `barState` in `Brand.kt`. They come from the canvas. If something looks wrong, stop and flag it to the user.
- Keep: the still, complete B with no scheduled frames when animations are off; `progressBarRangeInfo = Indeterminate` plus a content description on the loaders; all previews.
- Don't add loading states that weren't there before.
- `BldrLoader(width = …)` is the mark's true width; the mark is 1.3× taller than wide (54 × 70). Bars drop in from above, so a call site needs about a fifth of the width of empty space above it.
- Minimum sizes: mark 16 dp tall, horizontal logo 24 dp, stacked 56 dp. Clear space: two bar heights (a bar is 1/7 of the mark's height) on every side.
- Gradle in Git Bash: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`. After any build that will go on a phone, copy the APK to `G:\My Drive\BLDR\BLDR_<yyyy-MM-dd_HHmm>.apk`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Animations off** (Developer options → Animator duration scale off, or Remove animations). All three components show the complete still B and schedule no frames. *(Task 2: device check with `animator_duration_scale 0`; Task 1: still previews.)*
2. **TalkBack while the loader animates.** It is announced once ("Loading", in progress), not re-announced on every frame of the build loop. *(Task 2: accessibility-event check while the loader runs.)*
3. **Static preview pane.** The animated loader and splash previews render frame 0, where no bar has landed yet, so they look empty in Android Studio's static view. That's correct behaviour, but it reads as broken. *(Task 1: say so in the preview code; Interactive mode shows the motion; the "animations off" previews show the full B.)*
4. **Light app theme on the always-dark finish screen.** The new, narrower mark is still white, and still balanced over "WORKOUT COMPLETE". *(Task 3: device check in Light.)*
5. **Launcher masks.** The taller mark stays inside every mask (circle, squircle, rounded square, teardrop) and as a themed icon. *(Task 4: offline mask renders + emulator.)*

---

## What was checked before writing this plan (2026-10-02, working tree as delivered)

- **Build:** `./gradlew :app:assembleDebug` passes. **Unit tests: 197/197 green.** The merge broke nothing. One compiler warning, pre-existing since the first brand drop: `Brand.kt:60`, `@DrawableRes` on an enum constructor property ("applied to the value parameter only").
- **Imports:** no unused imports in `Brand.kt`, and no remaining reference to the deleted `bldr_loader_base`.
- **Mark geometry vs code:** `bldr_mark.xml` is seven subpaths in a 54 × 70 viewport. Each is a 10-high pill with radius 5, and they match `Bars` in `Brand.kt` exactly: (0,60,47) (0,45,15) (33,45,21) (0,30,45) (0,15,15) (31,15,19) (0,0,40). **Nothing to flag.**
- **Launcher icon (new):** the foreground and monochrome layers are identical white single paths. Their bounding box is x 38.57–69.43, y 34.00–74.00 in the 108 dp canvas: a 30.9 × 40 dp mark, centred exactly on (54, 54). The farthest corner is **25.3 dp from centre, inside the 33 dp safe-zone radius** every launcher mask keeps, with 7.7 dp to spare. (The old dumbbell was 47.8 × 33.1 dp with its farthest corner at 29.1 dp.)
- **Notification icon (new):** `ic_stat_bldr` is white on transparent, bounding box x 4.29–19.71, y 2.00–22.00 in the 24 dp viewport. It is 15.4 × 20 dp, centred, and **inside the 22 × 22 dp live area** (1–23).
- **Logos:** the horizontal logo is now 215.5 × 40 intrinsic (5.39:1, was 5.71:1); the stacked logo is 133.8 × 96. `bldr_wordmark.xml` is byte-identical to before; the canvas re-export produced the same file.
- **Call sites:** `MainActivity.LaunchLoader` → `BldrLoader(width = 72.dp)` (now 72 × 93 dp, too big). `CoachScreen.Loading` → `BldrLoader()` (default is now 40 dp). `BldrMark` on Home (18 dp) and Finish (40 dp). `BldrLogo` in Settings (24 dp). No `CircularProgressIndicator`, `LinearProgressIndicator` or pull-to-refresh anywhere.
- **Waits (re-audited):** the only screen-level waits are first-launch seeding and the Coach request (its tweak and regenerate reuse the same full-screen phase). Saving in the Coach, Settings and Builder is a local write with no waiting state. The remaining blank moments are under ~100 ms and were ruled out in the first plan, because an indicator would only flash: Builder edit-load, Home's first frame, Overview load, and execution before the service's first state. **No inline wait exists for `BldrRefreshIndicator`.**
- **Motion headroom:** `BuildTiming.DropDistance` is 14 units, about 0.26 of the width. The top bar starts that far above the mark, but at zero alpha. It becomes visible around 0.15 of the width above, so BRAND.md's "about a fifth" is right in practice. Not flagged.

## Decisions this plan makes

- **`BldrSplashBuild`: leave it unused.** It builds once and holds, about 1.4 s in all. Used as a splash, it would add that delay to **every** launch, including normal ones that are ready in a few frames. The one real wait, first-launch seeding, has an unknown length (~1 s on the emulator, possibly longer on a cold phone), which a looping `BldrLoader` fits and a one-shot build doesn't. It stays in `Brand.kt` for a future branded launch, as BRAND.md suggests.
- **`BldrRefreshIndicator`: leave it unused.** There is no pull-to-refresh and no small inline wait in the app today, and the brief says not to invent one.
- **Loader sizes:** full-screen wait (first launch) **44 dp** (44 × 57); section wait (Coach) **40 dp**, the component's default (40 × 52).

| Call site | Old box (dumbbell) | New box (striped B) | Ink, roughly | Headroom above |
|---|---|---|---|---|
| First launch | 72 × 50 dp | **44 × 57 dp** | similar height, narrower | Centred on an otherwise empty screen: ~300 dp clear |
| Coach "Drafting…" | 64 × 44 dp | **40 × 52 dp** | similar height, narrower | 48 dp column padding above (needs ~8) |

- **Logo placements: keep all three sizes** (each clears its minimum and its clear space), and update one comment that quotes the old mark's numbers.

| Placement | Size | Minimum | Clear space needed (2 bars) | Space actually there |
|---|---|---|---|---|
| Home header mark | 18 dp tall (≈ 13.9 dp wide) | 16 dp | ≈ 5.1 dp | 10 dp to the date; row height above/below |
| Settings About logo | 24 dp tall (≈ 129 dp wide) | 24 dp | ≈ 6.9 dp | 14 dp list gap above, 10 dp below |
| Finish screen mark | 40 dp tall (≈ 31 dp wide) | 16 dp | ≈ 11.4 dp | 20 dp below (8 + 12), open screen above |

---

## File Structure

| File | Task | Change |
|---|---|---|
| Canvas export (drawables, `Brand.kt`, `brand/`) | 0 | Commit as delivered, untouched |
| `ui/brand/Brand.kt` | 1 | Annotation-target warning; preview fixes only (no geometry or timing) |
| `MainActivity.kt` | 2 | `BldrLoader(width = 44.dp)` |
| `ui/coach/CoachScreen.kt` | 2b | Coach wait: loader centred on screen, cycling "working words" |
| `ui/brand/Brand.kt` | 2b | `rememberSystemAnimationsOn` private → internal, so the Coach reuses it (no geometry or timing change) |
| `ui/home/HomeScreen.kt` | 3 | Comment with the new clear-space figure |
| `brand/BRAND.md` | 3 | "Decisions": the unused splash and refresh components, and the reason |
| `docs/superpowers/plans/2026-10-02-bldr-brand-rollout.md` | 5 | Icon-geometry check redone; references to the deleted loader base and old plates |
| `docs/superpowers/STATUS.md` | 5 | Striped-B facts; status row |

---

### Task 0: Commit the canvas export as delivered

- [ ] **Step 1:** `git add -A app/src/main/res/drawable app/src/main/java/com/ottotech/workoutbuilder/ui/brand/Brand.kt brand` (this includes the deletion of `bldr_loader_base.xml`), then commit: `chore: striped-B mark and "build the B" motion from the brand canvas`. Untouched, so the history shows the canvas's version separately from the app-side fixes.

---

### Task 1: Brand.kt health: warning and previews

**Files:** Modify `ui/brand/Brand.kt` (enum annotation, previews only).

- [ ] **Step 1: Clear the compiler warning without changing behaviour.** Line 60:

```kotlin
enum class BldrLogoVariant(@param:DrawableRes val res: Int) {
```

- [ ] **Step 2: Make the splash/refresh preview deterministic, and add its still twin.** The combined preview currently leaves `animate` at its default, so it reads `Settings.Global` inside Android Studio's renderer, unlike the three loader previews. Replace it with:

```kotlin
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
```

- [ ] **Step 3:** `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Expected: exit 0, 197 tests, **no compiler warnings from `Brand.kt`**.
- [ ] **Step 4: Previews (the user, in Android Studio):** open `Brand.kt` → Split view. Expected: five previews. "Loader · animations off" and "Splash build and refresh · animations off" show the full striped B (plus the three bottom bars for refresh). "Loader · light" and "· dark" look empty in static mode (frame 0) and build bottom-up in Interactive mode, with the left bar landing first on split rows.
- [ ] **Step 5: Commit:** `chore: preview fixes and annotation warning in Brand.kt`.

**Risk:** very low; previews and an annotation target only. **Check:** build output + previews.

---

### Task 2: Loader sizes, motion and accessibility on device

**Files:** Modify `MainActivity.kt`.

- [ ] **Step 1:** In `LaunchLoader`, `BldrLoader(width = 72.dp)` → `BldrLoader(width = 44.dp)`. The Coach keeps `BldrLoader()` (40 dp default).
- [ ] **Step 2: Build, test, install** on the Pixel 8 Pro emulator (launch it with `-no-snapshot-save`, and start it with a 2-hour background allowance, so it isn't killed mid-check).
- [ ] **Step 3: Motion, animations on.** The emulator now seeds in ~1 s, so the 300 ms-delayed first-launch loader rarely shows. Install a **throwaway** build with `LaunchLoaderDelayMs = 0L`. Never commit it. Restore the file by copying back a saved copy, **not** with `git checkout`, which would wipe uncommitted work. `pm clear`, launch, and capture a burst of frames. Expected: bars arriving bottom-up, the full B, then fade; nothing clipped at the top.
- [ ] **Step 4: Animations off.** `adb shell settings put global animator_duration_scale 0`, repeat Step 3. Expected: the complete still B, with no build. Restore with `… 1`.
- [ ] **Step 5: TalkBack announced once.** Enable TalkBack with `adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService` and `accessibility_enabled 1`. Start `adb shell uiautomator events` in the background, then trigger the Coach loader with a dummy key, which holds for 1–4 s while the first call warms up. Expected: the loader node appears once with "Loading"; no repeated `TYPE_WINDOW_CONTENT_CHANGED` events tied to it while the bars animate (the canvas redraws without changing its semantics). Disable TalkBack afterwards.
- [ ] **Step 6: Coach size on screen.** With the dummy key, screenshot the Coach loader: 40 dp B centred above "Drafting your workout…", nothing clipped above.
- [ ] **Step 7: Commit** `fix: size the first-launch loader for the striped B`. Copy the APK to Drive.

**Risk:** low. **Check:** on-device (motion on/off, TalkBack events, two screenshots).

---

### Task 2b: Coach "working words" (added at the user's request, 2026-10-02)

While the Coach drafts a workout (15–30 s), the loader sits in the **centre of the screen** and a short status line steps through phrases, the way Claude shows what it's working on.

**Behaviour:**
- Phrases, in order: "Analyzing fitness objectives…", "Evaluating movement patterns…", "Selecting optimal exercises…", "Calibrating volume and intensity…", "Optimizing rest intervals…", "Finalizing workout structure…".
- A new phrase every **3 s**. It **holds on the last one** rather than looping (6 × 3 s = 18 s, inside the 15–30 s wait), so it never jumps from "Finalizing" back to "Analyzing". A tweak or regenerate starts again from the first phrase, because the Loading screen is composed afresh.
- Each change cross-fades (300 ms). With animations off, the text swaps without fading; the loader shows its still B.
- "This takes 15–30 seconds." stays as the muted line underneath.
- The loader is announced once as "Drafting your workout" (in progress). The phrase line is **not** a live region, so TalkBack doesn't read out a new phrase every 3 s; it's readable when focused.
- No layout jump: the phrase area is sized to the tallest phrase (invisible copies of all six, hidden from accessibility), so the loader stays put when a phrase wraps at large text sizes. Measured at 18 sp: the longest phrase is 299 dp, so all six fit on one line on a 360 dp phone at normal size and wrap to two at 1.3×.
- The phrases are visual reassurance, not real progress. The request is a single call with no intermediate steps to report.

**Files:** `ui/coach/CoachScreen.kt` (`Loading` → `ColumnScope.Loading`), `ui/brand/Brand.kt` (`rememberSystemAnimationsOn` becomes `internal`).

- [ ] **Step 1:** In `Brand.kt`, change `private fun rememberSystemAnimationsOn()` to `internal fun rememberSystemAnimationsOn()`. Nothing else in the file changes.
- [ ] **Step 2:** Replace `Loading()` in `CoachScreen.kt`:

```kotlin
/** Shown while the Coach drafts. Visual reassurance only: the request is one call with no real steps to report. */
private val WorkingWords = listOf(
    "Analyzing fitness objectives…",
    "Evaluating movement patterns…",
    "Selecting optimal exercises…",
    "Calibrating volume and intensity…",
    "Optimizing rest intervals…",
    "Finalizing workout structure…",
)
private const val WorkingWordMillis = 3_000L

@Composable
private fun ColumnScope.Loading() {
    val colors = LocalAppColors.current
    val animate = rememberSystemAnimationsOn()
    // Steps through the words once and holds on the last: a loop would jump from "Finalizing" back to the start.
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(WorkingWords.lastIndex) {
            delay(WorkingWordMillis)
            index++
        }
    }
    Column(
        Modifier.fillMaxWidth().weight(1f).padding(horizontal = Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BldrLoader(contentDescription = "Drafting your workout")
        Spacer(Modifier.height(24.dp))
        // Sized by invisible copies of every phrase, so a wrapping phrase never shifts the loader.
        Box(contentAlignment = Alignment.TopCenter) {
            WorkingWords.forEach {
                Text(it, style = AppType.cardTitle, textAlign = TextAlign.Center, modifier = Modifier.alpha(0f).clearAndSetSemantics {})
            }
            AnimatedContent(
                targetState = WorkingWords[index],
                transitionSpec = {
                    if (animate) fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    else EnterTransition.None togetherWith ExitTransition.None
                },
                label = "workingWords",
            ) { words ->
                Text(words, style = AppType.cardTitle, color = colors.onBackground, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("This takes 15–30 seconds.", style = AppType.cardMeta, color = colors.textMuted)
    }
}
```

- [ ] **Step 3:** Build, test, install. On the emulator with a dummy key, the first call's warm-up holds the Loading screen for a few seconds, enough to see the first phrase and the centring. To watch the full sequence, use a throwaway build with `WorkingWordMillis = 500L` (never committed; restore from a saved copy). Expected: the loader is centred in the space below the header; phrases cross-fade in order and hold on "Finalizing workout structure…"; nothing shifts when a phrase wraps at 1.3× text; with `animator_duration_scale 0` the loader is still and the phrases swap without fading.
- [ ] **Step 4:** TalkBack check as in Task 2 Step 5: "Drafting your workout", in progress, announced once; no announcements as the phrases change.
- [ ] **Step 5: Commit** `feat: Coach wait shows a centred loader with working words`.

**Risk:** low; one screen state. **Check:** on-device (centring, sequence, animations off, TalkBack, large text).

> **As built (after review):** the phrases are centred with no size animation (`contentAlignment = TopCenter`, `.using(null)`), because the default slid and clipped them. The whole phrase area is cleared from accessibility (decoration; a node that changes every 3 s steals TalkBack focus). The explicit animations-off branch was dropped, since Compose scales the fade by the system setting, so `rememberSystemAnimationsOn` stayed `private` in `Brand.kt`.

---

### Task 3: Logo placements and decisions in the docs

**Files:** Modify `ui/home/HomeScreen.kt` (comment only), `brand/BRAND.md` ("Decisions").

- [ ] **Step 1: Home comment.** Replace

```kotlin
            // 18 dp clears the mark's 16 dp minimum; 10 dp is well over its clear space (~3.4 dp).
```

with

```kotlin
            // 18 dp clears the mark's 16 dp minimum; 10 dp is well over its clear space (two bars, ~5 dp).
```

- [ ] **Step 2: BRAND.md "Decisions"**, append:

```markdown
- `BldrSplashBuild` and `BldrRefreshIndicator` are available but unused (2026-10-02). As a splash, the one-shot build would add ~1.4 s to every launch; the only real launch wait (first-launch seeding) has an unknown length, which the looping `BldrLoader` fits. The app has no pull-to-refresh or small inline waits for the refresh indicator.
```

- [ ] **Step 3: Device check, Light and Dark** (emulator resized to S22 width: `wm size 1080x2340`, `wm density 480`). Home: the mark is vertically centred on the date line and doesn't overpower it. Settings: About logo crisp at 24 dp. Run a workout to the end in **Light**: the white mark is centred over "WORKOUT COMPLETE". If the Home mark reads as too tall next to 13 sp caps, drop it to 16 dp (the minimum) and note it.
- [ ] **Step 4: Commit** `docs: striped-B clear space; record the unused splash and refresh components`.

**Risk:** very low. **Check:** on-device, both themes.

---

### Task 4: Launcher and notification icons under every mask

**Files:** none in the app (checks only; screenshots in the plan workspace).

- [ ] **Step 1: Offline mask renders.** With a short Python script, flatten `ic_launcher_foreground.xml`'s path, draw it white on black at 432 px (4 px per dp), then apply four masks: **circle** (Pixel), **squircle** (superellipse, n ≈ 4, Samsung One UI), **rounded square** (24 % corner radius) and **teardrop**. Composite them side by side. Expected: the mark fully inside every mask with even margins; nothing touches an edge.
- [ ] **Step 2: Emulator, circle mask:** app drawer and home screen.
- [ ] **Step 3: Emulator, themed icon:** Wallpaper & style → Home screen → Themed icons on. On the home screen (themed icons don't apply in the drawer), the mark takes the wallpaper tint.
- [ ] **Step 4: Notification:** start a workout, then pull down the shade. Expected: the striped B in the status bar and on the media notification, not a white square.
- [ ] **Step 5:** No commit (checks only). Record the results in Task 5's docs.

**Risk:** none to the app. **Check:** offline renders + emulator. The real S22 squircle stays on the user's list.

---

### Task 5: Docs

**Files:** Modify `docs/superpowers/plans/2026-10-02-bldr-brand-rollout.md`, `docs/superpowers/STATUS.md`.

- [ ] **Step 1: Old plan, "What was checked".** Replace the **Icon geometry** bullet with the new measurements from this plan's "What was checked" (launcher 30.9 × 40 dp, farthest corner 25.3 / 33; notification 15.4 × 20 inside 22 × 22). Replace the plate-alignment sentence with: "`Bars` in `Brand.kt` match `bldr_mark.xml`'s seven pills exactly." Add a one-line note at the top of that section: "Updated 2026-10-02 for the striped-B mark; see `plans/2026-10-02-bldr-striped-b.md`."
- [ ] **Step 2: Old plan, references to the deleted loader base.** In Task 3's code block (`painterResource(R.drawable.bldr_loader_base)`) and the loader description, add a note that the loader was later redrawn by the canvas (all bars drawn in code; `bldr_loader_base.xml` deleted), rather than rewriting history.
- [ ] **Step 3: STATUS.md.** Update the "BLDR brand rollout" row (striped-B update applied, test count, outstanding items). In "BLDR brand facts", add:
  - The mark is the striped B (54 × 70, seven pills); `BldrLoader` width is the true width; leave ~⅕ width of headroom above.
  - Loader sizes: 44 dp first launch, 40 dp Coach.
  - `BldrSplashBuild` / `BldrRefreshIndicator` unused, with the reason.
  - The static preview pane shows frame 0 (empty) for the animated previews.
- [ ] **Step 4: Full suite** `./gradlew :app:testDebugUnitTest` → 197 green. **Commit** `docs: striped-B checks and status`.

---

## Out of scope, deliberately

- Any change to the bar geometry, the timings or the drawables (canvas-owned).
- A custom splash, pull-to-refresh, or new loading states (the Coach working words restyle an existing wait; they don't add one).
- The pre-existing follow-ups from the first rollout (status-bar icons in Light on dark screens, chip rows at 2.0× font, nav-bar insets on other pushed screens).
- The real Galaxy S22 checks (not connected): they stay on the user's list, now including the squircle mask.
