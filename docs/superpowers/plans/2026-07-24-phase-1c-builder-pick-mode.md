# Phase 1C — Builder & Library Pick Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fill the Build sheet's "Build it yourself" seam with a Builder that creates a workout
from scratch — name it, add sections that repeat for rounds, add exercises picked from the
Library reused in pick mode, set each exercise's mode and fields, set rest between exercises /
rounds / sections — then save it so it appears on Home.

**Architecture:** Same shape as Plan 1B. Every edit the Builder makes is a **pure function** in
`domain/WorkoutDraft.kt` that takes a `Workout` and returns a new one; those functions are
unit-tested on the JVM before any UI exists. `BuilderViewModel` holds the draft in a
`MutableStateFlow<Workout>` and does nothing but call those pure functions and persist the
result through the existing `WorkoutRepository.save`. The Library screens from 1B are reused
verbatim in pick mode via new navigation routes — no fork of `LibraryScreen`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Navigation Compose, Lifecycle ViewModel
Compose, Room, JUnit 4 + kotlin.test. **No new dependencies.**

**Spec:** `docs/superpowers/specs/2026-07-21-workout-builder-android-design.md` — §3 (data model),
§5 (tokens), §6.2 (Build sheet), §6.3 (Library pick mode), §6.4 (Builder), §12 (deviations).

**Builds on:** `docs/superpowers/plans/2026-07-21-phase-1b-home-library.md`, merged to `main`.
Read the actual files before starting — they are authoritative over any snippet reproduced here.
In particular: `data/WorkoutRepository.kt` (`save` upserts the whole workout by id, replacing its
sections and items — `WorkoutDao.upsertFull`), `ui/library/LibraryViewModel.kt`,
`ui/library/CategoryScreen.kt`, `ui/library/LibraryScreen.kt`, `ui/components/Controls.kt`, and
`ui/AppNav.kt`.

---

## Global Constraints

Everything from Plans 1A and 1B still applies. Repeated here because a task's implementer sees
only their own task:

- Package `com.ottotech.workoutbuilder`
- **Toolchain is fixed:** Gradle 9.4.1, AGP 9.2.1, Kotlin 2.2.10, Compose BOM 2026.02.01, Nav
  2.9.6, Lifecycle 2.9.4. **Do not bump these and do not add dependencies** — 1C needs none.
- **Every Gradle command needs `JAVA_HOME` exported first** — there is no `java` on PATH:
  `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`
- `minSdk 26`, `compileSdk` / `targetSdk` 36
- **`domain/` must contain zero `android.*` and zero Compose imports.** `DomainPurityTest`
  enforces this and fails the build if a UI type lands in `domain/`.
- **Every colour is read from `LocalAppColors.current`.** No literal hex outside
  `ui/theme/Colors.kt`.
- **All sizes and radii come from `Dimens`; all text styles from `AppType`.** If a value you need
  is missing, add it to those files rather than inlining a number.
- **No shadows anywhere.** Hairline (1dp) borders only.
- **Mono accent rule:** the accent is maximum contrast against its own surface —
  `onSurfaceInverse` on `surfaceInverse`, `onBackground` on `background`. There is no accent hue.
- **No icon-font or icon-library dependency.** Chevrons, plus, minus and close are text glyphs
  (`‹`, `›`, `+`, `−`, `✕`, `↑`, `↓`), exactly as the HTML prototype and 1B use them.
- Tag values come only from `TagVocabulary` — no free text. (Applies to exercises; the Builder
  does not invent exercises in 1C.)
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

Baseline entering this plan: **76 tests, 0 failures** across eight classes.

### Device

The emulator is sufficient for all of 1C — these are screens and data, no audio and no background
service (that is Plan 1D). A Pixel 8 Pro AVD on API 36 exists; launch it from Android Studio's
Device Manager, then `./gradlew :app:installDebug`.

`adb`: `/c/Users/ottol/AppData/Local/Android/Sdk/platform-tools/adb.exe`

---

## Scope decisions

Read these before starting; they explain what 1C does and does not build.

**1. Reordering is by move controls, not drag.** Spec §6.4 says "reorder by drag". This plan
ships **`↑` / `↓` move controls** on each section and item instead. Compose has no first-party
drag-reorder and this project deliberately avoids third-party dependencies (it hand-draws its
icons). The reorder *logic* — `moveSection` / `moveItem` — is identical either way and is pure and
tested, so true drag can be layered on in a later motion pass without touching the domain. **This
is a documented deviation from the handoff**, recorded in STATUS.md in Task 5.

**2. Delete is an explicit `✕` with an Undo bar.** Also faithful to "delete with undo" from §6.4,
implemented reliably: deleting a section or item stashes a snapshot of the whole draft and shows
an Undo bar for a few seconds. No swipe gesture, no `SwipeToDismissBox`.

**3. The Builder creates new workouts only.** Editing an existing saved workout uses the exact
same draft functions and `save` path, but there is no entry point to reach it yet — Home rows are
reserved for "start workout" (Plan 1D). Loading an existing workout into the Builder is left as a
seam for a later plan. 1C's entry point is the Build sheet's "Build it yourself" card.

**4. The Coach seam stays a no-op.** The Build sheet's "Ask the Coach" card remains a `TODO(Plan
2)` no-op.

**5. Sections render always-expanded.** Spec §6.4 says "collapsible sections". This plan ships
them fully expanded — collapse/expand is per-section UI state and a header tap target with no
effect on the data model, so it is a later polish detail, not a core-loop requirement. Documented
as a deviation in STATUS.md.

---

## File Structure

```
app/src/main/java/com/ottotech/workoutbuilder/
  domain/
    WorkoutDraft.kt              NEW  — pure draft transforms + validation (RestSlot, MoveDirection)
  ui/
    AppNav.kt                    MODIFY — Builder + pick routes; wire the Build sheet seam
    builder/
      BuilderViewModel.kt        NEW  — holds the draft, calls WorkoutDraft, saves, undo, pick plumbing
      BuilderScreen.kt           NEW  — name, type, sections, rests, reorder, save, undo bar, item rows
    library/
      CategoryScreen.kt          MODIFY — optional pickMode (trailing +, tap picks)

app/src/test/java/com/ottotech/workoutbuilder/
  domain/WorkoutDraftTest.kt     NEW  — one test per transform + each validation rule
```

Task 4 rewrites the `ItemRow` composable inside `BuilderScreen.kt` to add the editors; no new
files. `LibraryScreen.kt`, `FiltersSheet.kt`, `LibraryViewModel.kt`, `Controls.kt` and
`WorkoutRepository.kt` are reused **unchanged**.

---

### Task 1: Builder draft domain

Every edit the Builder makes, written test-first as pure functions on `Workout`. IDs for new
sections and items are **passed in** rather than generated inside, so the functions stay pure and
the tests stay deterministic — the ViewModel generates UUIDs and hands them over, exactly as
`WorkoutSeed` passes explicit ids.

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/domain/WorkoutDraft.kt`
- Test: `app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutDraftTest.kt`

