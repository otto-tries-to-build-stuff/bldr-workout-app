# Court Speed

A single-file interactive HIIT timer web app for a tennis + running speed session. Dark UI, big countdown ring, audio cues, YouTube demos for each exercise, and a tappable session plan. Runs entirely in the browser — no build step, no dependencies, no backend.

**Live:** https://otto-tries-to-build-stuff.github.io/courtspeed-hiit/

---

## What it does

Court Speed runs the "oval portion" of a tennis-focused HIIT workout — everything between the warm-up jog and the cool-down jog:

- **Dynamic prep** — leg swings, walking lunges, A-skips, build-up strides
- **Block A · Tennis Circuit** — 3 rounds of lateral shuffles, skater bounds, split squat jumps, and 180° squat jumps (30s on / 15s rest)
- **Block B · Sprints** — 6 manual sprints with walk-back recovery
- **Bonus flyer** — an optional all-out 40m finisher

## Features

- **Big countdown ring** with second-by-second tick marks and a glanceable timer
- **Audio cues** — 3-2-1 countdown beeps and a "go" tone on every transition
- **Lock-screen support** — Media Session integration shows the current exercise; a background audio loop helps keep cues alive when the screen is off
- **Manual sets** — sprints and the bonus wait for a tap instead of counting down (tap the ring or the → button when you're done)
- **Session plan screen** — see the whole workout, with a YouTube demo for each exercise. Tap any set to jump straight to it
- **Jump anywhere** — open the plan mid-workout from the set counter; it highlights where you are and lets you jump to any other set
- **Responsive** — works in portrait and landscape; on a phone held sideways the ring and controls sit side by side
- **Wake Lock** — asks the browser to keep the screen on during the session
- **Keyboard shortcuts** (desktop) — `space` pause/resume, `←` / `→` previous/next set, `R` reset current timer

## Running it

It's one HTML file. Any of these work:

- **GitHub Pages** (recommended) — see below
- Open `index.html` directly in any modern browser
- Serve the folder with any static host

### Deploying to GitHub Pages

1. Put `index.html` in the root of a public repo
2. **Settings → Pages → Build and deployment**: Source = *Deploy from a branch*, Branch = `main`, Folder = `/ (root)`, Save
3. Wait ~60 seconds; your URL appears at the top of the Pages settings

### Best experience on mobile

Open the live URL on your phone and **Add to Home Screen**. It then runs fullscreen with no browser chrome, and gets better background-audio behaviour. If screen-locking interrupts the audio cues, the simplest fix is to bump your phone's auto-lock timeout in Settings for the duration of the session.

## Make your own workout app

Court Speed was built with a reusable **Claude skill** that turns any timed workout into an app in this exact format — describe a workout to Claude ("a 20-minute hill sprint session", "a morning mobility flow") and it designs the session, finds a YouTube demo for each move, and generates a ready-to-deploy app.

The skill is open and free to use:

**→ [github.com/otto-tries-to-build-stuff/workout-app-builder](https://github.com/otto-tries-to-build-stuff/workout-app-builder)**

That repo has install instructions for both claude.ai and Claude Code. (You'll need to be using [Claude](https://claude.ai) — the skill teaches Claude how to build the app; it isn't a standalone program.)

## Tech

Plain HTML, CSS, and vanilla JavaScript in a single file. Fonts (Bebas Neue + JetBrains Mono) load from Google Fonts; everything else is self-contained — the beep sounds are generated as WAV data at runtime, so there are no audio files to host.

## License

Personal project — free to use, copy, and adapt.

