# How-to help & welcome tour — design

Date: 2026-10-04. Approved in chat by the user, section by section; the user waived reviewing this file and the plan.

## Goal

A new person opens BLDR and within a minute or two knows what it does and how to make their first workout, without help.

Two features:

1. **Welcome tour** — a full-screen, five-card carousel shown on first open. Skip on every card, page dots, Next → **GET STARTED** on the last card. Shown once (existing installs see it once after updating); replayable from Settings.
2. **How to use BLDR** — five topics in Settings, each a page with a looping animation of the job and numbered steps that light up in sync with it.

## Decisions

- **Visuals are drawn mini-screens**, not recordings or screenshots: simplified versions of the real screens made from the app's own colours and type, with a fingertip dot tapping through. Always current in light/dark, sharp, tiny in APK size.
- **One shared kit + one script per animation.** A script is a list of timed beats; each beat belongs to a step. A single player renders a script in a framed mini-phone. Both the tour and the topics use it.
- **Synced steps.** The step the animation is showing is lit (bold, filled number); tapping a step jumps the animation to that step's first beat.
- **Reduced motion** (system animator scale 0): no frames scheduled; each step shows its still frame, and tapping a step changes the frame. Same rule as the brand loaders.
- **Battery:** a player only ticks while composed and visible (tour pages off-screen and left topic pages stop).
- **Content in one file** (wording + scripts), so wording edits are one-line changes. 
- **Tour fills the first-launch wait.** On a fresh install seeding takes ~10 s; the tour shows immediately instead of the loader. If GET STARTED is tapped before seeding finishes, the existing `LaunchLoader` shows for the remainder.
- The "seen tour" flag lives in `AppPrefs` (SharedPreferences), like the other one-shot flags.

## Where it lives in Settings

In the About section, above Privacy: **How to use BLDR** (topic list) and **Replay welcome tour** (moved from a separate Help section at the user's request; the font-licence credit line was removed at the same time).

## Welcome tour cards

| # | Title | Body | Animation |
|---|---|---|---|
| 1 | WELCOME TO BLDR | Build workouts that fit you, then train with a timer that coaches you through them. | The striped B builds (existing brand motion). |
| 2 | BUILD IT YOUR WAY | Pick from 100 exercises. Set reps or timers, rounds and rests. | Exercise rows drop into a workout card; a Reps tag flips to Timed; "× 3 rounds" appears. |
| 3 | OR ASK THE COACH | Describe your goal and get a workout drafted for you. Tweak it until it's right. | "30 min legs, dumbbells" types in, the B loader plays, a workout card appears. |
| 4 | TRAIN HANDS-FREE | A timer, voice and sound cues that keep going, even when your phone is locked. | Timer ring counts down, "Next up: Squats" pops up, the screen dims while the ring keeps going. |
| 5 | PRIVATE BY DESIGN | Your workouts stay on your phone. No accounts, no ads, no tracking. | A workout card slides into a phone outline and a padlock closes. Button: GET STARTED. |

## How-to topics

Each topic: title, one-line summary (for the list), steps, and an animation whose beats walk through the steps in order.

1. **Build a workout yourself** — (1) On Home, tap BUILD A WORKOUT, then Build it yourself. (2) Give your workout a name. (3) Tap + Add exercise and pick from the Library. (4) For each exercise, choose Timed, Reps or Manual, then set the time, or the sets and reps. (5) Set Rounds to repeat a section, and choose rests between exercises and rounds. Use + Add section for a warm-up or finisher. (6) Tap SAVE WORKOUT.
2. **Ask the Coach** — (1) Add your API key in Settings first. Tap How do I get a key? there for the steps. (2) Tap BUILD A WORKOUT, then Ask the Coach. (3) Say what you're training for, then pick a length and your equipment. (4) Tap BUILD MY WORKOUT and wait for the draft. (5) Not quite right? Type a change into Tweak, e.g. "swap lunges for step-ups", or tap Regenerate for a fresh take. (6) Tap SAVE to add it to your workouts.
3. **Run a workout** — (1) Tap a workout on Home, check the overview, then tap START. (2) A short Get ready countdown names the first exercise. (3) Timed exercises count down on the ring. For reps, tap Done › when you've finished. (4) Tap Pause any time. Skip › moves on, and ↺ Restart resets the clock. (5) Tap Watch demo to see how an exercise is done. The speaker icon mutes the cues. (6) Lock your phone if you like. The timer and voice cues keep going.
4. **Find exercises** — (1) Open the Library tab at the bottom. (2) Browse by muscle, or search by name. (3) Tap Filters to narrow by type, equipment, movement pattern or extra muscles worked. (4) Tap an exercise to see its tags, then tap WATCH DEMO. (5) Prefer a different demo? Paste a video link and tap SAVE LINK.
5. **Edit or delete a workout** — (1) On Home, tap ⋯ next to a workout. (2) Tap Edit to change it, then SAVE WORKOUT. You can also tap Edit on a workout's overview. (3) Or tap Delete and confirm. This can't be undone.

Removed after review (2026-10-04): a sixth topic, **Set up the Coach**, drew the Claude Console website, which is not part of the app; Settings' own "How do I get a key?" steps cover it.

## Pieces

1. `AppPrefs.welcomeSeen` flag.
2. Welcome tour screen.
3. How-to list screen and topic screen.
4. Mini-screen kit + animation player (script = timed beats tagged with a step).
5. Content file: tour cards, topics, steps, scripts.

## Testing

- JVM tests: every step has at least one beat; beats are in step order; step-start lookup and beat-at-time lookup are correct, including the loop wrap.
- Emulator: fresh install (tour during seeding, Skip, Get started), Replay from Settings, every topic, animations off, dark mode, 200% font, small screen.

## Build order

1. Kit + player + topic 1 end-to-end; checkpoint APK for the user to approve the look.
2. The other five topics.
3. Welcome tour + first-launch flow; final APK.