**Interfaces:**
- Consumes: `Workout`, `Section`, `WorkoutItem`, `Rest`, `ItemMode`, `TrainingType` (all Plan 1A)
- Produces:
  - `enum class RestSlot { BETWEEN_EXERCISES, BETWEEN_ROUNDS, AFTER_SECTION }`
  - `enum class MoveDirection { UP, DOWN }`
  - `object WorkoutDraft` with, all `(...): Workout` unless noted:
    - `newWorkout(id: String, createdAt: Instant)`
    - `rename(workout, name: String)`, `setType(workout, type: TrainingType)`
    - `addSection(workout, sectionId: String)`, `removeSection(workout, sectionId: String)`
    - `moveSection(workout, sectionId: String, direction: MoveDirection)`
    - `renameSection(workout, sectionId: String, name: String)`
    - `setRounds(workout, sectionId: String, rounds: Int)`
    - `setRest(workout, sectionId: String, slot: RestSlot, rest: Rest?)`
    - `addItem(workout, sectionId: String, itemId: String, exerciseId: String)`
    - `removeItem(workout, sectionId: String, itemId: String)`
    - `moveItem(workout, sectionId: String, itemId: String, direction: MoveDirection)`
    - `setItemMode(workout, sectionId: String, itemId: String, mode: ItemMode)`
    - `setDuration(workout, sectionId: String, itemId: String, durationSec: Int)`
    - `setSetsReps(workout, sectionId: String, itemId: String, sets: Int, reps: Int)`
    - `setWeight(workout, sectionId: String, itemId: String, weightKg: Float?)`
    - `setNote(workout, sectionId: String, itemId: String, note: String?)`
    - `saveBlockers(workout): List<String>`, `canSave(workout): Boolean`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutDraftTest.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutDraftTest {

    private fun blank() = WorkoutDraft.newWorkout("w", Instant.EPOCH)

    /** A workout with one section "s" holding one default (timed 30s) item "i". */
    private fun oneItem(): Workout =
        WorkoutDraft.addItem(WorkoutDraft.addSection(blank(), "s"), "s", "i", "push-up")

    // ---- newWorkout ----

    @Test
    fun `a new workout is empty with the given id and no sections`() {
        val w = WorkoutDraft.newWorkout("abc", Instant.ofEpochSecond(5))
        assertEquals("abc", w.id)
        assertEquals("", w.name)
        assertEquals(TrainingType.STRENGTH, w.type)
        assertEquals(Instant.ofEpochSecond(5), w.createdAt)
        assertNull(w.lastPerformedAt)
        assertTrue(w.sections.isEmpty())
    }

    // ---- rename / setType ----

    @Test
    fun `rename sets the name`() {
        assertEquals("Leg Day", WorkoutDraft.rename(blank(), "Leg Day").name)
    }

    @Test
    fun `setType sets the type`() {
        assertEquals(TrainingType.HIIT, WorkoutDraft.setType(blank(), TrainingType.HIIT).type)
    }

    // ---- addSection ----

    @Test
    fun `addSection appends a section named for its position`() {
        val w = WorkoutDraft.addSection(blank(), "s1")
        assertEquals(1, w.sections.size)
        assertEquals("s1", w.sections[0].id)
        assertEquals("Section 1", w.sections[0].name)
        assertEquals(1, w.sections[0].rounds)
    }

    @Test
    fun `addSection numbers by the current count`() {
        val w = WorkoutDraft.addSection(WorkoutDraft.addSection(blank(), "s1"), "s2")
        assertEquals(listOf("Section 1", "Section 2"), w.sections.map { it.name })
    }

    // ---- removeSection ----

    @Test
    fun `removeSection drops the section`() {
        val w = WorkoutDraft.addSection(WorkoutDraft.addSection(blank(), "s1"), "s2")
        assertEquals(listOf("s2"), WorkoutDraft.removeSection(w, "s1").sections.map { it.id })
    }

    @Test
    fun `removeSection of an unknown id is a no-op`() {
        val w = WorkoutDraft.addSection(blank(), "s1")
        assertEquals(1, WorkoutDraft.removeSection(w, "nope").sections.size)
    }

    // ---- moveSection ----

    @Test
    fun `moveSection UP swaps with the previous section`() {
        val w = WorkoutDraft.addSection(WorkoutDraft.addSection(blank(), "s1"), "s2")
        val out = WorkoutDraft.moveSection(w, "s2", MoveDirection.UP)
        assertEquals(listOf("s2", "s1"), out.sections.map { it.id })
    }

    @Test
    fun `moveSection UP at the top is a no-op`() {
        val w = WorkoutDraft.addSection(WorkoutDraft.addSection(blank(), "s1"), "s2")
        val out = WorkoutDraft.moveSection(w, "s1", MoveDirection.UP)
        assertEquals(listOf("s1", "s2"), out.sections.map { it.id })
    }

    @Test
    fun `moveSection DOWN at the bottom is a no-op`() {
        val w = WorkoutDraft.addSection(WorkoutDraft.addSection(blank(), "s1"), "s2")
        val out = WorkoutDraft.moveSection(w, "s2", MoveDirection.DOWN)
        assertEquals(listOf("s1", "s2"), out.sections.map { it.id })
    }

    // ---- renameSection / setRounds ----

    @Test
    fun `renameSection changes only the target`() {
        val w = WorkoutDraft.addSection(WorkoutDraft.addSection(blank(), "s1"), "s2")
        val out = WorkoutDraft.renameSection(w, "s2", "Sprints")
        assertEquals(listOf("Section 1", "Sprints"), out.sections.map { it.name })
    }

    @Test
    fun `setRounds sets the count`() {
        val w = WorkoutDraft.addSection(blank(), "s1")
        assertEquals(3, WorkoutDraft.setRounds(w, "s1", 3).sections[0].rounds)
    }

    @Test
    fun `setRounds clamps below one to one`() {
        val w = WorkoutDraft.addSection(blank(), "s1")
        assertEquals(1, WorkoutDraft.setRounds(w, "s1", 0).sections[0].rounds)
    }

    // ---- setRest ----

    @Test
    fun `setRest sets a timed rest in the requested slot`() {
        val w = WorkoutDraft.addSection(blank(), "s1")
        val out = WorkoutDraft.setRest(w, "s1", RestSlot.BETWEEN_EXERCISES, Rest.Timed(15))
        assertEquals(Rest.Timed(15), out.sections[0].restBetweenExercises)
    }

    @Test
    fun `setRest null clears the slot`() {
        val w = WorkoutDraft.setRest(
            WorkoutDraft.addSection(blank(), "s1"), "s1", RestSlot.BETWEEN_ROUNDS, Rest.Manual,
        )
        val out = WorkoutDraft.setRest(w, "s1", RestSlot.BETWEEN_ROUNDS, null)
        assertNull(out.sections[0].restBetweenRounds)
    }

    @Test
    fun `setRest touches only its own slot`() {
        val w = WorkoutDraft.addSection(blank(), "s1")
        val out = WorkoutDraft.setRest(w, "s1", RestSlot.AFTER_SECTION, Rest.Timed(60))
        assertEquals(Rest.Timed(60), out.sections[0].restAfterSection)
        assertNull(out.sections[0].restBetweenExercises)
        assertNull(out.sections[0].restBetweenRounds)
    }

    // ---- addItem ----

    @Test
    fun `addItem appends a timed item defaulting to thirty seconds`() {
        val item = oneItem().sections[0].items.single()
        assertEquals("i", item.id)
        assertEquals("push-up", item.exerciseId)
        assertEquals(ItemMode.TIMED, item.mode)
        assertEquals(30, item.durationSec)
    }

    // ---- removeItem / moveItem ----

    @Test
    fun `removeItem drops the item`() {
        val w = WorkoutDraft.addItem(oneItem(), "s", "i2", "air-squat")
        assertEquals(listOf("i2"), WorkoutDraft.removeItem(w, "s", "i").sections[0].items.map { it.id })
    }

    @Test
    fun `moveItem DOWN swaps with the next item`() {
        val w = WorkoutDraft.addItem(oneItem(), "s", "i2", "air-squat")
        val out = WorkoutDraft.moveItem(w, "s", "i", MoveDirection.DOWN)
        assertEquals(listOf("i2", "i"), out.sections[0].items.map { it.id })
    }

    @Test
    fun `moveItem UP at the top is a no-op`() {
        val w = WorkoutDraft.addItem(oneItem(), "s", "i2", "air-squat")
        val out = WorkoutDraft.moveItem(w, "s", "i", MoveDirection.UP)
        assertEquals(listOf("i", "i2"), out.sections[0].items.map { it.id })
    }

    // ---- setItemMode ----

    @Test
    fun `switching to REPS sets default sets and reps`() {
        val item = WorkoutDraft.setItemMode(oneItem(), "s", "i", ItemMode.REPS).sections[0].items.single()
        assertEquals(ItemMode.REPS, item.mode)
        assertEquals(3, item.sets)
        assertEquals(10, item.reps)
    }

    @Test
    fun `switching to TIMED restores a default duration`() {
        val manual = WorkoutDraft.setItemMode(oneItem(), "s", "i", ItemMode.MANUAL)
        val timed = WorkoutDraft.setItemMode(manual, "s", "i", ItemMode.TIMED).sections[0].items.single()
        assertEquals(ItemMode.TIMED, timed.mode)
        assertEquals(30, timed.durationSec)
    }

    @Test
    fun `switching to MANUAL keeps the workout saveable`() {
        val w = WorkoutDraft.rename(
            WorkoutDraft.setItemMode(oneItem(), "s", "i", ItemMode.MANUAL), "M",
        )
        assertTrue(WorkoutDraft.canSave(w))
    }

    // ---- field setters ----

    @Test
    fun `setDuration updates the item`() {
        assertEquals(45, WorkoutDraft.setDuration(oneItem(), "s", "i", 45).sections[0].items.single().durationSec)
    }

    @Test
    fun `setSetsReps updates both`() {
        val item = WorkoutDraft.setSetsReps(oneItem(), "s", "i", 5, 8).sections[0].items.single()
        assertEquals(5, item.sets)
        assertEquals(8, item.reps)
    }

    @Test
    fun `setWeight sets and clears`() {
        val set = WorkoutDraft.setWeight(oneItem(), "s", "i", 20f)
        assertEquals(20f, set.sections[0].items.single().weightKg)
        assertNull(WorkoutDraft.setWeight(set, "s", "i", null).sections[0].items.single().weightKg)
    }

    @Test
    fun `setNote sets and blank clears`() {
        val set = WorkoutDraft.setNote(oneItem(), "s", "i", "touch the line")
        assertEquals("touch the line", set.sections[0].items.single().note)
        assertNull(WorkoutDraft.setNote(set, "s", "i", "   ").sections[0].items.single().note)
    }

    // ---- validation ----

    @Test
    fun `a blank name blocks saving`() {
        val w = oneItem() // name is still ""
        assertTrue("Name your workout" in WorkoutDraft.saveBlockers(w))
        assertFalse(WorkoutDraft.canSave(w))
    }

    @Test
    fun `a workout with no items blocks saving`() {
        val w = WorkoutDraft.rename(WorkoutDraft.addSection(blank(), "s"), "Empty")
        assertTrue("Add at least one exercise" in WorkoutDraft.saveBlockers(w))
    }

    @Test
    fun `a timed item with no duration blocks saving`() {
        val w = WorkoutDraft.setDuration(WorkoutDraft.rename(oneItem(), "T"), "s", "i", 0)
        assertTrue("Set a duration for every timed exercise" in WorkoutDraft.saveBlockers(w))
    }

    @Test
    fun `a reps item missing reps blocks saving`() {
        val reps = WorkoutDraft.setItemMode(WorkoutDraft.rename(oneItem(), "R"), "s", "i", ItemMode.REPS)
        val bad = WorkoutDraft.setSetsReps(reps, "s", "i", 3, 0)
        assertTrue("Set sets and reps for every rep exercise" in WorkoutDraft.saveBlockers(bad))
    }

    @Test
    fun `a named workout with one valid item can save`() {
        val w = WorkoutDraft.rename(oneItem(), "Court Speed")
        assertEquals(emptyList(), WorkoutDraft.saveBlockers(w))
        assertTrue(WorkoutDraft.canSave(w))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest --tests "*WorkoutDraftTest*"
```

Expected: FAIL — `Unresolved reference: WorkoutDraft`

- [ ] **Step 3: Implement**

`app/src/main/java/com/ottotech/workoutbuilder/domain/WorkoutDraft.kt`:

```kotlin
package com.ottotech.workoutbuilder.domain

import java.time.Instant

/** Which of a section's three rest slots an edit targets. */
enum class RestSlot { BETWEEN_EXERCISES, BETWEEN_ROUNDS, AFTER_SECTION }

/** Reorder direction for the Builder's move controls. */
enum class MoveDirection { UP, DOWN }

/**
 * Pure edits on a workout draft. The Builder's ViewModel holds a `MutableStateFlow<Workout>`
 * and does nothing but call these; every rule that could be wrong — round clamping, rest
 * placement, mode defaults, save validation — lives here where a JVM test can pin it in
 * milliseconds, exactly like `ExerciseFilter`'s FilterState transitions.
 *
 * IDs for new sections and items are passed in, not generated here, so these functions are
 * deterministic and testable. The ViewModel supplies `UUID.randomUUID().toString()`.
 */
object WorkoutDraft {

    private const val DEFAULT_DURATION_SEC = 30
    private const val DEFAULT_SETS = 3
    private const val DEFAULT_REPS = 10

    fun newWorkout(id: String, createdAt: Instant): Workout =
        Workout(id = id, name = "", type = TrainingType.STRENGTH, createdAt = createdAt)

    fun rename(workout: Workout, name: String): Workout = workout.copy(name = name)

    fun setType(workout: Workout, type: TrainingType): Workout = workout.copy(type = type)

    // ---- sections ----

    fun addSection(workout: Workout, sectionId: String): Workout =
        workout.copy(
            sections = workout.sections + Section(id = sectionId, name = "Section ${workout.sections.size + 1}"),
        )

    fun removeSection(workout: Workout, sectionId: String): Workout =
        workout.copy(sections = workout.sections.filterNot { it.id == sectionId })

    fun moveSection(workout: Workout, sectionId: String, direction: MoveDirection): Workout {
        val index = workout.sections.indexOfFirst { it.id == sectionId }
        return workout.copy(sections = workout.sections.movedOrSame(index, direction))
    }

    fun renameSection(workout: Workout, sectionId: String, name: String): Workout =
        mapSection(workout, sectionId) { it.copy(name = name) }

    fun setRounds(workout: Workout, sectionId: String, rounds: Int): Workout =
        mapSection(workout, sectionId) { it.copy(rounds = rounds.coerceAtLeast(1)) }

    fun setRest(workout: Workout, sectionId: String, slot: RestSlot, rest: Rest?): Workout =
        mapSection(workout, sectionId) { section ->
            when (slot) {
                RestSlot.BETWEEN_EXERCISES -> section.copy(restBetweenExercises = rest)
                RestSlot.BETWEEN_ROUNDS -> section.copy(restBetweenRounds = rest)
                RestSlot.AFTER_SECTION -> section.copy(restAfterSection = rest)
            }
        }

    // ---- items ----

    fun addItem(workout: Workout, sectionId: String, itemId: String, exerciseId: String): Workout =
        mapSection(workout, sectionId) { section ->
            section.copy(
                items = section.items + WorkoutItem(
                    id = itemId, exerciseId = exerciseId,
                    mode = ItemMode.TIMED, durationSec = DEFAULT_DURATION_SEC,
                ),
            )
        }

    fun removeItem(workout: Workout, sectionId: String, itemId: String): Workout =
        mapSection(workout, sectionId) { it.copy(items = it.items.filterNot { item -> item.id == itemId }) }

    fun moveItem(workout: Workout, sectionId: String, itemId: String, direction: MoveDirection): Workout =
        mapSection(workout, sectionId) { section ->
            val index = section.items.indexOfFirst { it.id == itemId }
            section.copy(items = section.items.movedOrSame(index, direction))
        }

    fun setItemMode(workout: Workout, sectionId: String, itemId: String, mode: ItemMode): Workout =
        mapItem(workout, sectionId, itemId) { item ->
            when (mode) {
                // Fill in the fields the new mode requires so a mode switch never leaves the
                // item in a state saveBlockers would reject.
                ItemMode.TIMED -> item.copy(mode = mode, durationSec = item.durationSec ?: DEFAULT_DURATION_SEC)
                ItemMode.REPS -> item.copy(
                    mode = mode,
                    sets = item.sets ?: DEFAULT_SETS,
                    reps = item.reps ?: DEFAULT_REPS,
                )
                ItemMode.MANUAL -> item.copy(mode = mode)
            }
        }

    fun setDuration(workout: Workout, sectionId: String, itemId: String, durationSec: Int): Workout =
        mapItem(workout, sectionId, itemId) { it.copy(durationSec = durationSec) }

    fun setSetsReps(workout: Workout, sectionId: String, itemId: String, sets: Int, reps: Int): Workout =
        mapItem(workout, sectionId, itemId) { it.copy(sets = sets, reps = reps) }

    fun setWeight(workout: Workout, sectionId: String, itemId: String, weightKg: Float?): Workout =
        mapItem(workout, sectionId, itemId) { it.copy(weightKg = weightKg) }

    fun setNote(workout: Workout, sectionId: String, itemId: String, note: String?): Workout =
        mapItem(workout, sectionId, itemId) { it.copy(note = note?.takeIf { text -> text.isNotBlank() }) }

    // ---- validation ----

    /**
     * Human-readable reasons the workout cannot be saved yet, empty when it is ready. The
     * Builder disables SAVE and shows the first blocker while this is non-empty.
     */
    fun saveBlockers(workout: Workout): List<String> = buildList {
        if (workout.name.isBlank()) add("Name your workout")
        val items = workout.sections.flatMap { it.items }
        if (items.isEmpty()) add("Add at least one exercise")
        if (items.any { it.mode == ItemMode.TIMED && (it.durationSec == null || it.durationSec <= 0) }) {
            add("Set a duration for every timed exercise")
        }
        if (items.any { it.mode == ItemMode.REPS && (it.sets == null || it.sets <= 0 || it.reps == null || it.reps <= 0) }) {
            add("Set sets and reps for every rep exercise")
        }
    }

    fun canSave(workout: Workout): Boolean = saveBlockers(workout).isEmpty()

    // ---- helpers ----

    private fun mapSection(workout: Workout, sectionId: String, transform: (Section) -> Section): Workout =
        workout.copy(sections = workout.sections.map { if (it.id == sectionId) transform(it) else it })

    private fun mapItem(
        workout: Workout,
        sectionId: String,
        itemId: String,
        transform: (WorkoutItem) -> WorkoutItem,
    ): Workout = mapSection(workout, sectionId) { section ->
        section.copy(items = section.items.map { if (it.id == itemId) transform(it) else it })
    }

    /** Swap the element at [index] one step in [direction], or return the list unchanged. */
    private fun <T> List<T>.movedOrSame(index: Int, direction: MoveDirection): List<T> {
        if (index < 0) return this
        val target = when (direction) {
            MoveDirection.UP -> index - 1
            MoveDirection.DOWN -> index + 1
        }
        if (target !in indices) return this
        return toMutableList().apply {
            val moved = removeAt(index)
            add(target, moved)
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "*WorkoutDraftTest*" --tests "*DomainPurityTest*"
```

Expected: PASS — 33 tests (32 draft + 1 purity)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/domain/WorkoutDraft.kt \
  app/src/test/java/com/ottotech/workoutbuilder/domain/WorkoutDraftTest.kt
git commit -m "feat: add workout draft transforms and save validation"
```

---

### Task 2: Builder ViewModel and section-level screen

The Builder becomes reachable from the Build sheet and can name a workout, add/rename/reorder/
delete sections, set rounds and rests, and shows the SAVE gate with its blocker hint. Adding
exercises is a seam wired in Task 3, so sections start empty and SAVE is correctly blocked with
"Add at least one exercise" until then. The item editors arrive in Task 4.

**Files:**
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/builder/BuilderViewModel.kt`
- Create: `app/src/main/java/com/ottotech/workoutbuilder/ui/builder/BuilderScreen.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/AppNav.kt`

**Interfaces:**
- Consumes: `WorkoutDraft` + `RestSlot` + `MoveDirection`, `WorkoutRepository.save`,
  `ExerciseRepository.observeAll`, `Rest`, `ItemMode`, `TrainingType`, `Exercise`, `WorkoutApp`,
  `ScreenHeader`, `AppTextField`, `Chip`, `SectionLabel`, `InverseButton`, `HairlineCard`
- Produces:
  - `BuilderUiState(workout, exercises, saveBlockers, canUndo)` with `val canSave: Boolean` and
    `fun exerciseName(id: String): String`
  - `BuilderViewModel` with `state: StateFlow<BuilderUiState>`; methods `onRename`, `onSetType`,
    `onAddSection`, `onRemoveSection`, `onMoveSection`, `onRenameSection`, `onSetRounds`,
    `onSetRest`, `beginPick`, `onExercisePicked`, `onRemoveItem`, `onMoveItem`, `onSetItemMode`,
    `onSetDuration`, `onSetSetsReps`, `onSetWeight`, `onSetNote`, `onUndo`, `onUndoExpired`,
    `save(onSaved: () -> Unit)`; and `companion object { val Factory }`
  - `BuilderScreen(viewModel, onClose, onAddExercise: (sectionId: String) -> Unit, onSaved: () -> Unit)`

- [ ] **Step 1: Write the ViewModel**

`app/src/main/java/com/ottotech/workoutbuilder/ui/builder/BuilderViewModel.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ottotech.workoutbuilder.WorkoutApp
import com.ottotech.workoutbuilder.data.ExerciseRepository
import com.ottotech.workoutbuilder.data.WorkoutRepository
import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.ItemMode
import com.ottotech.workoutbuilder.domain.MoveDirection
import com.ottotech.workoutbuilder.domain.Rest
import com.ottotech.workoutbuilder.domain.RestSlot
import com.ottotech.workoutbuilder.domain.TrainingType
import com.ottotech.workoutbuilder.domain.Workout
import com.ottotech.workoutbuilder.domain.WorkoutDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

data class BuilderUiState(
    val workout: Workout,
    val exercises: Map<String, Exercise> = emptyMap(),
    val saveBlockers: List<String> = emptyList(),
    val canUndo: Boolean = false,
) {
    val canSave: Boolean get() = saveBlockers.isEmpty()

    /** Name for an item's exercise, tolerant of a since-deleted custom exercise. */
    fun exerciseName(id: String): String = exercises[id]?.name ?: "Removed exercise"
}

/**
 * Holds one workout draft and persists it. Thin by design: every edit is a `WorkoutDraft`
 * call, and the draft is the single source of truth the screen renders.
 *
 * Undo is a whole-draft snapshot rather than positional bookkeeping — drafts are small
 * immutable objects, so stashing the previous one and restoring it is both simpler and
 * exactly correct. Any non-delete edit clears the snapshot so Undo never resurrects stale
 * state after further changes.
 */
class BuilderViewModel(
    private val workouts: WorkoutRepository,
    exercises: ExerciseRepository,
) : ViewModel() {

    // Seed with one empty section so the screen opens on something to fill in rather than a
    // blank page. The id is generated here, keeping WorkoutDraft.addSection pure.
    private val draft = MutableStateFlow(
        WorkoutDraft.addSection(
            WorkoutDraft.newWorkout(UUID.randomUUID().toString(), Instant.now()),
            UUID.randomUUID().toString(),
        ),
    )
    private val undo = MutableStateFlow<Workout?>(null)

    /** The section awaiting an exercise while the Library pick flow is open. */
    private var pendingSectionId: String? = null

    val state: StateFlow<BuilderUiState> =
        combine(draft, exercises.observeAll(), undo) { workout, library, undoSnapshot ->
            BuilderUiState(
                workout = workout,
                exercises = library.associateBy { it.id },
                saveBlockers = WorkoutDraft.saveBlockers(workout),
                canUndo = undoSnapshot != null,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BuilderUiState(workout = draft.value, saveBlockers = WorkoutDraft.saveBlockers(draft.value)),
        )

    // Non-delete edits clear the undo snapshot; delete edits set it.
    private fun edit(transform: (Workout) -> Workout) {
        undo.value = null
        draft.value = transform(draft.value)
    }

    private fun editWithUndo(transform: (Workout) -> Workout) {
        val before = draft.value
        draft.value = transform(before)
        undo.value = before
    }

    fun onRename(name: String) = edit { WorkoutDraft.rename(it, name) }
    fun onSetType(type: TrainingType) = edit { WorkoutDraft.setType(it, type) }

    fun onAddSection() = edit { WorkoutDraft.addSection(it, UUID.randomUUID().toString()) }
    fun onRemoveSection(sectionId: String) = editWithUndo { WorkoutDraft.removeSection(it, sectionId) }
    fun onMoveSection(sectionId: String, direction: MoveDirection) =
        edit { WorkoutDraft.moveSection(it, sectionId, direction) }
    fun onRenameSection(sectionId: String, name: String) =
        edit { WorkoutDraft.renameSection(it, sectionId, name) }
    fun onSetRounds(sectionId: String, rounds: Int) = edit { WorkoutDraft.setRounds(it, sectionId, rounds) }
    fun onSetRest(sectionId: String, slot: RestSlot, rest: Rest?) =
        edit { WorkoutDraft.setRest(it, sectionId, slot, rest) }

    /** Called by the section's "+ Add exercise"; the picked id comes back to [onExercisePicked]. */
    fun beginPick(sectionId: String) {
        pendingSectionId = sectionId
    }

    fun onExercisePicked(exerciseId: String) {
        val sectionId = pendingSectionId ?: return
        edit { WorkoutDraft.addItem(it, sectionId, UUID.randomUUID().toString(), exerciseId) }
        pendingSectionId = null
    }

    fun onRemoveItem(sectionId: String, itemId: String) =
        editWithUndo { WorkoutDraft.removeItem(it, sectionId, itemId) }
    fun onMoveItem(sectionId: String, itemId: String, direction: MoveDirection) =
        edit { WorkoutDraft.moveItem(it, sectionId, itemId, direction) }
    fun onSetItemMode(sectionId: String, itemId: String, mode: ItemMode) =
        edit { WorkoutDraft.setItemMode(it, sectionId, itemId, mode) }
    fun onSetDuration(sectionId: String, itemId: String, durationSec: Int) =
        edit { WorkoutDraft.setDuration(it, sectionId, itemId, durationSec) }
    fun onSetSetsReps(sectionId: String, itemId: String, sets: Int, reps: Int) =
        edit { WorkoutDraft.setSetsReps(it, sectionId, itemId, sets, reps) }
    fun onSetWeight(sectionId: String, itemId: String, weightKg: Float?) =
        edit { WorkoutDraft.setWeight(it, sectionId, itemId, weightKg) }
    fun onSetNote(sectionId: String, itemId: String, note: String?) =
        edit { WorkoutDraft.setNote(it, sectionId, itemId, note) }

    fun onUndo() {
        undo.value?.let { draft.value = it }
        undo.value = null
    }

    fun onUndoExpired() {
        undo.value = null
    }

    /** Persists the draft and calls [onSaved] on the main thread once written. */
    fun save(onSaved: () -> Unit) {
        val workout = draft.value
        if (!WorkoutDraft.canSave(workout)) return
        viewModelScope.launch {
            workouts.save(workout)
            onSaved()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkoutApp
                BuilderViewModel(app.workouts, app.exercises)
            }
        }
    }
}
```

- [ ] **Step 2: Write the Builder screen**

Item rows are a read-only summary in this task (`ItemRow` gains its editors in Task 4). Numeric
inputs are `−`/`+` steppers, not keyboards — reliable and no IME to manage.

`app/src/main/java/com/ottotech/workoutbuilder/ui/builder/BuilderScreen.kt`:

```kotlin
package com.ottotech.workoutbuilder.ui.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ottotech.workoutbuilder.domain.ItemMode
import com.ottotech.workoutbuilder.domain.MoveDirection
import com.ottotech.workoutbuilder.domain.Rest
import com.ottotech.workoutbuilder.domain.RestSlot
import com.ottotech.workoutbuilder.domain.Section
import com.ottotech.workoutbuilder.domain.TrainingType
import com.ottotech.workoutbuilder.domain.WorkoutItem
import com.ottotech.workoutbuilder.ui.components.AppTextField
import com.ottotech.workoutbuilder.ui.components.Chip
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.components.InverseButton
import com.ottotech.workoutbuilder.ui.components.ScreenHeader
import com.ottotech.workoutbuilder.ui.components.SectionLabel
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

@Composable
fun BuilderScreen(
    viewModel: BuilderViewModel,
    onClose: () -> Unit,
    onAddExercise: (sectionId: String) -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = "Build", onBack = onClose)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Dimens.screenPadding, end = Dimens.screenPadding, bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            item {
                AppTextField(
                    value = state.workout.name,
                    onValueChange = viewModel::onRename,
                    placeholder = "Workout name",
                    textStyle = AppType.cardTitle,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { TypeRow(selected = state.workout.type, onSelect = viewModel::onSetType) }

            items(state.workout.sections, key = { it.id }) { section ->
                SectionCard(
                    state = state,
                    section = section,
                    viewModel = viewModel,
                    onAddExercise = { onAddExercise(section.id) },
                )
            }

            item {
                InverseButton(
                    text = "+ Add section",
                    style = AppType.primaryButton,
                    height = Dimens.primaryButtonHeight,
                    container = colors.surface,
                    content = colors.onSurface,
                    onClick = viewModel::onAddSection,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Undo bar sits above the save button while a snapshot is live, then self-expires.
        if (state.canUndo) {
            LaunchedEffect(Unit) {
                delay(5_000)
                viewModel.onUndoExpired()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(Dimens.radiusButton))
                    .background(colors.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Deleted", style = AppType.cardMeta, color = colors.onSurface)
                Text(
                    "Undo",
                    style = AppType.chip,
                    color = colors.onSurface,
                    modifier = Modifier.clickable(onClick = viewModel::onUndo),
                )
            }
        }

        if (!state.canSave) {
            Text(
                text = state.saveBlockers.first(),
                style = AppType.cardMeta,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding)
                    .padding(bottom = 6.dp),
            )
        }

        SaveButton(enabled = state.canSave, onClick = { viewModel.save(onSaved) })
    }
}

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    // Disabled = surface with muted text; enabled = the inverse accent. No ripple when disabled.
    InverseButton(
        text = "SAVE WORKOUT",
        container = if (enabled) colors.surfaceInverse else colors.surface,
        content = if (enabled) colors.onSurfaceInverse else colors.textMuted,
        onClick = { if (enabled) onClick() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenPadding)
            .padding(bottom = 12.dp),
    )
}

@Composable
private fun TypeRow(selected: TrainingType, onSelect: (TrainingType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        TrainingType.entries.forEach { type ->
            Chip(text = type.label, selected = type == selected) { onSelect(type) }
        }
    }
}

@Composable
private fun SectionCard(
    state: BuilderUiState,
    section: Section,
    viewModel: BuilderViewModel,
    onAddExercise: () -> Unit,
) {
    val colors = LocalAppColors.current
    HairlineCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(Dimens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppTextField(
                    value = section.name,
                    onValueChange = { viewModel.onRenameSection(section.id, it) },
                    placeholder = "Section name",
                    textStyle = AppType.cardTitle,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                GlyphButton("↑") { viewModel.onMoveSection(section.id, MoveDirection.UP) }
                GlyphButton("↓") { viewModel.onMoveSection(section.id, MoveDirection.DOWN) }
                GlyphButton("✕") { viewModel.onRemoveSection(section.id) }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rounds", style = AppType.cardMeta, color = colors.textMuted, modifier = Modifier.weight(1f))
                Stepper(value = section.rounds, min = 1, step = 1) { viewModel.onSetRounds(section.id, it) }
            }

            Spacer(Modifier.height(12.dp))
            RestControl("Between exercises", section.restBetweenExercises) {
                viewModel.onSetRest(section.id, RestSlot.BETWEEN_EXERCISES, it)
            }
            RestControl("Between rounds", section.restBetweenRounds) {
                viewModel.onSetRest(section.id, RestSlot.BETWEEN_ROUNDS, it)
            }
            RestControl("After section", section.restAfterSection) {
                viewModel.onSetRest(section.id, RestSlot.AFTER_SECTION, it)
            }

            if (section.items.isNotEmpty()) Spacer(Modifier.height(8.dp))
            section.items.forEach { item ->
                ItemRow(state = state, sectionId = section.id, item = item, viewModel = viewModel)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "+ Add exercise",
                style = AppType.chip,
                color = colors.onBackground,
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.radiusChip))
                    .background(colors.surface)
                    .clickable(onClick = onAddExercise)
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            )
        }
    }
}

/**
 * Read-only in this task — Task 4 replaces this with the mode toggle and per-mode field
 * editors. For now it shows the exercise name and a summary of its default values.
 */
@Composable
private fun ItemRow(
    state: BuilderUiState,
    sectionId: String,
    item: WorkoutItem,
    viewModel: BuilderViewModel,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(state.exerciseName(item.exerciseId), style = AppType.cardTitle, color = colors.onBackground)
        Text(itemSummary(item), style = AppType.cardMeta, color = colors.textMuted)
    }
}

private fun itemSummary(item: WorkoutItem): String = when (item.mode) {
    ItemMode.TIMED -> "Timed · ${item.durationSec ?: 0}s"
    ItemMode.REPS -> "Reps · ${item.sets ?: 0}×${item.reps ?: 0}"
    ItemMode.MANUAL -> "Manual"
}

/** A `−  value  +` control. Whole-number fields only; the label formats it. */
@Composable
private fun Stepper(
    value: Int,
    min: Int,
    step: Int,
    label: (Int) -> String = { it.toString() },
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlyphButton("−") { onChange((value - step).coerceAtLeast(min)) }
        Text(label(value), style = AppType.cardTitle, color = LocalAppColors.current.onBackground)
        GlyphButton("+") { onChange(value + step) }
    }
}

