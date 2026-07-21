# Project status

**Last updated:** 2026-07-21

## Where things stand

| Phase | State |
|---|---|
| Design spec | Complete, approved — `specs/2026-07-21-workout-builder-android-design.md` |
| **Plan 1A** — foundation & domain | **Merged to `main`.** 46 tests green, verified on device. |
| **Plan 1B** — Home + Library screens | **Complete** on `phase-1b-home-library`. 76 tests green, verified on the emulator. |
| Plan 1C — Builder | Not started |
| Plan 1D — Execution + audio | Not started |
| Phase 2 — AI Coach | Not started |

`main` is the verified baseline. Branch from it for each plan.

## Environment facts not obvious from the code

- **`java` is not on PATH.** Every Gradle command needs:
  `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`
- **adb:** `/c/Users/ottol/AppData/Local/Android/Sdk/platform-tools/adb.exe`
- **Emulator:** a Pixel 8 Pro AVD on API 36 exists. Launch from Android Studio's
  Device Manager, then `./gradlew :app:installDebug`.
- **`local.properties` is gitignored** and must exist with `sdk.dir=...` — recreate it
  on any new machine.
- **Court Speed ships as seed data** (`data/seed/WorkoutSeed.kt`) and is written on first
  launch, gated on an `AppPrefs` flag so deleting it keeps it deleted. It is also the
  acceptance fixture — `WorkoutSeedTest` compiles it against the shipped exercise library,
  which proves every `exerciseId` it references actually exists.
- **The library is 101 exercises**, not 100 — Plan 1B added `flyer` so the seeded workout's
  Bonus section resolves. Primary-muscle split: Chest 12, Back 14, Legs 20, Shoulders 10,
  Arms 9, Core 12, Cardio 9, Mobility 15. Nothing has `FULL_BODY` as its primary, so there
  is deliberately no Full body category tile.
- **Two unwired seams** are waiting in `ui/AppNav.kt`, both marked `TODO` with the plan that
  fills them: `onStartWorkout` (Plan 1D) and the Build sheet's two cards (Plans 1C and 2).
- **`LibraryViewModel` is scoped to the Library nav entry**, not per-screen, so the category
  and detail screens see the same filter. Reaching them from anywhere other than the Library
  would throw on `getBackStackEntry`.
- **First launch takes ~10s** on a cold install while 101 exercises plus their tags seed.
  `MainActivity` gates the UI on it, so the launch theme shows until it finishes. Later
  launches are ~3s.
- **`screencap` on this emulator draws a faint copy of the bottom nav behind the status
  bar.** It is a compositing artifact of the edge-to-edge scrim, not a layout bug — the
  `uiautomator` bounds dump shows exactly one nav bar. Do not chase it.
- **Watch demo:** a pinned `demoUrl` opens the YouTube app; a `demoQuery` search opens the
  browser, because YouTube's intent filter claims `/watch` and `/shorts` but not `/results`.
  Spec §7 allows this ("browser otherwise").

Useful verification one-liner after a test run:

```bash
for f in app/build/test-results/testDebugUnitTest/*.xml; do
  n=$(basename "$f" .xml | sed 's/TEST-com.ottotech.workoutbuilder.//')
  t=$(grep -oE 'tests="[0-9]+"' "$f" | head -1 | grep -oE '[0-9]+')
  fl=$(grep -oE 'failures="[0-9]+"' "$f" | head -1 | grep -oE '[0-9]+')
  echo "$n: $t tests, $fl failures"
done
```

## Device requirements per plan

- **1B and 1C** — the emulator is fine; these are screens and data. 1B was verified entirely
  on the Pixel 8 Pro AVD.
- **1D is different.** It proves audio cues survive the screen being off and the phone
  being in a pocket, and that manufacturer battery-savers don't kill the foreground
  service. **An emulator cannot verify any of that — use a real phone.**

## Known blemish

The Plan 1A seed commit (`5b7156b`) message says "101 exercises" when the count at that
point was **100**. Plan 1B later added `flyer`, so the number is now accurate by
coincidence. Code and tests were always correct; not worth rewriting history over.
