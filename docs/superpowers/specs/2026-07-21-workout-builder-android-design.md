# Workout Builder — Android App Design

**Date:** 2026-07-21
**Status:** Approved, ready for implementation planning
**Inputs:** `design_handoff_workout_builder/` (visual spec + HTML prototype), `web-app-reference/` (Court Speed HIIT timer)

---

## 1. Overview

An Android app for building your own workouts from a library of exercises, then executing
them with audio-guided timing. Personal use, sideloaded — no accounts, no backend, no Play
Store.

Three ways to get a workout: build it manually from the exercise library, ask an AI coach to
draft one, or run one you already made.

### Goals

- Build workouts with real structure — sections that repeat for rounds, mixing timed
  intervals, rep-based sets, and manual "tap when done" segments
- Execute them with audio cues that **keep working when the screen is off and the phone is in
  a pocket** — the primary failure of the existing web app
- Browse ~100 exercises by muscle group, each linking to a demo video
- Generate a workout from a plain-English goal via the Anthropic API

### Non-goals

- Progress tracking, history, personal records, or charts. This is a builder and a runner, not
  a logbook. (Explicitly stated in the design handoff.)
- Multi-user, accounts, cloud sync, or sharing
- Play Store distribution
- iOS

---

## 2. Decisions taken

| Question | Decision |
|---|---|
| Audience | Personal use, sideloaded APK. No backend, no accounts. |
| Workout structure | Sections containing exercises; each section repeats for N rounds |
| Demo videos | YouTube app deep-link searching `"<name> proper form"`; per-exercise URL override; 8 verified Court Speed URLs hardcoded |
| AI Coach interaction | Prompt + optional chips → generate → preview → Save / Regenerate / Tweak |
| Platform | Native Kotlin + Jetpack Compose |
| Accent colour | **Mono** — no accent hue. Accent resolves to maximum contrast against its own surface. |
| Themes | Full light and dark modes, following system by default, overridable in Settings |
| Execution screen | Always dark, in both themes |
| AI Coach entry point | Inside the "Build a workout" flow, not the bottom nav |
| Bottom nav | Two items, as designed: Home, Library |

---

## 3. Data model

### Exercise (library item)

| Field | Type | Notes |
|---|---|---|
| `id` | String | Stable slug, e.g. `barbell-bench-press` |
| `name` | String | `Barbell Bench Press` |
| `primaryMuscle` | Muscle | `CHEST, BACK, LEGS, SHOULDERS, ARMS, CORE, CARDIO, MOBILITY, FULL_BODY`. Exactly one — the exercise's home in the Browse-by-muscle grid. |
| `tags` | Set\<Tag\> | Everything else, faceted. **Replaces** the former `type` and `equipment` fields. |
| `demoQuery` | String | Search string, e.g. `barbell bench press proper form` |
| `demoUrl` | String? | Optional override. If set, opens this instead of searching. |
| `isCustom` | Boolean | User-added vs. seeded |

### Tag

A tag is a `(facet, value)` pair. Facets are fixed; values within a facet are a controlled
vocabulary, so filtering stays consistent and free-text drift (`dumbbell` vs `Dumbbells`) is
impossible.

| Facet | Values |
|---|---|
| `TYPE` | `Strength`, `HIIT`, `Mobility` — multi-valued (a Kettlebell Swing is both Strength and HIIT) |
| `EQUIPMENT` | `Bodyweight`, `Dumbbell`, `Barbell`, `Kettlebell`, `Bands`, `Machine`, `Bench`, `Pull-up bar` |
| `MUSCLE` | Secondary muscles worked. Same value set as `primaryMuscle`; the primary is **not** repeated here. |
| `PATTERN` | `Push`, `Pull`, `Hinge`, `Squat`, `Lunge`, `Carry`, `Plyometric` |

Example — `Kettlebell Swing`: `primaryMuscle = LEGS`, tags `TYPE:Strength`, `TYPE:HIIT`,
`EQUIPMENT:Kettlebell`, `MUSCLE:Back`, `MUSCLE:Core`, `PATTERN:Hinge`.

`primaryMuscle` is deliberately a field rather than a tag so every exercise has exactly one home
category, category counts do not double-count, and the Browse-by-muscle grid stays a clean
partition of the library.

