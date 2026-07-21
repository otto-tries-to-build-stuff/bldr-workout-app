# Phase 1A — Foundation & Domain Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Android project skeleton, both theme token tables, the Room database with a
seeded exercise library, and all pure domain logic (segment compilation, duration estimation,
exercise filtering) — fully unit-tested without a device.

**Architecture:** A single Gradle module. The `domain/` package contains plain Kotlin with zero
Android imports, so the logic that can actually be wrong runs on the JVM in milliseconds. Room
persists a flattened form of the domain model; a repository maps between them. Nothing in this
plan draws UI — that is Plans 1B–1D.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room + KSP, Gradle version catalog,
JUnit 4 + kotlin.test.

**Spec:** `docs/superpowers/specs/2026-07-21-workout-builder-android-design.md`

## Global Constraints

- Package: `com.ottotech.workoutbuilder`
- `minSdk 26` — gives `java.time` natively and notification channels, no desugaring required
- `compileSdk` / `targetSdk`: highest installed platform ≥ 34 (Task 1 Step 1 determines this)
- Kotlin JVM target 17
- **`domain/` must have zero `android.*` or `androidx.*` imports.** Task 4 adds a test that
  enforces this.
- All colours referenced by **role** (`surface`, `onSurface`, …), never as literal hex outside
  `ui/theme/Colors.kt`
- Tag values come from the controlled vocabulary in Task 3 — no free-text tags
- No shadows anywhere; hairline borders only
- Every task ends with a commit

---

## File Structure

```
app/build.gradle.kts
gradle/libs.versions.toml
app/src/main/java/com/ottotech/workoutbuilder/
  domain/
    Muscle.kt, TrainingType.kt, Tag.kt          — enums + controlled vocabulary
    Exercise.kt                                  — library item
    Workout.kt, Section.kt, WorkoutItem.kt, Rest.kt, ItemMode.kt
    Segment.kt                                   — the compiled execution unit
    SegmentCompiler.kt                           — Workout -> List<Segment>
    DurationEstimator.kt                         — List<Segment> -> minutes
    ExerciseFilter.kt                            — filter + search
  data/
    db/  Entities.kt, Converters.kt, ExerciseDao.kt, WorkoutDao.kt, AppDatabase.kt
    seed/ ExerciseSeed.kt
    WorkoutRepository.kt, ExerciseRepository.kt
  ui/theme/
    Colors.kt, Type.kt, Dimens.kt, Theme.kt
app/src/test/java/com/ottotech/workoutbuilder/
  domain/  SegmentCompilerTest.kt, CourtSpeedFixtureTest.kt,
           DurationEstimatorTest.kt, ExerciseFilterTest.kt, DomainPurityTest.kt
  data/    ExerciseSeedTest.kt
  fixtures/ CourtSpeed.kt
```

---

### Task 1: Project skeleton

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`,
  `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/MainActivity.kt`

**Interfaces:**
- Consumes: nothing
- Produces: a buildable Gradle project; `MainActivity` as the Compose entry point

- [ ] **Step 1: Determine the installed SDK platform**

```bash
ls "$LOCALAPPDATA/Android/Sdk/platforms"
```

Expected: one or more `android-NN` directories. Pick the **highest NN ≥ 34** and use it for
`compileSdk`/`targetSdk` below. If none ≥ 34 exists, install one via Android Studio's SDK
Manager before continuing — do not lower the floor.

- [ ] **Step 2: Create the Gradle version catalog**

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.12.01"
navigation = "2.8.5"
room = "2.6.1"
coroutines = "1.9.0"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test", version.ref = "kotlin" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 3: Create the settings and root build files**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "WorkoutBuilder"
include(":app")
```

`build.gradle.kts` (root):

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 4: Create the app module build file**

`app/build.gradle.kts` — replace `35` with the platform chosen in Step 1:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ottotech.workoutbuilder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ottotech.workoutbuilder"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 5: Create the manifest and entry point**

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="false"
        android:label="Workout Builder"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/java/com/ottotech/workoutbuilder/MainActivity.kt`:

```kotlin
package com.ottotech.workoutbuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Text("Workout Builder") }
    }
}
```

- [ ] **Step 6: Verify the project builds**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. If Gradle cannot find a JDK, point it at the one bundled with
Android Studio by adding to `gradle.properties`:
`org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr`

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle/ app/ gradlew gradlew.bat
git commit -m "chore: scaffold Android project with Compose and Room"
```

---

### Task 2: Design tokens for both themes

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/theme/Colors.kt`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/theme/Dimens.kt`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/theme/Type.kt`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/theme/Theme.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `WorkoutTheme { content }` composable; `LocalAppColors.current` returning
  `AppColors`; `Dimens` object; `AppType` object. Every later UI task reads colours **only**
  through `LocalAppColors.current`.

- [ ] **Step 1: Define the colour roles**

`Colors.kt` — values copied verbatim from spec §5:

```kotlin
package com.ottotech.workoutbuilder.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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
    surfaceInverse = Color(0xFFF4F4F2),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceInverse = Color(0xFF0B0B0D),
    textMuted = Color(0xFF9A9A9A),
    textMutedAlt = Color(0xFF8A8A8A),
    textDisabled = Color(0xFF5A5A5A),
    navInactive = Color(0xFF5A5A5A),
    border = Color(0x24FFFFFF),        // rgba(255,255,255,.14)
    borderSubtle = Color(0x14FFFFFF),  // rgba(255,255,255,.08)
)

/** Execution screen is always dark, in both themes — spec §5. */
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
```

- [ ] **Step 2: Define spacing, radii and type**

`Dimens.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.theme

import androidx.compose.ui.unit.dp

object Dimens {
    val screenPadding = 16.dp
    val cardPadding = 14.dp
    val heroPadding = 16.dp
    val gap = 11.dp

    val radiusChip = 10.dp
    val radiusCard = 14.dp
    val radiusHero = 18.dp
    val radiusButton = 13.dp
    val radiusCheckbox = 6.dp

    val hairline = 1.dp
    val buildButtonHeight = 48.dp
    val primaryButtonHeight = 40.dp
    val searchFieldHeight = 44.dp
    val badgeSize = 37.dp
    val categoryTileMinHeight = 56.dp
    val ringSize = 168.dp
    val ringStroke = 10.dp
    val progressBarHeight = 3.dp
    val progressBarGap = 4.dp
}
```

`Type.kt` — sizes from spec §5:

```kotlin
package com.ottotech.workoutbuilder.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

object AppType {
    val overline = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em)
    val screenTitle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.035).em, lineHeight = 29.sp)
    val libraryTitle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em)
    val heroLabel = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
    val heroName = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    val heroMeta = TextStyle(fontSize = 12.sp)
    val primaryButton = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val buildButton = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val cardTitle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val cardMeta = TextStyle(fontSize = 11.sp)
    val sectionLabel = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.06.em)
    val chip = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val tileName = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val tileCount = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val moveName = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em, textAlign = TextAlign.Center)
    val muscleTag = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em)
    val repsExerciseName = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
    val setRowLabel = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}
```

- [ ] **Step 3: Wire the theme**

`Theme.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun WorkoutTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(content = content)
    }
}
```

- [ ] **Step 4: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/ui/theme/
git commit -m "feat: add light and dark design token tables"
```

---

### Task 3: Domain models

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/domain/Muscle.kt`
- Create: `.../domain/TrainingType.kt`, `.../domain/Tag.kt`, `.../domain/Exercise.kt`
- Create: `.../domain/Rest.kt`, `.../domain/ItemMode.kt`, `.../domain/WorkoutItem.kt`
- Create: `.../domain/Section.kt`, `.../domain/Workout.kt`, `.../domain/Segment.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `Muscle`, `TrainingType`, `Facet`, `Tag`, `TagVocabulary`, `Exercise`, `Rest`,
  `ItemMode`, `WorkoutItem`, `Section`, `Workout`, `SegmentKind`, `Segment`. Every later task
  uses these exact names.

