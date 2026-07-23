# Phase 1B — Home & Library Screens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the Phase 1A shell into a real two-screen app — a Home screen listing your
workouts with an "Up next" hero, and a Library that browses ~100 exercises by muscle with
live-counted categories, search, filter chips, a Filters sheet, and per-exercise demo links.

**Architecture:** Compose screens driven by ViewModels that expose one immutable state object
each. The ViewModels are thin glue: every decision they make — summarising a workout, picking
"Up next", toggling a facet, counting a category — lives in a pure `domain/` function that is
unit-tested on the JVM. Navigation is a single `NavHost` with four destinations. Dependencies
are wired by hand in an `Application` subclass; a DI framework would cost more than it saves in
a one-module app.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Navigation Compose, Lifecycle ViewModel
Compose, Room, JUnit 4 + kotlin.test.

**Spec:** `docs/superpowers/specs/2026-07-21-workout-builder-android-design.md` — §5 (tokens),
§6.1 (Home), §6.2 (Build sheet), §6.3 (Library), §6.3a (Filters sheet), §7 (demo links), §10
(architecture).

**Builds on:** `docs/superpowers/plans/2026-07-21-phase-1a-foundation-domain.md`, merged to
`main`. Read `gradle/libs.versions.toml` and `app/build.gradle.kts` before starting — they are
authoritative over any snippet in the 1A plan.

---

## Global Constraints

Everything from Plan 1A still applies. Repeated here because a task's implementer sees only
their own task:

- Package `com.ottotech.workoutbuilder`
- **Toolchain is fixed:** Gradle **9.4.1**, AGP **9.2.1**, Kotlin **2.2.10**, Compose BOM
  **2026.02.01**. Do not bump these. AndroidX *libraries* (lifecycle, navigation) may be added
  and bumped; the toolchain may not.
- **No `kotlin-android` plugin** — AGP 9 has built-in Kotlin support. Java compatibility 11.
- **Every Gradle command needs `JAVA_HOME` exported first** — there is no `java` on PATH:
  `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`
- `minSdk 26`, `compileSdk` / `targetSdk` 36
- **`domain/` must contain zero `android.*` imports.** `DomainPurityTest` enforces this and will
  fail the build if you put a Compose type in `domain/`.
- **Every colour is read from `LocalAppColors.current`.** No literal hex outside
  `ui/theme/Colors.kt`. This is what makes dark mode free rather than a retrofit.
- **All sizes and radii come from `Dimens`; all text styles from `AppType`.** If a value you
  need is missing, add it to those files rather than inlining a number.
- **No shadows anywhere.** Hairline (1dp) borders only.
- **Mono accent rule:** the accent is maximum contrast against its own surface —
  `onSurfaceInverse` on `surfaceInverse`, `onBackground` on `background`. There is no accent hue.
- **No icon-font or icon-library dependency.** The handoff's icons are geometric placeholders;
  Task 1 draws the three we need with `Canvas`. Chevrons, plus and close are text glyphs
  (`›`, `+`, `✕`), exactly as the HTML prototype uses them.
- Tag values come only from `TagVocabulary` — no free text.
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

Baseline entering this plan: **46 tests, 0 failures** across six classes.

### Device

The emulator is sufficient for all of 1B — these are screens and data, no audio and no
background service. A Pixel 8 Pro AVD on API 36 exists; launch it from Android Studio's Device
Manager, then `./gradlew :app:installDebug`.

---

## Two deliberate scope decisions

Read these before starting; they explain files that are not obviously "Home and Library".

**1. Court Speed ships as seed data (Task 3).** Until the Builder exists in Plan 1C there is no
way to create a workout, so Home would have nothing but its empty state — untestable and
useless. Task 3 promotes the Court Speed acceptance fixture out of test sources into
`data/seed/WorkoutSeed.kt` and seeds it on first launch. This kills three birds: Home has real
content, the acceptance test now asserts against the data the app actually ships, and Plan 1D
gets a real workout to test pocket audio with.

**2. Two navigation seams are left unwired.** "Start workout" needs the execution screen
(Plan 1D) and the Build sheet's two cards need the Builder and the Coach (Plans 1C and 2). They
are `() -> Unit` parameters, defaulted to no-ops at the call site in `AppNav.kt` with a `TODO`
comment naming the plan that fills them. No placeholder screens are created.

---

## File Structure

```
gradle/libs.versions.toml                        MODIFY — navigation, lifecycle-viewmodel-compose
app/build.gradle.kts                             MODIFY — wire the two new dependencies
app/src/main/AndroidManifest.xml                 MODIFY — android:name=".WorkoutApp"

app/src/main/java/com/ottotech/workoutbuilder/
  WorkoutApp.kt                    NEW  — Application; owns the database and repositories
  MainActivity.kt                  MODIFY — seeds, then hosts AppNav

  domain/
    WorkoutSummary.kt              NEW  — WorkoutSummary, WorkoutSummarizer, UpNext (pure)
    ExerciseFilter.kt              MODIFY — FilterState transitions + category helpers (pure)

  data/
    AppPrefs.kt                    NEW  — one boolean: has the sample workout been seeded
    WorkoutRepository.kt           MODIFY — observeAll() hydrates; seedSampleIfNeeded()
    seed/WorkoutSeed.kt            NEW  — Court Speed, promoted from test fixtures
    seed/ExerciseSeed.kt           MODIFY — add the one missing row ("flyer")

  ui/
    AppNav.kt                      NEW  — routes + NavHost; the two unwired seams live here
    DemoLink.kt                    NEW  — Exercise -> Intent for Watch demo
    components/
      Glyphs.kt                    NEW  — home, library and search icons drawn with Canvas
      AppScaffold.kt               NEW  — page background, insets, bottom nav
      Controls.kt                  NEW  — Chip, AppTextField, SectionLabel, InverseButton, HairlineCard
    home/
      HomeViewModel.kt             NEW
      HomeScreen.kt                NEW  — hero card, workout rows, build button, empty state
      BuildSheet.kt                NEW  — spec §6.2
    library/
      LibraryViewModel.kt          NEW  — shared by Library, Category and Detail screens
      LibraryScreen.kt             NEW  — search, chip row, category grid
      FiltersSheet.kt              NEW  — spec §6.3a
      CategoryScreen.kt            NEW  — one muscle's exercise list
      ExerciseDetailScreen.kt      NEW  — tags, Watch demo, demo URL override

app/src/test/java/com/ottotech/workoutbuilder/
  domain/WorkoutSummaryTest.kt     NEW
  domain/FilterStateTest.kt        NEW
  data/WorkoutSeedTest.kt          NEW  — replaces domain/CourtSpeedFixtureTest.kt
  domain/CourtSpeedFixtureTest.kt  DELETE
  fixtures/CourtSpeed.kt           DELETE
  domain/DurationEstimatorTest.kt  MODIFY — point at WorkoutSeed + ExerciseSeed
```

---

### Task 1: Dependencies, app container and the navigation shell

Establishes the frame every later task hangs off: an `Application` that owns the repositories,
a two-tab `NavHost`, and a bottom nav that matches the handoff. Home and Library are stubs at
the end of this task — they get their real contents in Tasks 4 and 7.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/WorkoutApp.kt`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/data/AppPrefs.kt`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/components/Glyphs.kt`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/components/AppScaffold.kt`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/AppNav.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/MainActivity.kt`

**Interfaces:**
- Consumes: `AppDatabase.get`, `ExerciseRepository`, `WorkoutRepository`, `WorkoutTheme`,
  `LocalAppColors`, `AppType`, `Dimens` (all from Plan 1A)
- Produces:
  - `WorkoutApp` with `val prefs: AppPrefs`, `val exercises: ExerciseRepository`,
    `val workouts: WorkoutRepository`
  - `AppPrefs` with `var sampleWorkoutSeeded: Boolean`
  - `Tab` enum (`HOME`, `LIBRARY`) and
    `AppScaffold(tab: Tab, onSelectTab: (Tab) -> Unit, content: @Composable () -> Unit)`
  - `HomeGlyph`, `LibraryGlyph`, `SearchGlyph` — `@Composable (size: Dp, color: Color)`
  - `Routes` object and `AppNav()`

- [ ] **Step 1: Add the dependencies to the version catalog**

In `gradle/libs.versions.toml`, replace the `lifecycleRuntimeKtx = "2.6.1"` line under
`[versions]` with:

```toml
# Lifecycle 2.6.1 predates this Compose BOM by years; 2.9.4 is the version already present
# in the local Gradle cache and is what viewmodel-compose is aligned against.
lifecycle = "2.9.4"
navigation = "2.9.6"
```

Under `[libraries]`, replace the `androidx-lifecycle-runtime-ktx` line with:

```toml
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
```

> **If `navigation = "2.9.6"` fails to resolve**, do not guess again. Run
> `./gradlew :app:dependencies --configuration debugRuntimeClasspath` to see what Gradle reports,
> or check `~/.gradle/caches/modules-2/files-2.1/androidx.navigation/navigation-compose`. Pick
> the highest 2.9.x that resolves and **record the number you landed on in an `AS BUILT` note at
> the top of this task**, the way Plan 1A Task 1 does. Do not switch to `navigation3` — it is a
> different library with a different API.

- [ ] **Step 2: Wire them into the app module**

In `app/build.gradle.kts`, in the `dependencies` block, immediately after
`implementation(libs.androidx.lifecycle.runtime.ktx)` add:

```kotlin
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
```

- [ ] **Step 3: Verify the dependencies resolve**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. A `Could not find androidx.navigation:navigation-compose:...`
error means Step 1's version needs the treatment described in its callout.

- [ ] **Step 4: Create the preferences store**

`app/src/main/java/com/ottotech/workoutbuilder/data/AppPrefs.kt`:

```kotlin
package com.ottotech.workoutbuilder.data

import android.content.Context

/**
 * One-shot flags that are not app data.
 *
 * Deliberately SharedPreferences and not a Room table: "have we ever seeded the sample
 * workout" has to survive the user deleting that workout, so it cannot live in the table
 * it describes.
 */
class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("workout-builder", Context.MODE_PRIVATE)

    var sampleWorkoutSeeded: Boolean
        get() = prefs.getBoolean(KEY_SAMPLE_SEEDED, false)
        set(value) = prefs.edit().putBoolean(KEY_SAMPLE_SEEDED, value).apply()

    private companion object {
        const val KEY_SAMPLE_SEEDED = "sample_workout_seeded"
    }
}
```

- [ ] **Step 5: Create the application container**

`app/src/main/java/com/ottotech/workoutbuilder/WorkoutApp.kt`:

```kotlin
package com.ottotech.workoutbuilder

import android.app.Application
import com.ottotech.workoutbuilder.data.AppPrefs
import com.ottotech.workoutbuilder.data.ExerciseRepository
import com.ottotech.workoutbuilder.data.WorkoutRepository
import com.ottotech.workoutbuilder.data.db.AppDatabase

/**
 * Manual dependency wiring. One module, four objects, no injection framework — Hilt would
 * add a plugin, a compiler pass and a set of annotations to save four lines.
 *
 * ViewModels reach this through `APPLICATION_KEY` in their factory, so they still take
 * plain repository constructor parameters and stay independent of it.
 */
class WorkoutApp : Application() {

    private val database by lazy { AppDatabase.get(this) }

    val prefs by lazy { AppPrefs(this) }
    val exercises by lazy { ExerciseRepository(database.exerciseDao()) }
    val workouts by lazy { WorkoutRepository(database.workoutDao()) }
}
```

- [ ] **Step 6: Register it in the manifest**

In `app/src/main/AndroidManifest.xml`, add `android:name=".WorkoutApp"` as the first attribute
of the `<application>` element, so it reads:

```xml
    <application
        android:name=".WorkoutApp"
        android:allowBackup="false"
```

Leave every other attribute exactly as it is.

- [ ] **Step 7: Draw the three icons**

The handoff's icons are deliberate geometric placeholders — a rounded-square outline, three
stacked bars, a circle outline. Drawing them costs 60 lines and avoids adding an icon library.

`app/src/main/java/com/ottotech/workoutbuilder/ui/components/Glyphs.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The handoff's icons are geometric placeholders, described exactly as drawn here.
 * Keeping them as Canvas drawings means no icon-font or icon-library dependency, and
 * they inherit the mono palette by taking a colour parameter.
 */

