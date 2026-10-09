"""Builds the before/after design preview page for a BLDR design change.

    python make_preview.py --before <dir> --after <dir> --out preview.html \
        --title "Bigger timer ring" --summary "What changed and why, in plain words." [--branch name]

<dir> is an `app/build/screenshots` folder from `gradlew testDebugUnitTest -Pscreenshots`:
one subfolder per theme (light, dark) holding one PNG per screen. Only screens whose pictures
differ in either theme go on the page; the rest are listed as unchanged.
"""
import argparse
import base64
import html
import json
from pathlib import Path

THEMES = ("light", "dark")

# Screens drawn mid-countdown: the timer ring lands a fraction of a second apart from run to
# run, so up to this share of pixels may differ before the screen counts as changed.
LIVE_TOLERANCE = {"workout-timer": 0.02}


def png(path: Path):
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode() if path.exists() else None


def same_picture(before: Path, after: Path, tolerance: float) -> bool:
    if not (before.exists() and after.exists()):
        return False
    if before.read_bytes() == after.read_bytes():
        return True
    if tolerance == 0:
        return False
    from PIL import Image, ImageChops  # only needed for live screens
    a, b = Image.open(before).convert("RGB"), Image.open(after).convert("RGB")
    if a.size != b.size:
        return False
    differing = sum(1 for p in ImageChops.difference(a, b).convert("L").get_flattened_data() if p)
    return differing / (a.width * a.height) <= tolerance


def compare(before: Path, after: Path):
    names = sorted({p.stem for d in (before, after) for t in THEMES for p in (d / t).glob("*.png")})
    changed, unchanged = [], []
    for name in names:
        pairs = {t: ((before / t / f"{name}.png"), (after / t / f"{name}.png")) for t in THEMES}
        same = all(same_picture(b, a, LIVE_TOLERANCE.get(name, 0)) for b, a in pairs.values())
        if same:
            unchanged.append(name)
            continue
        status = "new" if not any(b.exists() for b, _ in pairs.values()) else \
                 "removed" if not any(a.exists() for _, a in pairs.values()) else "changed"
        changed.append({
            "name": name,
            "status": status,
            "shots": {t: {"before": png(b), "after": png(a)} for t, (b, a) in pairs.items()},
        })
    return changed, unchanged