- [ ] **Step 1: Create the enums and tag vocabulary**

`Muscle.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

enum class Muscle(val label: String) {
    CHEST("Chest"), BACK("Back"), LEGS("Legs"), SHOULDERS("Shoulders"),
    ARMS("Arms"), CORE("Core"), CARDIO("Cardio"), MOBILITY("Mobility"),
    FULL_BODY("Full body");

    companion object {
        fun fromLabel(label: String): Muscle? = entries.firstOrNull { it.label == label }
    }
}
```

`TrainingType.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

enum class TrainingType(val label: String) {
    STRENGTH("Strength"), HIIT("HIIT"), MOBILITY("Mobility")
}
```

`Tag.kt` — the controlled vocabulary from spec §3:

```kotlin
package com.ottotech.workoutbuilder.domain

enum class Facet { TYPE, EQUIPMENT, MUSCLE, PATTERN }

data class Tag(val facet: Facet, val value: String)

object TagVocabulary {
    val TYPE = listOf("Strength", "HIIT", "Mobility")
    val EQUIPMENT = listOf(
        "Bodyweight", "Dumbbell", "Barbell", "Kettlebell",
        "Bands", "Machine", "Bench", "Pull-up bar",
    )
    val MUSCLE = Muscle.entries.map { it.label }
    val PATTERN = listOf("Push", "Pull", "Hinge", "Squat", "Lunge", "Carry", "Plyometric")

    fun valuesFor(facet: Facet): List<String> = when (facet) {
        Facet.TYPE -> TYPE
        Facet.EQUIPMENT -> EQUIPMENT
        Facet.MUSCLE -> MUSCLE
        Facet.PATTERN -> PATTERN
    }

    fun isValid(tag: Tag): Boolean = tag.value in valuesFor(tag.facet)
}
```

- [ ] **Step 2: Create the library and workout models**

`Exercise.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

data class Exercise(
    val id: String,
    val name: String,
    val primaryMuscle: Muscle,
    val tags: Set<Tag> = emptySet(),
    val demoQuery: String,
    val demoUrl: String? = null,
    val isCustom: Boolean = false,
) {
    /** First EQUIPMENT tag, used for the reps sub-line — spec §6.5. */
    val equipmentLabel: String?
        get() = tags.firstOrNull { it.facet == Facet.EQUIPMENT }?.value
}
```

`Rest.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

sealed interface Rest {
    data class Timed(val seconds: Int) : Rest
    data object Manual : Rest
}
```

`ItemMode.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

enum class ItemMode { TIMED, REPS, MANUAL }
```

`WorkoutItem.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

data class WorkoutItem(
    val id: String,
    val exerciseId: String,
    val mode: ItemMode,
    val durationSec: Int? = null,
    val sets: Int? = null,
    val reps: Int? = null,
    val weightKg: Float? = null,
    val note: String? = null,
)
```

`Section.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

data class Section(
    val id: String,
    val name: String,
    val rounds: Int = 1,
    val restBetweenExercises: Rest? = null,
    val restBetweenRounds: Rest? = null,
    val restAfterSection: Rest? = null,
    val items: List<WorkoutItem> = emptyList(),
)
```

`Workout.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

import java.time.Instant

data class Workout(
    val id: String,
    val name: String,
    val type: TrainingType,
    val createdAt: Instant,
    val lastPerformedAt: Instant? = null,
    val sections: List<Section> = emptyList(),
)
```

- [ ] **Step 3: Create the Segment model**

`Segment.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

enum class SegmentKind { WORK, REST }

data class Segment(
    val kind: SegmentKind,
    val mode: ItemMode,
    val exerciseName: String,
    val label: String,
    val primaryMuscle: Muscle? = null,
    val equipmentLabel: String? = null,
    val note: String? = null,
    val demoQuery: String? = null,
    val demoUrl: String? = null,
    val durationSec: Int? = null,
    val sets: Int? = null,
    val reps: Int? = null,
    val weightKg: Float? = null,
    val index: Int = 0,
    val total: Int = 0,
)
```

- [ ] **Step 4: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/domain/
git commit -m "feat: add domain models for exercises, workouts and segments"
```

---

### Task 4: SegmentCompiler

The core of the app. Written test-first, one test per invariant in spec §4.

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/domain/SegmentCompiler.kt`
- Test: `app/src/test/java/com/ottotech/workoutbuilder/domain/SegmentCompilerTest.kt`
- Test: `app/src/test/java/com/ottotech/workoutbuilder/domain/DomainPurityTest.kt`

**Interfaces:**
- Consumes: all models from Task 3
- Produces: `SegmentCompiler.compile(workout: Workout, exercises: Map<String, Exercise>): List<Segment>`

- [ ] **Step 1: Write the failing tests**

`SegmentCompilerTest.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SegmentCompilerTest {

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

    private fun workout(vararg sections: Section) = Workout(
        id = "w", name = "W", type = TrainingType.HIIT,
        createdAt = Instant.EPOCH, sections = sections.toList(),
    )

    @Test
    fun `single section single round emits one segment per item in order`() {
        val out = SegmentCompiler.compile(
            workout(Section("s", "Main", items = listOf(
                timed("a", push.id, 30), timed("b", squat.id, 40),
            ))),
            lookup,
        )
        assertEquals(2, out.size)
        assertEquals(listOf("Push-up", "Air Squat"), out.map { it.exerciseName })
        assertEquals(listOf(30, 40), out.map { it.durationSec })
        assertTrue(out.all { it.kind == SegmentKind.WORK })
    }

    @Test
    fun `rest between exercises is inserted but not after the last item of a round`() {
        val out = SegmentCompiler.compile(
            workout(Section("s", "Main",
                restBetweenExercises = Rest.Timed(15),
                items = listOf(timed("a", push.id, 30), timed("b", squat.id, 30)),
            )),
            lookup,
        )
        assertEquals(3, out.size)
        assertEquals(listOf(SegmentKind.WORK, SegmentKind.REST, SegmentKind.WORK), out.map { it.kind })
    }

    @Test
    fun `rounds expand and round rest sits between rounds but not after the last`() {
        val out = SegmentCompiler.compile(
            workout(Section("s", "Circuit", rounds = 3,
                restBetweenRounds = Rest.Timed(60),
                items = listOf(timed("a", push.id, 30)),
            )),
            lookup,
        )
        // work, rest, work, rest, work
        assertEquals(5, out.size)
        assertEquals(
            listOf(SegmentKind.WORK, SegmentKind.REST, SegmentKind.WORK, SegmentKind.REST, SegmentKind.WORK),
            out.map { it.kind },
        )
    }

    @Test
    fun `section rest sits between sections but not after the last`() {
        val out = SegmentCompiler.compile(
            workout(
                Section("s1", "A", restAfterSection = Rest.Timed(60), items = listOf(timed("a", push.id, 30))),
                Section("s2", "B", restAfterSection = Rest.Timed(60), items = listOf(timed("b", squat.id, 30))),
            ),
            lookup,
        )
        assertEquals(3, out.size)
        assertEquals(listOf(SegmentKind.WORK, SegmentKind.REST, SegmentKind.WORK), out.map { it.kind })
    }

    @Test
    fun `single item section never emits an inter-exercise rest`() {
        val out = SegmentCompiler.compile(
            workout(Section("s", "Solo",
                restBetweenExercises = Rest.Timed(15),
                items = listOf(timed("a", push.id, 30)),
            )),
            lookup,
        )
        assertEquals(1, out.size)
    }

    @Test
    fun `null rest emits nothing rather than a zero length segment`() {
        val out = SegmentCompiler.compile(
            workout(Section("s", "Main", rounds = 2, items = listOf(timed("a", push.id, 30)))),
            lookup,
        )
        assertEquals(2, out.size)
        assertTrue(out.none { it.kind == SegmentKind.REST })
    }

    @Test
    fun `empty section contributes no segments at all`() {
        val out = SegmentCompiler.compile(
            workout(Section("s", "Empty", rounds = 3, restAfterSection = Rest.Timed(60))),
            lookup,
        )
        assertEquals(0, out.size)
    }

    @Test
    fun `manual rest produces a manual mode rest segment with no duration`() {
        val out = SegmentCompiler.compile(
            workout(Section("s", "Sprints", rounds = 2,
                restBetweenRounds = Rest.Manual,
                items = listOf(WorkoutItem("a", push.id, ItemMode.MANUAL)),
            )),
            lookup,
        )
        val rest = out.single { it.kind == SegmentKind.REST }
        assertEquals(ItemMode.MANUAL, rest.mode)
        assertEquals(null, rest.durationSec)
    }

    @Test
    fun `mixed modes are preserved in one workout`() {
        val out = SegmentCompiler.compile(
            workout(Section("s", "Mixed", items = listOf(
                timed("a", push.id, 60),
                WorkoutItem("b", squat.id, ItemMode.REPS, sets = 3, reps = 8, weightKg = 60f),
                WorkoutItem("c", push.id, ItemMode.MANUAL),
            ))),
            lookup,
        )
        assertEquals(listOf(ItemMode.TIMED, ItemMode.REPS, ItemMode.MANUAL), out.map { it.mode })
        assertEquals(3, out[1].sets)
        assertEquals(60f, out[1].weightKg)
    }

    @Test
    fun `label includes round marker only when rounds is greater than one`() {
        val multi = SegmentCompiler.compile(
            workout(Section("s", "Block A", rounds = 3, items = listOf(timed("a", push.id, 30)))),
            lookup,
        )
        assertEquals("BLOCK A · ROUND 1/3", multi.first().label)

        val single = SegmentCompiler.compile(
            workout(Section("s", "Warm Prep", items = listOf(timed("a", push.id, 30)))),
            lookup,
        )
        assertEquals("WARM PREP", single.first().label)
    }

    @Test
    fun `index and total are assigned across the whole compiled list`() {
        val out = SegmentCompiler.compile(
            workout(
                Section("s1", "A", items = listOf(timed("a", push.id, 30))),
                Section("s2", "B", items = listOf(timed("b", squat.id, 30))),
            ),
            lookup,
        )
        assertEquals(listOf(0, 1), out.map { it.index })
        assertTrue(out.all { it.total == 2 })
    }

    @Test
    fun `work segments carry exercise metadata for the execution screen`() {
        val out = SegmentCompiler.compile(
            workout(Section("s", "Main", items = listOf(timed("a", push.id, 30)))),
            lookup,
        )
        val s = out.single()
        assertEquals(Muscle.CHEST, s.primaryMuscle)
        assertEquals("Bodyweight", s.equipmentLabel)
        assertEquals("push up proper form", s.demoQuery)
    }
}
```

