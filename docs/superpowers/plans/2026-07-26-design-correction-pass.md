# Design Correction Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct a scale error inherited from the design handoff — every type size, spacing
value and radius in the app is ~62% of its intended size — then fix the light-mode contrast
failures and sub-minimum touch targets that error masked, rework Home from a fake
recommendation into an explicit "choose a workout / build a workout" fork, and stop the Builder
silently destroying a draft on Back.

**Architecture:** Almost all of the visual correction is two token files — `ui/theme/Type.kt` and
`ui/theme/Dimens.kt`. Because 1A–1C enforced "all sizes from `Dimens`, all text styles from
`AppType`, all colour from `LocalAppColors`", changing the tokens updates all five screens at
once. Only the Home rework and the Builder discard-guard touch screen code, and both push their
new logic into pure `domain/` functions with JVM tests first, exactly as 1B and 1C did.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Navigation Compose, Room, JUnit 4 +
kotlin.test. **No new dependencies.**

**Spec:** `docs/superpowers/specs/2026-07-21-workout-builder-android-design.md` — §5 (tokens),
§6.1 (Home). **This plan deliberately overrides the spec's numeric token values and its §6.1
"Up next" behaviour.** See "Why the tokens change" below, and Task 8 which amends the spec so it
stops disagreeing with the code.

**Builds on:** `docs/superpowers/plans/2026-07-24-phase-1c-builder-pick-mode.md`, merged to
`main`. Read the actual files before starting — they are authoritative over any snippet
reproduced here.

---

## Why the tokens change (read this before Task 1)

The handoff prototype (`design_handoff_workout_builder/Workout Builder.dc.html:44`) renders every
screen inside a mock phone frame that is **264 px wide**. That frame is a thumbnail for a desktop
comparison canvas, not a 1:1 device spec. The handoff README correctly says "don't ship the bezel,
status bar or gesture pill" but never says the frame is scaled down — so its "Design Tokens"
section lists literal px values (`11px` meta, `27px` title, `9px` hero label) that are only
correct inside a 264 px canvas.

A real device is **~411 dp wide** (Pixel 8) to **~448 dp** (the Pixel 8 Pro AVD). The prototype is
therefore **~1.6× too small**, and those px values were carried into `Type.kt` / `Dimens.kt`
one-for-one as sp/dp. The container grew 63%; its contents did not.

Measured consequence on a Home row today: badge + gap + text occupy ~194 dp of a 379 dp content
width. **Roughly half of every card is empty.**

The new values below are the old ones scaled by ~1.6 and then snapped to sensible steps. **Do not
"fix" them back toward the handoff numbers.** Task 8 amends the spec to match.

---

## Global Constraints

Everything from Plans 1A, 1B and 1C still applies. Repeated here because a task's implementer
sees only their own task:

- Package `com.ottotech.workoutbuilder`
- **Toolchain is fixed:** Gradle 9.4.1, AGP 9.2.1, Kotlin 2.2.10, Compose BOM 2026.02.01, Nav
  2.9.6, Lifecycle 2.9.4. **Do not bump these and do not add dependencies** — this plan needs none.
- **Every Gradle command needs `JAVA_HOME` exported first** — there is no `java` on PATH:
  `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`
- `minSdk 26`, `compileSdk` / `targetSdk` 36
- **`domain/` must contain zero `android.*` and zero Compose imports.** `DomainPurityTest`
  enforces this and fails the build if a UI type lands in `domain/`. `java.time` is allowed and
  already used.
- **Every colour is read from `LocalAppColors.current`.** No literal hex outside
  `ui/theme/Colors.kt`.
- **All sizes and radii come from `Dimens`; all text styles from `AppType`.** If a value you need
  is missing, add it to those files rather than inlining a number.
- **No shadows anywhere.** Hairline (1 dp) borders only.
- **Mono accent rule:** the accent is maximum contrast against its own surface —
  `onSurfaceInverse` on `surfaceInverse`, `onBackground` on `background`. There is no accent hue.
- **No icon-font or icon-library dependency.** Chevrons, plus, minus and close are text glyphs
  (`‹`, `›`, `+`, `−`, `✕`, `↑`, `↓`).
- **New in this plan — 48 dp minimum touch target.** Android's platform minimum. Every tappable
  element must be at least 48×48 dp of *touch* area. Use `Dimens.minTouchTarget`.
- **New in this plan — WCAG AA contrast.** Any text colour on any surface it actually renders on
  must reach 4.5:1. Disabled text is exempt.
- Every task ends with a commit.