private val StrokeWidth = 1.6.dp

/** Bottom nav, Home tab: a rounded-square outline. */
@Composable
fun HomeGlyph(size: Dp = 16.dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = StrokeWidth.toPx()
        val inset = w / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(this.size.width - w, this.size.height - w),
            cornerRadius = CornerRadius(this.size.width * 0.28f),
            style = Stroke(width = w),
        )
    }
}

/** Bottom nav, Library tab: three stacked bars. */
@Composable
fun LibraryGlyph(size: Dp = 16.dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val barHeight = this.size.height * 0.16f
        val gap = (this.size.height - barHeight * 3f) / 2f
        repeat(3) { i ->
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, i * (barHeight + gap)),
                size = Size(this.size.width, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f),
            )
        }
    }
}

/** Search field leading icon: a circle outline. */
@Composable
fun SearchGlyph(size: Dp = 13.dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = StrokeWidth.toPx()
        drawCircle(color = color, radius = (this.size.minDimension - w) / 2f, style = Stroke(width = w))
    }
}
```

- [ ] **Step 8: Build the page scaffold and bottom nav**

`app/src/main/java/com/ottotech/workoutbuilder/ui/components/AppScaffold.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

enum class Tab(val label: String) { HOME("Home"), LIBRARY("Library") }

/**
 * The page frame for the two bottom-nav destinations: background, status-bar inset, and
 * the nav bar itself. [content] is given the remaining height and is expected to do its
 * own scrolling.
 *
 * Deliberately not Material 3's Scaffold + NavigationBar — those bring an indicator pill,
 * their own colour scheme and their own height, all three of which would have to be
 * fought back to reach the handoff's 1px-border-and-glyph design.
 */
@Composable
fun AppScaffold(
    tab: Tab,
    onSelectTab: (Tab) -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Box(Modifier.weight(1f)) { content() }
        BottomNav(tab = tab, onSelectTab = onSelectTab)
    }
}

@Composable
private fun BottomNav(tab: Tab, onSelectTab: (Tab) -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().background(colors.background)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Dimens.hairline)
                .background(colors.borderSubtle),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavItem(Tab.HOME, tab == Tab.HOME) { onSelectTab(Tab.HOME) }
            NavItem(Tab.LIBRARY, tab == Tab.LIBRARY) { onSelectTab(Tab.LIBRARY) }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, active: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    // Active state is an instant swap with no transition and no ripple — spec §5, Motion.
    val tint = if (active) colors.onBackground else colors.navInactive
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 4.dp),
    ) {
        when (tab) {
            Tab.HOME -> HomeGlyph(color = tint)
            Tab.LIBRARY -> LibraryGlyph(color = tint)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = tab.label,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = tint,
        )
    }
}
```

- [ ] **Step 9: Create the navigation host with stub screens**

`app/src/main/java/com/ottotech/workoutbuilder/ui/AppNav.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.ui.components.AppScaffold
import com.ottotech.workoutbuilder.ui.components.Tab

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val CATEGORY = "library/{muscle}"
    const val EXERCISE = "exercise/{exerciseId}"

    fun category(muscle: Muscle) = "library/${muscle.name}"
    fun exercise(exerciseId: String) = "exercise/$exerciseId"
}

/**
 * Switching bottom-nav tabs must not stack destinations — otherwise Back walks through
 * every tap the user made instead of leaving the app.
 */
private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppNav() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            AppScaffold(tab = Tab.HOME, onSelectTab = { if (it == Tab.LIBRARY) nav.switchTab(Routes.LIBRARY) }) {
                Text("Home")
            }
        }

        composable(Routes.LIBRARY) {
            AppScaffold(tab = Tab.LIBRARY, onSelectTab = { if (it == Tab.HOME) nav.switchTab(Routes.HOME) }) {
                Text("Library")
            }
        }

        composable(
            route = Routes.CATEGORY,
            arguments = listOf(navArgument("muscle") { type = NavType.StringType }),
        ) { entry ->
            val muscle = Muscle.valueOf(entry.arguments?.getString("muscle") ?: Muscle.CHEST.name)
            Text(muscle.label)
        }

        composable(
            route = Routes.EXERCISE,
            arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
        ) { entry ->
            Text(entry.arguments?.getString("exerciseId").orEmpty())
        }
    }
}
```

- [ ] **Step 10: Rewrite MainActivity to seed then host the nav graph**

Replace `app/src/main/java/com/ottotech/workoutbuilder/MainActivity.kt` entirely:

```kotlin
package com.ottotech.workoutbuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.ottotech.workoutbuilder.ui.AppNav
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors
import com.ottotech.workoutbuilder.ui.theme.WorkoutTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as WorkoutApp

        // Seeding runs before the UI is composed rather than alongside it. It is a few
        // hundred inserts on a cold start only, and gating on it avoids Home flashing its
        // "Nothing here yet" empty state for one frame before the seed lands.
        var ready by mutableStateOf(false)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { app.exercises.seedIfNeeded() }
            ready = true
        }

        setContent {
            WorkoutTheme {
                Box(Modifier.fillMaxSize().background(LocalAppColors.current.background)) {
                    if (ready) AppNav()
                }
            }
        }
    }
}
```

- [ ] **Step 11: Build, install and verify the shell**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:installDebug
```

Expected on the emulator:
- App launches to a screen reading `Home` with a two-item bottom nav
- The Home glyph and label are near-black and bold; Library is grey
- Tapping **Library** swaps the screen text to `Library` and inverts which nav item is dark,
  with no animation
- Pressing the system Back button from Library returns to Home; pressing Back again exits the
  app (it must **not** cycle between the two tabs)
- The nav bar sits above the gesture pill, not under it

- [ ] **Step 12: Run the existing suite to confirm nothing regressed**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: 46 tests, 0 failures.

- [ ] **Step 13: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml \
  app/src/main/java/com/ottotech/workoutbuilder/
git commit -m "feat: add navigation shell, bottom nav and application container"
```

---

### Task 2: Home summary domain

Everything Home computes, written test-first as pure functions. Home's ViewModel will do no
arithmetic of its own.

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/domain/WorkoutSummary.kt`
- Test: `app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutSummaryTest.kt`

**Interfaces:**
- Consumes: `Workout`, `Section`, `WorkoutItem`, `Exercise`, `TrainingType`, `ItemMode`,
  `SegmentCompiler.compile`, `DurationEstimator.estimateMinutes`