`DomainPurityTest.kt` — enforces the Global Constraint that `domain/` stays Android-free:

```kotlin
package com.ottotech.workoutbuilder.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DomainPurityTest {

    @Test
    fun `domain package contains no android imports`() {
        val dir = File("src/main/java/com/ottotech/workoutbuilder/domain")
        assertTrue(dir.isDirectory, "domain source directory not found at ${dir.absolutePath}")

        val offenders = dir.walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .filter { it.trimStart().startsWith("import android") }
                    .map { "${file.name}: ${it.trim()}" }
            }
            .toList()

        assertTrue(offenders.isEmpty(), "domain/ must stay Android-free, found:\n${offenders.joinToString("\n")}")
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "*SegmentCompilerTest*"
```

Expected: FAIL — `Unresolved reference: SegmentCompiler`

- [ ] **Step 3: Implement the compiler**

`SegmentCompiler.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

object SegmentCompiler {

    fun compile(workout: Workout, exercises: Map<String, Exercise>): List<Segment> {
        val out = mutableListOf<Segment>()

        workout.sections.forEachIndexed { sectionIndex, section ->
            if (section.items.isEmpty()) return@forEachIndexed
            val isLastSection = sectionIndex == workout.sections.lastIndex

            repeat(section.rounds.coerceAtLeast(1)) { roundIndex ->
                val round = roundIndex + 1
                val isLastRound = round == section.rounds.coerceAtLeast(1)
                val label = labelFor(section, round)

                section.items.forEachIndexed { itemIndex, item ->
                    val exercise = requireNotNull(exercises[item.exerciseId]) {
                        "Unknown exerciseId '${item.exerciseId}' in section '${section.name}'"
                    }
                    out += workSegment(item, exercise, label)

                    val isLastItem = itemIndex == section.items.lastIndex
                    if (!isLastItem) {
                        section.restBetweenExercises?.let { out += restSegment(it, label) }
                    }
                }

                if (!isLastRound) {
                    section.restBetweenRounds?.let { out += restSegment(it, label) }
                }
            }

            if (!isLastSection) {
                section.restAfterSection?.let {
                    out += restSegment(it, labelFor(section, section.rounds.coerceAtLeast(1)))
                }
            }
        }

        return out.mapIndexed { i, segment -> segment.copy(index = i, total = out.size) }
    }

    private fun labelFor(section: Section, round: Int): String {
        val name = section.name.uppercase()
        val rounds = section.rounds.coerceAtLeast(1)
        return if (rounds > 1) "$name · ROUND $round/$rounds" else name
    }

    private fun workSegment(item: WorkoutItem, exercise: Exercise, label: String) = Segment(
        kind = SegmentKind.WORK,
        mode = item.mode,
        exerciseName = exercise.name,
        label = label,
        primaryMuscle = exercise.primaryMuscle,
        equipmentLabel = exercise.equipmentLabel,
        note = item.note,
        demoQuery = exercise.demoQuery,
        demoUrl = exercise.demoUrl,
        durationSec = if (item.mode == ItemMode.TIMED) item.durationSec else null,
        sets = item.sets,
        reps = item.reps,
        weightKg = item.weightKg,
    )

    private fun restSegment(rest: Rest, label: String) = Segment(
        kind = SegmentKind.REST,
        mode = if (rest is Rest.Timed) ItemMode.TIMED else ItemMode.MANUAL,
        exerciseName = "Rest",
        label = label,
        durationSec = (rest as? Rest.Timed)?.seconds,
    )
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "*SegmentCompilerTest*" --tests "*DomainPurityTest*"
```

Expected: PASS, 13 tests

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/domain/SegmentCompiler.kt app/src/test/
git commit -m "feat: add segment compiler with full invariant coverage"
```

---

### Task 5: Court Speed acceptance fixture

Proves the data model can express a real workout. This is the test that matters most.

**Files:**
- Create: `app/src/test/java/com/ottotech/workoutbuilder/fixtures/CourtSpeed.kt`
- Test: `app/src/test/java/com/ottotech/workoutbuilder/domain/CourtSpeedFixtureTest.kt`

**Interfaces:**
- Consumes: `SegmentCompiler.compile`, all Task 3 models
- Produces: `CourtSpeed.workout`, `CourtSpeed.exercises` — reused by later plans for manual testing

- [ ] **Step 1: Build the fixture**

`fixtures/CourtSpeed.kt` — mirrors `buildSegments()` in `web-app-reference/HIIT Workout.html`:

```kotlin
package com.ottotech.workoutbuilder.fixtures

import com.ottotech.workoutbuilder.domain.*
import java.time.Instant

object CourtSpeed {

    private fun ex(id: String, name: String, muscle: Muscle, url: String? = null) = Exercise(
        id = id, name = name, primaryMuscle = muscle,
        tags = setOf(Tag(Facet.TYPE, "HIIT"), Tag(Facet.EQUIPMENT, "Bodyweight")),
        demoQuery = "${name.lowercase()} proper form", demoUrl = url,
    )