### Verifying a test run

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest
for f in app/build/test-results/testDebugUnitTest/*.xml; do
  n=$(basename "$f" .xml | sed 's/TEST-com.ottotech.workoutbuilder.//')
  t=$(grep -oE 'tests="[0-9]+"' "$f" | head -1 | grep -oE '[0-9]+')
  fl=$(grep -oE 'failures="[0-9]+"' "$f" | head -1 | grep -oE '[0-9]+')
  echo "$n: $t tests, $fl failures"
done
```

Baseline entering this plan: **108 tests, 0 failures.**

Expected on exit: **~115**. The arithmetic: −5 (UpNext tests deleted in Task 5), −2 (metaLine
tests replaced in Task 5), +14 new. Record the real number in STATUS.md at Task 8.

### Device

The emulator is sufficient for this whole plan — it is tokens, layout and one dialog. A Pixel 8
Pro AVD on API 36 exists; launch it from Android Studio's Device Manager, then
`./gradlew :app:installDebug`.

`adb`: `/c/Users/ottol/AppData/Local/Android/Sdk/platform-tools/adb.exe`

**Tasks 1, 2, 3, 4 and 6 have no unit tests** — they are visual/layout changes with no pure
logic. Their verification step is "install on the emulator and look at it", and that is a real
gate, not a formality. Do not skip it and do not claim a task passed without running it.

---

## File Structure

| File | Change | Responsible for |
|---|---|---|
| `ui/theme/Type.kt` | Modify | Every text size in the app. Task 1. |
| `ui/theme/Dimens.kt` | Modify | Every spacing, radius and fixed size. Task 1. |
| `ui/components/Glyphs.kt` | Modify | Nav and search glyph sizes, rescaled to match. Task 1. |
| `ui/theme/Colors.kt` | Modify | Light-mode muted greys darkened to pass AA. Task 2. |
| `ui/components/Controls.kt` | Modify | Chip padding, back-chevron hit area. Task 3. |
| `ui/components/AppScaffold.kt` | Modify | Nav item hit area. Task 3. |
| `ui/builder/BuilderScreen.kt` | Modify | `GlyphButton` size (Task 3); discard guard (Task 7). |
| `domain/WorkoutSummary.kt` | Modify | Drop `UpNext` + `metaLine`; add `shortMeta`, `minutesLabel`, `lastDoneLine`, `sortedForHome`. Task 5. |
| `domain/WorkoutDraft.kt` | Modify | Add pure `hasContent`. Task 7. |
| `ui/home/HomeViewModel.kt` | Modify | Drop up-next selection, expose a sorted list. Task 5. |
| `ui/home/HomeScreen.kt` | Modify | The two-option Home. Task 6. |
| `test/domain/WorkoutSummaryTest.kt` | Modify | Replace metaLine + UpNext tests. Task 5. |
| `test/domain/WorkoutDraftTest.kt` | Modify | Add `hasContent` tests. Task 7. |
| `docs/superpowers/specs/...design.md` | Modify | Amend §5 and §6.1. Task 8. |
| `docs/superpowers/STATUS.md` | Modify | Record the pass. Task 8. |

---

### Task 1: Rescale the type and spacing tokens

The single highest-leverage change in the plan. Two token files plus the glyph sizes; every
screen follows automatically.

**Files:**
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/theme/Dimens.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/components/Glyphs.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `Dimens.minTouchTarget: Dp` (= 48.dp) and `Dimens.glyphButtonSize: Dp` (= 48.dp),
  both used by Task 3. All other names are unchanged — only their values move, so no call site
  needs editing.

- [ ] **Step 1: Replace the body of `AppType` in `Type.kt`**

Keep the file's existing package line, imports and KDoc. Replace only the `object AppType { ... }`
block with this. Every name is unchanged; only the numbers move.

```kotlin
object AppType {
    val overline = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em)
    val screenTitle = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.035).em, lineHeight = 42.sp)
    val libraryTitle = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em)
    val heroLabel = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
    val heroName = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
    val heroMeta = TextStyle(fontSize = 16.sp)
    val primaryButton = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val buildButton = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold)
    val cardTitle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val cardMeta = TextStyle(fontSize = 14.sp)
    val sectionLabel = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.06.em)
    val chip = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    val tileName = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val tileCount = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    val moveName = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em, textAlign = TextAlign.Center)
    val muscleTag = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em)
    val repsExerciseName = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
    val setRowLabel = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}
```

Also update the KDoc's second paragraph so the next reader knows why these numbers are what they
are. Replace the sentence beginning "Type scale from the design handoff" with:

```kotlin
/**
 * Type scale, corrected 2026-07-26. The design handoff's px values were measured inside a
 * 264px-wide mock phone frame — a thumbnail, not a device — so they landed in the app at ~62%
 * of intended size. These are those values scaled ~1.6x and snapped to sensible steps. See
 * docs/superpowers/plans/2026-07-26-design-correction-pass.md. Do not revert toward the
 * handoff numbers.
 *
 * Platform system sans (Roboto) throughout — the handoff specifies system fonts with no
 * bundled files.
 *
 * With a mono accent, hierarchy is carried by weight, size and tracking rather than colour,
 * so these values are load-bearing.
 */
```

- [ ] **Step 2: Replace the body of `Dimens` in `Dimens.kt`**

Two names are new (`minTouchTarget`, `glyphButtonSize`); the rest keep their names and change
value. `hairline` deliberately stays at 1 dp — a hairline does not scale.

```kotlin
/**
 * Spacing, radii and fixed sizes. Rescaled 2026-07-26 — see the note in Type.kt and
 * docs/superpowers/plans/2026-07-26-design-correction-pass.md. No shadows anywhere.
 */
object Dimens {
    val screenPadding = 20.dp
    val cardPadding = 18.dp
    val heroPadding = 20.dp
    val gap = 14.dp

    val radiusChip = 12.dp
    val radiusCard = 18.dp
    val radiusTile = 18.dp
    val radiusHero = 22.dp
    val radiusButton = 16.dp
    val radiusCheckbox = 8.dp

    // A hairline is a hairline at any scale — deliberately not rescaled.
    val hairline = 1.dp

    /** Android's platform minimum touch target. Nothing tappable may be smaller. */
    val minTouchTarget = 48.dp