/** Off / Timed / Manual segmented control; when Timed, a seconds stepper follows. */
@Composable
private fun RestControl(label: String, rest: Rest?, onChange: (Rest?) -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = AppType.cardMeta, color = colors.textMuted)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Chip("Off", selected = rest == null) { onChange(null) }
            Chip("Timed", selected = rest is Rest.Timed) {
                onChange(Rest.Timed((rest as? Rest.Timed)?.seconds ?: 30))
            }
            Chip("Manual", selected = rest is Rest.Manual) { onChange(Rest.Manual) }
            if (rest is Rest.Timed) {
                Spacer(Modifier.size(2.dp))
                Stepper(value = rest.seconds, min = 5, step = 5, label = { "${it}s" }) {
                    onChange(Rest.Timed(it))
                }
            }
        }
    }
}

/** A single text glyph in a `surface` square — the app's minus/plus/arrow/close affordance. */
@Composable
private fun GlyphButton(glyph: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(Dimens.radiusChip))
            .background(colors.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = AppType.cardTitle, color = colors.onSurface)
    }
}
```

- [ ] **Step 3: Wire the Builder route and the Build sheet seam**

In `ui/AppNav.kt`:

Add to the `Routes` object:

```kotlin
    const val BUILDER = "builder"
```

Add these imports:

```kotlin
import com.ottotech.workoutbuilder.ui.builder.BuilderScreen
import com.ottotech.workoutbuilder.ui.builder.BuilderViewModel
```

In the `composable(Routes.HOME) { ... }` block, replace the Build sheet's `onBuildYourself`
no-op so it navigates to the Builder:

```kotlin
                onBuildYourself = {
                    showBuildSheet = false
                    nav.navigate(Routes.BUILDER)
                },