    val exercises: Map<String, Exercise> = listOf(
        ex("leg-swings", "Leg Swings", Muscle.MOBILITY, "https://www.youtube.com/shorts/3l31E2cMGMk"),
        ex("walking-lunge-twist", "Walking Lunge + Twist", Muscle.LEGS, "https://www.youtube.com/shorts/2crIg2iqi8w"),
        ex("a-skips", "A-Skips", Muscle.CARDIO, "https://www.youtube.com/shorts/fSZUFBbK7CE"),
        ex("build-up-strides", "Build-Up Strides", Muscle.CARDIO, "https://www.youtube.com/watch?v=IQrbQMPbTtU"),
        ex("lateral-shuffle", "Lateral Shuffle", Muscle.LEGS, "https://www.youtube.com/shorts/iQjCIghvheI"),
        ex("skater-bounds", "Skater Bounds", Muscle.LEGS, "https://www.youtube.com/shorts/7-Qfb881k7o"),
        ex("split-squat-jumps", "Split Squat Jumps", Muscle.LEGS, "https://www.youtube.com/shorts/e6s05vAhAZo"),
        ex("squat-jumps-180", "180° Squat Jumps", Muscle.LEGS, "https://www.youtube.com/watch?v=_-Q5ywo24_s"),
        ex("sprint", "Sprint", Muscle.CARDIO),
        ex("flyer", "Flyer", Muscle.CARDIO),
    ).associateBy { it.id }

    private fun timed(id: String, exId: String, sec: Int, note: String? = null) =
        WorkoutItem(id = id, exerciseId = exId, mode = ItemMode.TIMED, durationSec = sec, note = note)

    val workout = Workout(
        id = "court-speed",
        name = "Court Speed",
        type = TrainingType.HIIT,
        createdAt = Instant.EPOCH,
        sections = listOf(
            Section(
                id = "warm-prep", name = "Warm Prep",
                items = listOf(
                    timed("wp1", "leg-swings", 45, "10 each direction · per leg"),
                    timed("wp2", "walking-lunge-twist", 45, "8 each side"),
                    timed("wp3", "a-skips", 45, "20m × 2"),
                    timed("wp4", "build-up-strides", 45, "2 × ~70% pace"),
                ),
            ),
            Section(
                id = "block-a", name = "Block A · Tennis Circuit",
                rounds = 3,
                restBetweenExercises = Rest.Timed(15),
                restBetweenRounds = Rest.Timed(60),
                restAfterSection = Rest.Timed(60),
                items = listOf(
                    timed("ba1", "lateral-shuffle", 30, "~5m apart · touch ground each end"),
                    timed("ba2", "skater-bounds", 30, "Explosive · stick each landing"),
                    timed("ba3", "split-squat-jumps", 30, "Alternating legs"),
                    timed("ba4", "squat-jumps-180", 30, "Alternate rotation direction"),
                ),
            ),
            Section(
                id = "block-b", name = "Block B · Sprints",
                rounds = 6,
                restBetweenRounds = Rest.Manual,
                items = listOf(
                    WorkoutItem("bb1", "sprint", ItemMode.MANUAL, note = "60–80m @ 90% · drive knees, stay tall"),
                ),
            ),
            Section(
                id = "bonus", name = "Bonus · Optional",
                items = listOf(
                    WorkoutItem("bo1", "flyer", ItemMode.MANUAL, note = "40m all-out · if you have it"),
                ),
            ),
        ),
    )
}
```

- [ ] **Step 2: Write the failing acceptance test**

`CourtSpeedFixtureTest.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