PAGE = r"""<title>__TITLE__</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Archivo:wdth,wght@125,900&family=Space+Grotesk:wght@400;500;700&display=swap">
<style>
  /* Layout: one column of screens; each screen shows Now and Proposed as two phones side by side. */
  :root {
    --paper: #ffffff; --ink: #0b0b0c; --muted: #6b6b70; --line: #dcdcdf; --well: #f2f2f3;
    --display: "Archivo", "Arial Black", system-ui, sans-serif;
    --body: "Space Grotesk", system-ui, -apple-system, "Segoe UI", sans-serif;
  }
  @media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
    --paper: #0b0b0c; --ink: #f4f4f5; --muted: #9a9aa0; --line: #2a2a2e; --well: #161618; color-scheme: dark } }
  :root[data-theme="dark"] {
    --paper: #0b0b0c; --ink: #f4f4f5; --muted: #9a9aa0; --line: #2a2a2e; --well: #161618; color-scheme: dark }

  body { background: var(--paper); color: var(--ink); font: 400 16px/1.5 var(--body); }
  .wrap { max-width: 860px; margin: 0 auto; padding: 28px 16px 64px; display: grid; gap: 28px; }
  .eyebrow { font: 700 12px/1 var(--body); letter-spacing: .12em; text-transform: uppercase; color: var(--muted); margin: 0; }
  h1 { font: 900 clamp(26px, 6vw, 40px)/1.05 var(--display); font-stretch: 125%; text-transform: uppercase; margin: 8px 0 0; text-wrap: balance; }
  .summary { margin: 12px 0 0; max-width: 62ch; }
  .meta { margin: 8px 0 0; color: var(--muted); font-size: 14px; }

  .controls { display: flex; flex-wrap: wrap; gap: 10px; position: sticky; top: env(safe-area-inset-top, 0px);
    background: var(--paper); padding-block: 10px; z-index: 1; border-bottom: 1px solid var(--line); }
  .seg { display: inline-flex; border: 1.5px solid var(--ink); border-radius: 999px; overflow: hidden; }
  .seg button { font: 700 13px/1 var(--body); letter-spacing: .06em; text-transform: uppercase; color: var(--ink);
    background: transparent; border: 0; padding: 10px 14px; min-height: 40px; cursor: pointer; }
  .seg button[aria-pressed="true"] { background: var(--ink); color: var(--paper); }
  .seg button:focus-visible, .phone:focus-visible { outline: 2px solid var(--ink); outline-offset: 2px; }

  section.screen { display: grid; gap: 12px; }
  .screen h2 { font: 700 20px/1.2 var(--body); margin: 0; display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
  .tag { font: 700 11px/1 var(--body); letter-spacing: .1em; text-transform: uppercase; border: 1.5px solid var(--ink);
    border-radius: 999px; padding: 5px 8px; }
  .pair { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
  .pair.flip { grid-template-columns: minmax(0, 420px); justify-content: center; }
  figure { margin: 0; display: grid; gap: 8px; min-width: 0; }
  figcaption { font: 700 12px/1 var(--body); letter-spacing: .12em; text-transform: uppercase; color: var(--muted); }
  figure.after figcaption { color: var(--ink); }
  .phone { display: block; width: 100%; padding: 0; border: 1.5px solid var(--line); border-radius: 22px; overflow: hidden;
    background: var(--well); cursor: zoom-in; }
  figure.after .phone { border-color: var(--ink); }
  .phone img { display: block; width: 100%; height: auto; }
  .missing { aspect-ratio: 412 / 915; display: grid; place-items: center; color: var(--muted); font-size: 14px; padding: 16px; text-align: center; }
  .hint { color: var(--muted); font-size: 14px; margin: 0; }

  .unchanged { color: var(--muted); font-size: 14px; margin: 0; }
  .approve { border: 1.5px solid var(--ink); border-radius: 18px; padding: 20px; display: grid; gap: 8px; }
  .approve h2 { font: 900 20px/1.1 var(--display); font-stretch: 125%; text-transform: uppercase; margin: 0; }
  .approve p { margin: 0; max-width: 62ch; }
  code { font: 500 15px/1 var(--body); background: var(--well); border-radius: 6px; padding: 2px 6px; }

  .zoom { position: fixed; inset: 0; background: var(--paper); z-index: 2; overflow: auto; padding: 16px;
    padding-top: calc(16px + env(safe-area-inset-top, 0px)); display: grid; justify-items: center; gap: 12px; }
  .zoom img { width: min(100%, 520px); border-radius: 22px; border: 1.5px solid var(--line); }
  .zoom button { font: 700 13px/1 var(--body); letter-spacing: .06em; text-transform: uppercase; color: var(--paper);
    background: var(--ink); border: 0; border-radius: 999px; padding: 12px 18px; min-height: 44px; cursor: pointer; }
</style>

<main class="wrap">
  <header>
    <p class="eyebrow">BLDR design preview</p>
    <h1>__TITLE__</h1>
    <p class="summary">__SUMMARY__</p>
    <p class="meta">__META__</p>
  </header>

  <div class="controls" role="toolbar" aria-label="Preview options">
    <div class="seg" aria-label="Theme">
      <button type="button" id="t-light" data-theme-pick="light">Light</button>
      <button type="button" id="t-dark" data-theme-pick="dark">Dark</button>
    </div>
    <div class="seg" aria-label="Layout">
      <button type="button" id="v-pair" data-view="pair">Side by side</button>
      <button type="button" id="v-flip" data-view="flip">Flip</button>
    </div>
  </div>

  <p class="hint" id="hint"></p>
  <div id="screens" style="display:grid;gap:36px"></div>
  <p class="unchanged" id="unchanged"></p>

  <section class="approve">
    <h2>Happy with it?</h2>
    <p>Reply <code>approve</code> in the chat with Claude and the change goes into the app. Anything else, like
      "make it bigger", sends it back for another round with a fresh preview.</p>
  </section>
</main>

<div class="zoom" id="zoom" hidden>
  <img id="zoom-img" alt="">
  <button type="button" id="zoom-close">Close</button>
</div>

<script>
const DATA = __DATA__;
const LABEL = { new: "New screen", removed: "Removed", changed: "Changed" };
const pretty = s => s.replace(/-/g, " ").replace(/^./, c => c.toUpperCase());
let theme = matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
let view = innerWidth < 560 ? "flip" : "pair";
const shown = {}; // per screen in flip view: "after" or "before"

function figure(kind, src, name) {
  const label = kind === "after" ? "Proposed" : "Now";
  if (!src) return `<figure class="${kind}"><figcaption>${label}</figcaption><div class="phone missing">Not in this version</div></figure>`;
  return `<figure class="${kind}"><figcaption>${label}</figcaption>
    <button type="button" class="phone" data-zoom="${name}|${kind}" aria-label="${label}: ${pretty(name)}, open larger"><img src="${src}" alt="${label}: ${pretty(name)} screen, ${theme} theme"></button></figure>`;
}

function render() {
  document.querySelectorAll("[data-theme-pick]").forEach(b => b.setAttribute("aria-pressed", b.dataset.themePick === theme));
  document.querySelectorAll("[data-view]").forEach(b => b.setAttribute("aria-pressed", b.dataset.view === view));
  document.getElementById("hint").textContent = view === "flip"
    ? "Tap a screen to flip between now and proposed." : "Tap a screen to see it larger.";
  document.getElementById("screens").innerHTML = DATA.changed.map(s => {
    const shot = s.shots[theme];
    const side = shown[s.name] || "after";
    const body = view === "pair"
      ? figure("before", shot.before, s.name) + figure("after", shot.after, s.name)
      : figure(side, shot[side], s.name);
    return `<section class="screen"><h2>${pretty(s.name)} <span class="tag">${LABEL[s.status]}</span></h2>
      <div class="pair ${view}">${body}</div></section>`;
  }).join("") || `<p>No screens look different. The change may not be visible on the screens the preview covers.</p>`;
  document.getElementById("unchanged").textContent = DATA.unchanged.length
    ? "Unchanged: " + DATA.unchanged.map(pretty).join(", ") + "." : "";
}

document.addEventListener("click", e => {
  const pick = e.target.closest("[data-theme-pick]"), v = e.target.closest("[data-view]"), z = e.target.closest("[data-zoom]");
  if (pick) { theme = pick.dataset.themePick; render(); }
  else if (v) { view = v.dataset.view; render(); }
  else if (z) {
    const [name, kind] = z.dataset.zoom.split("|");
    if (view === "flip") { shown[name] = kind === "after" ? "before" : "after"; render(); return; }
    const img = document.getElementById("zoom-img");
    img.src = DATA.changed.find(s => s.name === name).shots[theme][kind];
    img.alt = z.getAttribute("aria-label");
    document.getElementById("zoom").hidden = false;
  }
});
document.getElementById("zoom-close").addEventListener("click", () => { document.getElementById("zoom").hidden = true; });
document.addEventListener("keydown", e => { if (e.key === "Escape") document.getElementById("zoom").hidden = true; });
render();
</script>
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--before", type=Path, required=True)
    ap.add_argument("--after", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--title", required=True)
    ap.add_argument("--summary", required=True)
    ap.add_argument("--branch", default="")
    args = ap.parse_args()

    changed, unchanged = compare(args.before, args.after)
    meta = f"{len(changed)} screen{'s' if len(changed) != 1 else ''} changed" + (f" · draft {args.branch}" if args.branch else "")
    page = (PAGE.replace("__TITLE__", html.escape(args.title))
                .replace("__SUMMARY__", html.escape(args.summary))
                .replace("__META__", html.escape(meta))
                .replace("__DATA__", json.dumps({"changed": changed, "unchanged": unchanged}).replace("</", "<\\/")))
    args.out.write_text(page, encoding="utf-8")
    print(f"{args.out}: {len(changed)} changed ({', '.join(c['name'] for c in changed) or 'none'}), "
          f"{len(unchanged)} unchanged, {args.out.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