- Produces:
  - `WorkoutSummary(id, name, type, exerciseCount, estimatedMinutes, createdAt, lastPerformedAt)`
    with `val metaLine: String`
  - `WorkoutSummarizer.summarize(workout: Workout, exercises: Map<String, Exercise>): WorkoutSummary`
  - `WorkoutSummarizer.summarizeAll(workouts: List<Workout>, exercises: Map<String, Exercise>): List<WorkoutSummary>`
  - `UpNext.select(summaries: List<WorkoutSummary>): WorkoutSummary?`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutSummaryTest.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkoutSummaryTest {

    private val push = Exercise(
        id = "push-up", name = "Push-up", primaryMuscle = Muscle.CHEST,
        tags = setOf(Tag(Facet.EQUIPMENT, "Bodyweight")), demoQuery = "push up proper form",
    )
    private val squat = Exercise(
        id = "air-squat", name = "Air Squat", primaryMuscle = Muscle.LEGS,
        tags = setOf(Tag(Facet.EQUIPMENT, "Bodyweight")), demoQuery = "air squat proper form",
    )
    private val lookup = mapOf(push.id to push, squat.id to squat)

    private fun timed(id: String, exId: String, sec: Int) =
        WorkoutItem(id = id, exerciseId = exId, mode = ItemMode.TIMED, durationSec = sec)

    private fun workout(
        id: String = "w",
        createdAt: Instant = Instant.EPOCH,
        lastPerformedAt: Instant? = null,
        vararg sections: Section,
    ) = Workout(
        id = id, name = "W", type = TrainingType.HIIT,
        createdAt = createdAt, lastPerformedAt = lastPerformedAt, sections = sections.toList(),
    )

    private fun summary(
        id: String,
        createdAt: Instant = Instant.EPOCH,
        lastPerformedAt: Instant? = null,
    ) = WorkoutSummary(
        id = id, name = id, type = TrainingType.HIIT, exerciseCount = 1,
        estimatedMinutes = 1, createdAt = createdAt, lastPerformedAt = lastPerformedAt,
    )

    // ---- summarize ----

    @Test
    fun `exercise count is items across all sections, not round-expanded segments`() {
        val out = WorkoutSummarizer.summarize(
            workout(
                sections = arrayOf(
                    Section("s1", "A", rounds = 3, items = listOf(timed("a", push.id, 30), timed("b", squat.id, 30))),
                    Section("s2", "B", items = listOf(timed("c", push.id, 30))),
                ),
            ),
            lookup,
        )
        assertEquals(3, out.exerciseCount)
    }

    @Test
    fun `estimated minutes come from the compiled segments including rests`() {
        // 2 rounds x (60s work) + 1 x 60s round rest = 180s = 3 min
        val out = WorkoutSummarizer.summarize(
            workout(
                sections = arrayOf(
                    Section(
                        "s", "A", rounds = 2, restBetweenRounds = Rest.Timed(60),
                        items = listOf(timed("a", push.id, 60)),
                    ),
                ),
            ),
            lookup,
        )
        assertEquals(3, out.estimatedMinutes)
    }

    @Test
    fun `identity fields are carried through unchanged`() {
        val created = Instant.ofEpochSecond(1000)
        val performed = Instant.ofEpochSecond(2000)
        val out = WorkoutSummarizer.summarize(
            workout(id = "abc", createdAt = created, lastPerformedAt = performed,
                sections = arrayOf(Section("s", "A", items = listOf(timed("a", push.id, 30))))),
            lookup,
        )
        assertEquals("abc", out.id)
        assertEquals(TrainingType.HIIT, out.type)
        assertEquals(created, out.createdAt)
        assertEquals(performed, out.lastPerformedAt)
    }

    @Test
    fun `a workout referencing a missing exercise summarises instead of throwing`() {
        // SegmentCompiler requires every exerciseId to resolve. Home must survive a
        // dangling reference — a deleted custom exercise must not take the screen down.
        val out = WorkoutSummarizer.summarize(
            workout(
                sections = arrayOf(
                    Section("s", "A", items = listOf(timed("a", push.id, 60), timed("b", "deleted-id", 60))),
                ),
            ),
            lookup,
        )
        assertEquals(1, out.exerciseCount)
        assertEquals(1, out.estimatedMinutes)
    }

    @Test
    fun `an empty workout summarises to zeroes`() {
        val out = WorkoutSummarizer.summarize(workout(), lookup)
        assertEquals(0, out.exerciseCount)
        assertEquals(0, out.estimatedMinutes)
    }

    // ---- metaLine ----

    @Test
    fun `meta line reads count, minutes and type`() {
        val out = WorkoutSummarizer.summarize(
            workout(sections = arrayOf(
                Section("s", "A", items = listOf(timed("a", push.id, 60), timed("b", squat.id, 60))),
            )),
            lookup,
        )
        assertEquals("2 exercises · 2 min · HIIT", out.metaLine)
    }

    @Test
    fun `meta line singularises one exercise`() {
        val out = WorkoutSummarizer.summarize(
            workout(sections = arrayOf(Section("s", "A", items = listOf(timed("a", push.id, 60))))),
            lookup,
        )
        assertEquals("1 exercise · 1 min · HIIT", out.metaLine)
    }

    // ---- UpNext ----

    @Test
    fun `up next is null when there is nothing to do`() {
        assertNull(UpNext.select(emptyList()))
    }

    @Test
    fun `up next is the workout performed longest ago`() {
        val out = UpNext.select(listOf(
            summary("recent", lastPerformedAt = Instant.ofEpochSecond(3000)),
            summary("stale", lastPerformedAt = Instant.ofEpochSecond(1000)),
            summary("middle", lastPerformedAt = Instant.ofEpochSecond(2000)),
        ))
        assertEquals("stale", out?.id)
    }

    @Test
    fun `a never-performed workout outranks every performed one`() {
        val out = UpNext.select(listOf(
            summary("ancient", lastPerformedAt = Instant.EPOCH),
            summary("fresh", createdAt = Instant.ofEpochSecond(500), lastPerformedAt = null),
        ))
        assertEquals("fresh", out?.id)
    }

    @Test
    fun `among never-performed workouts the newest wins`() {
        val out = UpNext.select(listOf(
            summary("older", createdAt = Instant.ofEpochSecond(100)),
            summary("newest", createdAt = Instant.ofEpochSecond(900)),
            summary("middle", createdAt = Instant.ofEpochSecond(500)),
        ))
        assertEquals("newest", out?.id)
    }

    @Test
    fun `a single workout is always up next`() {
        assertEquals("only", UpNext.select(listOf(summary("only")))?.id)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest --tests "*WorkoutSummaryTest*"
```

Expected: FAIL — `Unresolved reference: WorkoutSummary`

- [ ] **Step 3: Implement**

`app/src/main/java/com/ottotech/workoutbuilder/domain/WorkoutSummary.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

import java.time.Instant

/**
 * Everything the Home screen shows about a workout, so the screen never re-walks
 * sections or re-runs the compiler while composing.
 */
data class WorkoutSummary(
    val id: String,
    val name: String,
    val type: TrainingType,
    val exerciseCount: Int,
    val estimatedMinutes: Int,
    val createdAt: Instant,
    val lastPerformedAt: Instant?,
) {
    /** `10 exercises · 22 min · HIIT` — the hero and row meta line, spec §6.1. */
    val metaLine: String
        get() {
            val noun = if (exerciseCount == 1) "exercise" else "exercises"
            return "$exerciseCount $noun · $estimatedMinutes min · ${type.label}"
        }
}

object WorkoutSummarizer {

    fun summarize(workout: Workout, exercises: Map<String, Exercise>): WorkoutSummary {
        // SegmentCompiler throws on an unresolvable exerciseId, which is right for
        // execution — you cannot run a workout with a hole in it. Home is a list, so it
        // drops the dangling item and shows what is left rather than taking the screen
        // down over a deleted custom exercise.
        val resolvable = workout.copy(
            sections = workout.sections.map { section ->
                section.copy(items = section.items.filter { it.exerciseId in exercises })
            },
        )
        val segments = SegmentCompiler.compile(resolvable, exercises)
        return WorkoutSummary(
            id = workout.id,
            name = workout.name,
            type = workout.type,
            exerciseCount = resolvable.sections.sumOf { it.items.size },
            estimatedMinutes = DurationEstimator.estimateMinutes(segments),
            createdAt = workout.createdAt,
            lastPerformedAt = workout.lastPerformedAt,
        )
    }

    fun summarizeAll(workouts: List<Workout>, exercises: Map<String, Exercise>): List<WorkoutSummary> =
        workouts.map { summarize(it, exercises) }
}

object UpNext {

    /**
     * Spec §6.1: the workout performed longest ago.
     *
     * Never-performed workouts sort ahead of every performed one — nothing is more
     * overdue than a workout you have not done at all — and among those the most recently
     * created wins, because the one you just built is the one you meant to do next.
     */
    fun select(summaries: List<WorkoutSummary>): WorkoutSummary? {
        val neverPerformed = summaries.filter { it.lastPerformedAt == null }
        return if (neverPerformed.isNotEmpty()) {
            neverPerformed.maxByOrNull { it.createdAt }
        } else {
            summaries.minByOrNull { it.lastPerformedAt!! }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "*WorkoutSummaryTest*" --tests "*DomainPurityTest*"
```

Expected: PASS, 13 tests (12 summary + 1 purity)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/domain/WorkoutSummary.kt \
  app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutSummaryTest.kt
git commit -m "feat: add workout summarisation and up-next selection"
```

---

### Task 3: Ship Court Speed as seed data

Promotes the Court Speed acceptance fixture out of test sources and into the app, then seeds it
on first launch. See "Two deliberate scope decisions" above for why.

After this task there is exactly one definition of Court Speed, it is the one the app ships, and
the acceptance test asserts against it using the real seeded exercise library — which also proves
every `exerciseId` the workout references exists in that library.

**Files:**
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/data/seed/ExerciseSeed.kt`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/data/seed/WorkoutSeed.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/data/WorkoutRepository.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/MainActivity.kt`
- Create: `app/src/test/java/com/ottotech/workoutbuilder/data/WorkoutSeedTest.kt`
- Delete: `app/src/test/java/com/ottotech/workoutbuilder/domain/CourtSpeedFixtureTest.kt`
- Delete: `app/src/test/java/com/ottotech/workoutbuilder/fixtures/CourtSpeed.kt`
- Modify: `app/src/test/java/com/ottotech/workoutbuilder/domain/DurationEstimatorTest.kt`

**Interfaces:**
- Consumes: `Workout`, `Section`, `WorkoutItem`, `Rest`, `ItemMode`, `TrainingType`,
  `ExerciseSeed.exercises`, `AppPrefs`, `WorkoutDao`
- Produces:
  - `WorkoutSeed.COURT_SPEED_ID: String`, `WorkoutSeed.courtSpeed: Workout`,
    `WorkoutSeed.workouts: List<Workout>`
  - `WorkoutRepository.observeAll(): Flow<List<Workout>>` — now **fully hydrated**
  - `WorkoutRepository.seedSampleIfNeeded(prefs: AppPrefs)`

- [ ] **Step 1: Add the one missing exercise**

The seeded workout's Bonus section references `flyer`, which is the only Court Speed exercise
not already in the library. In `ExerciseSeed.kt`, in the `CARDIO` block, immediately after the
`bear-crawl` line, add:

```kotlin
        e("flyer", "Flyer", Muscle.CARDIO, H, listOf("Bodyweight"), listOf(Muscle.LEGS)),
```

This takes the library from 100 rows to 101 and CARDIO from 8 to 9. `ExerciseSeedTest` asserts
`>=` its targets, so it stays green.

- [ ] **Step 2: Create the workout seed**

`app/src/main/java/com/ottotech/workoutbuilder/data/seed/WorkoutSeed.kt`:

```kotlin
package com.ottotech.workoutbuilder.data.seed

import com.ottotech.workoutbuilder.domain.ItemMode
import com.ottotech.workoutbuilder.domain.Rest
import com.ottotech.workoutbuilder.domain.Section
import com.ottotech.workoutbuilder.domain.TrainingType
import com.ottotech.workoutbuilder.domain.Workout
import com.ottotech.workoutbuilder.domain.WorkoutItem
import java.time.Instant

/**
 * The workouts a fresh install starts with.
 *
 * Court Speed is rebuilt from `web-app-reference/HIIT Workout.html` and mirrors that file's
 * `buildSegments()`. It is both the app's sample content and its acceptance fixture: if the
 * compiler turns this into the same 40-segment sequence the web app produces, the data model
 * can express a real workout. See `WorkoutSeedTest`.
 *
 * Every `exerciseId` below must be a row in [ExerciseSeed] — the test enforces that too.
 *
 * `createdAt` is EPOCH rather than the install time so the object stays a constant and the
 * test stays deterministic. Nothing renders a workout's creation date; it is only a
 * tiebreak in `UpNext`, where any workout the user builds later correctly outranks this one.
 */
object WorkoutSeed {

    const val COURT_SPEED_ID = "court-speed"

    private fun timed(id: String, exId: String, sec: Int, note: String? = null) =
        WorkoutItem(id = id, exerciseId = exId, mode = ItemMode.TIMED, durationSec = sec, note = note)

    val courtSpeed = Workout(
        id = COURT_SPEED_ID,
        name = "Court Speed",
        type = TrainingType.HIIT,
        createdAt = Instant.EPOCH,
        sections = listOf(
            Section(
                id = "warm-prep",
                name = "Warm Prep",
                items = listOf(
                    timed("wp1", "leg-swings", 45, "10 each direction · per leg"),
                    timed("wp2", "walking-lunge-twist", 45, "8 each side"),
                    timed("wp3", "a-skips", 45, "20m × 2"),
                    timed("wp4", "build-up-strides", 45, "2 × ~70% pace"),
                ),
            ),
            Section(
                id = "block-a",
                name = "Block A · Tennis Circuit",
                rounds = 3,
                restBetweenExercises = Rest.Timed(15),
                restBetweenRounds = Rest.Timed(60),
                // The web app's "Reset · walk to sprint start" transition.
                restAfterSection = Rest.Timed(60),
                items = listOf(
                    timed("ba1", "lateral-shuffle", 30, "~5m apart · touch ground each end"),
                    timed("ba2", "skater-bounds", 30, "Explosive · stick each landing"),
                    timed("ba3", "split-squat-jumps", 30, "Alternating legs"),
                    timed("ba4", "squat-jumps-180", 30, "Alternate rotation direction"),
                ),
            ),
            Section(
                id = "block-b",
                name = "Block B · Sprints",
                rounds = 6,
                // Walk-back recovery: tap when ready, not a timer.
                restBetweenRounds = Rest.Manual,
                items = listOf(
                    WorkoutItem(
                        "bb1", "sprint", ItemMode.MANUAL,
                        note = "60–80m @ 90% · drive knees, stay tall",
                    ),
                ),
            ),
            Section(
                id = "bonus",
                name = "Bonus · Optional",
                items = listOf(
                    WorkoutItem(
                        "bo1", "flyer", ItemMode.MANUAL,
                        note = "40m all-out · if you have it",
                    ),
                ),
            ),
        ),
    )

    val workouts: List<Workout> = listOf(courtSpeed)
}
```

- [ ] **Step 3: Delete the old fixture and its test**

```bash
git rm app/src/test/java/com/ottotech/workoutbuilder/fixtures/CourtSpeed.kt \
       app/src/test/java/com/ottotech/workoutbuilder/domain/CourtSpeedFixtureTest.kt
```

- [ ] **Step 4: Write the replacement acceptance test**

`app/src/test/java/com/ottotech/workoutbuilder/data/WorkoutSeedTest.kt`. Same assertions as the
deleted `CourtSpeedFixtureTest`, plus two new ones that only became possible once the workout and
the library shipped together:

```kotlin
package com.ottotech.workoutbuilder.data

import com.ottotech.workoutbuilder.data.seed.ExerciseSeed
import com.ottotech.workoutbuilder.data.seed.WorkoutSeed
import com.ottotech.workoutbuilder.domain.DurationEstimator
import com.ottotech.workoutbuilder.domain.ItemMode
import com.ottotech.workoutbuilder.domain.SegmentCompiler
import com.ottotech.workoutbuilder.domain.SegmentKind
import com.ottotech.workoutbuilder.domain.WorkoutSummarizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Acceptance test. Compiles the shipped Court Speed workout against the shipped exercise
 * library and asserts the output matches `buildSegments()` in the reference web app.
 *
 * If the segment count is not 40, the compiler or the seed is wrong — not this
 * expectation. Re-read spec §4 and `web-app-reference/HIIT Workout.html` before changing a
 * number here.
 */
class WorkoutSeedTest {

    private val library = ExerciseSeed.exercises.associateBy { it.id }
    private val segments = SegmentCompiler.compile(WorkoutSeed.courtSpeed, library)

    @Test
    fun `every exercise the seeded workout references exists in the seeded library`() {
        val missing = WorkoutSeed.workouts
            .flatMap { it.sections }
            .flatMap { it.items }
            .map { it.exerciseId }
            .filterNot { it in library }
            .distinct()
        assertTrue(missing.isEmpty(), "seeded workouts reference unknown exercises: $missing")
    }

    @Test
    fun `compiles to forty segments matching the reference web app`() {
        // Warm Prep 4 + Block A 23 + transition 1 + Block B 11 + Bonus 1
        assertEquals(40, segments.size)
    }

    @Test
    fun `warm prep is four timed segments of forty five seconds`() {
        val warm = segments.take(4)
        assertEquals(
            listOf("Leg Swings", "Walking Lunge + Twist", "A-Skips", "Build-Up Strides"),
            warm.map { it.exerciseName },
        )
        assertTrue(warm.all { it.durationSec == 45 && it.kind == SegmentKind.WORK })
    }

    @Test
    fun `block A round one alternates work and rest ending on work`() {
        val round = segments.subList(4, 11)
        assertEquals(
            listOf(
                SegmentKind.WORK, SegmentKind.REST, SegmentKind.WORK, SegmentKind.REST,
                SegmentKind.WORK, SegmentKind.REST, SegmentKind.WORK,
            ),
            round.map { it.kind },
        )
        assertEquals(listOf(30, 15, 30, 15, 30, 15, 30), round.map { it.durationSec })
        assertTrue(round.all { it.label == "BLOCK A · TENNIS CIRCUIT · ROUND 1/3" })
    }

    @Test
    fun `block A has two round rests plus one transition rest`() {
        val sixtySecondRests = segments.filter {
            it.kind == SegmentKind.REST && it.durationSec == 60 && it.label.contains("BLOCK A")
        }
        assertEquals(3, sixtySecondRests.size)
    }

    @Test
    fun `block B is six manual sprints separated by five manual rests`() {
        val blockB = segments.filter { it.label.contains("BLOCK B") }
        assertEquals(11, blockB.size)
        assertEquals(6, blockB.count { it.kind == SegmentKind.WORK })
        assertEquals(5, blockB.count { it.kind == SegmentKind.REST })
        assertTrue(blockB.all { it.mode == ItemMode.MANUAL && it.durationSec == null })
    }

    @Test
    fun `the workout ends on the bonus flyer with no trailing rest`() {
        val last = segments.last()
        assertEquals("Flyer", last.exerciseName)
        assertEquals(SegmentKind.WORK, last.kind)
        assertEquals(ItemMode.MANUAL, last.mode)
    }

    @Test
    fun `verified demo urls survive compilation`() {
        val shuffle = segments.first { it.exerciseName == "Lateral Shuffle" }
        assertEquals("https://www.youtube.com/shorts/iQjCIghvheI", shuffle.demoUrl)
    }

    @Test
    fun `court speed estimates to a plausible session length`() {
        // 180s warm prep + 675s block A + 420s block B + 20s bonus = 1295s ≈ 22 min.
        // Asserted as a band, not an exact figure, so tuning the manual constants in
        // DurationEstimator does not break this test.
        val minutes = DurationEstimator.estimateMinutes(segments)
        assertTrue(minutes in 20..35, "expected 20-35 min, got $minutes")
    }

    @Test
    fun `court speed summarises to the meta line Home will render`() {
        val summary = WorkoutSummarizer.summarize(WorkoutSeed.courtSpeed, library)
        assertEquals(10, summary.exerciseCount)
        assertEquals(22, summary.estimatedMinutes)
        assertEquals("10 exercises · 22 min · HIIT", summary.metaLine)
    }
}
```

- [ ] **Step 5: Repoint the duration estimator test**

`DurationEstimatorTest` imported the deleted fixture. Its last test now duplicates one in
`WorkoutSeedTest`, so delete it rather than repointing it — the estimator's own per-mode tests
stay, and the full-workout assertion lives with the workout.

In `app/src/test/java/com/ottotech/workoutbuilder/domain/DurationEstimatorTest.kt`:

1. Delete the import line `import com.ottotech.workoutbuilder.fixtures.CourtSpeed`
2. Delete the import line `import kotlin.test.assertTrue`
3. Delete the entire final test, from `@Test` above
   ``fun `court speed estimates to a plausible session length`()`` through its closing brace

The file must end with the `empty list is zero` test and the class's closing brace, and now has
6 tests.

- [ ] **Step 6: Add hydrated observation and sample seeding to the repository**

In `app/src/main/java/com/ottotech/workoutbuilder/data/WorkoutRepository.kt`:

Replace the `observeAll()` function and its doc comment with:

```kotlin
    /**
     * Fully hydrated workouts, sections and items.
     *
     * Home needs section contents to count exercises and estimate duration, so headers
     * alone are not enough. Re-loading every workout on each emission is cheap at this
     * scale — a personal app holds a handful of workouts, not thousands.
     *
     * Note the invalidation boundary: Room re-emits when the `workouts` table changes, not
     * when `sections` or `workout_items` change on their own. That is correct here because
     * [save] always writes the workout row too, so every edit that matters trips it.
     */
    fun observeAll(): Flow<List<Workout>> =
        dao.observeWorkouts().map { rows -> rows.mapNotNull { load(it.id) } }
```

Add these imports at the top of the file:

```kotlin
import com.ottotech.workoutbuilder.data.seed.WorkoutSeed
```

And add this function immediately after `save`:

```kotlin
    /**
     * Writes the sample workouts on first launch only.
     *
     * Gated on a preference rather than on "is the table empty" so that deleting the sample
     * workout keeps it deleted instead of resurrecting it on the next cold start.
     */
    suspend fun seedSampleIfNeeded(prefs: AppPrefs) {
        if (prefs.sampleWorkoutSeeded) return
        WorkoutSeed.workouts.forEach { save(it) }
        prefs.sampleWorkoutSeeded = true
    }
```

- [ ] **Step 7: Seed the sample workout at launch**

In `MainActivity.kt`, inside the `withContext(Dispatchers.IO) { ... }` block, add a second line
below the existing exercise seeding so it reads:

```kotlin
            withContext(Dispatchers.IO) {
                // Order matters: the sample workout references seeded exercise ids.
                app.exercises.seedIfNeeded()
                app.workouts.seedSampleIfNeeded(app.prefs)
            }
```

- [ ] **Step 8: Run the whole suite**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest
```

Expected: **60 tests, 0 failures** across seven classes (`CourtSpeedFixtureTest` is gone) —
`domain.SegmentCompilerTest` 12, `domain.DomainPurityTest` 1, `domain.DurationEstimatorTest` 6,
`domain.ExerciseFilterTest` 10, `domain.WorkoutSummaryTest` 12, `data.ExerciseSeedTest` 9,
`data.WorkoutSeedTest` 10.

If `every exercise the seeded workout references exists in the seeded library` fails, Step 1's
`flyer` row is missing or misspelled.

- [ ] **Step 9: Commit**

```bash
git add -A app/src/main app/src/test
git commit -m "feat: ship Court Speed as seed data and hydrate workout observation"
```

---

### Task 4: Home screen

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/components/Controls.kt`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/AppNav.kt`

**Interfaces:**
- Consumes: `WorkoutRepository.observeAll`, `ExerciseRepository.observeAll`,
  `WorkoutSummarizer.summarizeAll`, `UpNext.select`, `AppScaffold`, `Tab`, `WorkoutApp`
- Produces:
  - `HairlineCard`, `InverseButton`, `SectionLabel`, `AppTextField`, `Chip` in
    `ui/components/Controls.kt`
  - `HomeUiState(loading, upNext, others)` with `val isEmpty: Boolean`, and `HomeViewModel` with
    `val state: StateFlow<HomeUiState>` and `companion object { val Factory }`
  - `HomeScreen(onStartWorkout: (String) -> Unit, onBuild: () -> Unit, viewModel: HomeViewModel)`

- [ ] **Step 1: Create the shared controls**

These five are used by Home, Library, both sheets and both detail screens. Everything Home-only
(hero card, workout row) stays in `HomeScreen.kt`; everything Library-only stays in
`LibraryScreen.kt`.

`app/src/main/java/com/ottotech/workoutbuilder/ui/components/Controls.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

/** A flat card with a hairline border. No shadows anywhere in this app — spec §5. */
@Composable
fun HairlineCard(
    modifier: Modifier = Modifier,
    radius: Dp = Dimens.radiusCard,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(Dimens.hairline, colors.border), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

/**
 * The app's primary action: a filled block on `surfaceInverse` with `onSurfaceInverse`
 * text. Under the mono accent rule this *is* the accent — maximum contrast against the
 * page — so there is no separate accent colour to pass in.
 */
@Composable
fun InverseButton(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = Dimens.buildButtonHeight,
    leadingGlyph: String? = null,
    style: TextStyle = AppType.buildButton,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(Dimens.radiusButton))
            .background(colors.surfaceInverse)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingGlyph != null) {
            Text(leadingGlyph, style = style, color = colors.onSurfaceInverse)
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = style, color = colors.onSurfaceInverse)
    }
}

/** `BROWSE BY MUSCLE`, `SETS`, `FILTERS` — spec §5 section label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = AppType.sectionLabel,
        color = LocalAppColors.current.onBackground,
        modifier = modifier,
    )
}

/**
 * Filter and tag chip. Selected chips invert — the mono accent rule again.
 *
 * [onClick] is nullable because the exercise detail screen displays tags as chips with no
 * interaction; a chip that ripples and does nothing is a small lie about what it does.
 */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val background = if (selected) colors.surfaceInverse else colors.surface
    val foreground = if (selected) colors.onSurfaceInverse else colors.onSurface
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusChip))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(text, style = AppType.chip, color = foreground)
    }
}