Custom exercises created by the user or the Coach pick a `primaryMuscle` and any tags from the
same controlled vocabulary — the vocabulary itself is not user-extensible.

### Workout

| Field | Type | Notes |
|---|---|---|
| `id` | String | UUID |
| `name` | String | |
| `type` | TrainingType | `STRENGTH, HIIT, MOBILITY`. Single value, shown on the Home card meta line. Same enum as the `TYPE` facet's value set. Workouts are **not** tagged — see §12 #8. |
| `createdAt` | Instant | |
| `lastPerformedAt` | Instant? | Drives "Up next" selection |
| `sections` | List\<Section\> | Ordered |

### Section

| Field | Type | Notes |
|---|---|---|
| `id`, `workoutId`, `orderIndex` | | |
| `name` | String | `Warm Prep`, `Block A · Circuit` |
| `rounds` | Int | Default 1, minimum 1 |
| `restBetweenExercises` | Rest? | null = none |
| `restBetweenRounds` | Rest? | null = none |
| `restAfterSection` | Rest? | null = none. Suppressed after the last section. |
| `items` | List\<WorkoutItem\> | Ordered |

### Rest (sealed type)

- `Rest.Timed(seconds: Int)` — counts down, auto-advances
- `Rest.Manual` — waits for a tap ("tap next when ready")

Persisted in Room as two nullable columns per rest field: a mode string (`TIMED` / `MANUAL` /
null) and a seconds integer.

### WorkoutItem

| Field | Type | Notes |
|---|---|---|
| `id`, `sectionId`, `orderIndex` | | |
| `exerciseId` | String | FK → Exercise |
| `mode` | ItemMode | `TIMED, REPS, MANUAL` |
| `durationSec` | Int? | Required when `TIMED` |
| `sets` | Int? | Required when `REPS` |
| `reps` | Int? | Required when `REPS` |
| `weightKg` | Float? | Optional, `REPS` only |
| `note` | String? | e.g. `~5m apart · touch ground each end` |

### Item modes

| Mode | Screen | Advances when |
|---|---|---|
| `TIMED` | Countdown ring | Timer reaches zero (automatic) |
| `REPS` | Set checklist | User taps "Next exercise" |
| `MANUAL` | Count-**up** timer, large Done button | User taps Done |

A single workout may mix all three modes freely.

---

## 4. Segment compiler

The core of the app. Converts a `Workout` into a flat, ordered `List<Segment>` for execution.
Mirrors `buildSegments()` in the reference web app.

### Segment

| Field | Type | Notes |
|---|---|---|
| `kind` | `WORK` \| `REST` | |
| `mode` | `TIMED` \| `REPS` \| `MANUAL` | `REST` + `REPS` is invalid and never produced |
| `exerciseName` | String | For rests: `"Rest"` |
| `primaryMuscle` | Muscle? | null for rests |
| `equipmentLabel` | String? | First `EQUIPMENT` tag, denormalised for the reps sub-line. null for rests. |
| `note` | String? | |
| `demoQuery` / `demoUrl` | String? | null for rests |
| `durationSec` | Int? | null when `mode == MANUAL` or `REPS` |
| `sets`, `reps`, `weightKg` | | `REPS` only |
| `label` | String | Header line, e.g. `BLOCK A · CIRCUIT · ROUND 2/3` |
| `index`, `total` | Int | Position in the compiled list |

### Compilation rules

```
for each section in workout.sections (in order):
    for round in 1..section.rounds:
        for (i, item) in section.items.withIndex():
            emit WORK segment for item
            if i is not the last item AND section.restBetweenExercises != null:
                emit REST segment
        if round is not the last round AND section.restBetweenRounds != null:
            emit REST segment
    if section is not the last section AND section.restAfterSection != null:
        emit REST segment
```

Invariants, each of which gets a test:

1. No rest is emitted after the final item of a round — the round rest covers that gap
2. No round rest after the final round of a section
3. No section rest after the final section
4. A section with one item never emits an inter-exercise rest
5. A null rest emits nothing (not a zero-length segment)
6. `label` includes `ROUND n/N` only when `section.rounds > 1`
7. `index` and `total` are assigned after the full list is built

### Duration estimation

Used for the Home card meta line (`45 min`) and the Coach preview. Constants are tunable and
defined in one place.

