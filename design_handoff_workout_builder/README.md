# Handoff: Workout Builder — Fitness App (Android)

## Overview
Reference designs for a **workout-builder** mobile app (Android). The app's purpose is to
**assemble workouts from a library of exercises and then execute them** — it is *not* an
ongoing progress tracker. Three core screens:

1. **Home** — the workouts the user has built, entry point to build a new one and to start one.
2. **Library** — browse exercises by muscle/type to add into a workout.
3. **Workout** — execution. Adapts to the workout type: a **rep-based checklist** for
   strength moves, a **timed countdown** for HIIT/circuits. Every exercise links to a demo video.

## About the Design Files
The files in this bundle are **design references created in HTML** — prototypes that show
the intended look and layout, not production code to copy directly. The task is to
**recreate these designs in the target codebase's environment** (Jetpack Compose, React
Native, Flutter, etc.) using its established patterns, components, and theming — or, if no
environment exists yet, pick the most appropriate framework and implement there.

`Workout Builder.dc.html` is a design-exploration canvas: it renders every direction and
colour option side by side inside mock Android phone frames. The phone bezel, status bar,
and gesture pill are presentation chrome only — **do not ship them**; the real app renders
fullscreen inside the OS.

## Fidelity
**High-fidelity.** Final colours, type sizes, spacing, radii and layout are all specified
below and should be recreated faithfully using the codebase's existing UI toolkit. Icons in
the mock are deliberate geometric placeholders (see Assets) — swap in the app's real icon set.

## Chosen Direction
The user selected the **bold-contrast** direction (option `1c`): white app background,
**black hero surfaces**, heavy display type, and a **single accent colour**. During colour
exploration (turn 2) the accent options were Ember orange, Cobalt blue, Coral, Violet, and a
pure-mono (no colour) take. **Confirm the final accent with the user before building.** Every
value below is written so that the accent is a single swappable token.

---

## Screens / Views

### 1. Home
- **Purpose:** See the workouts you've built; start one; build a new one.
- **Layout:** Single scrolling column, 16px horizontal padding. Top to bottom:
  overline date → large 2-line title → "Up next" hero card (black) → list of remaining
  workout cards → sticky-feeling "Build a workout" button above the bottom nav.
- **Components:**
  - **Overline date** — text `TUE · JUL 21`, 11px / weight 600 / letter-spacing .02em, colour `#9A9A9A`.
  - **Title** — `YOUR WORKOUTS`, two lines, 27–30px / weight 800 / letter-spacing −.035em / line-height 1.02, colour `#0B0B0D`.
  - **Up-next hero card** — background `#0B0B0D`, radius 18px, padding 15–17px.
    - Label `Up next` — 9px / 700 / uppercase / letter-spacing .08em, colour = **accent**.
    - Workout name `Push Day` — 19–20px / 800, colour `#FFFFFF`.
    - Meta `6 exercises · 45 min · Strength` — 11–12px, colour `#9A9A9A`.
    - Primary button `Start workout ›` — full width, height 38–40px, radius 10–11px,
      background = **accent**, text = on-accent colour, 12–13px / 700.
  - **Workout list card** (repeat per built workout) — row, 1px border `rgba(0,0,0,.13)`,
    radius 14–15px, padding 12–13px, gap 12–13px.
    - Leading badge — 36–38px square, radius 10–11px, background `#F1F1EF`, centred
      duration number, 14–15px / 800, colour `#0B0B0D`.
    - Title — 14–15px / 700, colour `#0B0B0D`. Meta — 10–11px, colour `#9A9A9A`.
    - Example rows: `20-Min Burner` / `5 rounds · HIIT`; `Morning Mobility` / `9 moves · Mobility`.
  - **Build button** — full width, height 46–50px, radius 13–14px, background `#0B0B0D`,
    text `BUILD A WORKOUT` white 13–14px / 700, leading `+` glyph coloured = **accent**.
  - **Bottom nav** — two items (Home, Library), 1px top border `rgba(0,0,0,.08)`, per item a
    16px glyph + 10px label; active colour `#0B0B0D` weight 700, inactive `#B3B3B3`.

### 2. Library
- **Purpose:** Browse exercises by muscle/type to add to a workout.
- **Layout:** Column, 16–18px padding: title → search field → type filter chips (horizontal
  scroll) → "Browse by muscle" section label → 2-column category grid → bottom nav.
