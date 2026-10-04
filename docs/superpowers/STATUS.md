# Project status

**Last updated:** 2026-10-03

## Where things stand

| Phase | State |
|---|---|
| Design spec | Complete, approved — `specs/2026-07-21-workout-builder-android-design.md` |
| **Plan 1A** — foundation & domain | **Merged to `main`.** 46 tests green, verified on device. |
| **Plan 1B** — Home + Library screens | **Merged to `main`.** 76 tests green, verified on the emulator. |
| **Plan 1C** — Builder + Library pick mode | **Merged to `main`.** 108 tests green, verified on the emulator. |
| **Design correction pass** — scale, contrast, targets, Home | **Merged to `main`.** 116 tests green, verified on the emulator. |
| **Plan 1D** — Execution + audio | **Complete on `phase-1d-execution-audio`, not yet merged.** 141 tests green. Installed on the real phone (Samsung Galaxy S22) and ready for the user's hands-on verification pass (§5 of the design spec — pocket test, music ducking, headset button, lock screen, battery-saver survival), which is outstanding and owned by the user, not yet run. |
| **Edit & delete workouts** | **Complete on `edit-delete-workouts`** (branched from the 1D branch, so it carries 1D too). 144 tests green, verified on the emulator. Not yet merged — waits on the 1D hands-on pass. |
| **Phase 2 — AI Coach** | **Built on `phase-2-ai-coach`** (branched from `edit-delete-workouts`, so it carries 1D and edit/delete too). 177 tests green; independent review done, its 4 important findings fixed. Verified on the emulator up to the network boundary: no-key gate, Settings round-trip, bad-key and no-network messages. **Outstanding, needs the user:** one real generation with their own key (plan Task 7 Step 5) — the first call that proves the request body (schema, refusal-fallback header) is accepted. |
| **BLDR brand rollout** | **Complete on `bldr-brand`, not yet merged** (branched from `phase-2-ai-coach`). Plans: `plans/2026-10-02-bldr-brand-rollout.md`, then `plans/2026-10-02-bldr-striped-b.md` (the striped-B mark and "build the B" motion from the design canvas, plus the Coach's centred loader with working words). 197 tests green. Verified on the emulator, incl. a Galaxy S22-sized screen at 1.0x–2.0x font size, animations on and off, and the launcher icon under circle, squircle, rounded-square, teardrop and themed masks. **Outstanding, needs the user:** a look on the real S22 (Samsung squircle icon; Settings scrolled to the end with button navigation; a workout at 2.0x; a cold start after a reboot), Android Studio previews in `Brand.kt`, and the Play Store assets below. |
| **Voice announcements** | **Complete on `voice-announcements`, not yet merged** (branched from `bldr-brand`; built in the separate worktree `../workout-builder-voice` because another session was using the main folder). Plan: `plans/2026-10-03-voice-announcements.md`. The phone's built-in voice says "First up / Next up: <exercise>" in a 6 s get-ready step before every exercise, "Rest" as rests begin, and "Workout complete"; Settings → Sound → Voice announcements (default on). The engine is warmed up before the clock starts (logcat `CuePlayer: Voice warmed up in Nms`), since a cold engine took ~3 s to say its first line on the emulator. 210 tests green. Verified on the emulator: Settings switch, first/next lines on time from a cold engine, mute cuts a line, "Workout complete" plays in full before teardown, voice off = old behaviour. **Outstanding, needs the user:** hear it on the real S22 — with music playing (ducks for the whole sentence), screen off in a pocket, pause mid-sentence. Note: Android 16 logs "AudioHardening background playback would be muted" for the TTS engine (log-only on the emulator); if a future Android enforces it, the fallback is rendering lines to files and playing them from the app. APK: `G:\My Drive\BLDR\BLDR_2026-10-03_1809_voice.apk`. |
| **Privacy page** | **Complete on `bldr-brand`, not yet merged.** Settings → About → Privacy (also linked as "How your data is used" under the API key) opens a plain-language page: data stays on the phone (`allowBackup` is off, no accounts/ads/tracking), what the Coach sends to Anthropic (typed text, length, equipment, exercise names — never history), why the user brings their own key, and a link to Anthropic's privacy policy. If the Coach ever sends more, update `PrivacyScreen.kt` too. 210 tests green; verified on the emulator (both entry points, policy link opens the browser, back returns to Settings). APK: `G:\My Drive\BLDR\BLDR_2026-10-04_1408_privacy.apk`. |

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
- **One unwired seam** remains in `ui/AppNav.kt`, marked `TODO`: `onStartWorkout` (Plan 1D).
  The design correction pass removed Home's hero button, so it is now called from tapping
  anywhere on a workout card rather than a dedicated button. The Build sheet's "Build it
  yourself" now reaches the Builder; "Ask the Coach" is still a `TODO(Plan 2)` no-op.
- **`LibraryViewModel` is scoped to the Library nav entry**, not per-screen, so the category
  and detail screens see the same filter. Reaching them from anywhere other than the Library
  would throw on `getBackStackEntry`.
- **The Builder edits a `Workout` draft entirely through pure `domain/WorkoutDraft.kt` functions**
  — add/remove/move/rename section and item, rounds, rests, mode and field setters, plus
  `saveBlockers`. `BuilderViewModel` holds the draft in a `MutableStateFlow` and only calls
  these; it generates the UUIDs the pure functions take as parameters, and persists via the
  existing `WorkoutRepository.save` (which upserts the whole workout by id).
- **Reordering is `↑` / `↓` move controls, not drag — a documented deviation from spec §6.4.**
  Compose has no first-party drag-reorder and the project avoids third-party deps. `moveSection`
  / `moveItem` are the same pure logic drag would use, so drag can be added later without touching
  the domain. Delete is an explicit `✕` with a whole-draft-snapshot Undo bar (also §6.4-faithful,
  no swipe gesture). **Builder sections render always-expanded** — a second §6.4 deviation
  (collapse is pure UI state, deferred as polish).
- **The Library is reused in pick mode via `PICK_LIBRARY` / `PICK_CATEGORY` routes**, each with a
  pick-scoped `LibraryViewModel` (independent filter from the Library tab). The pick Category
  screen reaches the Builder's ViewModel through `getBackStackEntry(Routes.BUILDER)`. **Picking
  pops both pick destinations at once**, so those back-stack lookups are wrapped in `remember`
  in the `PICK_CATEGORY` composable — without it, the leaving frame re-runs
  `getBackStackEntry(PICK_LIBRARY)` during the exit transition and crashes (the same
  getBackStackEntry-during-exit hazard 1B flagged for the Library tab).
- **Edit and Delete live behind a "⋯" menu on each Home card**, because tapping the card itself
  starts the workout. Edit reuses the Builder via the optional `builder?workoutId=` route
  argument, read from `SavedStateHandle`; saving upserts by id, so `createdAt` and
  `lastPerformedAt` survive. An edit renders no form until the saved workout has loaded.
  **Unsaved-changes rules differ by mode** (`WorkoutDraft.hasUnsavedChanges`): a new draft uses
  `hasContent`, an edit uses "anything differs from what was loaded" — so a structure-only edit
  to a saved workout *does* prompt. Delete asks first and has no undo.
- **All confirmation dialogs go through `ConfirmDialog`** in `ui/components/Controls.kt`
  (Builder discard, Home delete, Execution quit). Execution passes its always-dark palette.
- **First launch takes ~10s** on a cold install while 101 exercises plus their tags seed.
  `MainActivity` gates the UI on it, so the launch theme shows until it finishes. Later
  launches are ~3s.
- **`screencap` on this emulator draws a faint copy of the bottom nav behind the status
  bar.** It is a compositing artifact of the edge-to-edge scrim, not a layout bug — the
  `uiautomator` bounds dump shows exactly one nav bar. Do not chase it.
- **Watch demo:** a pinned `demoUrl` opens the YouTube app; a `demoQuery` search opens the
  browser, because YouTube's intent filter claims `/watch` and `/shorts` but not `/results`.
  Spec §7 allows this ("browser otherwise").
- **The design handoff's px values are ~62% of true scale.** Its mock phone frame is 264px
  wide — a thumbnail, not a device (a real phone is ~411–448 dp). Every size in the handoff and
  in spec §5 was transplanted 1:1 and shipped too small. Corrected 2026-07-26 in `Type.kt` /
  `Dimens.kt`, which are now the authority. **Do not "restore" handoff numbers.**
- **Light-mode muted greys are deliberately darker than dark-mode's.** `#6B6B6B` vs `#9A9A9A`.
  The original symmetric value was 2.8:1 on white — an accessibility failure. The two themes
  must not share these values. **`navInactive`'s dark value had a separate, later-caught bug:**
  the original pass only audited light mode, so dark `navInactive` stayed at `#5A5A5A`
  (≈2.85:1 on `#0B0B0D` — also an AA fail). A final review found it and raised it to `#8A8A8A`
  (≈5.7:1).
- **`UpNext` is deleted and Home does not recommend.** It ranked by `lastPerformedAt`, which is
  still never written — `WorkoutRepository.markPerformed` remains callerless until Plan 1D.
  **When 1D wires up execution, do not resurrect the hero**; the product decision is that Home
  offers choose-or-build and nothing else.
- **Every touch target is 48 dp** (`Dimens.minTouchTarget`). Chips reach it via padding, glyph
  buttons and the back chevron via an explicit sized `Box`. New controls must clear this bar.
- **The Builder's rest stepper now sits on its own line.** `RestControl`'s −/+ seconds stepper
  didn't fit beside the Off / Timed / Manual chips at 48dp touch targets, so it wraps to a
  second line beneath them. Deliberate layout change, not a bug — don't squeeze it back onto
  the chip row.
- **The empty-Home state is only reachable by deleting every workout** — clearing app storage
  also resets the seed flag, so Court Speed reseeds on next launch.
- **The Builder's discard confirmation checks `WorkoutDraft.hasContent`, not `sections.isEmpty()`.**
  Every new draft is seeded with one empty section, so `hasContent` deliberately ignores that
  seeded section and only counts a name or an actual exercise. Back and the header `‹` both ask
  "Discard this workout?" only when there's real content to lose. **This also means
  structure-only edits don't count as content** — renaming a section, adding an extra empty
  section, or setting a rest, with no name and no exercise ever added, all leave `hasContent`
  false. That draft closes on Back with no prompt. Deliberate: those are structural edits, not
  workout content.
- **Cue tones are generated at runtime, not shipped as assets.** `CuePlayer.load()` computes the
  three WAV files (`ToneWav`, ported from the reference web app's tone maths) and writes them into
  `context.cacheDir` the first time they're needed. It checks the existing file's byte size against
  the exact expected size before reusing it, not just whether the file exists — a process killed
  mid-write would otherwise leave a truncated WAV that exists forever but never decodes, silencing
  that cue permanently. A size mismatch triggers a rewrite instead.
- **The execution timer lives in `ExecutionService`, a foreground service (`mediaPlayback` type
  in the manifest); the UI owns no clock.** A Compose screen dies with the Activity, which is the
  whole point of the phase — cues must keep firing with the screen off. The service ticks the pure
  `WorkoutSession` domain model at 10 Hz and the screen only observes the result. Its `state`
  `StateFlow` is deliberately on the companion object, not the instance: the screen navigates and
  starts collecting *before* `startForegroundService` has actually constructed the service, so an
  instance-scoped flow would be null at exactly the moment the UI needs it and the screen would sit
  blank forever. A companion-level flow always exists and just holds an empty state until the
  service fills it in.
- **`ACTION_START` for a workout that's already running and unfinished is a deliberate no-op**,
  not a restart. The ongoing notification's tap target re-fires `ACTION_START` every time it's
  tapped; without this guard, checking on a running workout from the notification would restart it
  from segment 1 instead of returning to where it is.
- **The test phone is a Samsung Galaxy S22 (SM-S901E, Android 16, serial `R5CT81Z2SRP`).**
  Samsung is one of the manufacturers the spec's highest-severity risk section names for
  aggressively killing foreground services to save battery, so the battery-saver survival item in
  the hands-on checklist (§5, item 11) is a live, unresolved question on this specific device —
  its outcome decides whether a deferred battery-optimization-exemption prompt becomes its own
  follow-up plan.
- **`adb shell input keyevent 85` (the media-button keycode) cannot reach the app on the
  emulator** — a system telecom session holds `FLAG_EXCLUSIVE_GLOBAL_PRIORITY` on it there.
  Headset play/pause/skip must be verified on real hardware, not the emulator.
- **In Git Bash on this machine, adb commands containing an on-device path (e.g. `/sdcard/ui.xml`)
  get silently mangled** by MSYS path conversion before they reach adb. Prefix the command with
  `MSYS_NO_PATHCONV=1`.
- **`dumpsys activity activities | grep -c "ActivityRecord.*MainActivity"` does not count Activity
  instances** — it returns roughly a dozen matches even in a healthy single-instance state, because
  the string appears in unrelated log lines too. To check for accidental multiple instances, read
  the `Hist #N:` back-stack entries and the task's `sz=` field instead.
- **The real phone's screen locks itself (screen timeout, then a secure PIN/pattern bouncer)
  after a period of inactivity, and adb cannot dismiss that bouncer.** `wm dismiss-keyguard` only
  clears a non-secure keyguard; once Samsung's lock has escalated to `Window{... Bouncer}` (visible
  in `dumpsys window`), screenshots taken over adb come back solid black even though the screen is
  reported `Awake`, and only entering the real PIN/pattern on the device clears it. Don't attempt to
  guess or brute-force it over adb — repeated failed attempts risk Samsung/Knox lockout escalation.
  Anyone driving this phone via adb after a gap should expect to physically unlock it first.

Useful verification one-liner after a test run:

```bash
for f in app/build/test-results/testDebugUnitTest/*.xml; do
  n=$(basename "$f" .xml | sed 's/TEST-com.ottotech.workoutbuilder.//')
  t=$(grep -oE 'tests="[0-9]+"' "$f" | head -1 | grep -oE '[0-9]+')
  fl=$(grep -oE 'failures="[0-9]+"' "$f" | head -1 | grep -oE '[0-9]+')
  echo "$n: $t tests, $fl failures"
done
```

## Phase 2 SDK viability spike (2026-09-24) — passed

Spec §9 made "`anthropic-java` on Android" the first task of Phase 2. Checked on the Pixel 8 Pro
emulator in a throwaway branch (deleted; nothing merged):

- **It works.** `anthropic-java:2.34.0` built into the app, and a real `messages().create` against
  `claude-opus-5` with a deliberately invalid key came back as a typed `UnauthorizedException`
  (401, `authentication_error`) — so OkHttp, TLS, Jackson and the SDK's error mapping all run on
  Android. **Use the SDK; the OkHttp fallback is not needed.**
- **Packaging needs one exclusion block.** The SDK pulls in Apache `httpclient5` / `httpcore5`,
  whose duplicate `META-INF/DEPENDENCIES` breaks `mergeDebugJavaResource`. Fix:
  `packaging { resources { excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*",
  "META-INF/NOTICE*", "META-INF/INDEX.LIST") } }`.
- **Debug APK grows 12.7 MB → 21.6 MB** (unminified; R8 is off in this project).
- **First call pays a ~4 s one-time warm-up** (class loading); the next call took ~1.1 s. Build the
  client when the Coach screen opens, not on the Generate tap.
- **Spec §9 still names `claude-opus-4-8`.** Current default is `claude-opus-5` (same $5/$25
  price; thinking is adaptive by default). Switching is proposed, pending the user's OK.

## Phase 2 facts

- **The Coach's request body is unproven until a real key is used.** The API rejects a bad key
  before it validates the body, so the structured-output schema and the
  `server-side-fallback-2026-07-01` header / `"fallbacks": "default"` body field have never been
  accepted by the server yet. If the first real generation shows "Something went wrong", check
  `adb logcat` for a 400 naming one of them; dropping the two fallback lines in `CoachClient` is
  the documented escape hatch.
- **All back actions go through `dropUnlessResumed`.** A double press of "‹" used to pop Home as
  well, leaving an empty back stack — a blank screen that Back then closed. Found while testing
  the Coach; the bug was in every screen, fixed once in `AppNav`.
- **Multi-line `AppTextField`s open four lines tall** (`minLines = 4`) so the text field itself
  fills the grey box. Before, only the first line accepted taps.
- **Theme is a user setting** (Settings → Appearance: System / Light / Dark), stored as
  `theme_mode` in `AppPrefs` and exposed as a `StateFlow` that `MainActivity` observes, so a change
  re-themes the app instantly. `MainActivity` also re-applies `enableEdgeToEdge` with bar styles
  keyed to the app's theme, not the phone's — otherwise Light on a dark phone gives white status-bar
  icons on white. `ThemeMode` lives in `domain/` so `data/` never imports from `ui/`.
- **The API key lives in `shared_prefs/secure.xml` as base64 IV + ciphertext**; the AES key is in
  the Android Keystore under alias `anthropic_api_key`. `run-as … cat` shows no plaintext.

## BLDR brand facts

- **The mark is the striped B**: seven 10-high pills in a 54 × 70 frame (taller than wide). `BldrLoader(width = …)` is its true
  width, so the box is 1.3x taller; bars drop in from above, so leave ~⅕ of the width clear above a loader. Sizes in use:
  44 dp at first launch, 40 dp (the default) in the Coach. Geometry and timings (`Bars`, `BuildTiming`) belong to the design
  canvas: never edit them in code.
- **`BldrSplashBuild` and `BldrRefreshIndicator` exist but are unused**: a one-shot splash build would add ~1.4 s to every
  launch, and the app has no pull-to-refresh or small inline waits. See BRAND.md "Decisions".
- **The Coach's wait** centres the loader ("Drafting your workout" to TalkBack) and steps through six working words, 3 s each,
  holding on the last. They're reassurance, not real progress. The phrase area is sized to the tallest phrase so nothing shifts.
- **In Android Studio's static preview pane, the animated loader and splash previews look empty**: they render frame 0, before
  any bar has landed. Use Interactive mode; the "animations off" previews show the full B.
- **Library exercise rows have a "Demo" link** (2026-10-03), in browse and pick mode, using the same `demoIntent` as the
  Overview. It opens its own link; tapping the rest of the row still opens the exercise (or adds it when picking).
- **The app's name is "BLDR"**, set once as the application label in `AndroidManifest.xml` (the
  activity inherits it). TalkBack spells a launcher label out ("B-L-D-R"); Android has no separate
  spoken name for it. Accepted.