/**
 * Text input styled as a `surface` block rather than a Material text field, which would
 * bring its own container, label slot and indicator line to fight back.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    textStyle: TextStyle = AppType.heroMeta,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .heightIn(min = Dimens.searchFieldHeight)
            .clip(RoundedCornerShape(Dimens.radiusButton))
            .background(colors.surface)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = textStyle, color = colors.textMuted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = textStyle.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.onSurface),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```

- [ ] **Step 2: Write the Home ViewModel**

`app/src/main/java/com/ottotech/workoutbuilder/ui/home/HomeViewModel.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ottotech.workoutbuilder.WorkoutApp
import com.ottotech.workoutbuilder.data.ExerciseRepository
import com.ottotech.workoutbuilder.data.WorkoutRepository
import com.ottotech.workoutbuilder.domain.UpNext
import com.ottotech.workoutbuilder.domain.WorkoutSummarizer
import com.ottotech.workoutbuilder.domain.WorkoutSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    val upNext: WorkoutSummary? = null,
    val others: List<WorkoutSummary> = emptyList(),
) {
    val isEmpty: Boolean get() = !loading && upNext == null
}

/**
 * Thin by design. Every decision it appears to make — summarising, choosing "Up next" —
 * is a pure function in `domain/` with its own unit tests; this class only joins two
 * flows and holds the result.
 */
class HomeViewModel(
    workouts: WorkoutRepository,
    exercises: ExerciseRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> =
        combine(workouts.observeAll(), exercises.observeAll()) { workoutList, exerciseList ->
            val library = exerciseList.associateBy { it.id }
            val summaries = WorkoutSummarizer.summarizeAll(workoutList, library)
            val up = UpNext.select(summaries)
            HomeUiState(
                loading = false,
                upNext = up,
                others = summaries.filter { it.id != up?.id },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkoutApp
                HomeViewModel(app.workouts, app.exercises)
            }
        }
    }
}
```

> If `androidx.lifecycle.viewmodel.initializer` / `viewModelFactory` do not resolve, the
> `lifecycle-viewmodel-compose` dependency from Task 1 Step 2 is missing. Do not work around it
> by making the ViewModel take no constructor arguments.

- [ ] **Step 3: Write the Home screen**

`app/src/main/java/com/ottotech/workoutbuilder/ui/home/HomeScreen.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ottotech.workoutbuilder.domain.WorkoutSummary
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.components.InverseButton
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    onStartWorkout: (String) -> Unit,
    onBuild: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val upNext = state.upNext

    Column(Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Dimens.screenPadding,
                end = Dimens.screenPadding,
                top = 14.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            item { Header() }

            // While loading, the header stands alone rather than flashing an empty state.
            // In practice this is at most one frame — MainActivity gates on seeding.
            if (state.isEmpty) {
                item { EmptyHero() }
            } else if (upNext != null) {
                item { UpNextHero(upNext, onStart = { onStartWorkout(upNext.id) }) }
                items(state.others, key = { it.id }) { summary ->
                    WorkoutRow(summary, onClick = { onStartWorkout(summary.id) })
                }
            }
        }

        // Pinned above the bottom nav rather than scrolling away — the handoff calls it
        // "sticky-feeling", and it is the screen's only creative action.
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
        Spacer(Modifier.height(6.dp))
        Text("YOUR\nWORKOUTS", style = AppType.screenTitle, color = colors.onBackground)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun UpNextHero(summary: WorkoutSummary, onStart: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusHero))
            .background(colors.surfaceInverse)
            .padding(Dimens.heroPadding),
    ) {
        // Mono accent: on an inverse surface the accent is onSurfaceInverse.
        Text("UP NEXT", style = AppType.heroLabel, color = colors.onSurfaceInverse)
        Spacer(Modifier.height(8.dp))
        Text(summary.name, style = AppType.heroName, color = colors.onSurfaceInverse)
        Spacer(Modifier.height(4.dp))
        Text(summary.metaLine, style = AppType.heroMeta, color = colors.textMuted)
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.primaryButtonHeight)
                .clip(RoundedCornerShape(Dimens.radiusButton))
                .background(colors.onSurfaceInverse)
                .clickable(onClick = onStart),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Start workout ›", style = AppType.primaryButton, color = colors.surfaceInverse)
        }
    }
}