- **Components:**
  - **Title** — `LIBRARY`, 30px / 800 / letter-spacing −.03em, colour `#0B0B0D`.
  - **Search field** — height 44px, radius 13px, background `#F1F1EF`, leading 13px circle
    outline (search-icon placeholder), placeholder `Search exercises` 13px colour `#A0A0A0`.
  - **Filter chips** — `All` (active: background = **accent**, text = on-accent, 700) then
    `Strength`, `HIIT`, `Yoga` (inactive: background `#F1F1EF`, text `#5A5A5A` / 600). Chip
    padding 7×13px, radius 10px, 11px text. Row scrolls horizontally, gap 7px.
  - **Section label** — `Browse by muscle`, 11px / 800 / uppercase / letter-spacing .06em, colour `#0B0B0D`.
  - **Category grid** — 2 columns, gap 11px. Each tile radius 15px, padding 15×14px, min-height 56px,
    name at top (15px / 700) and count bottom-left (11px / 600). **First/featured tile is black**
    (`#0B0B0D`, white name, count coloured = **accent**); the rest are `#F1F1EF` with `#0B0B0D`
    name and `#9A9A9A` count. Categories & counts: Chest 12, Back 14, Legs 18, Shoulders 10,
    Core 11, Cardio 8 (also referenced: Arms 9, Mobility 15, Full body).
  - **Bottom nav** — same as Home, Library active.

### 3. Workout — Timed (HIIT / circuit)
- **Purpose:** Guided execution of a timed interval, one move at a time.
- **Layout:** **Fullscreen black** (`#0B0B0D`), column, 18px padding, centred content:
  header row → move name → muscle tag → countdown ring → watch-demo card → up-next card →
  Pause / Skip controls.
- **Components:**
  - **Header row** — left `20-MIN BURNER · 2/5` 11px / 600 colour `#9A9A9A`; right close `×` 19px white.
  - **Move name** — `Mountain Climbers`, up to 2 lines, 24–26px / 800 / letter-spacing −.02em, white, centred.
  - **Muscle tag** — `FULL BODY`, 10px / 600 / uppercase / letter-spacing .04em, colour = **accent**.
  - **Countdown ring** — ~158–176px square SVG. Track: circle stroke `rgba(255,255,255,.12)`,
    width 10. Progress: same circle, stroke = **accent**, width 10, `stroke-linecap:round`,
    rotated −90°, `stroke-dashoffset` drives remaining fraction. Centre overlay: time `0:24`
    44–50px / 800 / letter-spacing −.03em / tabular-nums white, sublabel `REMAINING` 9–10px /
    700 / uppercase colour `#9A9A9A`.
  - **Watch-demo card** — background `#18181C`, radius 13px, padding ~12px, gap 10px.
    Leading 26–28px square radius 8px background = **accent** containing a play triangle
    (on-accent colour). Title `Watch demo` 12–13px / 700 white; sub `How to: <move>` 10–11px `#9A9A9A`.
    Tapping opens the exercise's demo video.
  - **Up-next card** — 1px border `rgba(255,255,255,.14)`, radius 13px. Label `UP NEXT`
    9–10px / 700 uppercase `#9A9A9A`; value `Rest 15s → Jump Squats` 13–14px / 700 white.
  - **Controls** — row, gap 9–10px. **Pause**: flex-1, height 46–50px, radius 13–14px,
    1px border `rgba(255,255,255,.2)`, two 3×12px white bars + label `Pause` 13–14px / 700 white.
    **Skip**: 52–56px wide, same height, background = **accent**, `›` glyph on-accent 19–20px / 700.

### 3b. Workout — Reps (strength checklist)  *(see option `1a` for the light treatment)*
- **Purpose:** Tick off sets for a rep-based exercise.
- **Layout:** Light screen. Header (`Push Day · 2 of 6` + close) → segmented progress bar →
  exercise name + muscle/equipment → watch-demo card → `SETS` label → checkable set rows →
  `Next exercise ›` button.
- **Components:**
  - **Progress bar** — N equal segments (one per exercise), gap 4px, height 3px, radius 2px;
    completed = solid ink, remaining = `rgba(0,0,0,.12)`.
  - **Exercise name** — 28px / 700 / letter-spacing −.02em `#0A0A0A`; sub `Chest · Barbell` 12px `#8A8A8A`.
  - **Set row** — border-bottom `rgba(0,0,0,.1)`, padding 12px vertical, gap 12px. Leading
    **checkbox** 20px radius 6px: checked = filled ink with white CSS check (a rotated
    `border-width:0 2px 2px 0` box), unchecked = 1.6px border. Label `Set 1` 14px / 600;
    completed rows show label struck-through and greyed `#C2C2C2`. Trailing `10 · 60 kg` 13px `#8A8A8A`.
  - **Next button** — full width, height 50px, radius 14px, background ink, white `Next exercise ›` 14px / 700.