```

Add a new destination inside the `NavHost { ... }` (after the `EXERCISE` composable). The
`onAddExercise` callback is a no-op here and gets its pick navigation in Task 3:

```kotlin
        composable(Routes.BUILDER) {
            val builderVm: BuilderViewModel = viewModel(factory = BuilderViewModel.Factory)
            BuilderScreen(
                viewModel = builderVm,
                onClose = { nav.popBackStack() },
                // TODO(Task 3): navigate to the Library in pick mode for this section.
                onAddExercise = { },
                onSaved = { nav.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
```

- [ ] **Step 4: Build, install and verify the section-level Builder**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:installDebug
```

Expected on the emulator:
- Home → `+ BUILD A WORKOUT` → **Build it yourself** opens a `‹ BUILD` screen with a
  `Workout name` field, a `Strength / HIIT / Mobility` chip row (Strength selected), one card
  `Section 1` with a `Rounds` stepper, three rest controls, a `+ Add exercise` chip, and below
  the card a grey `+ Add section` button
- The bottom shows the hint `Add at least one exercise` above a **greyed-out** `SAVE WORKOUT`
  button (nothing has been added yet)
- Type a name — the hint stays `Add at least one exercise` (name alone is not enough), and SAVE
  stays grey
- `+ Add section` adds `Section 2`; the section `↑` `↓` controls reorder the two cards, and `↑`
  on the top card / `↓` on the bottom card do nothing
- `✕` on a section removes it and shows a `Deleted · Undo` bar for ~5s; `Undo` restores it
- The `Rounds` stepper will not go below 1; a rest control's `Timed` chip reveals a seconds
  stepper that will not go below 5s
- `‹` returns to Home, discarding the draft (Home still shows only Court Speed)
- Switch the emulator to dark mode and re-open the Builder — every field, chip, card and the
  disabled SAVE button stays legible

`+ Add exercise` does nothing yet — that is Task 3.

- [ ] **Step 5: Run the suite to confirm no regression**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: 108 tests, 0 failures. (Task 1 added 32; nothing here changes a test.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/ui/
git commit -m "feat: add builder screen with sections, rounds, rests and save gate"
```

---

### Task 3: Library pick mode

Reuse the 1B Library and Category screens as an exercise picker. Tapping `+ Add exercise` on a
section opens the Library (browse → category), and picking an exercise returns to the Builder
with a new item appended to that section. This closes the loop: a workout can now be built, saved
and seen on Home.

**Files:**
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/library/CategoryScreen.kt`
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/AppNav.kt`

**Interfaces:**
- Consumes: `LibraryScreen`, `FiltersSheet`, `LibraryViewModel`, `CategoryScreen`,
  `BuilderViewModel` (`beginPick`, `onExercisePicked`), `Routes`
- Produces:
  - `CategoryScreen(..., pickMode: Boolean = false)` — a trailing `+` on each row and tap = pick
  - `Routes.PICK_LIBRARY`, `Routes.PICK_CATEGORY`, `Routes.pickCategory(muscle)`

- [ ] **Step 1: Give CategoryScreen a pick mode**

In `ui/library/CategoryScreen.kt`, add a `pickMode` parameter to `CategoryScreen` and pass it
through to the row. Replace the `CategoryScreen` signature and its `ExerciseRow` call, and the
`ExerciseRow` composable, as follows.

Change the function signature to add the parameter (default `false`, so the existing Library call
site needs no change):

```kotlin
@Composable
fun CategoryScreen(
    muscle: Muscle,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    pickMode: Boolean = false,
) {
```

Change the row call inside the `items(matches, ...)` block to pass `pickMode`:

```kotlin
            items(matches, key = { it.id }) { exercise ->
                ExerciseRow(exercise, pickMode) { onOpenExercise(exercise.id) }
            }
```

Replace the `ExerciseRow` composable with one that shows a trailing `+` in pick mode:

```kotlin
@Composable
private fun ExerciseRow(exercise: Exercise, pickMode: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    // The sub-line is the exercise's equipment plus its secondary muscles — the two things
    // that decide whether you can actually do it right now.
    val equipment = exercise.tags.filter { it.facet == Facet.EQUIPMENT }.map { it.value }.sorted()
    val alsoHits = exercise.tags.filter { it.facet == Facet.MUSCLE }.map { it.value }.sorted()
    val sub = (equipment + alsoHits).joinToString(" · ")

    HairlineCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = AppType.cardTitle, color = colors.onBackground)
                if (sub.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(sub, style = AppType.cardMeta, color = colors.textMuted)
                }
            }
            if (pickMode) {
                Text("+", style = AppType.heroName, color = colors.onBackground)
            }
        }
    }
}
```

This adds a `Row` and `Alignment` usage; confirm these imports are present at the top of the file
(they already are for `androidx.compose.foundation.layout.Row`, `androidx.compose.ui.Alignment`,
`androidx.compose.foundation.layout.fillMaxWidth`, and `androidx.compose.foundation.layout.Column`).

- [ ] **Step 2: Add pick routes and wire the Builder to them**

In `ui/AppNav.kt`, add to the `Routes` object:

```kotlin
    const val PICK_LIBRARY = "pick"
    const val PICK_CATEGORY = "pick/{muscle}"

    fun pickCategory(muscle: Muscle) = "pick/${muscle.name}"
```

Replace the Builder destination's `onAddExercise` no-op so it records the section and opens the
picker:

```kotlin
                onAddExercise = { sectionId ->
                    builderVm.beginPick(sectionId)
                    nav.navigate(Routes.PICK_LIBRARY)
                },
```

Add the two pick destinations inside the `NavHost { ... }`, after the `BUILDER` composable. The
pick Library reuses `LibraryScreen` + `FiltersSheet` with its own `LibraryViewModel` scoped to the
pick entry (an independent filter from the main Library tab). The pick Category reuses
`CategoryScreen` in pick mode; picking calls the Builder's ViewModel — reached the same way the
category screen reaches the shared Library ViewModel — then pops back to the Builder.

```kotlin
        composable(Routes.PICK_LIBRARY) {
            // A fresh, pick-scoped Library filter — independent of the Library tab's state.
            val pickVm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
            var showFilters by rememberSaveable { mutableStateOf(false) }

            LibraryScreen(
                viewModel = pickVm,
                onOpenCategory = { nav.navigate(Routes.pickCategory(it)) },
                onOpenFilters = { showFilters = true },
            )
            if (showFilters) {
                FiltersSheet(viewModel = pickVm, onDismiss = { showFilters = false })
            }
        }

        composable(
            route = Routes.PICK_CATEGORY,
            arguments = listOf(navArgument("muscle") { type = NavType.StringType }),
        ) { entry ->
            val muscle = Muscle.valueOf(entry.arguments?.getString("muscle") ?: Muscle.CHEST.name)
            val pickVm: LibraryViewModel = viewModel(
                viewModelStoreOwner = nav.getBackStackEntry(Routes.PICK_LIBRARY),
                factory = LibraryViewModel.Factory,
            )
            val builderVm: BuilderViewModel = viewModel(
                viewModelStoreOwner = nav.getBackStackEntry(Routes.BUILDER),
                factory = BuilderViewModel.Factory,
            )
            CategoryScreen(
                muscle = muscle,
                viewModel = pickVm,
                onBack = { nav.popBackStack() },
                onOpenExercise = { exerciseId ->
                    builderVm.onExercisePicked(exerciseId)
                    nav.popBackStack(Routes.BUILDER, inclusive = false)
                },
                pickMode = true,
            )
        }
```

> `nav.getBackStackEntry(Routes.BUILDER)` is safe here — pick is only ever reached from the
> Builder, so the Builder entry is always on the back stack beneath it. Scoping `builderVm` this
> way returns the *same* ViewModel instance the Builder created, so the picked item lands in the
> draft the Builder is showing.

- [ ] **Step 3: Build, install and verify the pick loop**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:installDebug
```

Expected:
- Builder → a section's `+ Add exercise` opens the Library (title `LIBRARY`, search, chip row,
  category grid) — the picker