/**
 * Spec §6.1: the empty state uses the hero treatment so a fresh install looks designed
 * rather than broken.
 */
@Composable
private fun EmptyHero() {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusHero))
            .background(colors.surfaceInverse)
            .padding(Dimens.heroPadding),
    ) {
        Text("NOTHING YET", style = AppType.heroLabel, color = colors.onSurfaceInverse)
        Spacer(Modifier.height(8.dp))
        Text(
            "Nothing here yet. Build one from the library, or let the Coach draft one.",
            style = AppType.heroMeta,
            color = colors.onSurfaceInverse,
        )
    }
}

@Composable
private fun WorkoutRow(summary: WorkoutSummary, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    HairlineCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.badgeSize)
                    .clip(RoundedCornerShape(Dimens.radiusChip))
                    .background(colors.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    summary.estimatedMinutes.toString(),
                    style = AppType.cardTitle,
                    color = colors.onSurface,
                )
            }
            Column {
                Text(summary.name, style = AppType.cardTitle, color = colors.onBackground)
                Spacer(Modifier.height(2.dp))
                Text(summary.metaLine, style = AppType.cardMeta, color = colors.textMuted)
            }
        }
    }
}
```

- [ ] **Step 4: Wire Home into the nav graph**

In `ui/AppNav.kt`, replace the `composable(Routes.HOME) { ... }` block with:

```kotlin
        composable(Routes.HOME) {
            AppScaffold(tab = Tab.HOME, onSelectTab = { if (it == Tab.LIBRARY) nav.switchTab(Routes.LIBRARY) }) {
                HomeScreen(
                    // TODO(Plan 1D): route to the execution screen.
                    onStartWorkout = { },
                    // TODO(Plan 1C): raise the Build sheet. Wired in Task 5.
                    onBuild = { },
                )
            }
        }
```

and add the import:

```kotlin
import com.ottotech.workoutbuilder.ui.home.HomeScreen
```

- [ ] **Step 5: Build, install and verify on the emulator**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:installDebug
```

Expected on a **fresh install** (uninstall first if the app is already there:
`/c/Users/ottol/AppData/Local/Android/Sdk/platform-tools/adb.exe uninstall com.ottotech.workoutbuilder`):

- Overline reads today's date in the form `TUE · JUL 21`
- Title `YOUR WORKOUTS` on two lines, heavy and tight
- A black hero card labelled `UP NEXT` with `Court Speed` and the meta line
  **`10 exercises · 22 min · HIIT`**, and a white `Start workout ›` button (tapping it does
  nothing yet — that is Plan 1D)
- No workout rows below it, because Court Speed is the only workout
- A black `+ BUILD A WORKOUT` button pinned above the bottom nav (tapping it does nothing yet —
  Task 5 wires it)
- Switch the emulator to dark mode (Settings → Display → Dark theme). The page goes near-black,
  the hero card goes **off-white with black text**, and every border stays visible. Nothing
  should disappear or turn into black-on-black.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/ui/
git commit -m "feat: add Home screen with up-next hero, workout rows and empty state"
```

---

### Task 5: Build sheet

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/home/BuildSheet.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/AppNav.kt`

**Interfaces:**
- Consumes: `HairlineCard`, `AppType`, `Dimens`, `LocalAppColors`
- Produces: `BuildSheet(onDismiss: () -> Unit, onBuildYourself: () -> Unit, onAskCoach: () -> Unit)`

- [ ] **Step 1: Write the sheet**

`app/src/main/java/com/ottotech/workoutbuilder/ui/home/BuildSheet.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.components.SectionLabel
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

/**
 * Spec §6.2. Two equal-weight bordered cards — building it yourself and asking the Coach
 * are two ways to do the same thing, so neither gets the filled treatment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildSheet(
    onDismiss: () -> Unit,
    onBuildYourself: () -> Unit,
    onAskCoach: () -> Unit,
) {
    val colors = LocalAppColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = colors.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Dimens.screenPadding)
                .padding(top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            SectionLabel("Build a workout")
            Spacer(Modifier.height(2.dp))
            Option(
                title = "Build it yourself",
                subtitle = "Pick exercises from the library",
                onClick = onBuildYourself,
            )
            Option(
                title = "Ask the Coach",
                subtitle = "Describe your goal, get a workout drafted",
                onClick = onAskCoach,
            )
        }
    }
}

@Composable
private fun Option(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    HairlineCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(Dimens.cardPadding)) {
            Text(title, style = AppType.cardTitle, color = colors.onBackground)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = AppType.cardMeta, color = colors.textMuted)
        }
    }
}
```

- [ ] **Step 2: Raise it from the Build button**

In `ui/AppNav.kt`, replace the whole `composable(Routes.HOME) { ... }` block with:

```kotlin
        composable(Routes.HOME) {
            // The two Build destinations do not exist yet; the sheet is the seam they
            // plug into. Left as no-ops rather than placeholder screens.
            var showBuildSheet by rememberSaveable { mutableStateOf(false) }

            AppScaffold(tab = Tab.HOME, onSelectTab = { if (it == Tab.LIBRARY) nav.switchTab(Routes.LIBRARY) }) {
                HomeScreen(
                    // TODO(Plan 1D): route to the execution screen.
                    onStartWorkout = { },
                    onBuild = { showBuildSheet = true },
                )
            }

            if (showBuildSheet) {
                BuildSheet(
                    onDismiss = { showBuildSheet = false },
                    // TODO(Plan 1C): navigate to the Builder.
                    onBuildYourself = { showBuildSheet = false },
                    // TODO(Plan 2): navigate to the Coach.
                    onAskCoach = { showBuildSheet = false },
                )
            }
        }
```

and add these imports:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ottotech.workoutbuilder.ui.home.BuildSheet
```

- [ ] **Step 3: Build, install and verify**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:installDebug
```

Expected:
- Tapping `+ BUILD A WORKOUT` slides up a bottom sheet with the label `BUILD A WORKOUT` and two
  bordered cards
- Swiping the sheet down dismisses it; tapping the dimmed area above it dismisses it
- Tapping either card dismisses it (their destinations arrive in Plans 1C and 2)
- The sheet background matches the page background in both light and dark mode

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/ui/
git commit -m "feat: add build sheet forking to manual build or coach"
```

---

### Task 6: Filter state transitions

The Library's chip row, Filters sheet and category grid all mutate or read one `FilterState`.
Those transitions are pure and get tested here, before any of the three screens exist.

**Files:**
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/domain/ExerciseFilter.kt`
- Test: `app/src/test/java/com/ottotech/workoutbuilder/domain/FilterStateTest.kt`