- **Themed icons only show on the Pixel home screen**, not in the app drawer, so test the
  monochrome layer with the icon placed on the home screen. The launcher's themed-icons switch
  can't be flipped over adb (permission denied); use Wallpaper & style → Home screen → Themed icons.
- **Type is Archivo Expanded (titles, timer, lead-in) + Space Grotesk (everything else)**, all through `AppType`;
  `AppTypeTest` fails if any style falls back to the system font or lacks an explicit line height. Archivo runs
  ~1.55x wider than Roboto, so titles are 32 sp and go through `ScreenTitle` (auto-shrinks to 14 sp, then
  ellipsis) and the timer is 38 sp and auto-shrinks inside the ring. SemiBold maps to Space Grotesk Medium.
- **The execution screen's upper part (name, ring, Restart) scrolls when it doesn't fit**, so Watch demo, Up next
  and Pause/Done stay pinned and reachable at large font sizes. Before, Pause/Done fell off-screen at 2.0x (and,
  with the new type, from ~1.6x).
- **Known at 2.0x font size, pre-existing (same with Roboto):** single-line chip rows (Builder type and rest
  chips, Settings Appearance) and Home card meta lines break words mid-way; the Library "Shoulders" tile wraps.
  Fine at 1.3x. A FlowRow / card layout follow-up would fix them.
