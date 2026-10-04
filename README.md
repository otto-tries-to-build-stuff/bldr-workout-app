# BLDR

**Build workouts that fit you, then train with a timer that coaches you through them.**

BLDR (said "builder") is an Android app for putting together your own workouts from a library of
about 100 exercises and then running them hands-free: a big countdown timer, sound cues and
spoken exercise names that keep going with your phone locked in a pocket. An optional AI Coach
drafts a workout from a goal you describe, using your own Anthropic API key.

Everything is stored on your phone. There are no accounts, no ads, no analytics and no backend.

## Why I built this

I'd been building HIIT workouts with Claude as small web pages, but a web page only keeps its
timer and sound going while the screen is on, so I had to keep my phone unlocked for the whole
workout (...which was annoying).

The original idea was just a mobile app version of my HIIT workout, so I could have audio cues
while my phone was locked. From there I thought it might be neat to be able to build workouts
from a pre-defined library. Then I thought it might be cool to incorporate some kind of AI Coach.

I wanted to create something that I could easily share with friends as well, which is where the
idea came from of letting people add their own Anthropic API key within the app itself to enable
the Coach, as opposed to:

- a) hardcoding my own API key (which would cost me money), or
- b) making it a variable within the code, which would make it harder for people to use the app
  without first cloning the repo (waaaay too technical for most of my friends).

## Features

- **Build it yourself.** Pick exercises from the library and set each one to timed, reps
  (sets × reps, optional weight) or manual. Group them into sections that repeat for a number
  of rounds, with rests between exercises, between rounds and after a section.
- **Exercise library.** About 100 exercises, browsable by muscle and filterable by type,
  equipment, movement pattern and secondary muscles. Each one links to a demo video (a YouTube
  search by default, or a link you pin).
- **Run a workout.** A "get ready" countdown, a timer ring, set checklists for reps, pause,
  skip, restart, and an "up next" card. Cue tones and the phone's built-in voice keep going with
  the screen off: the timer runs in an Android foreground service.
- **AI Coach (optional).** Describe what you're training for, pick a length and your equipment,
  and Claude drafts a workout from the library. Tweak it in plain words or regenerate it, then
  save it like any other workout.
- **Built-in help.** A welcome tour on first open, and How-to guides in Settings with animated
  walkthroughs drawn in code.
- **Light and dark themes**, and support for large text sizes and "remove animations".

## Privacy

Workouts, exercises, history and settings live only on the phone. Android's cloud backup is
turned off for the app, so uninstalling it deletes everything.

The only time anything leaves the phone is when you use the Coach. It sends what you type, the
length and equipment you pick, and the names of the exercises in your library, straight to
Anthropic's API using your key. Workout history is never sent. The key is stored encrypted on
the phone.

## Build it yourself

### What you need

- [Android Studio](https://developer.android.com/studio), a recent version. It brings the Java
  runtime and Android SDK tools the build needs, and will offer to install Android SDK
  platform 36 the first time you open the project.
- An Android phone on Android 8.0 or later, or the emulator that comes with Android Studio.

### Run it from Android Studio

1. Clone the repo:
   ```
   git clone https://github.com/otto-tries-to-build-stuff/bldr-workout-app.git
   ```
2. In Android Studio, choose **Open** and pick the cloned folder. Let Gradle sync finish.
3. Plug in your phone with USB debugging on (or start an emulator), then press **Run**.

### Or from the command line

```
./gradlew assembleDebug        # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # runs the unit tests
./gradlew installDebug         # installs on a connected phone or emulator
```

On Windows use `gradlew.bat`. If Gradle can't find Java, point `JAVA_HOME` at the runtime that
ships with Android Studio (the `jbr` folder inside its install directory).

You can also copy `app-debug.apk` to a phone and open it there to install it. Android will ask
you to allow installs from that source.

### Set up the Coach (optional)

The Coach calls Anthropic's API with a key you provide, so usage is billed to your own account
(roughly 5–15¢ per generated workout).

1. Sign in to the [Claude Console](https://platform.claude.com/) and add some credit under
   Billing. This is separate from a Claude.ai subscription.
2. Under [API keys](https://platform.claude.com/settings/keys), create a key.
3. In the app, open **Settings**, paste the key and tap **Save key**.

The model is set in `app/src/main/java/com/ottotech/workoutbuilder/ai/CoachClient.kt`.

## Make it your own

| To change | Look in |
|---|---|
| The app's ID (do this first if you publish your own version) | `applicationId` and `namespace` in `app/build.gradle.kts` |
| Name, logo, icon, fonts and brand rules | `brand/BRAND.md`, `app/src/main/res`, `ui/brand/Brand.kt` |
| Colours, type sizes and spacing | `ui/theme/` |
| The exercise library | `data/seed/ExerciseSeed.kt` |
| The sample workout a fresh install starts with | `data/seed/WorkoutSeed.kt` |
| The Coach's instructions | `ai/CoachPrompt.kt` |
| Welcome tour and How-to wording and animations | `ui/help/HelpContent.kt`, `ui/help/HelpScenes.kt` |

Code paths are under `app/src/main/java/com/ottotech/workoutbuilder/`.

## How it's put together

- **Kotlin** and **Jetpack Compose** for every screen, using a small set of the app's own
  components on top of Material 3.
- **Room** (SQLite) for workouts and the exercise library, seeded on first launch.
- **Navigation Compose** between screens, with plain ViewModels and no dependency-injection
  framework.
- A **foreground service** runs the workout clock and plays cues, so a workout survives the
  screen turning off.
- The **official Anthropic Java SDK** for the Coach, with structured outputs so drafts always
  come back as valid workouts.

| Folder | What's there |
|---|---|
| `domain/` | Plain Kotlin: the workout model, the compiler that turns a workout into a timed sequence, filters. No Android code. |
| `data/` | Room database, repositories, settings, the encrypted key store, seed data. |
| `execution/` | The foreground service that runs a workout. |
| `audio/` | Cue tones (generated as WAV at runtime) and the spoken cues. |
| `ai/` | The Coach: prompt, schema, client and response checks. |
| `ui/` | Screens, grouped by feature, plus `components/`, `theme/`, `brand/` and `help/`. |

The unit tests in `app/src/test` cover the domain logic, the seed data and the Coach's request
and response handling.

## Built with Claude Code

This app was designed and built with [Claude Code](https://claude.com/claude-code).
`CLAUDE.md` holds the standing notes Claude reads at the start of a session.

## Licence

The code is released under the [MIT License](LICENSE).

The bundled fonts, Archivo and Space Grotesk, are under the SIL Open Font License 1.1. Their
licence texts are in `brand/licenses/`.

The BLDR name and logo are included so the project builds as-is. If you publish your own
version, please give it your own name and icon.