- Tapping a category shows its exercises, each with a trailing `+`; the search and Filters
  narrow the list exactly as on the Library tab
- Tapping an exercise returns straight to the Builder, and that exercise now appears in the
  section as a row reading its name and `Timed · 30s`
- Add a second exercise to the same section, and one to a different section — each lands in the
  section whose `+ Add exercise` you tapped
- The system Back button from the picker's category returns to the category grid; Back again
  returns to the Builder without picking
- With a name typed and at least one exercise added, the SAVE hint disappears and `SAVE WORKOUT`
  turns black. Tap it → returns to Home, where the new workout is now the `UP NEXT` hero (a
  never-performed workout outranks Court Speed), with a meta line counting the exercises you added
- Cold-restart the app — the new workout is still there (it went through Room), and there is no
  duplicate

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/ui/
git commit -m "feat: reuse the library in pick mode to add exercises to the builder"
```

---

### Task 4: Item editors

Replace the read-only `ItemRow` with the real editor: switch an item between Timed / Reps /
Manual, edit the fields each mode needs, add an optional note, and reorder or delete the item.

**Files:**
- Modify: `app/src/main/java/com/ottotech/workoutbuilder/ui/builder/BuilderScreen.kt`

**Interfaces:**
- Consumes: `BuilderViewModel` (`onSetItemMode`, `onSetDuration`, `onSetSetsReps`, `onSetWeight`,
  `onSetNote`, `onMoveItem`, `onRemoveItem`), `ItemMode`, `MoveDirection`
- Produces: none new — this rewrites the private `ItemRow` composable and drops `itemSummary`

- [ ] **Step 1: Replace the ItemRow composable**

In `ui/builder/BuilderScreen.kt`, delete the private `itemSummary` function and replace the
read-only `ItemRow` composable with this editor. Weight is the one text field, kept in local state
so a partial entry like `2.` is not lost to a re-parse; everything else drives from the draft.

```kotlin
@Composable
private fun ItemRow(
    state: BuilderUiState,
    sectionId: String,
    item: WorkoutItem,
    viewModel: BuilderViewModel,
) {
    val colors = LocalAppColors.current

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(Dimens.radiusChip))
            .background(colors.surface)
            .padding(Dimens.cardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.exerciseName(item.exerciseId),
                style = AppType.cardTitle,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            GlyphButton("↑") { viewModel.onMoveItem(sectionId, item.id, MoveDirection.UP) }
            GlyphButton("↓") { viewModel.onMoveItem(sectionId, item.id, MoveDirection.DOWN) }
            GlyphButton("✕") { viewModel.onRemoveItem(sectionId, item.id) }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ItemMode.entries.forEach { mode ->
                Chip(text = mode.chipLabel(), selected = mode == item.mode) {
                    viewModel.onSetItemMode(sectionId, item.id, mode)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        when (item.mode) {
            ItemMode.TIMED -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Duration", style = AppType.cardMeta, color = colors.textMuted, modifier = Modifier.weight(1f))
                Stepper(value = item.durationSec ?: 30, min = 5, step = 5, label = { "${it}s" }) {
                    viewModel.onSetDuration(sectionId, item.id, it)
                }
            }

            ItemMode.REPS -> Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sets", style = AppType.cardMeta, color = colors.textMuted, modifier = Modifier.weight(1f))
                    Stepper(value = item.sets ?: 3, min = 1, step = 1) {
                        viewModel.onSetSetsReps(sectionId, item.id, it, item.reps ?: 10)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reps", style = AppType.cardMeta, color = colors.textMuted, modifier = Modifier.weight(1f))
                    Stepper(value = item.reps ?: 10, min = 1, step = 1) {
                        viewModel.onSetSetsReps(sectionId, item.id, item.sets ?: 3, it)
                    }
                }
                Spacer(Modifier.height(8.dp))
                WeightField(item = item) { viewModel.onSetWeight(sectionId, item.id, it) }
            }

            ItemMode.MANUAL -> Text(
                "Tap Done to advance — no timer.",
                style = AppType.cardMeta,
                color = colors.textMuted,
            )
        }

        Spacer(Modifier.height(10.dp))
        AppTextField(
            value = item.note.orEmpty(),
            onValueChange = { viewModel.onSetNote(sectionId, item.id, it) },
            placeholder = "Note (optional)",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun ItemMode.chipLabel(): String = when (this) {
    ItemMode.TIMED -> "Timed"
    ItemMode.REPS -> "Reps"
    ItemMode.MANUAL -> "Manual"
}

/**
 * Weight is optional and decimal, so it is a text field kept in local state — driving it from
 * the draft would re-parse mid-entry and eat a trailing decimal point. Blank clears it.
 */
@Composable
private fun WeightField(item: WorkoutItem, onChange: (Float?) -> Unit) {
    var text by remember(item.id) {
        mutableStateOf(item.weightKg?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() } ?: "")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Weight (kg)", style = AppType.cardMeta, color = LocalAppColors.current.textMuted, modifier = Modifier.weight(1f))
        AppTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it.toFloatOrNull())
            },
            placeholder = "—",
            modifier = Modifier.size(width = 96.dp, height = Dimens.searchFieldHeight),
        )
    }
}
```

Add these imports to the file (the others it needs are already present from Task 2):

```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
```

> `androidx.compose.foundation.layout.size` may already be imported from Task 2 (the `GlyphButton`
> uses `Modifier.size(30.dp)`). If Android Studio flags a duplicate import, keep the single one.

- [ ] **Step 2: Build, install and verify item editing**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:installDebug
```