**Interfaces:**
- Consumes: `FilterState`, `Facet`, `Muscle`, `Exercise`, `ExerciseFilter.apply`
- Produces:
  - `FilterState.toggle(facet: Facet, value: String): FilterState`
  - `FilterState.selectOnly(facet: Facet, value: String?): FilterState`
  - `FilterState.clearFacets(): FilterState`
  - `FilterState.isSelected(facet: Facet, value: String): Boolean`
  - `ExerciseFilter.categoriesIn(exercises: List<Exercise>): List<Muscle>`
  - `ExerciseFilter.forMuscle(exercises: List<Exercise>, state: FilterState, muscle: Muscle): List<Exercise>`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/ottotech/workoutbuilder/domain/FilterStateTest.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterStateTest {

    private fun ex(id: String, muscle: Muscle, vararg tags: Tag) =
        Exercise(id = id, name = id, primaryMuscle = muscle, tags = tags.toSet(), demoQuery = "$id form")

    private val bench = ex("bench", Muscle.CHEST, Tag(Facet.TYPE, "Strength"), Tag(Facet.EQUIPMENT, "Barbell"))
    private val pushUp = ex("push-up", Muscle.CHEST, Tag(Facet.TYPE, "Strength"), Tag(Facet.EQUIPMENT, "Bodyweight"))
    private val swing = ex("swing", Muscle.LEGS, Tag(Facet.TYPE, "HIIT"), Tag(Facet.EQUIPMENT, "Kettlebell"))
    private val all = listOf(bench, pushUp, swing)

    // ---- toggle ----

    @Test
    fun `toggle adds a value that is not selected`() {
        val out = FilterState().toggle(Facet.EQUIPMENT, "Barbell")
        assertEquals(setOf("Barbell"), out.facets[Facet.EQUIPMENT])
    }

    @Test
    fun `toggle accumulates within a facet`() {
        val out = FilterState()
            .toggle(Facet.EQUIPMENT, "Barbell")
            .toggle(Facet.EQUIPMENT, "Kettlebell")
        assertEquals(setOf("Barbell", "Kettlebell"), out.facets[Facet.EQUIPMENT])
    }

    @Test
    fun `toggle removes a value that is selected`() {
        val out = FilterState()
            .toggle(Facet.EQUIPMENT, "Barbell")
            .toggle(Facet.EQUIPMENT, "Kettlebell")
            .toggle(Facet.EQUIPMENT, "Barbell")
        assertEquals(setOf("Kettlebell"), out.facets[Facet.EQUIPMENT])
    }

    @Test
    fun `toggling the last value drops the facet key entirely`() {
        // An empty set applies no constraint anyway, but leaving the key behind would
        // make activeFacetCount and the Filters badge disagree with the UI.
        val out = FilterState().toggle(Facet.TYPE, "HIIT").toggle(Facet.TYPE, "HIIT")
        assertTrue(out.facets.isEmpty())
        assertEquals(0, out.activeFacetCount)
        assertTrue(out.isEmpty)
    }

    @Test
    fun `toggle leaves other facets alone`() {
        val out = FilterState()
            .toggle(Facet.TYPE, "HIIT")
            .toggle(Facet.EQUIPMENT, "Kettlebell")
        assertEquals(setOf("HIIT"), out.facets[Facet.TYPE])
        assertEquals(setOf("Kettlebell"), out.facets[Facet.EQUIPMENT])
        assertEquals(2, out.activeFacetCount)
    }

    // ---- selectOnly ----

    @Test
    fun `selectOnly replaces the whole facet selection`() {
        val out = FilterState()
            .toggle(Facet.TYPE, "HIIT")
            .toggle(Facet.TYPE, "Mobility")
            .selectOnly(Facet.TYPE, "Strength")
        assertEquals(setOf("Strength"), out.facets[Facet.TYPE])
    }

    @Test
    fun `selectOnly with null clears the facet - this is the All chip`() {
        val out = FilterState().toggle(Facet.TYPE, "HIIT").selectOnly(Facet.TYPE, null)
        assertTrue(out.facets[Facet.TYPE].isNullOrEmpty())
    }

    @Test
    fun `the All chip does not disturb other facets`() {
        val out = FilterState()
            .toggle(Facet.TYPE, "HIIT")
            .toggle(Facet.EQUIPMENT, "Kettlebell")
            .selectOnly(Facet.TYPE, null)
        assertEquals(setOf("Kettlebell"), out.facets[Facet.EQUIPMENT])
    }

    // ---- clearFacets ----

    @Test
    fun `clearFacets empties every facet but keeps the search query`() {
        // Clear all lives inside the Filters sheet; search is a control outside it.
        val out = FilterState(query = "kettlebell")
            .toggle(Facet.TYPE, "HIIT")
            .toggle(Facet.PATTERN, "Hinge")
            .clearFacets()
        assertEquals(0, out.activeFacetCount)
        assertEquals("kettlebell", out.query)
    }

    // ---- isSelected ----

    @Test
    fun `isSelected reports chip state`() {
        val state = FilterState().toggle(Facet.TYPE, "HIIT")
        assertTrue(state.isSelected(Facet.TYPE, "HIIT"))
        assertFalse(state.isSelected(Facet.TYPE, "Strength"))
        assertFalse(state.isSelected(Facet.EQUIPMENT, "HIIT"))
    }

    // ---- categoriesIn ----

    @Test
    fun `categories are the muscles the library actually has, in enum order`() {
        assertEquals(listOf(Muscle.CHEST, Muscle.LEGS), ExerciseFilter.categoriesIn(all))
    }

    @Test
    fun `a muscle with no exercises is not a category at all`() {
        // Distinct from a category that filters down to zero, which stays on screen dimmed.
        assertFalse(Muscle.MOBILITY in ExerciseFilter.categoriesIn(all))
    }

    @Test
    fun `categories of an empty library are empty`() {
        assertEquals(emptyList(), ExerciseFilter.categoriesIn(emptyList()))
    }

    // ---- forMuscle ----

    @Test
    fun `forMuscle narrows the active filter to one home category`() {
        val out = ExerciseFilter.forMuscle(
            all,
            FilterState(facets = mapOf(Facet.EQUIPMENT to setOf("Bodyweight"))),
            Muscle.CHEST,
        )
        assertEquals(listOf(pushUp), out)
    }

    @Test
    fun `forMuscle with no filter returns the whole category`() {
        assertEquals(listOf(bench, pushUp), ExerciseFilter.forMuscle(all, FilterState(), Muscle.CHEST))
    }

    @Test
    fun `forMuscle can return nothing without error`() {
        val out = ExerciseFilter.forMuscle(
            all,
            FilterState(facets = mapOf(Facet.TYPE to setOf("HIIT"))),
            Muscle.CHEST,
        )
        assertEquals(emptyList(), out)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest --tests "*FilterStateTest*"
```

Expected: FAIL — `Unresolved reference: toggle`

- [ ] **Step 3: Implement**

Append to `app/src/main/java/com/ottotech/workoutbuilder/domain/ExerciseFilter.kt`, **outside**
the `ExerciseFilter` object, at the end of the file:

```kotlin
// ---- FilterState transitions ----
//
// The chip row and the Filters sheet edit the same state, so these live here rather than
// in either screen. Extensions on an immutable data class: every call returns a new state,
// which is what a Compose StateFlow wants.

/** Multi-select within a facet — the Filters sheet's chips. */
fun FilterState.toggle(facet: Facet, value: String): FilterState {
    val current = facets[facet].orEmpty()
    val next = if (value in current) current - value else current + value
    // Drop the key rather than leaving an empty set, so activeFacetCount — which drives
    // the Filters badge — never counts a facet the user has fully deselected.
    return copy(facets = if (next.isEmpty()) facets - facet else facets + (facet to next))
}

/**
 * Single-select within a facet — the Library's TYPE chip row. Passing null is the `All`
 * chip: it clears TYPE and nothing else (spec §6.3a).
 */
fun FilterState.selectOnly(facet: Facet, value: String?): FilterState =
    if (value == null) copy(facets = facets - facet)
    else copy(facets = facets + (facet to setOf(value)))

/**
 * `Clear all` in the Filters sheet. The search query is a control outside the sheet and
 * is deliberately left alone.
 */
fun FilterState.clearFacets(): FilterState = copy(facets = emptyMap())

fun FilterState.isSelected(facet: Facet, value: String): Boolean =
    value in facets[facet].orEmpty()
```

And add these two functions **inside** the `ExerciseFilter` object, after `countsByMuscle`:

```kotlin
    /**
     * The categories the grid shows. A muscle with no exercises at all is not a category —
     * it would be a permanently dead tile. This is distinct from a category that the active
     * filter narrows to zero, which stays on screen dimmed and untappable (spec §6.3).
     */
    fun categoriesIn(exercises: List<Exercise>): List<Muscle> =
        Muscle.entries.filter { muscle -> exercises.any { it.primaryMuscle == muscle } }

    /** The category screen: the active filter, narrowed to one home category. */
    fun forMuscle(exercises: List<Exercise>, state: FilterState, muscle: Muscle): List<Exercise> =
        apply(exercises, state).filter { it.primaryMuscle == muscle }
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "*FilterStateTest*" --tests "*ExerciseFilterTest*" --tests "*DomainPurityTest*"
```

Expected: PASS, 27 tests (16 filter state + 10 exercise filter + 1 purity)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/domain/ExerciseFilter.kt \
  app/src/test/java/com/ottotech/workoutbuilder/domain/FilterStateTest.kt
git commit -m "feat: add filter state transitions and category helpers"
```

---

### Task 7: Library screen

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/library/LibraryViewModel.kt`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/AppNav.kt`

**Interfaces:**
- Consumes: `ExerciseRepository.observeAll`, `ExerciseFilter.apply`,
  `ExerciseFilter.countsByMuscle`, `ExerciseFilter.categoriesIn`, `FilterState` +
  its transitions, `AppScaffold`, `Chip`, `AppTextField`, `SectionLabel`, `SearchGlyph`
- Produces:
  - `LibraryUiState(exercises, filter, categories, counts, matchCount)`
  - `LibraryViewModel` with `state: StateFlow<LibraryUiState>`, `onQueryChange(String)`,
    `onSelectType(String?)`, `onToggle(Facet, String)`, `onClearFacets()`, and
    `companion object { val Factory }`
  - `LibraryScreen(viewModel: LibraryViewModel, onOpenCategory: (Muscle) -> Unit, onOpenFilters: () -> Unit)`

> **The Library ViewModel is shared, not per-screen.** The category list and exercise detail
> screens must see the same filter the Library screen set, so they scope their `viewModel()`
> call to the nav graph rather than to their own destination. Task 9 does this; the factory
> defined here is what makes it possible.

- [ ] **Step 1: Write the Library ViewModel**

`app/src/main/java/com/ottotech/workoutbuilder/ui/library/LibraryViewModel.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ottotech.workoutbuilder.WorkoutApp
import com.ottotech.workoutbuilder.data.ExerciseRepository
import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.ExerciseFilter
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.FilterState
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.domain.clearFacets
import com.ottotech.workoutbuilder.domain.selectOnly
import com.ottotech.workoutbuilder.domain.toggle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    /** The whole library, unfiltered — the category and detail screens filter it themselves. */
    val exercises: List<Exercise> = emptyList(),
    val filter: FilterState = FilterState(),
    /** Muscles the library has at all, in enum order. */
    val categories: List<Muscle> = emptyList(),
    /** Per-category counts **after** the active filter — spec §6.3. */
    val counts: Map<Muscle, Int> = emptyMap(),
    /** Total exercises matching the filter, for the sheet's `SHOW N EXERCISES` button. */
    val matchCount: Int = 0,
) {
    /** Every category filtered down to nothing — the grid is replaced by an empty state. */
    val allCategoriesEmpty: Boolean
        get() = categories.isNotEmpty() && categories.all { (counts[it] ?: 0) == 0 }

    fun exercisesIn(muscle: Muscle): List<Exercise> =
        ExerciseFilter.forMuscle(exercises, filter, muscle)

    fun exercise(id: String): Exercise? = exercises.firstOrNull { it.id == id }
}

/**
 * Shared by the Library grid, a category's exercise list and the exercise detail screen,
 * so the filter the user set on the grid is still in force when they drill in (spec §6.3).
 */
class LibraryViewModel(
    private val exercises: ExerciseRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(FilterState())

    val state: StateFlow<LibraryUiState> =
        combine(exercises.observeAll(), filter) { library, activeFilter ->
            LibraryUiState(
                exercises = library,
                filter = activeFilter,
                categories = ExerciseFilter.categoriesIn(library),
                counts = ExerciseFilter.countsByMuscle(library, activeFilter),
                matchCount = ExerciseFilter.apply(library, activeFilter).size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onQueryChange(query: String) {
        filter.value = filter.value.copy(query = query)
    }

    /** The TYPE chip row. Null is the `All` chip. */
    fun onSelectType(value: String?) {
        filter.value = filter.value.selectOnly(Facet.TYPE, value)
    }

    /** The Filters sheet's chips. */
    fun onToggle(facet: Facet, value: String) {
        filter.value = filter.value.toggle(facet, value)
    }

    fun onClearFacets() {
        filter.value = filter.value.clearFacets()
    }

    /** Exercise detail: paste a specific demo URL, or clear it back to search. */
    fun onSetDemoUrl(exerciseId: String, url: String?) {
        viewModelScope.launch {
            exercises.setDemoUrl(exerciseId, url?.takeIf { it.isNotBlank() })
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkoutApp
                LibraryViewModel(app.exercises)
            }
        }
    }
}
```

- [ ] **Step 2: Add the category tile radius to Dimens**

The handoff gives category tiles a 15dp radius, one step up from the 14dp card. Add it to
`app/src/main/java/com/ottotech/workoutbuilder/ui/theme/Dimens.kt` immediately after
`radiusCard` rather than writing `radiusCard + 1.dp` at the call site:

```kotlin
    val radiusTile = 15.dp
```

- [ ] **Step 3: Write the Library screen**

Note the whole screen is one `LazyVerticalGrid` with full-width header items. A lazy grid nested
inside a `verticalScroll` column has unbounded height and crashes at runtime; spanning header
items is the way to mix a header with a grid.

`app/src/main/java/com/ottotech/workoutbuilder/ui/library/LibraryScreen.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.domain.TagVocabulary
import com.ottotech.workoutbuilder.ui.components.AppTextField
import com.ottotech.workoutbuilder.ui.components.Chip
import com.ottotech.workoutbuilder.ui.components.SearchGlyph
import com.ottotech.workoutbuilder.ui.components.SectionLabel
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenCategory: (Muscle) -> Unit,
    onOpenFilters: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current
    val selectedType = state.filter.facets[Facet.TYPE]?.firstOrNull()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = Dimens.screenPadding,
            end = Dimens.screenPadding,
            top = 14.dp,
            bottom = 20.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(Dimens.gap),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("LIBRARY", style = AppType.libraryTitle, color = colors.onBackground)
                Spacer(Modifier.height(12.dp))
                AppTextField(
                    value = state.filter.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = "Search exercises",
                    leading = { SearchGlyph(color = colors.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                // `All` is the absence of a TYPE selection, not a value in the vocabulary.
                Chip("All", selected = selectedType == null) { viewModel.onSelectType(null) }
                TagVocabulary.TYPE.forEach { type ->
                    Chip(type, selected = selectedType == type) { viewModel.onSelectType(type) }
                }
                Spacer(Modifier.padding(start = 2.dp))
                FiltersChip(activeCount = state.filter.activeFacetCount, onClick = onOpenFilters)
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionLabel("Browse by muscle", Modifier.padding(top = 6.dp))
        }

        if (state.allCategoriesEmpty) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyGrid(onClear = viewModel::onClearFacets)
            }
        } else {
            itemsIndexed(state.categories, key = { _, muscle -> muscle.name }) { index, muscle ->
                CategoryTile(
                    muscle = muscle,
                    count = state.counts[muscle] ?: 0,
                    featured = index == 0,
                    onClick = { onOpenCategory(muscle) },
                )
            }
        }
    }
}

@Composable
private fun FiltersChip(activeCount: Int, onClick: () -> Unit) {
    val label = if (activeCount > 0) "Filters · $activeCount" else "Filters"
    Chip(text = label, selected = activeCount > 0, onClick = onClick)
}

/**
 * Spec §6.3. The first tile is featured on `surfaceInverse`; a category the filter has
 * narrowed to zero renders at 40% and is not tappable.
 */
@Composable
private fun CategoryTile(muscle: Muscle, count: Int, featured: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val empty = count == 0
    val alpha = if (empty) 0.4f else 1f
    val background = if (featured) colors.surfaceInverse else colors.surface
    val nameColor = if (featured) colors.onSurfaceInverse else colors.onSurface
    // On the featured tile both name and count would be pure white. Weight alone is not
    // enough separation, so the count drops to 70% — spec §5.
    val countColor = if (featured) colors.onSurfaceInverse.copy(alpha = 0.7f) else colors.textMuted

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.categoryTileMinHeight)
            .clip(RoundedCornerShape(Dimens.radiusTile))
            .background(background.copy(alpha = alpha))
            .clickable(enabled = !empty, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(muscle.label, style = AppType.tileName, color = nameColor.copy(alpha = alpha))
        Text("$count", style = AppType.tileCount, color = countColor.copy(alpha = alpha))
    }
}

@Composable
private fun EmptyGrid(onClear: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Nothing matches those filters.", style = AppType.cardMeta, color = colors.textMuted)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.radiusChip))
                .background(colors.surfaceInverse)
                .clickable(onClick = onClear)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Clear filters", style = AppType.chip, color = colors.onSurfaceInverse)
        }
    }
}
```

- [ ] **Step 4: Wire Library into the nav graph**

In `ui/AppNav.kt`, replace the `composable(Routes.LIBRARY) { ... }` block with:

```kotlin
        composable(Routes.LIBRARY) {
            val libraryVm: LibraryViewModel = viewModel(
                viewModelStoreOwner = nav.getBackStackEntry(Routes.LIBRARY),
                factory = LibraryViewModel.Factory,
            )
            AppScaffold(tab = Tab.LIBRARY, onSelectTab = { if (it == Tab.HOME) nav.switchTab(Routes.HOME) }) {
                LibraryScreen(
                    viewModel = libraryVm,
                    onOpenCategory = { nav.navigate(Routes.category(it)) },
                    // Wired in Task 8.
                    onOpenFilters = { },
                )
            }
        }
```

and add these imports:

```kotlin
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ottotech.workoutbuilder.ui.library.LibraryScreen
import com.ottotech.workoutbuilder.ui.library.LibraryViewModel
```

- [ ] **Step 5: Build, install and verify**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:installDebug
```

Expected on the Library tab:
- Title `LIBRARY`, then a grey search field reading `Search exercises` with a small circle icon
- A chip row: `All` (selected/inverted), `Strength`, `HIIT`, `Mobility`, then `Filters`
- `BROWSE BY MUSCLE`, then a 2-column grid whose **first tile (Chest) is black/inverted** and
  the rest grey
- **Counts, in this order, summing to 101:** Chest 12, Back 14, Legs 20, Shoulders 10, Arms 9,
  Core 12, Cardio 9, Mobility 15. There is **no Full body tile** — nothing in the library has it
  as a primary muscle, so it is not a category. (These come from the seed's primary-muscle
  distribution; if a count is off by one, `flyer` from Task 3 is missing or landed in the wrong
  block.)
- Tapping `HIIT` recomputes every count downward, and at least one category drops to 0 and
  renders dimmed and untappable; tapping `All` restores them
- Typing `kettlebell` in the search field narrows the counts without opening a filter
- Typing `zzzz` empties every category and the grid is replaced by
  `Nothing matches those filters.` with a `Clear filters` button. Note this clears **facets
  only** — the search box still says `zzzz` and must be cleared by hand, which is the intended
  behaviour, not a bug
- Tapping a category navigates to a screen showing the muscle's name (the stub from Task 1);
  Back returns to the Library with the filter and search text still in place

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/ui/
git commit -m "feat: add library screen with search, type chips and live category counts"
```

---

### Task 8: Filters sheet

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/library/FiltersSheet.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/AppNav.kt`

**Interfaces:**
- Consumes: `LibraryViewModel` (`state`, `onToggle`, `onClearFacets`), `TagVocabulary`,
  `FilterState.isSelected`, `Chip`, `SectionLabel`, `InverseButton`
- Produces: `FiltersSheet(viewModel: LibraryViewModel, onDismiss: () -> Unit)`

- [ ] **Step 1: Write the sheet**

`app/src/main/java/com/ottotech/workoutbuilder/ui/library/FiltersSheet.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.TagVocabulary
import com.ottotech.workoutbuilder.domain.isSelected
import com.ottotech.workoutbuilder.ui.components.Chip
import com.ottotech.workoutbuilder.ui.components.InverseButton
import com.ottotech.workoutbuilder.ui.components.SectionLabel
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

/**
 * Spec §6.3a. Changes apply live, so dismissing the sheet keeps them — there is no Cancel.
 * The pinned button is a confirmation of what you already did, not a commit step.
 *
 * TYPE appears here as well as in the Library's chip row; both edit the same FilterState,
 * so `Clear all` genuinely clears everything. The one difference is multiplicity: the chip
 * row single-selects, this sheet multi-selects.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FiltersSheet(viewModel: LibraryViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Dimens.screenPadding)
                .padding(top = 20.dp, bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("Filters")
                Text(
                    text = "Clear all",
                    style = AppType.chip,
                    color = colors.textMuted,
                    modifier = Modifier.clickable(onClick = viewModel::onClearFacets),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                FacetGroup("Type", Facet.TYPE, TagVocabulary.TYPE, viewModel, state)
                FacetGroup("Equipment", Facet.EQUIPMENT, TagVocabulary.EQUIPMENT, viewModel, state)
                FacetGroup("Pattern", Facet.PATTERN, TagVocabulary.PATTERN, viewModel, state)
                // The MUSCLE facet is secondary muscles, so it reads better as "also hits".
                FacetGroup("Also hits", Facet.MUSCLE, TagVocabulary.MUSCLE, viewModel, state)
            }

            Spacer(Modifier.height(16.dp))
            InverseButton(
                text = "SHOW ${state.matchCount} ${if (state.matchCount == 1) "EXERCISE" else "EXERCISES"}",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FacetGroup(
    label: String,
    facet: Facet,
    values: List<String>,
    viewModel: LibraryViewModel,
    state: LibraryUiState,
) {
    Spacer(Modifier.height(20.dp))
    SectionLabel(label)
    Spacer(Modifier.height(10.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        values.forEach { value ->
            Chip(
                text = value,
                selected = state.filter.isSelected(facet, value),
                onClick = { viewModel.onToggle(facet, value) },
            )
        }
    }
}
```

> **If the sheet renders at zero height or throws about an infinite height constraint**, the
> `weight(1f, fill = false)` on the scrolling column did not find a bounded parent. Replace it
> with `heightIn(max = 420.dp)` and add the `heightIn` import. Do not remove the scroll — with
> 27 chips across four groups the sheet is taller than a phone.

- [ ] **Step 2: Raise it from the Filters chip**

In `ui/AppNav.kt`, replace the `composable(Routes.LIBRARY) { ... }` block with:

```kotlin
        composable(Routes.LIBRARY) {
            val libraryVm: LibraryViewModel = viewModel(
                viewModelStoreOwner = nav.getBackStackEntry(Routes.LIBRARY),
                factory = LibraryViewModel.Factory,
            )
            var showFilters by rememberSaveable { mutableStateOf(false) }

            AppScaffold(tab = Tab.LIBRARY, onSelectTab = { if (it == Tab.HOME) nav.switchTab(Routes.HOME) }) {
                LibraryScreen(
                    viewModel = libraryVm,
                    onOpenCategory = { nav.navigate(Routes.category(it)) },
                    onOpenFilters = { showFilters = true },
                )
            }

            if (showFilters) {
                FiltersSheet(viewModel = libraryVm, onDismiss = { showFilters = false })
            }
        }
```

and add the import:

```kotlin
import com.ottotech.workoutbuilder.ui.library.FiltersSheet
```

- [ ] **Step 3: Build, install and verify the filter logic**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:installDebug
```

Expected — verify each one, since this is where OR/AND is easy to get backwards:

- Tapping `Filters` raises a sheet with `FILTERS` / `Clear all` and four labelled groups:
  `TYPE`, `EQUIPMENT`, `PATTERN`, `ALSO HITS`
- The bottom button reads `SHOW 101 EXERCISES` with nothing selected
- **OR within a facet:** select `EQUIPMENT: Barbell`, note the number; add
  `EQUIPMENT: Kettlebell`; the number goes **up**
- **AND across facets:** now add `TYPE: HIIT`; the number goes **down**
- Behind the sheet, the category counts update live as you tap
- Selecting `TYPE: HIIT` in the sheet makes the Library's `HIIT` chip appear selected when you
  dismiss — the two controls share one state
- The `Filters` chip shows `Filters · 2` with two facets active
- `Clear all` returns the button to `SHOW 101 EXERCISES` and deselects the `HIIT` chip on the
  row behind
- Swiping the sheet down keeps the selections (there is no Cancel — changes apply live)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/ui/
git commit -m "feat: add filters sheet with faceted OR-within AND-across selection"
```

---

### Task 9: Category exercise list

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/library/CategoryScreen.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/AppNav.kt`

**Interfaces:**
- Consumes: `LibraryViewModel` (`state`, `LibraryUiState.exercisesIn`), `HairlineCard`,
  `AppType`, `Dimens`
- Produces: `CategoryScreen(muscle: Muscle, viewModel: LibraryViewModel, onBack: () -> Unit, onOpenExercise: (String) -> Unit)`
- Also produces `ScreenHeader(title: String, onBack: () -> Unit)`, reused by Task 10

- [ ] **Step 1: Write the screen**

`app/src/main/java/com/ottotech/workoutbuilder/ui/library/CategoryScreen.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

/**
 * One category's exercises. Inherits the Library's active filter and search — spec §6.3 —
 * which is why it shares the LibraryViewModel rather than owning one.
 */
@Composable
fun CategoryScreen(
    muscle: Muscle,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current
    val matches = state.exercisesIn(muscle)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = muscle.label, onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(
                start = Dimens.screenPadding,
                end = Dimens.screenPadding,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item {
                Text(
                    text = "${matches.size} ${if (matches.size == 1) "exercise" else "exercises"}" +
                        if (state.filter.isEmpty) "" else " · filtered",
                    style = AppType.cardMeta,
                    color = colors.textMuted,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(matches, key = { it.id }) { exercise ->
                ExerciseRow(exercise) { onOpenExercise(exercise.id) }
            }
            if (matches.isEmpty()) {
                item {
                    Text(
                        "Nothing in this category matches the active filter.",
                        style = AppType.cardMeta,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }
}

/** Back affordance and title for the screens that sit outside the bottom nav. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenPadding)
            .padding(top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // heroName rather than a title style: a 30sp chevron overwhelms the word beside it.
        Text(
            text = "‹",
            style = AppType.heroName,
            color = colors.onBackground,
            modifier = Modifier.clickable(onClick = onBack),
        )
        Text(title.uppercase(), style = AppType.screenTitle, color = colors.onBackground)
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    // The sub-line is the exercise's equipment plus its secondary muscles — the two things
    // that decide whether you can actually do it right now.
    val equipment = exercise.tags.filter { it.facet == Facet.EQUIPMENT }.map { it.value }.sorted()
    val alsoHits = exercise.tags.filter { it.facet == Facet.MUSCLE }.map { it.value }.sorted()
    val sub = (equipment + alsoHits).joinToString(" · ")

    HairlineCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(Dimens.cardPadding)) {
            Text(exercise.name, style = AppType.cardTitle, color = colors.onBackground)
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(sub, style = AppType.cardMeta, color = colors.textMuted)
            }
        }
    }
}
```

- [ ] **Step 2: Wire it into the nav graph**

In `ui/AppNav.kt`, replace the `composable(route = Routes.CATEGORY, ...) { ... }` block with:

```kotlin
        composable(
            route = Routes.CATEGORY,
            arguments = listOf(navArgument("muscle") { type = NavType.StringType }),
        ) { entry ->
            val muscle = Muscle.valueOf(entry.arguments?.getString("muscle") ?: Muscle.CHEST.name)
            // Scoped to the Library entry so the filter set on the grid is still in force.
            val libraryVm: LibraryViewModel = viewModel(
                viewModelStoreOwner = nav.getBackStackEntry(Routes.LIBRARY),
                factory = LibraryViewModel.Factory,
            )
            CategoryScreen(
                muscle = muscle,
                viewModel = libraryVm,
                onBack = { nav.popBackStack() },
                onOpenExercise = { nav.navigate(Routes.exercise(it)) },
            )
        }
```

and add the import:

```kotlin
import com.ottotech.workoutbuilder.ui.library.CategoryScreen
```

> `nav.getBackStackEntry(Routes.LIBRARY)` throws if Library is not on the back stack. That is
> guaranteed here — Category is only ever reached from Library — and letting it throw is better
> than silently creating a second ViewModel whose filter would be empty.

- [ ] **Step 3: Build, install and verify**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:installDebug
```

Expected:
- From Library, tapping **Cardio** opens a screen headed `‹ CARDIO` listing 9 exercises, each
  with a sub-line of equipment and secondary muscles
- Tapping `‹` returns to the Library
- Set `TYPE: Mobility` in the Filters sheet, then open **Legs** — the list is much shorter and
  the count line reads `N exercises · filtered`
- Search `swing` on the Library screen, then open **Legs** — only Kettlebell Swing is listed
- Open a category, then Back, then a different category — the filter survives both trips
- Tapping an exercise navigates to the id stub from Task 1

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/ui/
git commit -m "feat: add category exercise list inheriting the active filter"
```

---

### Task 10: Exercise detail and Watch demo

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/DemoLink.kt`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/library/ExerciseDetailScreen.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/AppNav.kt`

**Interfaces:**
- Consumes: `Exercise`, `LibraryViewModel` (`state`, `LibraryUiState.exercise`, `onSetDemoUrl`),
  `ScreenHeader`, `Chip`, `HairlineCard`, `AppTextField`, `InverseButton`
- Produces:
  - `demoIntent(exercise: Exercise): Intent`
  - `ExerciseDetailScreen(exerciseId: String, viewModel: LibraryViewModel, onBack: () -> Unit)`

- [ ] **Step 1: Write the demo intent helper**

`app/src/main/java/com/ottotech/workoutbuilder/ui/DemoLink.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui

import android.content.Intent
import android.net.Uri
import com.ottotech.workoutbuilder.domain.Exercise

/**
 * Spec §7. An implicit ACTION_VIEW on a youtube.com URL, which the YouTube app claims when
 * installed and the browser handles otherwise — no YouTube SDK, no API key, no package
 * check. A per-exercise `demoUrl` overrides the search.
 *
 * Lives in `ui/` rather than `domain/` because it returns an android.content.Intent, and
 * `domain/` is enforced Android-free by DomainPurityTest.
 */
fun demoIntent(exercise: Exercise): Intent {
    val url = exercise.demoUrl?.takeIf { it.isNotBlank() }
        ?: "https://www.youtube.com/results?search_query=" + Uri.encode(exercise.demoQuery)
    return Intent(Intent.ACTION_VIEW, Uri.parse(url))
}
```

- [ ] **Step 2: Write the detail screen**

`app/src/main/java/com/ottotech/workoutbuilder/ui/library/ExerciseDetailScreen.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.ui.components.AppTextField
import com.ottotech.workoutbuilder.ui.components.Chip
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.components.InverseButton
import com.ottotech.workoutbuilder.ui.components.SectionLabel
import com.ottotech.workoutbuilder.ui.demoIntent
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(
    exerciseId: String,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val exercise = state.exercise(exerciseId)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = exercise?.primaryMuscle?.label ?: "Exercise", onBack = onBack)

        if (exercise == null) {
            // Only reachable if the exercise was deleted while this screen was open.
            Text(
                "That exercise is no longer in the library.",
                style = AppType.cardMeta,
                color = colors.textMuted,
                modifier = Modifier.padding(horizontal = Dimens.screenPadding),
            )
            return@Column
        }

        // Keyed on the id so navigating between exercises resets the field instead of
        // carrying the previous exercise's URL across.
        var urlDraft by remember(exercise.id) { mutableStateOf(exercise.demoUrl.orEmpty()) }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding)
                .padding(bottom = 28.dp),
        ) {
            Text(exercise.name, style = AppType.repsExerciseName, color = colors.onBackground)
            Spacer(Modifier.height(14.dp))

            SectionLabel("Tags")
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Primary muscle first and selected — it is the exercise's home category,
                // not one tag among many. No onClick: these are labels, not controls.
                Chip(exercise.primaryMuscle.label, selected = true)
                Facet.entries.forEach { facet ->
                    exercise.tags.filter { it.facet == facet }.map { it.value }.sorted()
                        .forEach { value -> Chip(value, selected = false) }
                }
            }

            Spacer(Modifier.height(22.dp))
            InverseButton(
                text = "WATCH DEMO",
                leadingGlyph = "▸",
                onClick = { context.startActivity(demoIntent(exercise)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = exercise.demoUrl?.takeIf { it.isNotBlank() }
                    ?: "Searches YouTube for “${exercise.demoQuery}”",
                style = AppType.cardMeta,
                color = colors.textMuted,
            )

            Spacer(Modifier.height(26.dp))
            SectionLabel("Demo video")
            Spacer(Modifier.height(10.dp))
            HairlineCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(Dimens.cardPadding)) {
                    Text(
                        "Paste a YouTube link to pin a specific demo. Leave it empty to search instead.",
                        style = AppType.cardMeta,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    AppTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        placeholder = "https://youtube.com/…",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    InverseButton(
                        text = "SAVE LINK",
                        height = Dimens.primaryButtonHeight,
                        style = AppType.primaryButton,
                        onClick = { viewModel.onSetDemoUrl(exercise.id, urlDraft) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Wire it into the nav graph**

In `ui/AppNav.kt`, replace the `composable(route = Routes.EXERCISE, ...) { ... }` block with:

```kotlin
        composable(
            route = Routes.EXERCISE,
            arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
        ) { entry ->
            val libraryVm: LibraryViewModel = viewModel(
                viewModelStoreOwner = nav.getBackStackEntry(Routes.LIBRARY),
                factory = LibraryViewModel.Factory,
            )
            ExerciseDetailScreen(
                exerciseId = entry.arguments?.getString("exerciseId").orEmpty(),
                viewModel = libraryVm,
                onBack = { nav.popBackStack() },
            )
        }
```

and add the import:

```kotlin
import com.ottotech.workoutbuilder.ui.library.ExerciseDetailScreen
```

- [ ] **Step 4: Build, install and verify the YouTube handoff**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:installDebug
```

Expected:
- Library → **Legs** → **Lateral Shuffle** shows the name at 28sp, a chip row starting with the
  selected `Legs` chip, a black `▸ WATCH DEMO` button, and beneath it the verified URL
  `https://www.youtube.com/shorts/iQjCIghvheI`
- Tapping `WATCH DEMO` leaves the app and opens that Short (YouTube app if installed on the
  emulator, browser otherwise)
- Library → **Chest** → **Push-up** shows `Searches YouTube for "Push-up proper form"` instead
  of a URL; tapping `WATCH DEMO` opens a YouTube **search results** page for that phrase
- On Push-up, paste `https://www.youtube.com/watch?v=IODxDxX7oi4` into the field and tap
  `SAVE LINK`. The sub-line under `WATCH DEMO` changes to that URL **without leaving the
  screen** — proving the change went through Room and back out of the observed flow
- Go back twice and re-enter Push-up: the URL is still there
- Clear the field and tap `SAVE LINK`: the sub-line returns to `Searches YouTube for …`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/ui/
git commit -m "feat: add exercise detail with tag chips and demo link override"
```

---

### Task 11: Full verification and status update

**Files:**
- Modify: `docs/superpowers/STATUS.md`

- [ ] **Step 1: Run the whole unit suite**

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

Expected: **76 tests, 0 failures** across eight classes —
`domain.SegmentCompilerTest` 12, `domain.DomainPurityTest` 1, `domain.DurationEstimatorTest` 6,
`domain.ExerciseFilterTest` 10, `domain.WorkoutSummaryTest` 12, `domain.FilterStateTest` 16,
`data.ExerciseSeedTest` 9, `data.WorkoutSeedTest` 10.

If the total differs, reconcile it before writing it into STATUS.md — do not adjust the number
in this plan to match a broken run.

- [ ] **Step 2: Verify a fresh install end to end**

```bash
ADB=/c/Users/ottol/AppData/Local/Android/Sdk/platform-tools/adb.exe
"$ADB" uninstall com.ottotech.workoutbuilder
./gradlew :app:installDebug
```

Walk the list and confirm each:

1. Home shows `Court Speed` in the hero with `10 exercises · 22 min · HIIT`
2. Cold-start the app a second time — still exactly one workout, no duplicate (seeding is
   idempotent)
3. Library grid counts total 101 across the eight categories: Chest 12, Back 14, Legs 20,
   Shoulders 10, Arms 9, Core 12, Cardio 9, Mobility 15
4. Filters sheet: OR within a facet raises the count, AND across facets lowers it
5. A category filtered to zero is dimmed and does not respond to taps
6. Search + filter combine; `Clear filters` restores the grid
7. A category screen inherits the filter; Back preserves it
8. `WATCH DEMO` opens YouTube for both a pinned URL and a search
9. **Dark mode** (Settings → Display → Dark theme): walk Home → Library → a category →
   an exercise → both sheets. Every surface, border and label must remain legible; nothing
   black-on-black or white-on-white
10. **Empty state:** the Builder does not exist yet, so force it by clearing app data
    (`"$ADB" shell pm clear com.ottotech.workoutbuilder` reseeds, which is not what we want).
    Instead verify it directly: temporarily comment out the
    `app.workouts.seedSampleIfNeeded(app.prefs)` line in `MainActivity`, uninstall, install, and
    confirm Home shows the black `NOTHING YET` card with the "Build one from the library" copy
    and the Build button still present. **Then restore the line, reinstall, and confirm
    `git status` is clean** — this is the one step that can leave the tree modified.

- [ ] **Step 3: Update the project status**

Rewrite the table and add the new environment facts in `docs/superpowers/STATUS.md`:

```markdown
| Phase | State |
|---|---|
| Design spec | Complete, approved — `specs/2026-07-21-workout-builder-android-design.md` |
| **Plan 1A** — foundation & domain | **Merged to `main`.** 46 tests green, verified on device. |
| **Plan 1B** — Home + Library screens | **Complete.** 76 tests green, verified on the emulator. |
| Plan 1C — Builder | Not started |
| Plan 1D — Execution + audio | Not started |
| Phase 2 — AI Coach | Not started |
```

Under "Environment facts not obvious from the code", add:

```markdown
- **Court Speed ships as seed data** (`data/seed/WorkoutSeed.kt`) and is written on first
  launch, gated on an `AppPrefs` flag so deleting it keeps it deleted. It is also the
  acceptance fixture — `WorkoutSeedTest` compiles it against the shipped exercise library.
- **The library is 101 exercises**, not 100 — Plan 1B added `flyer` so the seeded workout's
  Bonus section resolves.
- **Two unwired seams** are waiting in `ui/AppNav.kt`, both marked `TODO` with the plan that
  fills them: `onStartWorkout` (Plan 1D) and the Build sheet's two cards (Plans 1C and 2).
- **`LibraryViewModel` is scoped to the Library nav entry**, not per-screen, so the category
  and detail screens see the same filter. Navigating to them from anywhere other than the
  Library would throw on `getBackStackEntry`.
```

Also update the "Known blemish" section — the "101 exercises" note is now correct in substance
but for the wrong reason:

```markdown
## Known blemish

The Plan 1A seed commit (`5b7156b`) message says "101 exercises" when the count at that point
was **100**. Plan 1B later added `flyer`, so the number is now accurate by coincidence. Code
and tests were always correct; not worth rewriting history over.
```

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/STATUS.md
git commit -m "docs: record Plan 1B completion and its environment facts"
```

---

## Done when

- `./gradlew :app:testDebugUnitTest` is green: 76 tests across eight classes
- A fresh install shows Court Speed on Home with `10 exercises · 22 min · HIIT`, and a second
  launch does not duplicate it
- The Library grid counts 101 exercises across eight categories, and every count responds to the
  chip row, the search field and the Filters sheet
- OR-within-a-facet and AND-across-facets are both demonstrated on the device
- A category filtered to zero is dimmed and untappable; all-zero replaces the grid with
  `Clear filters`
- `WATCH DEMO` reaches YouTube for both a pinned `demoUrl` and a `demoQuery` search, and a
  pasted URL survives a round trip through Room
- Every screen and both sheets are legible in light and dark mode
- `DomainPurityTest` still passes — no Compose or Android type leaked into `domain/`

## Next

**Plan 1C — Builder + Library pick mode.** It fills the Build sheet's "Build it yourself" seam,
adds workout creation and editing on top of `WorkoutRepository.save`, and reuses
`LibraryScreen` in pick mode. `WorkoutSummary` and the whole filter stack are already in place;
1C should not need new domain primitives beyond section and item reordering.