---

## Interactions & Behavior
- **Home → Workout:** tapping a card's Start (or a list card) opens the execution screen for
  that workout. The execution mode is chosen by exercise type: timed → countdown, rep-based → checklist.
- **Home → Build:** "Build a workout" opens the builder flow (not yet designed — next step).
- **Library:** filter chips filter the grid; tapping a category opens its exercise list; each
  exercise has an add-to-workout affordance (builder context).
- **Timed execution:** ring animates down over the interval; on 0 it auto-advances to the next
  segment (`Up next`). Pause halts the timer; Skip jumps to the next segment. `×` exits with a confirm.
- **Rep execution:** tapping a set toggles it complete (strike-through + greyed); `Next exercise`
  advances and fills the next progress segment.
- **Watch demo:** opens the exercise's demo video (external/native player).
- **Animations:** ring countdown is a linear `stroke-dashoffset` tween tied to remaining time;
  keep transitions subtle (150–250ms ease) elsewhere. Bottom-nav active state is an instant swap.

## State Management
- `workouts[]` — built workouts: `{ id, name, type, exercises[], durationMin }`.
- `exerciseLibrary[]` grouped by `muscle`/`type`, each `{ id, name, muscle, equipment, demoVideoUrl, defaultSets? }`.
- Execution session: `{ workoutId, currentIndex, mode: 'timed'|'reps', secondsRemaining, isPaused, completedSets{} }`.
- `activeLibraryFilter` (chip) and `activeCategory`.
- Data is local/offline-first for a builder app; no server fetch implied by these screens.

## Design Tokens
**Colours**
- Ink / text: `#0A0A0A`, `#0B0B0D` (near-black surfaces)
- Neutral text: `#9A9A9A`, `#8A8A8A`; disabled/complete `#C2C2C2`, `#B3B3B3`
- Surfaces: `#FFFFFF`, light fill `#F1F1EF`; dark card `#18181C`
- Borders: light `rgba(0,0,0,.13)` / `.08`; on-dark `rgba(255,255,255,.14)` / `.2` / `.12`
- **Accent (single token — confirm choice):** Ember `#FF6A2B` · Cobalt `#3F74FF` · Coral `#FF4D5E` · Violet `#9A7BFF` · or **none / mono** (`#FFFFFF` on dark, `#0B0B0D` on light).
  On-accent text is `#FFFFFF` for all colour options (mono uses `#0B0B0D`).
- Other directions for reference: `1b` Paper uses warm bg `#F4F1EA`, card `#FFFDF8`, ink `#211E18`, calm green accent `#5C7350`.

**Typography** — `Helvetica Neue, Arial, sans-serif` throughout. Weights 600/700/800. Display
sizes 24–30px (letter-spacing −.02 to −.035em); body 12–16px; meta/label 9–11px (uppercase
labels use letter-spacing .04–.08em). Timer digits use `font-variant-numeric: tabular-nums`.

**Spacing** — screen padding 16–18px; card padding 12–17px; gaps 9–13px. **Radii** — chips 10px,
cards 13–15px, hero 18px, buttons 10–14px, phone bezel N/A (chrome). **Shadows** — cards are
flat (hairline borders); only the mock phone frame carries a drop shadow (not shipped).

## Assets
- **Icons are geometric placeholders** — bottom-nav Home (rounded-square outline), Library
  (three stacked bars), search (circle outline), play (CSS/border triangle), pause (two bars),
  checkbox check (CSS rotated-border tick). Replace with the app's real icon set (Material
  Symbols or equivalent) at matching sizes.
- **No photography or logos** are used. If the real Library uses exercise images, design a
  thumbnail treatment consistent with the tile/card system.
- **Demo videos** are referenced by URL per exercise — source not included; wire to your video source.
- Fonts are system (`Helvetica Neue`/`Arial`) — no web-font files to bundle.

## Files
- `Workout Builder.dc.html` — the full exploration canvas. Turn 1: directions `1a` Mono,
  `1b` Paper, `1c` Contrast (chosen). Turn 2: accent-colour explorations `2a`–`2d` plus `2e` mono,
  each showing Home + timed Workout. Open in a browser; pan/zoom is enabled.
