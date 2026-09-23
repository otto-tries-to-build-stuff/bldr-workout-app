# Plan 1D — Execution & Audio Design

**Date:** 2026-09-23
**Status:** Approved, ready for implementation planning
**Extends:** `2026-07-21-workout-builder-android-design.md` §6.5 (execution screen) and §7
(execution engine), which remain authoritative for visual treatment, timing maths, tone
frequencies, and permissions. This document records only what that spec left open, plus the
decisions taken on 2026-09-23.

---

## 1. Scope

Plan 1D delivers the last item of Phase 1's build order: a workout you have built can be run
end to end, with audio cues that survive the screen being off and the phone being in a pocket.

`SegmentCompiler` already produces the ordered list of steps a workout runs through, test-first
and covered by 12 tests. 1D *plays* that list; it does not recompute it.

**In scope**

- Execution screen in all three modes — `TIMED`, `REPS`, `MANUAL` (§6.5)
- Foreground service owning the clock and the audio (§7)
- Tone generation, cue points, audio focus, `MediaSession` (§7)
- Notification with pause / skip
- Finish screen
- Mute control (see §3.1)
- Recording that a workout was performed (see §3.4)
- Wiring the `onStartWorkout` seam already marked `TODO(Plan 1D)` in `ui/AppNav.kt`

**Out of scope**

- Settings screen and its toggles — Phase 2, build order item 6
- Battery-optimisation exemption prompt (see §3.2)
- Workout history, progress tracking, charts — out of scope project-wide (§15 of the main spec)
- Editing a saved workout from Home — still an unwired seam, reserved for a later plan

---

## 2. Architecture

Three units, each independently testable, following the boundary the project already enforces
via `DomainPurityTest`.

### 2.1 `domain/WorkoutSession.kt` — the brain (pure, Android-free)

Holds the compiled `List<Segment>`, the current index, and the timestamps that describe the
run. It is a **pure function of a clock reading**: given `elapsedRealtime()`, it answers

- which segment is current, and how much time remains on it
- whether the session has advanced past the end
- which cue, if any, fires at this instant

It never reads the clock itself and never plays a sound — the caller passes the time in. This
is what makes "does it beep at 3, 2, 1 and advance at zero" a JVM test that runs in
milliseconds rather than a stopwatch-and-phone exercise.

Timing follows §7: wall-clock, never tick accumulation. `startedAt` per segment,
`pausedAccumulated` subtracted, so a 30-second interval stays 30 seconds through scheduling
delay and device sleep.

Transitions it must model: advance at zero (timed), advance on tap (manual, reps), skip,
pause, resume, and finish.

### 2.2 `ExecutionService` — the phone bits

A foreground service, type `mediaPlayback`, which:

- reads the real clock and drives the session at ~10 Hz
- plays tones through `SoundPool` (720 Hz countdown, 1100 Hz go, 880 Hz finish)
- generates those tones as PCM on first use from the reference app's maths
  (`makeWav(freq, duration, volume)`, defined at `web-app-reference/HIIT Workout.html:1466`,
  called with the three frequencies at 1542–1544) and caches them — no audio assets shipped
- requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` so music dips rather than stops
- publishes session state as a `StateFlow` for the UI
- runs the notification (current exercise, remaining time, pause and skip actions)
- exposes pause/skip to lock screen and headset via `MediaSession`

### 2.3 `ui/execution/` — the screen

Observes the service's state and draws it. Sends user intent (pause, skip, done, tick a set,
mute, exit) back to the service. It never owns the clock, and it must render correctly when
re-attached mid-workout — returning from the notification is the normal path, not an edge case.

`keepScreenOn` while the execution screen is visible; no toggle in this phase.

---

## 3. Decisions taken 2026-09-23

### 3.1 Sound control without a Settings screen

The main spec says cues "respect the Settings sound toggle", but Settings is Phase 2. Until it
exists:

- A **mute control in the execution screen header**, persisted in `AppPrefs`
- Cues play on the media stream, so the hardware volume keys are a second, free control
- Phase 2's Settings toggle reads the same `AppPrefs` value — no rework

### 3.2 Battery-optimisation prompt: deferred, deliberately

The main spec's highest-severity risk is manufacturer battery-savers killing the service, with
a one-time exemption prompt as mitigation. **1D does not build that prompt.** The foreground
service is the strongest available defence regardless; whether this specific phone needs the
prompt is answerable by testing on it. Building the prompt first would be speculative code
against a problem we may not have, and it is awkward to verify on a device that behaves.

If hands-on testing (§5) shows cues dying in the background, the prompt becomes its own small
plan, written knowing exactly what it fixes.

### 3.3 Timer brain separated from the phone bits

See §2.1. The alternative — logic inside the service — was rejected because it makes the
correctness-critical part of the phase testable only by hand.

### 3.4 Reaching the finish screen records the workout as performed

`WorkoutRepository.markPerformed` exists and has never been called, so `lastPerformedAt` is
never written and Home shows "Never done" under every workout permanently. 1D calls it when the
finish screen is reached, so Home's existing `lastDoneLine` ("Yesterday", "3 days ago") starts
telling the truth.

This is not the start of workout history: nothing new is stored, no per-run records, no charts.
It writes the single timestamp field the data model already has and Home already renders.

---

## 4. Testing

### Automated (JVM, no device)

`WorkoutSessionTest` carries the weight of this phase:

- advances at zero on a timed segment; does not advance early
- manual and reps segments advance only on explicit completion
- pause freezes remaining time; resume continues from the same point, not from the start
- skip jumps to the next segment and re-arms its cues
- countdown cues fire at exactly 3, 2 and 1 seconds remaining, once each, on **every** timed
  segment including rests
- the go cue fires on entering each new segment; the finish cue fires once at the end
- a long scheduling gap (simulating device sleep) lands on the correct segment rather than
  replaying every intermediate one
- the final segment completing moves to finished, and marks the workout performed

### Emulator

The three screen modes render correctly; the notification appears; returning to the app from
the notification shows the run still in progress.

### Hands-on, real phone

Cannot be automated, and is the point of the phase. Full checklist in §5.

---

## 5. Hands-on verification checklist (real phone)

Run a real workout — Court Speed is the acceptance fixture and takes 22 minutes; a short
2-exercise timed workout is enough for most of these.

1. **Pocket test.** Start a workout, lock the screen, put the phone in a pocket, and do the
   workout. Every countdown and transition cue must fire, on time, for the whole workout.
2. **Screen-off accuracy.** With the screen off for a full timed segment, the cue at the end
   arrives at the right moment — not late, not skipped.
3. **Notification controls.** Pause and skip from the notification shade both work, and the
   screen agrees with them when reopened.
4. **Lock-screen and headset.** Pause/skip from the lock screen; headset button pauses.
5. **Music ducking.** Play music, start a workout: music dips for each cue and recovers,
   rather than stopping.
6. **Mute.** Muting silences cues for the rest of the workout and the setting survives leaving
   and restarting the app.
7. **Interruptions.** Take a phone call mid-workout; the workout pauses or continues
   sensibly, and does not silently die.
8. **Exit.** ✕ asks before quitting; quitting stops the service and clears the notification.
9. **YouTube handoff.** Watch demo opens the YouTube app for a pinned URL, and the browser for
   a search.
10. **Finish.** Reaching the end plays the finish tone, shows the finish screen, and Home then
    shows "Today" instead of "Never done" for that workout.
11. **Battery-saver survival.** Leave a workout running in the background for its full length
    without touching the phone. If cues stop, note the phone model and maker — that is the §3.2
    risk materialising, and the trigger for building the exemption prompt.

---

## 6. Risks specific to this phase

| Risk | Severity | Mitigation |
|---|---|---|
| Manufacturer battery-saver kills the service | High | Foreground service; measured on a real phone before writing mitigation code (§3.2) |
| Cue fires twice, or is missed entirely, across a scheduling gap | Medium | The session derives cues from elapsed time rather than counting ticks; covered by the gap test in §4 |
| Service and UI disagree after the screen is re-attached | Medium | Service is the single source of truth; the UI holds no clock state of its own |
| Tone generation on first use adds latency to the first cue | Low | Generate and cache at service start, before the first segment begins |
