# Project status

**Last updated:** 2026-07-21

## Where things stand

| Phase | State |
|---|---|
| Design spec | Complete, approved — `specs/2026-07-21-workout-builder-android-design.md` |
| **Plan 1A** — foundation & domain | **Merged to `main`.** 46 tests green, verified on device. |
| Plan 1B — Home + Library screens | Not started |
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

- **1B and 1C** — the emulator is fine; these are screens and data.
- **1D is different.** It proves audio cues survive the screen being off and the phone
  being in a pocket, and that manufacturer battery-savers don't kill the foreground
  service. **An emulator cannot verify any of that — use a real phone.**

## Known blemish

The seed commit (`5b7156b`) message says "101 exercises". The real count is **100**.
Code and tests were always correct; only that commit's prose is wrong. Not worth
rewriting history over.