Expected on an item row:
- Three mode chips `Timed / Reps / Manual`; the selected one is inverted
- `Timed` shows a `Duration` stepper (5s steps, floor 5s)
- `Reps` shows `Sets` and `Reps` steppers (floor 1) and a `Weight (kg)` field that accepts a
  decimal like `22.5` and can be cleared back to empty
- `Manual` shows the "Tap Done to advance" note and no numeric fields
- Every row has a `Note (optional)` field
- `↑` / `↓` reorder items within the section and no-op at the ends; `✕` deletes with the same
  `Deleted · Undo` bar as sections
- Build a small mixed workout — one Timed, one Reps with a weight, one Manual, in a 3-round
  section with `Between exercises: Timed 15s` and `Between rounds: Manual` — name it, SAVE, and it
  appears on Home. Its meta line's minute estimate is non-trivial (the round expansion and rests
  are counted by the existing `DurationEstimator`)
- Cold-restart: re-... the workout persists. (There is no in-app editor entry point yet, per
  scope decision 3 — persistence is verified by the workout surviving the restart, not by
  re-opening it in the Builder.)

- [ ] **Step 3: Run the whole suite**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: 108 tests, 0 failures.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ottotech/workoutbuilder/ui/builder/BuilderScreen.kt
git commit -m "feat: add per-item mode toggle, field editors, note and reorder to the builder"
```

---

### Task 5: Full verification and status update

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

Expected: **108 tests, 0 failures** across nine classes —
`domain.SegmentCompilerTest` 12, `domain.DomainPurityTest` 1, `domain.DurationEstimatorTest` 6,
`domain.ExerciseFilterTest` 10, `domain.WorkoutSummaryTest` 12, `domain.FilterStateTest` 16,
`domain.WorkoutDraftTest` 32, `data.ExerciseSeedTest` 9, `data.WorkoutSeedTest` 10.

If the total differs, reconcile it before writing it into STATUS.md — do not adjust the number in
this plan to match a broken run.

- [ ] **Step 2: Verify a fresh install end to end**

```bash
ADB=/c/Users/ottol/AppData/Local/Android/Sdk/platform-tools/adb.exe
"$ADB" uninstall com.ottotech.workoutbuilder
./gradlew :app:installDebug
```

Walk the list and confirm each:

1. Home shows only `Court Speed`
2. `+ BUILD A WORKOUT` → `Build it yourself` opens the Builder on `Section 1`
3. Add two sections; reorder them with `↑` / `↓`; delete one and `Undo` it
4. Set a section to 3 rounds; set `Between exercises: Timed 15s`, `Between rounds: Manual`
5. `+ Add exercise` opens the picker; search, filter, open a category, tap an exercise → it lands
   in the right section
6. Switch one item to `Reps`, set sets/reps and a decimal weight; switch another to `Manual`;
   give one a note
7. Reorder and delete items with the row controls; `Undo` a delete
8. Name the workout — `SAVE WORKOUT` turns black — save → the workout is the `UP NEXT` hero on
   Home with a sensible `N exercises · M min · <Type>` meta line
9. Cold-restart: the built workout persists and is not duplicated; Court Speed is still present
10. **Dark mode** (Settings → Display → Dark theme): walk Home → Builder → picker → a category.
    Every surface, border, chip, stepper and the disabled/enabled SAVE button stays legible;
    nothing black-on-black or white-on-white

- [ ] **Step 3: Update the project status**

Rewrite the table row and add the environment facts in `docs/superpowers/STATUS.md`.

Table:

```markdown
| **Plan 1C** — Builder + Library pick mode | **Complete.** 108 tests green, verified on the emulator. |
| Plan 1D — Execution + audio | Not started |
```

Under "Environment facts not obvious from the code", add:

```markdown
- **The Builder edits a `Workout` draft entirely through pure `domain/WorkoutDraft.kt` functions**
  — add/remove/move/rename section and item, rounds, rests, mode and field setters, plus
  `saveBlockers`. `BuilderViewModel` holds the draft in a `MutableStateFlow` and only calls these;
  it generates the UUIDs the pure functions take as parameters.