| Segment | Estimate |
|---|---|
| `TIMED` | `durationSec` |
| `REPS` | `sets × 45s` (work plus inter-set rest) |
| `MANUAL` work | 20s |
| `MANUAL` rest | 60s |

Reference values from the web app's `MANUAL_ESTIMATES` were work 15s / rest 75s; rounded here
for legibility.

---

## 5. Design tokens

> **Amended 2026-07-26 — token rescale.** The px values originally recorded here were measured
> inside the handoff's 264px-wide mock phone frame, which is a thumbnail rather than a device.
> They shipped at ~62% of intended size. `ui/theme/Type.kt` and `ui/theme/Dimens.kt` are now the
> authority for every size, spacing and radius in the app; this section describes intent, not
> exact numbers. See `docs/superpowers/plans/2026-07-26-design-correction-pass.md`.

Every colour is referenced by **role**, never as a literal hex value, from the first screen
onward. This is what makes dark mode approximately free rather than a retrofit.

### Colour roles

| Role | Light | Dark | Used for |
|---|---|---|---|
| `background` | `#FFFFFF` | `#0B0B0D` | Page |
| `surface` | `#F1F1EF` | `#18181C` | Chips, search field, category tiles, duration badges |
| `surfaceInverse` | `#0B0B0D` | `#F4F4F2` | Featured category tile, Build button. *(Was also the Home hero card; the hero is deleted — see §6.1. The token is kept, rescaled, reserved for Plan 1D's execution "Up next" card.)* |
| `onBackground` | `#0A0A0A` | `#FFFFFF` | Primary text |
| `onSurface` | `#0B0B0D` | `#FFFFFF` | Text on `surface` |
| `onSurfaceInverse` | `#FFFFFF` | `#0B0B0D` | Text on `surfaceInverse` |
| `textMuted` | `#6B6B6B` | `#9A9A9A` | Meta lines, counts, placeholders |
| `textMutedAlt` | `#5F5F5F` | `#8A8A8A` | Secondary meta |
| `textDisabled` | `#C2C2C2` | `#5A5A5A` | Completed / struck-through set rows |
| `navInactive` | `#767676` | `#5A5A5A` | Inactive bottom-nav item |
| `border` | `rgba(0,0,0,.13)` | `rgba(255,255,255,.14)` | Card hairlines |
| `borderSubtle` | `rgba(0,0,0,.08)` | `rgba(255,255,255,.08)` | Bottom-nav top border |

**Amended 2026-07-26 — light and dark muted greys deliberately differ.** `textMuted` /
`textMutedAlt` / `navInactive` are no longer symmetric across themes: the original shared values
were a WCAG AA contrast failure on a white background (light-mode `textMuted` was 2.8:1 on
white). The light column above is corrected; the dark column is unchanged because it was never
the problem. See `ui/theme/Colors.kt` and STATUS.md.

**Mono accent rule:** the accent equals maximum contrast against its own surface —
`onSurfaceInverse` when on `surfaceInverse`, `onBackground` when on `background`. There is no
accent hue, so no per-theme contrast auditing is required.

Because colour no longer carries hierarchy, hierarchy comes from **weight, size, letter-spacing
and spacing**. Two places need explicit attention:

- **Featured (black) category tile:** name and count would both be white. Name stays `w700`
  at full opacity; count drops to 70% opacity to preserve the hierarchy the accent used to
  carry.
- **"Up next" hero label** *(reserved for Plan 1D's execution screen now that Home's hero is
  deleted — see §6.1)*: already differentiated at 12sp / w700 / uppercase / +0.08em against
  a 26sp / w800 name. No change needed.

### Execution screen (fixed in both themes)

| Token | Value |
|---|---|
| Background | `#0B0B0D` |
| Card | `#18181C` |
| Border | `rgba(255,255,255,.14)`, controls `rgba(255,255,255,.2)` |
| Ring track | `rgba(255,255,255,.12)` |
| Ring progress | `#FFFFFF` |
| Text / muted | `#FFFFFF` / `#9A9A9A` |

### Typography

Platform system sans (Roboto on Android) — the handoff specifies system fonts with no bundled
files. Weights 600 / 700 / 800 map to `SemiBold` / `Bold` / `ExtraBold`.

| Element | Size | Weight | Tracking |
|---|---|---|---|
| Overline date | 13sp | 600 | +0.02em |
| Screen title | 40sp | 800 | −0.035em, line-height 42sp |
| Library title | 40sp | 800 | −0.03em |
| Hero label | 12sp | 700 | +0.08em, uppercase |
| Hero workout name | 26sp | 800 | |
| Hero meta | 16sp | 400 | |
| Primary button | 16sp | 700 | |
| Build button | 17sp | 700 | |
| Card title / meta | 18sp / 14sp | 700 / 400 | |
| Section label | 13sp | 800 | +0.06em, uppercase |
| Chip | 14sp | 600 | |
| Category tile name / count | 18sp / 14sp | 700 / 600 | |
| Execution move name | 34sp | 800 | −0.02em |
| Muscle tag | 13sp | 600 | +0.04em, uppercase |
| Timer digits | 44–50sp (unchanged — Plan 1D has not built the execution screen; no token exists yet) | 800 | −0.03em, tabular figures |
| Reps exercise name | 38sp | 700 | −0.02em |

### Spacing, radii, elevation

- Screen padding 20dp; card padding 18dp; gaps 14dp
- Radii: chips 12dp, cards/tiles 18dp, hero 22dp, buttons 16dp, checkbox 8dp
- **No shadows.** Hairline borders only. The drop shadow in the prototype belongs to the mock
  phone frame and is not shipped.
- The prototype's phone bezel, status bar, and gesture pill are presentation chrome — not
  shipped. The app renders fullscreen.

### Motion

- Countdown ring: linear sweep tied to remaining time
- Everything else: 150–250ms ease
- Bottom-nav active state: instant swap, no transition
- Build sheet: standard Material bottom-sheet spring, swipe-to-dismiss

---

## 6. Screens

### 6.1 Home

Single scrolling column, 20dp horizontal padding.

**Populated (amended 2026-07-26 — describes the current build, not the handoff):** overline
date (`TUE · JUL 21`) → two-line title `YOUR WORKOUTS` → `CHOOSE A WORKOUT` label → one
full-width hairline card per workout, newest-created first (name + estimated minutes on row 1,
short meta + last-done line on row 2) → pinned `BUILD A WORKOUT` button → bottom nav. The
handoff's hero card is gone; every workout is an equal-weight card. Authority:
`ui/home/HomeScreen.kt`.

**Empty (amended 2026-07-26):** same header, no `CHOOSE A WORKOUT` label, a single hairline
"No workouts yet" card ("Build one from the library and it will appear here.") in place of the
workout list → the Build button. The handoff's hero-styled empty treatment is gone.

**Amended 2026-07-26 — no "Up next".** Home does not rank or recommend. It presents two
options: **choose a workout** (equal-weight cards under a `CHOOSE A WORKOUT` label, ordered
newest-created first) or **build a workout** (the pinned button). Each card carries its own
`Never done` / `4 days ago` last-done line. The original rule ranked by `lastPerformedAt`, which
nothing writes until Plan 1D, so it could only ever resolve to "the last workout you created"
while presenting itself as a recommendation. `domain/UpNext` is deleted.

**Components as built (amended 2026-07-26 — supersedes the handoff description):** workout card
(`HairlineCard`, 1dp hairline border, radius 18dp, padding 18dp), empty-state card (same
`HairlineCard` treatment), Build button (56dp, radius 16dp, `surfaceInverse`, leading `+` glyph).
The handoff's hero card, its 36–38dp leading minutes badge, and its 13–15dp card radii no longer
exist on this screen — `surfaceInverse` now appears on Home only via the Build button.

### 6.2 Build sheet

Raised by the Build button. Modal bottom sheet, swipe-down or tap-outside to dismiss. Two
equal-weight bordered cards:

- **Build it yourself** — "Pick exercises from the library" → Builder
- **Ask the Coach** — "Describe your goal, get a workout drafted" → Coach

### 6.3 Library

Column, 16–18dp padding: title `LIBRARY` → search field (44dp, radius 13dp, `surface`) → filter
row → `Browse by muscle` section label → two-column category grid (gap 11dp, radius 15dp,
min-height 56dp) → bottom nav.

**Filter row.** Horizontally scrolling `TYPE` chips (`All · Strength · HIIT · Mobility`) exactly
as the handoff specifies, plus a trailing **Filters** control carrying a count badge of active
facets set in the sheet. The chip row is a shortcut onto the same filter state the sheet edits.

**Category grid.** The first tile is the featured `surfaceInverse` tile; the rest use `surface`.
**Counts are computed from the database and respect the active filter** — selecting
`EQUIPMENT:Bodyweight` changes `Chest 12` to `Chest 4`. A category reaching zero renders at 40%
opacity and is not tappable. If every category is empty, the grid is replaced by an empty state
offering **Clear filters**.

**Search** matches exercise names *and* tag values, so typing `kettlebell` finds every
kettlebell exercise without opening a filter. Search combines with active filters.

Tapping a category opens its exercise list, which inherits the active filter. Tapping an
exercise opens its detail screen: name, primary muscle, tags, Watch demo, and a field to paste a
specific YouTube URL.

The Library is reused in **pick mode** from the Builder, where each row gains an add affordance
and selection returns to the Builder. Filters and search behave identically in pick mode.

### 6.3a Filters sheet

Modal bottom sheet raised by the Filters control. Swipe-down or tap-outside to dismiss; changes
apply live, so dismissing keeps them.

Contents: a header (`FILTERS`, with **Clear all**), then one labelled group per facet — `TYPE`,
`EQUIPMENT`, `PATTERN`, and `ALSO HITS` (the `MUSCLE` facet, i.e. secondary muscles) — each a
wrapping grid of chips using the standard chip token. A pinned primary button at the bottom
reads **`SHOW N EXERCISES`** with a live count and dismisses the sheet.

`TYPE` appears in the sheet as well as the chip row; both edit the same state and stay in sync,
so **Clear all** genuinely clears everything.

**Filter logic:** multi-select within a facet combines with **OR**; facets combine with **AND**.
`TYPE:HIIT` + `EQUIPMENT:Dumbbell` + `EQUIPMENT:Kettlebell` means *HIIT exercises using either
dumbbells or kettlebells*. An empty facet applies no constraint. Selecting `All` on the chip row
clears the `TYPE` facet.

### 6.4 Builder

Not designed in the handoff — built in the same visual language.

Workout name field → ordered list of collapsible sections → `+ Add section` → `SAVE WORKOUT`.

Each section shows its name, round count, its ordered items, `+ Add exercise` (opens Library in
pick mode), and rest settings (between exercises, between rounds, after section — each
Off / Timed / Manual). **Both sections and items reorder by drag**, and both delete by swipe
with undo.

Each item row edits its mode and the fields that mode requires: duration for `TIMED`, sets ×
reps and optional weight for `REPS`, nothing for `MANUAL`. An optional note field is available
on every item.

### 6.5 Execution

Always dark. Header row (`WORKOUT NAME · 2/5`, close ✕) and a segmented progress bar are
constant across all modes.

The progress bar has **one segment per `WORK` segment, excluding rests** (3dp tall, gap 4dp,
radius 2dp; completed solid, remaining at 12% opacity). Counting every compiled segment would
produce ~40 slivers on a workout like Court Speed. This matches the handoff's intent — "N equal
segments (one per exercise)" — and the header counter uses the same denominator.

**Timed / Manual:** centred move name (24–26sp) → muscle tag → countdown ring (158–176dp, 10dp
stroke, round cap, starting at 12 o'clock) with time and `REMAINING` inside → Watch demo card
(`#18181C`, radius 13dp) → Up next card (1dp border) → Pause / Skip controls.

`MANUAL` uses the same layout with the ring counting **up** and acting as a tap target, plus a
`Done ›` primary control in place of Skip.

**Reps:** same header and progress bar, then exercise name (28sp) → `Chest · Barbell` sub-line
(`primaryMuscle` plus the first `EQUIPMENT` tag; muscle alone when the exercise has none)
→ Watch demo card → `SETS` label → checkable set rows (20dp checkbox, radius 6dp; completed
rows struck through and set to `textDisabled`) → `Next exercise ›`.

Behaviour: the ring animates down and auto-advances at zero; Pause halts the clock; Skip jumps
forward; ✕ exits with a confirmation. On the final segment, a finish screen.

### 6.6 AI Coach

Prompt field ("What are you training for?") + optional duration and equipment chips →
`BUILD MY WORKOUT` → loading state → preview (workout name, estimated duration, collapsed
sections) → `Save` / `Regenerate` / `Tweak…` (a second prompt refining the previous result).

### 6.7 Settings

Anthropic API key (masked, with a link to where to get one), theme (Light / Dark / System),
sound on/off, keep screen on during workouts.

---

## 7. Execution engine

### Foreground service

The timer and audio live in a foreground service, not in the UI. This is the mechanism that
makes cues survive screen-off and backgrounding — the primary failure of the web app.

- Service type `mediaPlayback` (required from Android 14)
- Persistent notification shows current exercise and remaining time, with pause and skip actions
- The UI observes service state; it never owns the clock

### Timing

Wall-clock, never tick-counting. Each segment records
`startedAt = SystemClock.elapsedRealtime()`; remaining is
`durationSec − (now − startedAt − pausedAccumulated)`. `elapsedRealtime()` continues through
device sleep, so a 30-second interval remains 30 seconds regardless of scheduling delay. Tick
accumulation would drift.

### Audio

- `SoundPool` with pre-loaded tones — built for short, low-latency, repeated sounds
- Tones generated on first launch as PCM using the reference app's maths (sine wave, 5ms
  fade-in, exponential decay), written to cache, loaded into `SoundPool`. No audio assets
  shipped. Frequencies: **720Hz** countdown, **1100Hz** go, **880Hz** finish.
- Cue points: 3-2-1 countdown beeps in the final three seconds of **every timed segment, work
  and rest alike**; go tone on every transition into a new segment; finish tone at workout end
- Audio focus requested as `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` — music dips for the cue rather
  than stopping
- `MediaSession` exposes pause/skip to lock screen and headset controls
- Cues respect the Settings sound toggle

### Screen and video

- `keepScreenOn` while the execution screen is visible (subject to the Settings toggle)
- Watch demo fires an implicit `Intent` — `demoUrl` if set, otherwise a YouTube search for
  `demoQuery`. Opens the YouTube app when installed, browser otherwise.

### Permissions

`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` (Android 13+,
requested once at first workout start with an explanation), `INTERNET`. No location, contacts,
or external storage.

---

## 8. Storage

- **Room** database: `exercises`, `exercise_tags`, `workouts`, `sections`, `workout_items`.
  Fully offline. `exercise_tags` is a join table (`exerciseId`, `facet`, `value`) indexed on
  `(facet, value)` so filtering stays fast without loading the library into memory.
- Library seeded on first launch from `ExerciseSeed.kt` (~100 entries across Chest, Back, Legs,
  Shoulders, Arms, Core, Cardio, Mobility, Full body). Seed data is versioned so future app
  updates can add exercises without wiping custom ones or duplicating existing rows.
- The eight Court Speed exercises carry their verified `demoUrl` values from
  `web-app-reference/HIIT Workout.html`.
- **API key** in `EncryptedSharedPreferences` — separate from the database, excluded from
  Android backup (`allowBackup="false"`), never logged.

---

## 9. AI Coach

| | |
|---|---|
| Model | `claude-opus-4-8` |
| Thinking | `{"type": "adaptive"}` |
| Effort | `output_config.effort = "medium"` — tunable if quality or latency needs adjusting |
| Max tokens | 8000, non-streaming |
| Output | **Structured outputs** via `output_config.format` with a JSON schema |
| Caching | `cache_control: {"type": "ephemeral"}` on the library block in the system prompt |
| Expected cost | ~5–15¢ per generated workout |
| Expected latency | 15–30s |

### Why structured outputs

Claude is constrained to emit exactly the declared schema — no prose parsing, no markdown
fences, no shape drift. This is the largest single reliability gain available and removes an
entire class of failure.

### Schema shape

Non-recursive, three fixed levels (workout → sections → items), `additionalProperties: false`
on every object, explicit `required` arrays. Optional fields use `anyOf: [{type: X}, {type:
"null"}]` rather than union type arrays, which are not documented as supported.

```
workout:  name, type (Strength|HIIT|Mobility), sections[]
section:  name, rounds, restBetweenExercisesSec?, restBetweenRoundsSec?, items[]
item:     exerciseId?, exerciseName, primaryMuscle, tags[], mode (TIMED|REPS|MANUAL),
          durationSec?, sets?, reps?, note?
tag:      facet (TYPE|EQUIPMENT|MUSCLE|PATTERN), value
```

**Deliberate simplifications in the Coach schema**, both to keep it flat and unambiguous:

- Rests are plain integers and map to `Rest.Timed`. The Coach cannot produce `Rest.Manual`;
  the user adds those in the Builder when needed.
- `restAfterSection` is not exposed. The Coach expresses between-section recovery as a section
  rest or an explicit rest item; the field remains available in the Builder.

### Prompt

System prompt contains the exercise library as `id · name · primaryMuscle · tags` (~2.5k tokens),
plus the controlled tag vocabulary from §3, with instructions to prefer existing IDs and to set
`exerciseId: null` when inventing a new exercise. Invented exercises are created in the library
on save, marked `isCustom`, with a generated `demoQuery`.

Tags on invented exercises are validated against the controlled vocabulary; unrecognised facets
or values are dropped rather than rejecting the whole workout, so one bad tag never costs the
user a generation.

User message carries the free-text goal plus any selected chips.

### Client-side validation

Structured outputs do not support numeric constraints, so the response is validated after
parsing: `rounds >= 1`; `TIMED` requires `durationSec > 0`; `REPS` requires `sets > 0` and
`reps > 0`; every item has a non-empty `exerciseName`; at least one section with at least one
item. Failure shows *"Couldn't build that one"* with a Regenerate action.

### Error handling

| Condition | Behaviour |
|---|---|
| No API key | Route to Settings with an explanation |
| No network | Clear message, Retry |
| 429 / 5xx | SDK automatic retry with backoff; then a Retry action |
| `stop_reason == "refusal"` | Show the refusal text; do not auto-retry |
| Validation failure | "Couldn't build that one" + Regenerate |

### SDK

Official `com.anthropic:anthropic-java` (Kotlin uses the Java SDK). **Unverified on Android** —
the library targets server environments and pulls in OkHttp and Jackson. Viability is proven as
the first task of Phase 2. Documented fallback: direct HTTPS via OkHttp + `kotlinx.serialization`
against `POST /v1/messages`, approximately 100 lines.

---

## 10. Architecture

Single Gradle module, organised by responsibility:

```
domain/     Workout, Section, WorkoutItem, Exercise, Segment
            SegmentCompiler.kt      ← pure, no Android imports
            DurationEstimator.kt    ← pure, no Android imports
data/       Room entities, DAOs, database
            ExerciseSeed.kt
            SecurePrefs.kt          ← EncryptedSharedPreferences
            WorkoutRepository.kt
ai/         CoachClient.kt, CoachSchema.kt, CoachResponseMapper.kt
workout/    WorkoutService.kt       ← foreground service, owns the clock
            ToneGenerator.kt, AudioCues.kt, WorkoutNotification.kt
ui/         theme/  Colors.kt, Type.kt, Dimens.kt, Theme.kt
            components/  HeroCard, WorkoutRow, CategoryTile, FilterChip,
                         CountdownRing, SetRow, DemoCard, BottomNav
            home/  library/  builder/  execution/  coach/  settings/
MainActivity.kt
```

**Key boundary:** `domain/` has zero Android imports. All the logic capable of being wrong —
round expansion, rest insertion, ordering, duration maths — is testable on the JVM in
milliseconds without a device or emulator.

Compose screens are driven by ViewModels exposing immutable UI state; the repository mediates
between Room and ViewModels; the execution screen observes the service.

---

## 11. Testing

Test-driven on the compiler: tests are written before the implementation.

### Unit tests (JVM, no device)

**`SegmentCompiler`** — one test per invariant in §4, plus: single section / single round;
multi-round expansion; rest between exercises; round rest present between rounds and absent
after the last; section rest present between sections and absent after the last; manual rests;
mixed `TIMED`/`REPS`/`MANUAL` in one workout; empty section; single-item section; null rests
emit nothing; `index`/`total` correctness; `ROUND n/N` label only when `rounds > 1`.

**Acceptance test — Court Speed fixture.** Rebuild the reference workout (Warm Prep 4 moves;
Block A 4 moves × 3 rounds, 30s work / 15s rest, 60s round rest; manual transition; Block B 6
manual sprints with manual walk-back; manual bonus) in the data model, compile it, and assert
the resulting segment list matches `buildSegments()` in `web-app-reference/HIIT Workout.html`.
Asserts segment count, ordering, kind, mode, durations, and work-segment exercise names. Rest
labels are generic and not asserted. **If this passes, the data model can express a real
workout.**

**`DurationEstimator`** — per-mode estimates and full-workout totals.

**`ExerciseFilter`** — OR within a facet; AND across facets; empty facet applies no constraint;
`All` clears the `TYPE` facet; search matches names and tag values; search combines with
filters; category counts reflect the active filter; a filter matching nothing yields an empty
result rather than an error.

**`CoachResponseMapper`** — a recorded JSON response maps to correct domain objects, including
an invented exercise; each validation rule in §9 rejects appropriately.

### Manual verification (real device, cannot be automated)

Audio with the screen off and the phone in a pocket; notification pause/skip; lock-screen
controls; music ducking; YouTube handoff for both search and override URLs; light and dark
themes across every screen; empty state on a fresh install.

---

## 12. Deviations from the design handoff

Each was raised and resolved with the user.

| # | Handoff | Built | Rationale |
|---|---|---|---|
| 1 | Filter chip `Yoga` | `Mobility` | No Yoga category exists; the prototype tags workouts `Mobility`. Spec slip. |
| 2 | Bottom nav 2 items, no Coach | 2 items; Coach inside the Build flow | Build sheet forks to manual or Coach — conceptually both are ways to make a workout |
| 3 | Reps screen light, timed dark | Both dark | A mixed workout would invert the screen repeatedly mid-session |
| 4 | Timed and reps only | `MANUAL` mode added | Court Speed sprints cannot be expressed otherwise |
| 5 | Hardcoded category counts | Computed from the database | Counts go stale as soon as an exercise is added |
| 6 | Library fixed | Custom exercises supported | Needed for both user additions and Coach-invented moves |
| 7 | Single theme (light) | Light + dark | User request |
| 8 | Exercise has one `type` and an `equipment` string | Faceted `tags` + a `primaryMuscle` field, with a Filters sheet | A single type can't express that a Kettlebell Swing is both Strength and HIIT, and `equipment` was unfilterable. Applies to exercises only — workouts keep a single `type`. |

Also newly designed, absent from the handoff: Builder, Build sheet, AI Coach, Settings, empty
state, finish screen.

---

## 13. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Manufacturer battery optimisation (Samsung, Xiaomi, OnePlus) kills the foreground service | High | Foreground service with `mediaPlayback` type is the strongest available defence; detect and offer a one-time battery-optimisation exemption prompt. Not fully solvable — an Android ecosystem constraint. |
| `anthropic-java` incompatible with Android | Medium | Proven as the first task of Phase 2; documented OkHttp fallback |
| ~100 exercises is content work with judgement calls, now including 3–6 tags each | Medium | Drafted for user review; expect corrections to names, primary muscles, and tags. Tagging is the largest judgement surface — a wrong tag makes an exercise unfindable by filter, so this warrants a real review pass rather than a skim. |
| Mono removes colour as a hierarchy signal | Low | Explicit weight/size/opacity treatment; two known cases handled in §5 |

---

## 14. Build order

**Phase 1 — core loop.** Delivers a usable app: build a workout, run it with audio.

1. Project skeleton, both theme token tables, Room database, seeded exercise library
2. `SegmentCompiler` + `DurationEstimator` with tests **(test-first)**
3. Home screen (populated and empty) + Library (browse, chip row, Filters sheet, search,
   exercise detail)
4. Builder + Build sheet + Library pick mode
5. Execution screens (timed / reps / manual), foreground service, tone generation, audio cues,
   notification, finish screen

**Phase 2 — AI Coach.** Bolts on without touching the data model.

6. Settings screen + encrypted API key storage
7. `anthropic-java` Android viability check, then Coach client, schema, preview, save

---

## 15. Out of scope

Workout history and progress tracking; charts and personal records; rest-day scheduling; social
or sharing features; cloud sync; watch companion; Play Store release; iOS.