import com.ottotech.workoutbuilder.fixtures.CourtSpeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CourtSpeedFixtureTest {

    private val segments = SegmentCompiler.compile(CourtSpeed.workout, CourtSpeed.exercises)

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
    fun `block A has two sixty second round rests and no third`() {
        val roundRests = segments.filter {
            it.kind == SegmentKind.REST && it.durationSec == 60 && it.label.contains("BLOCK A")
        }
        // 2 between rounds + 1 after the section
        assertEquals(3, roundRests.size)
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
}
```

- [ ] **Step 3: Run to verify it fails, then passes**

```bash
./gradlew :app:testDebugUnitTest --tests "*CourtSpeedFixtureTest*"
```

Expected first run: FAIL — the fixture file does not compile until Step 1 is saved.
After Steps 1–2 are both in place: PASS, 7 tests.

If the segment count is not 40, **the compiler is wrong, not the test** — re-read spec §4 and
compare against `buildSegments()` in `web-app-reference/HIIT Workout.html` before changing the
expected number.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/
git commit -m "test: add Court Speed acceptance fixture proving the model expresses a real workout"
```

---

### Task 6: DurationEstimator

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/domain/DurationEstimator.kt`
- Test: `app/src/test/java/com/ottotech/workoutbuilder/domain/DurationEstimatorTest.kt`

**Interfaces:**
- Consumes: `Segment`, `SegmentKind`, `ItemMode`
- Produces: `DurationEstimator.estimateSeconds(List<Segment>): Int`,
  `DurationEstimator.estimateMinutes(List<Segment>): Int`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ottotech.workoutbuilder.domain

import com.ottotech.workoutbuilder.fixtures.CourtSpeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DurationEstimatorTest {

    private fun seg(kind: SegmentKind, mode: ItemMode, sec: Int? = null, sets: Int? = null) =
        Segment(kind = kind, mode = mode, exerciseName = "X", label = "L", durationSec = sec, sets = sets)

    @Test
    fun `timed segments contribute their duration`() {
        val out = DurationEstimator.estimateSeconds(
            listOf(seg(SegmentKind.WORK, ItemMode.TIMED, sec = 30), seg(SegmentKind.REST, ItemMode.TIMED, sec = 15)),
        )
        assertEquals(45, out)
    }

    @Test
    fun `reps segments are estimated at forty five seconds per set`() {
        val out = DurationEstimator.estimateSeconds(listOf(seg(SegmentKind.WORK, ItemMode.REPS, sets = 3)))
        assertEquals(135, out)
    }

    @Test
    fun `reps segment with null sets counts as a single set`() {
        val out = DurationEstimator.estimateSeconds(listOf(seg(SegmentKind.WORK, ItemMode.REPS)))
        assertEquals(45, out)
    }

    @Test
    fun `manual work and manual rest use their own constants`() {
        val out = DurationEstimator.estimateSeconds(
            listOf(seg(SegmentKind.WORK, ItemMode.MANUAL), seg(SegmentKind.REST, ItemMode.MANUAL)),
        )
        assertEquals(80, out)
    }

    @Test
    fun `minutes round to nearest`() {
        assertEquals(2, DurationEstimator.estimateMinutes(listOf(seg(SegmentKind.WORK, ItemMode.TIMED, sec = 100))))
        assertEquals(1, DurationEstimator.estimateMinutes(listOf(seg(SegmentKind.WORK, ItemMode.TIMED, sec = 80))))
    }

    @Test
    fun `empty list is zero`() {
        assertEquals(0, DurationEstimator.estimateSeconds(emptyList()))
    }

    @Test
    fun `court speed estimates to a plausible session length`() {
        val minutes = DurationEstimator.estimateMinutes(
            SegmentCompiler.compile(CourtSpeed.workout, CourtSpeed.exercises),
        )
        // 180s warm prep + 675s block A + 420s block B + 20s bonus = 1295s ≈ 22 min.
        // Asserted as a band, not an exact figure, so tuning the manual constants
        // in DurationEstimator does not break this test.
        assertTrue(minutes in 20..35, "expected 20-35 min, got $minutes")
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "*DurationEstimatorTest*"
```

Expected: FAIL — `Unresolved reference: DurationEstimator`

- [ ] **Step 3: Implement**

```kotlin
package com.ottotech.workoutbuilder.domain

import kotlin.math.roundToInt

object DurationEstimator {

    const val REPS_SECONDS_PER_SET = 45
    const val MANUAL_WORK_SECONDS = 20
    const val MANUAL_REST_SECONDS = 60

    fun estimateSeconds(segments: List<Segment>): Int = segments.sumOf { segment ->
        when (segment.mode) {
            ItemMode.TIMED -> segment.durationSec ?: 0
            ItemMode.REPS -> (segment.sets ?: 1) * REPS_SECONDS_PER_SET
            ItemMode.MANUAL ->
                if (segment.kind == SegmentKind.WORK) MANUAL_WORK_SECONDS else MANUAL_REST_SECONDS
        }
    }

    fun estimateMinutes(segments: List<Segment>): Int =
        (estimateSeconds(segments) / 60.0).roundToInt()
}
```

- [ ] **Step 4: Run to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "*DurationEstimatorTest*"
```

Expected: PASS, 7 tests

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/domain/DurationEstimator.kt app/src/test/
git commit -m "feat: add duration estimator for workout cards and coach preview"
```

---

### Task 7: ExerciseFilter

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/domain/ExerciseFilter.kt`
- Test: `app/src/test/java/com/ottotech/workoutbuilder/domain/ExerciseFilterTest.kt`

**Interfaces:**
- Consumes: `Exercise`, `Tag`, `Facet`, `Muscle`
- Produces: `FilterState(facets: Map<Facet, Set<String>>, query: String)`,
  `ExerciseFilter.apply(List<Exercise>, FilterState): List<Exercise>`,
  `ExerciseFilter.countsByMuscle(List<Exercise>, FilterState): Map<Muscle, Int>`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ottotech.workoutbuilder.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ExerciseFilterTest {

    private fun ex(id: String, name: String, muscle: Muscle, vararg tags: Tag) =
        Exercise(id = id, name = name, primaryMuscle = muscle, tags = tags.toSet(), demoQuery = "$name form")

    private val bench = ex("bench", "Bench Press", Muscle.CHEST,
        Tag(Facet.TYPE, "Strength"), Tag(Facet.EQUIPMENT, "Barbell"),
        Tag(Facet.MUSCLE, "Arms"), Tag(Facet.PATTERN, "Push"))

    private val pushUp = ex("push-up", "Push-up", Muscle.CHEST,
        Tag(Facet.TYPE, "Strength"), Tag(Facet.EQUIPMENT, "Bodyweight"), Tag(Facet.PATTERN, "Push"))

    private val swing = ex("swing", "Kettlebell Swing", Muscle.LEGS,
        Tag(Facet.TYPE, "Strength"), Tag(Facet.TYPE, "HIIT"),
        Tag(Facet.EQUIPMENT, "Kettlebell"), Tag(Facet.MUSCLE, "Core"), Tag(Facet.PATTERN, "Hinge"))

    private val all = listOf(bench, pushUp, swing)

    @Test
    fun `empty filter returns everything`() {
        assertEquals(all, ExerciseFilter.apply(all, FilterState()))
    }

    @Test
    fun `multiple values within a facet combine with OR`() {
        val out = ExerciseFilter.apply(all,
            FilterState(facets = mapOf(Facet.EQUIPMENT to setOf("Barbell", "Kettlebell"))))
        assertEquals(listOf(bench, swing), out)
    }

    @Test
    fun `different facets combine with AND`() {
        val out = ExerciseFilter.apply(all, FilterState(facets = mapOf(
            Facet.TYPE to setOf("HIIT"),
            Facet.EQUIPMENT to setOf("Kettlebell"),
        )))
        assertEquals(listOf(swing), out)
    }

    @Test
    fun `AND across facets can produce an empty result without error`() {
        val out = ExerciseFilter.apply(all, FilterState(facets = mapOf(
            Facet.TYPE to setOf("HIIT"),
            Facet.EQUIPMENT to setOf("Barbell"),
        )))
        assertEquals(emptyList(), out)
    }

    @Test
    fun `a facet present with an empty set applies no constraint`() {
        val out = ExerciseFilter.apply(all, FilterState(facets = mapOf(Facet.TYPE to emptySet())))
        assertEquals(all, out)
    }

    @Test
    fun `muscle facet matches secondary tags only not the primary muscle`() {
        // bench has MUSCLE:Arms as a secondary tag; nothing has MUSCLE:Chest
        assertEquals(listOf(bench),
            ExerciseFilter.apply(all, FilterState(facets = mapOf(Facet.MUSCLE to setOf("Arms")))))
        assertEquals(emptyList(),
            ExerciseFilter.apply(all, FilterState(facets = mapOf(Facet.MUSCLE to setOf("Chest")))))
    }

    @Test
    fun `query matches exercise name case insensitively`() {
        assertEquals(listOf(bench), ExerciseFilter.apply(all, FilterState(query = "bench")))
    }

    @Test
    fun `query matches tag values so kettlebell finds the swing`() {
        assertEquals(listOf(swing), ExerciseFilter.apply(all, FilterState(query = "kettlebell")))
    }

    @Test
    fun `query and facets combine`() {
        val out = ExerciseFilter.apply(all, FilterState(
            facets = mapOf(Facet.TYPE to setOf("Strength")),
            query = "push",
        ))
        assertEquals(listOf(pushUp), out)
    }

    @Test
    fun `counts by muscle reflect the active filter`() {
        val unfiltered = ExerciseFilter.countsByMuscle(all, FilterState())
        assertEquals(2, unfiltered[Muscle.CHEST])
        assertEquals(1, unfiltered[Muscle.LEGS])

        val bodyweightOnly = ExerciseFilter.countsByMuscle(all,
            FilterState(facets = mapOf(Facet.EQUIPMENT to setOf("Bodyweight"))))
        assertEquals(1, bodyweightOnly[Muscle.CHEST])
        assertEquals(0, bodyweightOnly[Muscle.LEGS])
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "*ExerciseFilterTest*"
```

Expected: FAIL — `Unresolved reference: FilterState`

- [ ] **Step 3: Implement**

```kotlin
package com.ottotech.workoutbuilder.domain

data class FilterState(
    val facets: Map<Facet, Set<String>> = emptyMap(),
    val query: String = "",
) {
    val activeFacetCount: Int get() = facets.count { it.value.isNotEmpty() }
    val isEmpty: Boolean get() = activeFacetCount == 0 && query.isBlank()
}

object ExerciseFilter {

    fun apply(exercises: List<Exercise>, state: FilterState): List<Exercise> =
        exercises.filter { matchesFacets(it, state) && matchesQuery(it, state.query) }

    /** Category tile counts — every muscle is present, zero included. */
    fun countsByMuscle(exercises: List<Exercise>, state: FilterState): Map<Muscle, Int> {
        val matching = apply(exercises, state)
        return Muscle.entries.associateWith { muscle ->
            matching.count { it.primaryMuscle == muscle }
        }
    }

    private fun matchesFacets(exercise: Exercise, state: FilterState): Boolean =
        state.facets.all { (facet, values) ->
            // OR within a facet; an empty selection constrains nothing
            values.isEmpty() || exercise.tags.any { it.facet == facet && it.value in values }
        }

    private fun matchesQuery(exercise: Exercise, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return exercise.name.lowercase().contains(q) ||
            exercise.tags.any { it.value.lowercase().contains(q) }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "*ExerciseFilterTest*"
```

Expected: PASS, 10 tests

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/domain/ExerciseFilter.kt app/src/test/
git commit -m "feat: add faceted exercise filter with OR within and AND across facets"
```

---

### Task 8: Room database

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/data/db/Entities.kt`
- Create: `.../data/db/Converters.kt`, `.../data/db/ExerciseDao.kt`, `.../data/db/WorkoutDao.kt`
- Create: `.../data/db/AppDatabase.kt`

**Interfaces:**
- Consumes: domain models from Task 3
- Produces: `AppDatabase.get(context)`, `ExerciseDao`, `WorkoutDao`, and the entity classes
  `ExerciseEntity`, `ExerciseTagEntity`, `WorkoutEntity`, `SectionEntity`, `WorkoutItemEntity`

- [ ] **Step 1: Define entities**

`Entities.kt`:

```kotlin
package com.ottotech.workoutbuilder.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val primaryMuscle: String,
    val demoQuery: String,
    val demoUrl: String?,
    val isCustom: Boolean,
)

@Entity(
    tableName = "exercise_tags",
    primaryKeys = ["exerciseId", "facet", "value"],
    foreignKeys = [ForeignKey(
        entity = ExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("facet", "value"), Index("exerciseId")],
)
data class ExerciseTagEntity(
    val exerciseId: String,
    val facet: String,
    val value: String,
)

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val createdAt: Long,
    val lastPerformedAt: Long?,
)

@Entity(
    tableName = "sections",
    foreignKeys = [ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("workoutId")],
)
data class SectionEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    val orderIndex: Int,
    val name: String,
    val rounds: Int,
    val restBetweenExercisesMode: String?,
    val restBetweenExercisesSec: Int?,
    val restBetweenRoundsMode: String?,
    val restBetweenRoundsSec: Int?,
    val restAfterSectionMode: String?,
    val restAfterSectionSec: Int?,
)

@Entity(
    tableName = "workout_items",
    foreignKeys = [ForeignKey(
        entity = SectionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sectionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sectionId")],
)
data class WorkoutItemEntity(
    @PrimaryKey val id: String,
    val sectionId: String,
    val orderIndex: Int,
    val exerciseId: String,
    val mode: String,
    val durationSec: Int?,
    val sets: Int?,
    val reps: Int?,
    val weightKg: Float?,
    val note: String?,
)
```

- [ ] **Step 2: Define DAOs and the database**

`ExerciseDao.kt`:

```kotlin
package com.ottotech.workoutbuilder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise_tags")
    suspend fun getAllTags(): List<ExerciseTagEntity>

    @Query("SELECT * FROM exercise_tags")
    fun observeAllTags(): Flow<List<ExerciseTagEntity>>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(rows: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(rows: List<ExerciseTagEntity>)

    @Transaction
    suspend fun insertAll(exercises: List<ExerciseEntity>, tags: List<ExerciseTagEntity>) {
        insertExercises(exercises)
        insertTags(tags)
    }

    @Query("UPDATE exercises SET demoUrl = :url WHERE id = :id")
    suspend fun setDemoUrl(id: String, url: String?)
}
```

`WorkoutDao.kt`:

```kotlin
package com.ottotech.workoutbuilder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Query("SELECT * FROM workouts ORDER BY createdAt DESC")
    fun observeWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkout(id: String): WorkoutEntity?

    @Query("SELECT * FROM sections WHERE workoutId = :workoutId ORDER BY orderIndex")
    suspend fun getSections(workoutId: String): List<SectionEntity>

    @Query("SELECT * FROM workout_items WHERE sectionId IN (:sectionIds) ORDER BY orderIndex")
    suspend fun getItems(sectionIds: List<String>): List<WorkoutItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(row: WorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(rows: List<SectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(rows: List<WorkoutItemEntity>)

    @Query("DELETE FROM sections WHERE workoutId = :workoutId")
    suspend fun deleteSectionsFor(workoutId: String)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: String)

    @Query("UPDATE workouts SET lastPerformedAt = :timestamp WHERE id = :id")
    suspend fun markPerformed(id: String, timestamp: Long)

    @Transaction
    suspend fun upsertFull(
        workout: WorkoutEntity,
        sections: List<SectionEntity>,
        items: List<WorkoutItemEntity>,
    ) {
        insertWorkout(workout)
        deleteSectionsFor(workout.id)   // cascades to items
        insertSections(sections)
        insertItems(items)
    }
}
```

`AppDatabase.kt`:

```kotlin
package com.ottotech.workoutbuilder.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ExerciseEntity::class, ExerciseTagEntity::class,
        WorkoutEntity::class, SectionEntity::class, WorkoutItemEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "workout-builder.db",
            ).build().also { instance = it }
        }
    }
}
```

- [ ] **Step 3: Verify Room's annotation processor generates cleanly**

```bash
./gradlew :app:kspDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. Room reports schema errors at this stage — if it complains about
a missing index on a foreign key, add it rather than suppressing the warning.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/data/db/
git commit -m "feat: add Room database with exercise, tag and workout tables"
```

---

### Task 9: Exercise seed library

The one place where content volume means the plan specifies the **contract** and a worked
sample rather than all 100 rows. The test enforces correctness on the data.

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/data/seed/ExerciseSeed.kt`
- Test: `app/src/test/java/com/ottotech/workoutbuilder/data/ExerciseSeedTest.kt`

**Interfaces:**
- Consumes: `Exercise`, `Tag`, `Facet`, `Muscle`, `TagVocabulary`
- Produces: `ExerciseSeed.VERSION: Int`, `ExerciseSeed.exercises: List<Exercise>`

- [ ] **Step 1: Write the contract test first**

`ExerciseSeedTest.kt`:

```kotlin
package com.ottotech.workoutbuilder.data

import com.ottotech.workoutbuilder.data.seed.ExerciseSeed
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.domain.TagVocabulary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExerciseSeedTest {

    private val seed = ExerciseSeed.exercises

    /** Target counts from the design handoff. */
    private val targets = mapOf(
        Muscle.CHEST to 12, Muscle.BACK to 14, Muscle.LEGS to 18,
        Muscle.SHOULDERS to 10, Muscle.CORE to 11, Muscle.CARDIO to 8,
        Muscle.ARMS to 9, Muscle.MOBILITY to 15,
    )

    @Test
    fun `every muscle group meets its target count`() {
        targets.forEach { (muscle, target) ->
            val actual = seed.count { it.primaryMuscle == muscle }
            assertTrue(actual >= target, "$muscle has $actual, expected at least $target")
        }
    }

    @Test
    fun `ids are unique`() {
        assertEquals(seed.size, seed.map { it.id }.toSet().size)
    }

    @Test
    fun `ids are lowercase kebab slugs`() {
        val bad = seed.map { it.id }.filterNot { it.matches(Regex("^[a-z0-9]+(-[a-z0-9]+)*$")) }
        assertTrue(bad.isEmpty(), "malformed ids: $bad")
    }

    @Test
    fun `every exercise has at least one TYPE and one EQUIPMENT tag`() {
        val missing = seed.filterNot { ex ->
            ex.tags.any { it.facet == Facet.TYPE } && ex.tags.any { it.facet == Facet.EQUIPMENT }
        }
        assertTrue(missing.isEmpty(), "missing TYPE/EQUIPMENT: ${missing.map { it.id }}")
    }

    @Test
    fun `all tag values come from the controlled vocabulary`() {
        val invalid = seed.flatMap { ex -> ex.tags.filterNot { TagVocabulary.isValid(it) }.map { ex.id to it } }
        assertTrue(invalid.isEmpty(), "invalid tags: $invalid")
    }

    @Test
    fun `no exercise repeats its primary muscle as a secondary muscle tag`() {
        val offenders = seed.filter { ex ->
            ex.tags.any { it.facet == Facet.MUSCLE && it.value == ex.primaryMuscle.label }
        }
        assertTrue(offenders.isEmpty(), "primary repeated as secondary: ${offenders.map { it.id }}")
    }

    @Test
    fun `every exercise has a non-blank demo query`() {
        assertTrue(seed.none { it.demoQuery.isBlank() })
    }

    @Test
    fun `nothing in the seed is marked custom`() {
        assertTrue(seed.none { it.isCustom })
    }

    @Test
    fun `the eight Court Speed exercises are present with their verified urls`() {
        val expected = mapOf(
            "leg-swings" to "https://www.youtube.com/shorts/3l31E2cMGMk",
            "walking-lunge-twist" to "https://www.youtube.com/shorts/2crIg2iqi8w",
            "a-skips" to "https://www.youtube.com/shorts/fSZUFBbK7CE",
            "build-up-strides" to "https://www.youtube.com/watch?v=IQrbQMPbTtU",
            "lateral-shuffle" to "https://www.youtube.com/shorts/iQjCIghvheI",
            "skater-bounds" to "https://www.youtube.com/shorts/7-Qfb881k7o",
            "split-squat-jumps" to "https://www.youtube.com/shorts/e6s05vAhAZo",
            "squat-jumps-180" to "https://www.youtube.com/watch?v=_-Q5ywo24_s",
        )
        expected.forEach { (id, url) ->
            val ex = seed.firstOrNull { it.id == id }
            assertTrue(ex != null, "missing seeded exercise: $id")
            assertEquals(url, ex.demoUrl, "wrong demo url for $id")
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "*ExerciseSeedTest*"
```

Expected: FAIL — `Unresolved reference: ExerciseSeed`

- [ ] **Step 3: Create the seed file**

Start from this structure and worked sample, then extend to meet every target count in the
test. The helper keeps each row to one line.

```kotlin
package com.ottotech.workoutbuilder.data.seed

import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.domain.Tag

object ExerciseSeed {

    /** Bump when rows are added so first-run seeding can top up an existing install. */
    const val VERSION = 1

    private fun e(
        id: String,
        name: String,
        muscle: Muscle,
        type: List<String>,
        equipment: List<String>,
        also: List<Muscle> = emptyList(),
        pattern: List<String> = emptyList(),
        url: String? = null,
    ) = Exercise(
        id = id,
        name = name,
        primaryMuscle = muscle,
        tags = buildSet {
            type.forEach { add(Tag(Facet.TYPE, it)) }
            equipment.forEach { add(Tag(Facet.EQUIPMENT, it)) }
            also.forEach { add(Tag(Facet.MUSCLE, it.label)) }
            pattern.forEach { add(Tag(Facet.PATTERN, it)) }
        },
        demoQuery = "$name proper form",
        demoUrl = url,
    )

    val exercises: List<Exercise> = listOf(
        // ---- CHEST (target 12) ----
        e("barbell-bench-press", "Barbell Bench Press", Muscle.CHEST,
            type = listOf("Strength"), equipment = listOf("Barbell", "Bench"),
            also = listOf(Muscle.SHOULDERS, Muscle.ARMS), pattern = listOf("Push")),
        e("dumbbell-bench-press", "Dumbbell Bench Press", Muscle.CHEST,
            type = listOf("Strength"), equipment = listOf("Dumbbell", "Bench"),
            also = listOf(Muscle.SHOULDERS, Muscle.ARMS), pattern = listOf("Push")),
        e("push-up", "Push-up", Muscle.CHEST,
            type = listOf("Strength"), equipment = listOf("Bodyweight"),
            also = listOf(Muscle.CORE, Muscle.ARMS), pattern = listOf("Push")),
        // ... continue to 12

        // ---- BACK (target 14) ----
        e("pull-up", "Pull-up", Muscle.BACK,
            type = listOf("Strength"), equipment = listOf("Bodyweight", "Pull-up bar"),
            also = listOf(Muscle.ARMS), pattern = listOf("Pull")),
        e("barbell-row", "Barbell Row", Muscle.BACK,
            type = listOf("Strength"), equipment = listOf("Barbell"),
            also = listOf(Muscle.ARMS), pattern = listOf("Pull")),
        // ... continue to 14

        // ---- LEGS (target 18) ----
        e("back-squat", "Back Squat", Muscle.LEGS,
            type = listOf("Strength"), equipment = listOf("Barbell"),
            also = listOf(Muscle.CORE), pattern = listOf("Squat")),
        e("kettlebell-swing", "Kettlebell Swing", Muscle.LEGS,
            type = listOf("Strength", "HIIT"), equipment = listOf("Kettlebell"),
            also = listOf(Muscle.BACK, Muscle.CORE), pattern = listOf("Hinge")),
        e("lateral-shuffle", "Lateral Shuffle", Muscle.LEGS,
            type = listOf("HIIT"), equipment = listOf("Bodyweight"),
            pattern = listOf("Plyometric"), url = "https://www.youtube.com/shorts/iQjCIghvheI"),
        e("skater-bounds", "Skater Bounds", Muscle.LEGS,
            type = listOf("HIIT"), equipment = listOf("Bodyweight"),
            pattern = listOf("Plyometric"), url = "https://www.youtube.com/shorts/7-Qfb881k7o"),
        e("split-squat-jumps", "Split Squat Jumps", Muscle.LEGS,
            type = listOf("HIIT"), equipment = listOf("Bodyweight"),
            pattern = listOf("Plyometric", "Lunge"), url = "https://www.youtube.com/shorts/e6s05vAhAZo"),
        e("squat-jumps-180", "180° Squat Jumps", Muscle.LEGS,
            type = listOf("HIIT"), equipment = listOf("Bodyweight"),
            pattern = listOf("Plyometric", "Squat"), url = "https://www.youtube.com/watch?v=_-Q5ywo24_s"),
        e("walking-lunge-twist", "Walking Lunge + Twist", Muscle.LEGS,
            type = listOf("Mobility"), equipment = listOf("Bodyweight"),
            also = listOf(Muscle.CORE), pattern = listOf("Lunge"),
            url = "https://www.youtube.com/shorts/2crIg2iqi8w"),
        // ... continue to 18

        // ---- CARDIO (target 8) ----
        e("a-skips", "A-Skips", Muscle.CARDIO,
            type = listOf("HIIT"), equipment = listOf("Bodyweight"),
            url = "https://www.youtube.com/shorts/fSZUFBbK7CE"),
        e("build-up-strides", "Build-Up Strides", Muscle.CARDIO,
            type = listOf("HIIT"), equipment = listOf("Bodyweight"),
            url = "https://www.youtube.com/watch?v=IQrbQMPbTtU"),
        // ... continue to 8

        // ---- MOBILITY (target 15) ----
        e("leg-swings", "Leg Swings", Muscle.MOBILITY,
            type = listOf("Mobility"), equipment = listOf("Bodyweight"),
            also = listOf(Muscle.LEGS), url = "https://www.youtube.com/shorts/3l31E2cMGMk"),
        // ... continue to 15

        // ---- SHOULDERS (10), ARMS (9), CORE (11) ----
        // ... same pattern
    )
}
```

Note `squat-jumps-180` — the id is a slug, so the leading `180°` becomes a trailing `-180`
to satisfy the id-format test while the display name keeps the degree symbol.

- [ ] **Step 4: Run the contract test until it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "*ExerciseSeedTest*"
```

Expected: PASS, 9 tests. The failure messages name exactly which muscle group is short and
which tags are invalid — work through them until green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/data/seed/ app/src/test/
git commit -m "feat: add seeded exercise library with enforced tagging contract"
```

---

### Task 10: Repositories and first-run seeding

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/data/ExerciseRepository.kt`
- Create: `.../data/WorkoutRepository.kt`
- Create: `.../data/Mappers.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/MainActivity.kt`

**Interfaces:**
- Consumes: `AppDatabase`, `ExerciseDao`, `WorkoutDao`, `ExerciseSeed`, all domain models
- Produces:
  - `ExerciseRepository.observeAll(): Flow<List<Exercise>>`
  - `ExerciseRepository.seedIfNeeded()`
  - `ExerciseRepository.setDemoUrl(id: String, url: String?)`
  - `WorkoutRepository.observeAll(): Flow<List<Workout>>` (headers only, no sections)
  - `WorkoutRepository.load(id: String): Workout?` (fully hydrated)
  - `WorkoutRepository.save(workout: Workout)`
  - `WorkoutRepository.delete(id: String)`
  - `WorkoutRepository.markPerformed(id: String)`

- [ ] **Step 1: Write the entity/domain mappers**

`Mappers.kt`:

```kotlin
package com.ottotech.workoutbuilder.data

import com.ottotech.workoutbuilder.data.db.*
import com.ottotech.workoutbuilder.domain.*
import java.time.Instant

fun Exercise.toEntity() = ExerciseEntity(
    id = id, name = name, primaryMuscle = primaryMuscle.name,
    demoQuery = demoQuery, demoUrl = demoUrl, isCustom = isCustom,
)

fun Exercise.toTagEntities() = tags.map { ExerciseTagEntity(id, it.facet.name, it.value) }

fun ExerciseEntity.toDomain(tags: List<ExerciseTagEntity>) = Exercise(
    id = id, name = name, primaryMuscle = Muscle.valueOf(primaryMuscle),
    tags = tags.map { Tag(Facet.valueOf(it.facet), it.value) }.toSet(),
    demoQuery = demoQuery, demoUrl = demoUrl, isCustom = isCustom,
)

fun Rest?.mode(): String? = when (this) {
    is Rest.Timed -> "TIMED"
    Rest.Manual -> "MANUAL"
    null -> null
}

fun Rest?.seconds(): Int? = (this as? Rest.Timed)?.seconds

fun restFrom(mode: String?, seconds: Int?): Rest? = when (mode) {
    "TIMED" -> Rest.Timed(seconds ?: 0)
    "MANUAL" -> Rest.Manual
    else -> null
}

fun Workout.toEntity() = WorkoutEntity(
    id = id, name = name, type = type.name,
    createdAt = createdAt.toEpochMilli(), lastPerformedAt = lastPerformedAt?.toEpochMilli(),
)

fun WorkoutEntity.toDomain(sections: List<Section>) = Workout(
    id = id, name = name, type = TrainingType.valueOf(type),
    createdAt = Instant.ofEpochMilli(createdAt),
    lastPerformedAt = lastPerformedAt?.let { Instant.ofEpochMilli(it) },
    sections = sections,
)

fun Section.toEntity(workoutId: String, orderIndex: Int) = SectionEntity(
    id = id, workoutId = workoutId, orderIndex = orderIndex, name = name, rounds = rounds,
    restBetweenExercisesMode = restBetweenExercises.mode(),
    restBetweenExercisesSec = restBetweenExercises.seconds(),
    restBetweenRoundsMode = restBetweenRounds.mode(),
    restBetweenRoundsSec = restBetweenRounds.seconds(),
    restAfterSectionMode = restAfterSection.mode(),
    restAfterSectionSec = restAfterSection.seconds(),
)

fun SectionEntity.toDomain(items: List<WorkoutItem>) = Section(
    id = id, name = name, rounds = rounds,
    restBetweenExercises = restFrom(restBetweenExercisesMode, restBetweenExercisesSec),
    restBetweenRounds = restFrom(restBetweenRoundsMode, restBetweenRoundsSec),
    restAfterSection = restFrom(restAfterSectionMode, restAfterSectionSec),
    items = items,
)

fun WorkoutItem.toEntity(sectionId: String, orderIndex: Int) = WorkoutItemEntity(
    id = id, sectionId = sectionId, orderIndex = orderIndex, exerciseId = exerciseId,
    mode = mode.name, durationSec = durationSec, sets = sets, reps = reps,
    weightKg = weightKg, note = note,
)

fun WorkoutItemEntity.toDomain() = WorkoutItem(
    id = id, exerciseId = exerciseId, mode = ItemMode.valueOf(mode),
    durationSec = durationSec, sets = sets, reps = reps, weightKg = weightKg, note = note,
)
```

- [ ] **Step 2: Write the repositories**

`ExerciseRepository.kt`:

```kotlin
package com.ottotech.workoutbuilder.data

import com.ottotech.workoutbuilder.data.db.ExerciseDao
import com.ottotech.workoutbuilder.data.seed.ExerciseSeed
import com.ottotech.workoutbuilder.domain.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ExerciseRepository(private val dao: ExerciseDao) {

    fun observeAll(): Flow<List<Exercise>> =
        combine(dao.observeAll(), dao.observeAllTags()) { exercises, tags ->
            val byExercise = tags.groupBy { it.exerciseId }
            exercises.map { it.toDomain(byExercise[it.id].orEmpty()) }
        }

    /** Inserts any seed row not already present. REPLACE conflict strategy makes this idempotent. */
    suspend fun seedIfNeeded() {
        val existing = dao.getAll().associateBy { it.id }
        val missing = ExerciseSeed.exercises.filter { it.id !in existing }
        if (missing.isEmpty()) return
        dao.insertAll(missing.map { it.toEntity() }, missing.flatMap { it.toTagEntities() })
    }

    suspend fun setDemoUrl(id: String, url: String?) = dao.setDemoUrl(id, url)
}
```

`WorkoutRepository.kt`:

```kotlin
package com.ottotech.workoutbuilder.data

import com.ottotech.workoutbuilder.data.db.WorkoutDao
import com.ottotech.workoutbuilder.domain.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class WorkoutRepository(private val dao: WorkoutDao) {

    /** Headers only — sections are not hydrated. Use [load] for a full workout. */
    fun observeAll(): Flow<List<Workout>> =
        dao.observeWorkouts().map { rows -> rows.map { it.toDomain(emptyList()) } }

    suspend fun load(id: String): Workout? {
        val workout = dao.getWorkout(id) ?: return null
        val sections = dao.getSections(id)
        val items = dao.getItems(sections.map { it.id }).groupBy { it.sectionId }
        return workout.toDomain(
            sections.map { section ->
                section.toDomain(items[section.id].orEmpty().map { it.toDomain() })
            },
        )
    }

    suspend fun save(workout: Workout) {
        val sections = workout.sections.mapIndexed { i, s -> s.toEntity(workout.id, i) }
        val items = workout.sections.flatMap { section ->
            section.items.mapIndexed { i, item -> item.toEntity(section.id, i) }
        }
        dao.upsertFull(workout.toEntity(), sections, items)
    }

    suspend fun delete(id: String) = dao.deleteWorkout(id)

    suspend fun markPerformed(id: String) =
        dao.markPerformed(id, Instant.now().toEpochMilli())
}
```

- [ ] **Step 3: Seed on first launch**

Replace `MainActivity.kt`:

```kotlin
package com.ottotech.workoutbuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.ottotech.workoutbuilder.data.ExerciseRepository
import com.ottotech.workoutbuilder.data.db.AppDatabase
import com.ottotech.workoutbuilder.ui.theme.WorkoutTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.get(this)
        val exercises = ExerciseRepository(db.exerciseDao())

        var seeded by mutableIntStateOf(-1)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { exercises.seedIfNeeded() }
            seeded = withContext(Dispatchers.IO) { db.exerciseDao().count() }
        }

        setContent {
            WorkoutTheme {
                Box(Modifier.fillMaxSize()) {
                    Text(if (seeded < 0) "Seeding…" else "Library: $seeded exercises")
                }
            }
        }
    }
}
```

- [ ] **Step 4: Build, install and verify seeding on a device**

```bash
./gradlew :app:installDebug
```

Expected: the app launches and shows `Library: N exercises`, where N matches
`ExerciseSeed.exercises.size`. Launch it a second time — the count must be identical, proving
seeding is idempotent and does not duplicate rows.

- [ ] **Step 5: Run the whole suite**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS — all 46 tests across the six test classes.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/
git commit -m "feat: add repositories and idempotent first-run library seeding"
```

---

## Done when

- `./gradlew :app:testDebugUnitTest` is green across all six test classes
- The Court Speed fixture compiles to exactly 40 segments
- The app installs, seeds the library once, and reports the exercise count
- `domain/` contains no Android imports (enforced by `DomainPurityTest`)

## Next

Plan 1B — Home & Library screens, built on `ExerciseRepository.observeAll()`,
`ExerciseFilter.apply`, `ExerciseFilter.countsByMuscle`, `WorkoutRepository.observeAll()` and
`DurationEstimator.estimateMinutes`.