- **Reordering is `↑` / `↓` move controls, not drag — a documented deviation from spec §6.4.**
  Compose has no first-party drag-reorder and the project avoids third-party deps. `moveSection` /
  `moveItem` are the same pure logic drag would use, so drag can be added later without touching
  the domain. Delete is an explicit `✕` with a whole-draft-snapshot Undo bar (also §6.4-faithful,
  no swipe gesture).
- **The Library is reused in pick mode via `PICK_LIBRARY` / `PICK_CATEGORY` routes**, each with a
  pick-scoped `LibraryViewModel` (independent filter from the Library tab). The pick Category
  screen reaches the Builder's ViewModel through `getBackStackEntry(Routes.BUILDER)` — the same
  shared-ViewModel trick the Category screen uses for the Library ViewModel — so a picked exercise
  lands in the open draft.
- **The Builder creates new workouts only.** Editing an existing saved workout uses the same draft
  functions and `WorkoutRepository.save` (which upserts by id), but has no entry point yet — Home
  rows are reserved for "start workout" (Plan 1D). Loading a workout into the Builder is a seam
  for a later plan.
- **One unwired seam remains** in `ui/AppNav.kt`: `onStartWorkout` (Plan 1D). The Build sheet's
  "Ask the Coach" card is still a `TODO(Plan 2)` no-op.
