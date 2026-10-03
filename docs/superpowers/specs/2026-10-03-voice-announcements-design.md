# Voice announcements — design

**Date:** 2026-10-03 · **Branch:** `voice-announcements` (from `bldr-brand`) · **Status:** approved in conversation, awaiting spec review

## Goal

Run a workout without looking at the phone: a spoken voice names each exercise before it
starts, alongside the existing beeps. This first version uses the phone's built-in
text-to-speech, to settle *what* is said and *when* before deciding whether recorded
ElevenLabs clips are worth it.

## What the person experiences (voice on)

1. **Workout start** — the GET READY screen lasts 6 s (was 3). Voice: "First up: Push-ups".
   Then 3-2-1 beeps, go.
2. **Exercise ends** — unchanged: a timed exercise beeps 3-2-1; reps/manual wait for Done.
3. **Rest** — voice says "Rest" as it begins. The rest counts down **silently**: its 3-2-1
   moves to the get-ready step that follows.
4. **Get ready (new, 6 s, before every exercise)** — GET READY screen, big countdown,
   "Next up: Push-ups". Voice: "Next up: Push-ups". Then 3-2-1, go.
5. **Back-to-back exercises (no rest)** — exercise's own 3-2-1 to finish → get-ready step →
   3-2-1 → go. Two countdowns, by choice: one to finish, one to start.
6. **Workout end** — finish tone, then "Workout complete".

Only the exercise name is spoken — no reps, durations, "halfway" or "last round".

**Voice off:** behaviour is exactly today's (one 3 s lead-in before the first segment, rests
beep 3-2-1, no speech).

## Settings and mute

- Settings → Sound: new **"Voice announcements"** switch under "Workout cues", **on by default**.
- "Workout cues" becomes the master: off silences beeps *and* voice.
- The voice switch is read when a workout starts; changing it affects the next workout.
- The in-workout mute button silences beeps and voice. Get-ready steps already inserted stay,
  so a workout's timing never changes mid-run.
- The get-ready screen keeps today's lead-in layout: no on-screen Pause/Skip. Lock-screen and
  headset controls still work.

## Technical design

### Domain (pure, JVM-tested) — `domain/WorkoutSession.kt`

- `WorkoutSession.start(segments, now, leadInSec, voice: Boolean = false)`.
  - `voice = false`: unchanged — one `LEAD_IN` copy of the first segment.
  - `voice = true`: a `LEAD_IN` copy (TIMED, `leadInSec`) is inserted before **every** `WORK`
    segment. The service passes `leadInSec = 6` with voice, `3` without.
- `SessionTick` gains `speech: String? = null` — the line to say at that transition:
  - entering a `LEAD_IN`: "First up: <name>" at session index 0, else "Next up: <name>";
  - entering a `REST`: "Rest";
  - entering a `WORK` segment: nothing (the get-ready step already named it);
  - entering a `LEAD_IN` also plays **no go tone** — the voice opens the step, and "go" is
    kept for the exercise itself;
  - finishing: "Workout complete".
  Emitted by `start`, `advance` (tick auto-advance, landing segment only on catch-up), `skip`
  / `complete`, and `restart` (re-says the current line unless paused). It is emitted
  regardless of the voice setting; the service decides whether to speak it.
- **Rest countdown suppression:** `tick` plays no 3-2-1 on a `REST` segment whose next segment
  is a `LEAD_IN`. With voice off a rest is never followed by a `LEAD_IN`, so today's
  behaviour is untouched.

### Audio — `audio/CuePlayer.kt`

`CuePlayer` gains speech, so tones and voice share one audio-focus (music ducking) policy:

- Wraps `android.speech.tts.TextToSpeech`. `prime()` also waits for its init (same 2 s
  budget as the tones). Language: the phone's own locale if it is English, else US English
  (exercise names are English).
- `say(text)` — no-op when muted, voice off, released, or TTS unavailable. `QUEUE_FLUSH`, so a
  new line replaces an unfinished one. Same audio attributes as the tones.
- Focus: requested on `say`, and the release is pushed out again when the utterance finishes
  (`UtteranceProgressListener.onDone`), so music stays ducked for the whole sentence.
- `stopSpeech()` — called on pause.
- If the phone has no TTS engine or no English voice: log a warning, stay silent; the workout
  runs normally with beeps.

### Service — `execution/ExecutionService.kt`

- Reads `prefs.voiceOn` at workout start; sets `cues.voiceOn`; calls
  `WorkoutSession.start(..., leadInSec = if (voice) 6 else 3, voice = voice)`.
- Speaks `tick.speech` alongside `tick.cues`, under the same "only what was committed" rule
  `updateWithCues` already applies to cues.

### Prefs and UI

- `data/AppPrefs.kt`: `voiceOn: Boolean`, default `true`.
- `ui/settings/`: `voiceOn` in UI state, `onVoiceOn` handler, a second switch row (shared
  private row composable rather than duplicating the switch styling).
- `ui/execution/ExecutionScreen.kt`: the lead-in caption reads "First up" at session index 0,
  "Next up" otherwise.

### Not changing

- Home-card time estimates do not include get-ready time (~6 s per exercise with voice on).
- No ElevenLabs, no recorded clips, no network.

## Testing

- `WorkoutSessionTest`: voice-on insertion (before every WORK, none before REST); voice-off
  segments identical to today; rest followed by get-ready plays no countdown, work followed by
  get-ready still does; speech lines for start, rest, get-ready, finish, skip, restart and
  sleep catch-up; existing tests unchanged.
- Build and full test suite green.
- Emulator: run a workout with voice on and off, confirm the screen sequence and Settings
  switch; confirm speech calls in logcat.
- Real phone (user): hear it with music playing and the screen off.