- **The emulator now seeds a fresh install in ~1 s**, not the ~10 s noted above, so the first-launch loader
  (300 ms delay) rarely shows there; it was verified with a throwaway zero-delay build.
- **Play Store listing still needs:** the 512 px icon re-exported as 32-bit PNG with alpha
  (`brand/png/bldr-play-store-icon-512.png` is 24-bit RGB); a 1024 × 500 feature graphic; at least
  two phone screenshots (take them after the typography pass). The store title can differ from the
  launcher label — the user's call.

## Device requirements per plan

- **1B and 1C** — the emulator is fine; these are screens and data. 1B was verified entirely
  on the Pixel 8 Pro AVD.
- **1D is different.** It proves audio cues survive the screen being off and the phone
  being in a pocket, and that manufacturer battery-savers don't kill the foreground
  service. **An emulator cannot verify any of that — use a real phone.** The build is installed
  on the Samsung Galaxy S22 test phone and ready; the hands-on pass itself (pocket test, music
  ducking, headset button, lock screen, battery-saver survival) has not been run yet and is the
  user's to do.

## Known blemish

The Plan 1A seed commit (`5b7156b`) message says "101 exercises" when the count at that
point was **100**. Plan 1B later added `flyer`, so the number is now accurate by
coincidence. Code and tests were always correct; not worth rewriting history over.