- **Builder sections render always-expanded** — a deviation from spec §6.4's "collapsible
  sections". Collapse/expand is pure per-section UI state with no data-model effect, deferred as
  polish.
```

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/STATUS.md
git commit -m "docs: record Plan 1C completion and its environment facts"
```

---

## Done when

- `./gradlew :app:testDebugUnitTest` is green: 108 tests across nine classes, including
  `WorkoutDraftTest`'s 32
- From Home, the Build sheet's "Build it yourself" reaches a Builder that names a workout, adds
  sections with rounds and rests, adds exercises via the reused Library picker, edits each item's
  mode and fields, reorders and deletes both sections and items with Undo, and saves
- A saved workout appears on Home as the `UP NEXT` hero with a correct meta line, survives a cold
  restart, and does not duplicate
- SAVE is blocked with a readable hint until the workout has a name and at least one fully
  specified item
- Every Builder and picker surface is legible in light and dark mode
- `DomainPurityTest` still passes — no Compose or Android type leaked into `domain/`

## Next

**Plan 1D — Execution + audio.** It fills the last `onStartWorkout` seam: compiles a saved
workout with the existing `SegmentCompiler` and runs it with the countdown/reps/manual execution
screens, a foreground service owning the clock, generated tones and audio cues, and the finish
screen. **This is the plan that needs a real phone**, not the emulator — it proves cues survive
screen-off and pocketing.