    val buildButtonHeight = 56.dp
    val primaryButtonHeight = 48.dp
    val searchFieldHeight = 52.dp
    val glyphButtonSize = 48.dp
    val badgeSize = 52.dp
    val categoryTileMinHeight = 80.dp
    val ringSize = 240.dp
    val ringStroke = 14.dp
    val progressBarHeight = 4.dp
    val progressBarGap = 5.dp
}
```

- [ ] **Step 3: Rescale the glyph default sizes in `Glyphs.kt`**

Three one-line changes to the default parameter values. Nothing else in the file moves.

```kotlin
@Composable
fun HomeGlyph(size: Dp = 22.dp, color: Color) {
```

```kotlin
@Composable
fun LibraryGlyph(size: Dp = 22.dp, color: Color) {
```

```kotlin
@Composable
fun SearchGlyph(size: Dp = 18.dp, color: Color) {
```

Also bump the stroke so the glyphs do not go spindly at the larger size:

```kotlin
private val StrokeWidth = 2.dp
```

- [ ] **Step 4: Build and confirm nothing broke**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. No call site changes are needed — every name kept its identity.
If you get an unresolved-reference error, you renamed something; put the name back.

- [ ] **Step 5: Install and look at every screen**

```bash
./gradlew :app:installDebug
```

Walk all five screens: Home, Library, a category list, an exercise detail, the Builder. You are
checking three things:

1. Text is comfortably readable at arm's length — no more 9sp/11sp.
2. Nothing overflows or clips. **The most likely casualty is the two-line `YOUR WORKOUTS`
   title at 40sp and the `LIBRARY` title.** Check them on the narrowest screen you can set.
3. Cards look filled rather than half-empty.

If a title overflows, reduce `screenTitle` / `libraryTitle` to 36sp — not lower — and note it.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/ui/theme/Type.kt \
        app/src/main/java/com/ottotech/workoutbuilder/ui/theme/Dimens.kt \
        app/src/main/java/com/ottotech/workoutbuilder/ui/components/Glyphs.kt
git commit -m "fix: rescale type and spacing tokens to real-device scale

The design handoff's px values were measured inside a 264px-wide mock phone
frame, so they landed in the app at ~62% of intended size. Scaled ~1.6x and
snapped to sensible steps."
```

---

### Task 2: Fix light-mode contrast

The muted greys were chosen on the assumption that "mid-greys read correctly on both white and
near-black". That holds for near-black (6.8:1) and fails on white. Dark mode is untouched.

**Files:**
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/theme/Colors.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: no new names. `LightColors.textMuted`, `.textMutedAlt` and `.navInactive` change value.

Measured ratios today, against the surfaces these colours actually render on:

| Role | Value | On | Ratio | Verdict |
|---|---|---|---|---|
| `textMuted` | `#9A9A9A` | `#FFFFFF` | 2.8:1 | fails (needs 4.5) |
| `textMuted` | `#9A9A9A` | `#F1F1EF` | 2.5:1 | fails |
| `textMutedAlt` | `#8A8A8A` | `#FFFFFF` | 3.5:1 | fails |
| `navInactive` | `#B3B3B3` | `#FFFFFF` | 2.1:1 | fails |
| `textDisabled` | `#C2C2C2` | `#FFFFFF` | 1.9:1 | exempt (disabled) |

- [ ] **Step 1: Change the three light-mode greys**

In `LightColors` only. `DarkColors` is correct as-is and must not be touched.

```kotlin
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
```

- [ ] **Step 2: Correct the now-wrong comment in `DarkColors`**

The line `// Mid-greys read correctly on both white and near-black, so they do not vary.` is the
assumption that caused the bug. Replace it with:

```kotlin
    // Dark-mode muted greys are lighter than light-mode's because they sit on near-black:
    // #9A9A9A is 6.8:1 on #0B0B0D and 6.4:1 on #18181C. They are NOT the same value as
    // LightColors' — that symmetry was the original contrast bug.
```

- [ ] **Step 3: Build**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Check both themes on the emulator**

```bash
./gradlew :app:installDebug
```

In light mode, confirm the meta lines under workout names, the Builder's field labels
("Rounds", "Duration", "Sets", "Reps", "Weight (kg)", "Between exercises"), the date overline,
search placeholders and the inactive nav label all read as clearly grey-but-legible rather than
washed out.

Then switch the emulator to dark mode (Settings → Display → Dark theme) and confirm nothing got
muddier — dark mode should look exactly as it did before this task.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/ui/theme/Colors.kt
git commit -m "fix: darken light-mode muted greys to meet WCAG AA

textMuted was 2.8:1 on white and 2.5:1 on the light fill; navInactive was
2.1:1. Dark mode was already compliant and is unchanged."
```

---

### Task 3: Bring every touch target to 48 dp

Android's minimum is 48×48 dp. Five things are currently under it.

**Files:**
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/components/Controls.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/components/AppScaffold.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/builder/BuilderScreen.kt`

**Interfaces:**
- Consumes: `Dimens.minTouchTarget`, `Dimens.glyphButtonSize` from Task 1.
- Produces: no new public names. `Chip` and `ScreenHeader` keep their existing signatures.

`primaryButtonHeight` (40 → 48) and `searchFieldHeight` (44 → 52) were already fixed by Task 1.
This task covers the four that need structural changes.

- [ ] **Step 1: Give `Chip` a 48 dp height in `Controls.kt`**

At 14sp the text line is ~19 dp, so 14 dp of vertical padding lands the chip at ~47 dp — one dp
inside tolerance, and `defaultMinSize` guarantees the floor. Replace the `Box` in `Chip`:

```kotlin
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = Dimens.minTouchTarget)
            .clip(RoundedCornerShape(Dimens.radiusChip))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppType.chip, color = foreground)
    }
```

Add the import:

```kotlin
import androidx.compose.foundation.layout.defaultMinSize
```

- [ ] **Step 2: Give the back chevron a real hit area in `Controls.kt`**

Today the tap target is the `‹` character's own text bounds — roughly 14×24 dp. Wrap it in a
sized, clickable `Box`. In `ScreenHeader`, replace the chevron `Text` with:

```kotlin
        Box(
            modifier = Modifier
                .size(Dimens.minTouchTarget)
                .clip(RoundedCornerShape(Dimens.radiusChip))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            // heroName rather than a title style: a 40sp chevron overwhelms the word beside it.
            Text(text = "‹", style = AppType.heroName, color = colors.onBackground)
        }
```

The `Row`'s `horizontalArrangement = Arrangement.spacedBy(12.dp)` now double-counts the chevron's
own padding, so reduce it to `Arrangement.spacedBy(4.dp)` in the same `Row`.

Add the import:

```kotlin
import androidx.compose.foundation.layout.size
```

- [ ] **Step 3: Enlarge the nav item hit area in `AppScaffold.kt`**

The clickable `Column` currently has `vertical = 4.dp`, giving ~41 dp. In `NavItem`, change:

```kotlin
            .padding(horizontal = 24.dp, vertical = 10.dp),
```

and raise the nav label from the hardcoded `10.sp` to `12.sp` so it matches the rescale:

```kotlin
        Text(
            text = tab.label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = tint,
        )
```

- [ ] **Step 4: Enlarge `GlyphButton` in `BuilderScreen.kt`**

These are the `↑ ↓ ✕ − +` controls — the most-tapped things in the app, currently 30 dp.

```kotlin
/** A single text glyph in a `surface` square — the app's minus/plus/arrow/close affordance. */
@Composable
private fun GlyphButton(glyph: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(Dimens.glyphButtonSize)
            .clip(RoundedCornerShape(Dimens.radiusChip))
            .background(colors.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = AppType.cardTitle, color = colors.onSurface)
    }
}
```

- [ ] **Step 5: Build**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Verify the targets on the emulator**

```bash
./gradlew :app:installDebug
```

Enable Settings → Developer options → **Show layout bounds**, then check that every chip, every
`↑ ↓ ✕ − +` button, the back chevron and both nav items draw a box at least 48 dp on a side.

**Watch for a regression here:** three `↑ ↓ ✕` buttons at 48 dp plus a section-name field now
have to fit one Builder row on a 411 dp screen. 3 × 48 + 8 gap = 152 dp of controls, leaving
~190 dp for the field — tight but workable. If the section name field is squeezed to
unusability, move the three glyph buttons onto their own row beneath the name field rather than
shrinking them back below 48 dp.

Turn layout bounds off again when done.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/ui/components/Controls.kt \
        app/src/main/java/com/ottotech/workoutbuilder/ui/components/AppScaffold.kt \
        app/src/main/java/com/ottotech/workoutbuilder/ui/builder/BuilderScreen.kt
git commit -m "fix: bring every touch target to Android's 48dp minimum

Chips were ~29dp, builder glyph buttons 30dp, nav items ~41dp, and the back
chevron's target was the glyph's own text bounds (~14x24dp)."
```

---

### Task 4: Animate Builder reordering

Small, and it belongs with the touch-target work: at 48 dp the move buttons are easy to hit, but
rows still teleport when you use them, so you lose track of what moved.

**Files:**
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/builder/BuilderScreen.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: nothing later tasks depend on.

- [ ] **Step 1: Add `animateItem()` to the Builder's section list**

In `BuilderScreen`, the `items(state.workout.sections, key = { it.id })` block already supplies a
stable key, which is all `animateItem` needs. Change the lambda to apply it:

```kotlin
            items(state.workout.sections, key = { it.id }) { section ->
                SectionCard(
                    state = state,
                    section = section,
                    viewModel = viewModel,
                    onAddExercise = { onAddExercise(section.id) },
                    modifier = Modifier.animateItem(),
                )
            }
```

- [ ] **Step 2: Thread the modifier through `SectionCard`**

`SectionCard` currently hardcodes its own modifier. Give it a parameter:

```kotlin
@Composable
private fun SectionCard(
    state: BuilderUiState,
    section: Section,
    viewModel: BuilderViewModel,
    onAddExercise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    HairlineCard(modifier = modifier.fillMaxWidth()) {
```

The rest of `SectionCard`'s body is unchanged.

Items inside a section are rendered with `section.items.forEach` inside a `Column`, not a lazy
list, so they get no `animateItem`. **Leave them.** Animating a non-lazy list needs
`Modifier.animateContentSize` plus manual offset work, which is not worth it here — section
reordering is the move that actually disorients.

- [ ] **Step 3: Build**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verify on the emulator**

```bash
./gradlew :app:installDebug
```

Open the Builder, add three sections, and press `↑` / `↓`. Sections should slide past each other
rather than jump. Compose honours the system "Remove animations" setting for `animateItem`
automatically, so no reduced-motion branch is needed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/ui/builder/BuilderScreen.kt
git commit -m "feat: animate builder section reordering

Rows teleported on move, so you lost track of what shifted."
```

---

### Task 5: Replace "Up next" selection with a sorted list and a last-done line

Pure domain work, tested on the JVM before any UI changes. Task 6 consumes what this produces.

**Why `UpNext` is deleted rather than kept:** its rule is "oldest `lastPerformedAt`; if none has
ever been performed, the newest created". Nothing in the app has ever written
`lastPerformedAt` — `WorkoutRepository.markPerformed` exists but has no caller, because starting
a workout is the unbuilt Plan 1D. So every workout is permanently "never performed" and the
tiebreaker is the only branch that ever runs: **"Up next" can currently only ever mean "the last
workout you created."** The product decision (2026-07-26) is that Home should not recommend at
all — it offers two options, choose or build.

**Files:**
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/domain/WorkoutSummary.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/home/HomeViewModel.kt`
- Test: `app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutSummaryTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces, all used by Task 6:
  - `WorkoutSummary.shortMeta: String` — `"10 exercises · HIIT"` (no minutes; they get their own
    slot on the card)
  - `WorkoutSummary.minutesLabel: String` — `"37 MIN"`
  - `WorkoutSummary.lastDoneLine(now: Instant): String` — `"Never done"` / `"Today"` /
    `"Yesterday"` / `"4 days ago"` / `"3 weeks ago"` / `"2 months ago"`
  - `WorkoutSummarizer.sortedForHome(summaries: List<WorkoutSummary>): List<WorkoutSummary>` —
    newest `createdAt` first
  - `HomeUiState.workouts: List<WorkoutSummary>` replaces `upNext` + `others`
- Removed: `WorkoutSummary.metaLine`, the whole `object UpNext`, `HomeUiState.upNext`,
  `HomeUiState.others`.

- [ ] **Step 1: Write the failing tests**

In `WorkoutSummaryTest.kt`, **delete** the two tests under the `// ---- metaLine ----` heading
and **delete** all five tests under `// ---- UpNext ----`. Replace both blocks with:

```kotlin
    // ---- card lines ----

    @Test
    fun `short meta reads count and type, without minutes`() {
        val out = WorkoutSummarizer.summarize(
            workout(
                sections = arrayOf(
                    Section("s", "A", items = listOf(timed("a", push.id, 60), timed("b", squat.id, 60))),
                ),
            ),
            lookup,
        )
        assertEquals("2 exercises · HIIT", out.shortMeta)
    }

    @Test
    fun `short meta singularises one exercise`() {
        val out = WorkoutSummarizer.summarize(
            workout(sections = arrayOf(Section("s", "A", items = listOf(timed("a", push.id, 60))))),
            lookup,
        )
        assertEquals("1 exercise · HIIT", out.shortMeta)
    }

    @Test
    fun `minutes label carries its unit`() {
        val out = WorkoutSummarizer.summarize(
            workout(sections = arrayOf(Section("s", "A", items = listOf(timed("a", push.id, 60))))),
            lookup,
        )
        assertEquals("1 MIN", out.minutesLabel)
    }

    // ---- lastDoneLine ----

    private val now: Instant = Instant.parse("2026-07-26T12:00:00Z")

    private fun daysAgo(days: Long) = now.minus(days, ChronoUnit.DAYS)

    @Test
    fun `a never-performed workout says so plainly`() {
        assertEquals("Never done", summary("w", lastPerformedAt = null).lastDoneLine(now))
    }

    @Test
    fun `same day reads Today`() {
        assertEquals("Today", summary("w", lastPerformedAt = now.minus(3, ChronoUnit.HOURS)).lastDoneLine(now))
    }

    @Test
    fun `one day reads Yesterday`() {
        assertEquals("Yesterday", summary("w", lastPerformedAt = daysAgo(1)).lastDoneLine(now))
    }

    @Test
    fun `under a week counts days`() {
        assertEquals("4 days ago", summary("w", lastPerformedAt = daysAgo(4)).lastDoneLine(now))
    }

    @Test
    fun `under two months counts weeks`() {
        assertEquals("1 week ago", summary("w", lastPerformedAt = daysAgo(7)).lastDoneLine(now))
        assertEquals("3 weeks ago", summary("w", lastPerformedAt = daysAgo(21)).lastDoneLine(now))
    }

    @Test
    fun `beyond two months counts months`() {
        assertEquals("2 months ago", summary("w", lastPerformedAt = daysAgo(70)).lastDoneLine(now))
    }

    // ---- sortedForHome ----

    @Test
    fun `home order is newest created first`() {
        val out = WorkoutSummarizer.sortedForHome(
            listOf(
                summary("older", createdAt = Instant.ofEpochSecond(100)),
                summary("newest", createdAt = Instant.ofEpochSecond(900)),
                summary("middle", createdAt = Instant.ofEpochSecond(500)),
            ),
        )
        assertEquals(listOf("newest", "middle", "older"), out.map { it.id })
    }

    @Test
    fun `sorting an empty list is empty, not an error`() {
        assertEquals(emptyList(), WorkoutSummarizer.sortedForHome(emptyList()))
    }
```

Add the import at the top of the test file:

```kotlin
import java.time.temporal.ChronoUnit
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest --tests '*WorkoutSummaryTest*'
```

Expected: FAIL — `Unresolved reference: shortMeta` (and `minutesLabel`, `lastDoneLine`,
`sortedForHome`).

- [ ] **Step 3: Implement in `WorkoutSummary.kt`**

Replace the `metaLine` property with the three new members, and replace `object UpNext` with the
new sort on `WorkoutSummarizer`. The `WorkoutSummary` data-class fields and `summarize` /
`summarizeAll` are unchanged.

```kotlin
data class WorkoutSummary(
    val id: String,
    val name: String,
    val type: TrainingType,
    val exerciseCount: Int,
    val estimatedMinutes: Int,
    val createdAt: Instant,
    val lastPerformedAt: Instant?,
) {
    /** `10 exercises · HIIT` — the Home card's second line. Minutes get their own slot. */
    val shortMeta: String
        get() {
            val noun = if (exerciseCount == 1) "exercise" else "exercises"
            return "$exerciseCount $noun · ${type.label}"
        }

    /** `37 MIN` — right-aligned on the Home card. Carries its unit; the old bare badge did not. */
    val minutesLabel: String get() = "$estimatedMinutes MIN"

    /**
     * `Never done` / `Today` / `Yesterday` / `4 days ago` / `3 weeks ago` / `2 months ago`.
     *
     * [now] is a parameter rather than `Instant.now()` so this stays pure and testable —
     * the same reason `WorkoutDraft` takes its ids from the caller.
     *
     * Until Plan 1D calls `WorkoutRepository.markPerformed`, every workout returns
     * "Never done". That is accurate, not a placeholder.
     */
    fun lastDoneLine(now: Instant): String {
        val performed = lastPerformedAt ?: return "Never done"
        val days = ChronoUnit.DAYS.between(performed, now)
        return when {
            days <= 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7L -> "$days days ago"
            days < 60L -> {
                val weeks = days / 7L
                if (weeks == 1L) "1 week ago" else "$weeks weeks ago"
            }
            else -> {
                val months = days / 30L
                if (months == 1L) "1 month ago" else "$months months ago"
            }
        }
    }
}
```

Add the import:

```kotlin
import java.time.temporal.ChronoUnit
```

Then add to `WorkoutSummarizer` (keeping `summarize` and `summarizeAll` exactly as they are):

```kotlin
    /**
     * Home's card order: newest created first, so the workout you just built is at the top.
     *
     * This replaces `UpNext`, deleted 2026-07-26. That rule ranked by `lastPerformedAt`, which
     * nothing ever writes — `markPerformed` has no caller until Plan 1D — so it always fell
     * through to its "newest created" tiebreak while presenting itself as a recommendation.
     * Home now offers two plain options, choose or build, and does not rank.
     */
    fun sortedForHome(summaries: List<WorkoutSummary>): List<WorkoutSummary> =
        summaries.sortedByDescending { it.createdAt }
```

Finally, **delete the entire `object UpNext { ... }` block.**

- [ ] **Step 4: Run the tests to verify they pass**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest --tests '*WorkoutSummaryTest*'
```

Expected: PASS.

- [ ] **Step 5: Update `HomeViewModel` to match**

`HomeUiState` loses `upNext` and `others` and gains a single list. Replace the state class and
the `combine` block:

```kotlin
data class HomeUiState(
    val loading: Boolean = true,
    val workouts: List<WorkoutSummary> = emptyList(),
) {
    val isEmpty: Boolean get() = !loading && workouts.isEmpty()
}
```

```kotlin
    val state: StateFlow<HomeUiState> =
        combine(workouts.observeAll(), exercises.observeAll()) { workoutList, exerciseList ->
            val library = exerciseList.associateBy { it.id }
            val summaries = WorkoutSummarizer.summarizeAll(workoutList, library)
            HomeUiState(
                loading = false,
                workouts = WorkoutSummarizer.sortedForHome(summaries),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
```

Remove the now-unused import:

```kotlin
import com.ottotech.workoutbuilder.domain.UpNext
```

At this point `HomeScreen.kt` will not compile — it still reads `state.upNext` and
`state.others`. That is expected; Task 6 rewrites it. **Do not commit a broken build:** run
Task 6 before committing, or temporarily leave this step's edit uncommitted. The step order here
is for review clarity, not for a green build at every line.

- [ ] **Step 6: Commit together with Task 6**

This task's domain changes and Task 6's screen rewrite are one compilable unit. Complete Task 6,
then make one commit covering both. The commit command is in Task 6, Step 6.

---

### Task 6: Rework Home into two explicit options

Home stops recommending. It offers exactly two things: **choose a workout** (equal cards, no
hero) or **build a workout** (the pinned button that is already there).

**Files:**
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `HomeUiState.workouts`, `WorkoutSummary.shortMeta`, `.minutesLabel`,
  `.lastDoneLine(now)` — all from Task 5.
- Produces: nothing later tasks depend on.

**Card layout.** Two rows, each with its content pushed to both edges, so the card is full by
construction rather than left-heavy:

```
┌──────────────────────────────────────────┐
│ Court Speed                       37 MIN │   name cardTitle · minutes cardTitle
│ 10 exercises · HIIT            Never done│   both cardMeta, muted
└──────────────────────────────────────────┘
```

This also retires the old leading badge, which showed a bare unlabelled number that was then
repeated in the meta line three words later.

**A note on the choice of full-width cards over a 2-column grid:** a grid uses horizontal space
more aggressively, but it strands a lone tile beside an empty half — and the app currently ships
with exactly one workout. Full-width cards degrade gracefully from one to many and handle long
workout names. If the library of workouts grows past ~8, revisit.

- [ ] **Step 1: Replace `HomeScreen.kt` wholesale**

The hero components go away entirely, so this is cleaner as a full rewrite than as edits.

```kotlin
package com.ottotech.workoutbuilder.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ottotech.workoutbuilder.domain.WorkoutSummary
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.components.InverseButton
import com.ottotech.workoutbuilder.ui.components.SectionLabel
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home offers two options and does not rank: choose a workout, or build one.
 *
 * The old "Up next" hero was removed 2026-07-26. It presented itself as a recommendation but
 * could only ever resolve to "the last workout you created" — nothing writes `lastPerformedAt`
 * until Plan 1D — and the user does not train in a fixed rotation, so even a working version
 * would have promised a schedule the app does not have. See
 * docs/superpowers/plans/2026-07-26-design-correction-pass.md.
 */
@Composable
fun HomeScreen(
    onStartWorkout: (String) -> Unit,
    onBuild: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Read once per composition rather than per card — every card would otherwise get a
    // slightly different "now", and none of them need second-level accuracy.
    val now = remember { Instant.now() }

    Column(Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Dimens.screenPadding,
                end = Dimens.screenPadding,
                top = 16.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            item { Header() }

            // While loading, the header stands alone rather than flashing an empty state.
            // In practice this is at most one frame — MainActivity gates on seeding.
            if (state.isEmpty) {
                item { EmptyCard() }
            } else if (state.workouts.isNotEmpty()) {
                item { SectionLabel("Choose a workout") }
                items(state.workouts, key = { it.id }) { summary ->
                    WorkoutCard(
                        summary = summary,
                        now = now,
                        onClick = { onStartWorkout(summary.id) },
                    )
                }
            }
        }

        // The second of Home's two options, pinned above the bottom nav rather than scrolling
        // away — the handoff calls it "sticky-feeling".
        InverseButton(
            text = "BUILD A WORKOUT",
            leadingGlyph = "+",
            onClick = onBuild,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenPadding)
                .padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun Header() {
    val colors = LocalAppColors.current
    val overline = remember {
        LocalDate.now()
            .format(DateTimeFormatter.ofPattern("EEE · MMM d", Locale.getDefault()))
            .uppercase(Locale.getDefault())
    }
    Column {
        Text(overline, style = AppType.overline, color = colors.textMuted)
        Spacer(Modifier.height(8.dp))
        Text("YOUR\nWORKOUTS", style = AppType.screenTitle, color = colors.onBackground)
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * One workout, equal in weight to every other. Both rows push their content to the card's two
 * edges, so the card is filled by construction rather than left-heavy with dead space.
 */
@Composable
private fun WorkoutCard(summary: WorkoutSummary, now: Instant, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    HairlineCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(Dimens.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    summary.name,
                    style = AppType.cardTitle,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(summary.minutesLabel, style = AppType.cardTitle, color = colors.onBackground)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(summary.shortMeta, style = AppType.cardMeta, color = colors.textMuted)
                Text(summary.lastDoneLine(now), style = AppType.cardMeta, color = colors.textMuted)
            }
        }
    }
}

/**
 * Spec §6.1 gives the empty state a designed treatment so a fresh install looks intentional
 * rather than broken. It names the one option that is still available.
 */
@Composable
private fun EmptyCard() {
    val colors = LocalAppColors.current
    HairlineCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(Dimens.cardPadding)) {
            Text("No workouts yet", style = AppType.cardTitle, color = colors.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(
                "Build one from the library and it will appear here.",
                style = AppType.cardMeta,
                color = colors.textMuted,
            )
        }
    }
}
```

Note the empty-state copy no longer mentions "the Coach" — that is Plan 2 and does not exist yet,
so advertising it sends a new user hunting for a button that is not there.

- [ ] **Step 2: Build**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. This is the first point since Task 5 Step 5 where the project
compiles.

- [ ] **Step 3: Run the full test suite**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest
```

Expected: 0 failures. Use the verification one-liner from Global Constraints to print per-class
counts and record the total.

- [ ] **Step 4: Verify Home on the emulator**

```bash
./gradlew :app:installDebug
```

Check, in order:

1. With the seeded Court Speed only: header, `CHOOSE A WORKOUT` label, one full-width card
   reading `Court Speed` / `37 MIN` / `10 exercises · HIIT` / `Never done`, then the pinned
   `+ BUILD A WORKOUT`. No hero, no dead band across the middle.
2. Build two more workouts. Confirm they appear **newest first**, above Court Speed.
3. Delete all workouts via the app's data (Settings → Apps → clear storage) and confirm the
   empty card renders and the Build button still works.

- [ ] **Step 5: Confirm `UpNext` is really gone**

```bash
grep -rn "UpNext\|metaLine\|state.others\|state.upNext" app/src/
```

Expected: **no output.** If anything matches, remove it before committing.

- [ ] **Step 6: Commit Tasks 5 and 6 together**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/domain/WorkoutSummary.kt \
        app/src/main/java/com/ottotech/workoutbuilder/ui/home/HomeViewModel.kt \
        app/src/main/java/com/ottotech/workoutbuilder/ui/home/HomeScreen.kt \
        app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutSummaryTest.kt
git commit -m "feat: Home offers two options instead of a fake recommendation

'Up next' ranked by lastPerformedAt, which nothing writes until Plan 1D, so it
always fell through to 'newest created' while presenting itself as a
recommendation. Home now shows equal cards under 'Choose a workout' plus the
existing Build button, and each card carries its own last-done line."
```

---

### Task 7: Stop the Builder destroying a draft on Back

You can spend ten minutes assembling a workout, tap Back once, and lose all of it with no
warning — while deleting a *single item* gets a five-second Undo bar. The protection is inverted
relative to the stakes.

**Files:**
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/domain/WorkoutDraft.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/builder/BuilderScreen.kt`
- Test: `app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutDraftTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `WorkoutDraft.hasContent(workout: Workout): Boolean`.

**Why a pure `hasContent` rather than an inline check:** `BuilderViewModel` seeds every new draft
with one empty section (`WorkoutDraft.addSection(newWorkout(...))`), so "the draft is untouched"
is *not* the same as "the draft is empty" — a naive `sections.isEmpty()` would be false on a
brand-new, entirely unedited draft and would prompt on every single Back. The rule belongs in
`domain/` with a test pinning exactly that case.

- [ ] **Step 1: Write the failing tests**

Append to `WorkoutDraftTest.kt`:

```kotlin
    // ---- hasContent ----

    @Test
    fun `a brand-new draft with its seeded empty section has no content`() {
        // BuilderViewModel seeds one empty section, so "untouched" is not "no sections".
        val fresh = WorkoutDraft.addSection(
            WorkoutDraft.newWorkout("w", Instant.EPOCH),
            "s1",
        )
        assertFalse(WorkoutDraft.hasContent(fresh))
    }

    @Test
    fun `naming the workout counts as content`() {
        val named = WorkoutDraft.rename(
            WorkoutDraft.addSection(WorkoutDraft.newWorkout("w", Instant.EPOCH), "s1"),
            "Leg Day",
        )
        assertTrue(WorkoutDraft.hasContent(named))
    }

    @Test
    fun `adding an exercise counts as content`() {
        val withItem = WorkoutDraft.addItem(
            WorkoutDraft.addSection(WorkoutDraft.newWorkout("w", Instant.EPOCH), "s1"),
            "s1", "i1", "push-up",
        )
        assertTrue(WorkoutDraft.hasContent(withItem))
    }
```

If `assertFalse` / `assertTrue` are not already imported in that file, add:

```kotlin
import kotlin.test.assertFalse
import kotlin.test.assertTrue
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest --tests '*WorkoutDraftTest*'
```

Expected: FAIL — `Unresolved reference: hasContent`.

- [ ] **Step 3: Implement `hasContent` in `WorkoutDraft.kt`**

Add it in the `// ---- validation ----` section, directly above `saveBlockers`:

```kotlin
    /**
     * Is there anything in this draft worth warning about before discarding it?
     *
     * Deliberately *not* `saveBlockers(workout).isEmpty()` — a half-finished draft is not
     * saveable but is absolutely worth protecting. And deliberately not `sections.isEmpty()`,
     * because `BuilderViewModel` seeds every new draft with one empty section, so a brand-new
     * untouched draft already has one.
     */
    fun hasContent(workout: Workout): Boolean =
        workout.name.isNotBlank() || workout.sections.any { it.items.isNotEmpty() }
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest --tests '*WorkoutDraftTest*'
```

Expected: PASS.

- [ ] **Step 5: Add the confirm dialog to `BuilderScreen.kt`**

Guard both exits — the header's `‹` and the system Back gesture. The Android platform rule from
Global Constraints applies: **System Back must always work**, so the dialog must be dismissible
and Back must never trap the user.

Add these imports:

```kotlin
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.ottotech.workoutbuilder.domain.WorkoutDraft
```

Inside `BuilderScreen`, immediately after `val colors = LocalAppColors.current`:

```kotlin
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = WorkoutDraft.hasContent(state.workout)

    // One exit path for both the header chevron and the system Back gesture, so they cannot
    // diverge. An untouched draft closes straight away — prompting on an empty form is noise.
    val requestClose = { if (dirty) confirmDiscard = true else onClose() }

    BackHandler(enabled = dirty) { confirmDiscard = true }
```

Change the header to route through it:

```kotlin
        ScreenHeader(title = "Build", onBack = requestClose)
```

And add the dialog at the very end of the outer `Column`'s content, after `SaveButton(...)`:

```kotlin
        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                containerColor = colors.background,
                title = { Text("Discard this workout?", style = AppType.cardTitle, color = colors.onBackground) },
                text = {
                    Text(
                        "It hasn't been saved. Everything you've added will be lost.",
                        style = AppType.cardMeta,
                        color = colors.textMuted,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { confirmDiscard = false; onClose() }) {
                        Text("Discard", style = AppType.chip, color = colors.onBackground)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscard = false }) {
                        Text("Keep editing", style = AppType.chip, color = colors.onBackground)
                    }
                },
            )
        }
```

- [ ] **Step 6: Show all save blockers, not just the first**

While in this file: the screen currently renders `state.saveBlockers.first()`, so you fix one
problem and discover the next. Replace that `Text` block with:

```kotlin
        if (!state.canSave) {
            Text(
                text = state.saveBlockers.joinToString(" · "),
                style = AppType.cardMeta,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding)
                    .padding(bottom = 6.dp),
            )
        }
```

- [ ] **Step 7: Run the full suite and build**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: 0 failures, `BUILD SUCCESSFUL`.

- [ ] **Step 8: Verify on the emulator**

```bash
./gradlew :app:installDebug
```

Four cases, all of which must pass:

1. Open the Builder and immediately press Back → closes with **no** prompt (nothing to lose).
2. Type a name, press Back → prompt appears. "Keep editing" returns you to the draft intact.
3. Type a name, press Back, "Discard" → returns to Home, nothing saved.
4. Add an exercise, tap the header `‹` → same prompt. Confirm the system Back gesture also
   dismisses the *dialog* without closing the screen.

Also confirm that with an unnamed, exercise-less draft the blocker line now reads
`Name your workout · Add at least one exercise` rather than just the first.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/domain/WorkoutDraft.kt \
        app/src/main/java/com/ottotech/workoutbuilder/ui/builder/BuilderScreen.kt \
        app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutDraftTest.kt
git commit -m "feat: confirm before discarding a builder draft

Backing out destroyed the whole draft silently while deleting one item got a
five-second undo bar. Also shows every save blocker rather than only the first."
```

---

### Task 8: Reconcile the spec and STATUS

The spec currently prescribes the wrong numbers and the removed "Up next" rule. Leaving it as-is
guarantees someone re-introduces both.

**Files:**
- Modify: `docs/superpowers/specs/2026-07-21-workout-builder-android-design.md`
- Modify: `docs/superpowers/STATUS.md`

**Interfaces:**
- Consumes: the final token values from Task 1 and the Home behaviour from Task 6.
- Produces: nothing.

- [ ] **Step 1: Amend the spec's §5 token section**

Add this note at the top of §5, before the existing token tables, and update any numeric values
in that section to the Task 1 values:

```markdown
> **Amended 2026-07-26 — token rescale.** The px values originally recorded here were measured
> inside the handoff's 264px-wide mock phone frame, which is a thumbnail rather than a device.
> They shipped at ~62% of intended size. `ui/theme/Type.kt` and `ui/theme/Dimens.kt` are now the
> authority for every size, spacing and radius in the app; this section describes intent, not
> exact numbers. See `docs/superpowers/plans/2026-07-26-design-correction-pass.md`.
```

- [ ] **Step 2: Amend the spec's §6.1**

Replace the "**Up next" selection:**" paragraph with:

```markdown
**Amended 2026-07-26 — no "Up next".** Home does not rank or recommend. It presents two
options: **choose a workout** (equal-weight cards under a `CHOOSE A WORKOUT` label, ordered
newest-created first) or **build a workout** (the pinned button). Each card carries its own
`Never done` / `4 days ago` last-done line. The original rule ranked by `lastPerformedAt`, which
nothing writes until Plan 1D, so it could only ever resolve to "the last workout you created"
while presenting itself as a recommendation. `domain/UpNext` is deleted.
```

- [ ] **Step 3: Update `STATUS.md`**

In the phase table, add a row above `Plan 1D`:

```markdown
| **Design correction pass** — scale, contrast, targets, Home | **Merged to `main`.** N tests green, verified on the emulator. |
```

Replace `N` with the real total from Task 6 Step 3.

Then add these entries to "Environment facts not obvious from the code":

```markdown
- **The design handoff's px values are ~62% of true scale.** Its mock phone frame is 264px
  wide — a thumbnail, not a device (a real phone is ~411–448 dp). Every size in the handoff and
  in spec §5 was transplanted 1:1 and shipped too small. Corrected 2026-07-26 in `Type.kt` /
  `Dimens.kt`, which are now the authority. **Do not "restore" handoff numbers.**
- **Light-mode muted greys are deliberately darker than dark-mode's.** `#6B6B6B` vs `#9A9A9A`.
  The original symmetric value was 2.8:1 on white — an accessibility failure. The two themes
  must not share these values.
- **`UpNext` is deleted and Home does not recommend.** It ranked by `lastPerformedAt`, which is
  still never written — `WorkoutRepository.markPerformed` remains callerless until Plan 1D.
  **When 1D wires up execution, do not resurrect the hero**; the product decision is that Home
  offers choose-or-build and nothing else.
- **Every touch target is 48 dp** (`Dimens.minTouchTarget`). Chips reach it via padding, glyph
  buttons and the back chevron via an explicit sized `Box`. New controls must clear this bar.
```

Finally, update the "One unwired seam" entry — `onStartWorkout` is still the 1D TODO, but Home
now calls it from the whole card rather than a hero button. Adjust its wording accordingly.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-07-21-workout-builder-android-design.md \
        docs/superpowers/STATUS.md
git commit -m "docs: record the design correction pass in the spec and STATUS

Spec §5's px values and §6.1's Up-next rule are both superseded."
```

---

## Out of scope, deliberately

Named here so they are decisions rather than oversights:

- **Nested cards in the Builder** (a bordered section card containing filled item cards). Real
  hierarchy smell, but unpicking it is a Builder redesign, not a correction pass.
- **Deleting the now-unused hero tokens.** Removing the Home hero in Task 6 leaves
  `AppType.heroLabel`, `Dimens.heroPadding` and `Dimens.radiusHero` with no call site. **Keep
  them, rescaled.** Spec §5 gives the Plan 1D execution screen an "Up next" card with exactly
  this label treatment and an 18dp-class radius, so 1D re-uses all three within one plan. This
  is the one place the plan deliberately tolerates a temporarily unreferenced token; if 1D ends
  up not using them, delete them there.
- **The Undo bar's timer not resetting.** `LaunchedEffect(Unit)` inside `if (state.canUndo)` does
  not relaunch on a second delete, so deleting twice in five seconds cuts the second window
  short. Real, small, and it wants its own fix with its own test.
- **Editing a saved workout.** Still no entry point. Reserved for a later plan, unchanged by this
  one.
- **Duplicating a workout, drag-to-reorder, bulk exercise picking.** Efficiency features, not
  corrections.
- **Revisiting the mono accent.** The app shipped with no accent hue, which is a documented
  choice; whether it should stay mono is a design conversation, not a bug.
- **State-change motion beyond Task 4.** Selection and press feedback across the app would help,
  but it is additive polish rather than a correction.

## Risks

- **Task 1 is a large visual jump.** Everything gets ~60% bigger. Screens that fit will still
  fit — content is scrolling everywhere — but the two-line `YOUR WORKOUTS` and `LIBRARY` titles
  at 40sp are the realistic overflow candidates. Task 1 Step 5 checks exactly this and gives a
  36sp fallback.
- **Task 3's 48 dp glyph buttons squeeze the Builder's section-name row.** Three buttons plus a
  text field on a 411 dp screen. Task 3 Step 6 checks it and gives the fix (wrap to a second row)
  rather than the wrong fix (shrink below 48 dp).
- **Tasks 5 and 6 do not compile independently.** They are split for review clarity and share one
  commit. Do not try to build between them.
