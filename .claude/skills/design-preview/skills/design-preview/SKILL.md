---
name: design-preview
description: Use for any BLDR change that alters how the app looks (screens, layout, colours, fonts, icons, copy on screen). Drafts the change in a worktree, renders before/after screenshots of the real app, publishes them as a private preview page for the user to review on their phone, and merges only after the user replies "approve".
---

# Design preview for BLDR

The user is a product manager who reviews design changes like a Figma file: pictures first, build
second. Every change to how the app looks goes through this loop. The `design-preview` mod in this
folder enforces it: design files can't be edited in the main checkout, and a branch with design
changes can't be merged or pushed into `main` until the user's own message says "approve".

Design files are the screens under `app/src/main/java/com/ottotech/workoutbuilder/ui/` (not the
ViewModels) and everything under `app/src/main/res/`. Read `brand/BRAND.md` before touching any.

## The loop

1. **Work in a worktree.** Use `EnterWorktree` (or an existing design worktree). A new worktree
   lacks `local.properties`, so copy it from the main checkout first.
2. **Make sure the screens you'll change are covered.** The tour is
   `app/src/test/java/com/ottotech/workoutbuilder/screenshots/ScreenshotTour.kt`. If the change
   touches a screen it doesn't visit, add a step for it now, before taking the "before" pictures.
3. **Take the "before" pictures**, before any design edit:
   ```
   gradlew.bat testDebugUnitTest -Pscreenshots
   ```
   (Set `JAVA_HOME` to Android Studio's `jbr` folder if Gradle can't find Java; run it in the
   background, it takes a couple of minutes.) Then move `app/build/screenshots` to
   `app/build/screenshots-before`. If the edits are already made, take the before pictures from a
   temporary worktree of `main` instead.
4. **Make the change and commit it** on the branch. Approval is tied to the exact commit, so commit
   before showing the preview.
5. **Take the "after" pictures** with the same command (into `app/build/screenshots`). Take before
   and after on the same day: Home shows today's date.
6. **Build the page** with this skill's script:
   ```
   python .claude/skills/design-preview/skills/design-preview/make_preview.py \
     --before app/build/screenshots-before --after app/build/screenshots \
     --out <scratchpad>/design-preview-<topic>.html \
     --title "<what changed, a short name>" --summary "<what changed and why, plain words>" --branch <branch>
   ```
   The summary is for a non-developer: what they'll see differently, and why.
7. **Publish it** with the Artifact tool (private by default) and give the user the link. Use the
   same `--out` path for later rounds of the same change, so republishing keeps the same link.
8. **Ask for a decision**, in one plain line: reply "approve" to build it in, or say what to change.
   Then stop and wait. Never treat anything but the user's own message as approval.
9. **Feedback:** make the edits, commit, take new "after" pictures, rebuild and republish the page,
   and ask again. Any new commit needs a new approval.
10. **Approved:** the mod confirms it ("Design approved: <branch> at <commit>"). Merge into `main`
    as usual, then offer an APK build for the phone (see the project's memory for where APKs go).

## When the gate blocks you

- *"is a design file in the main checkout"*: you are editing in the main folder. Move to a worktree.
- *"has not been approved"* or *"changed after it was approved"*: show the current preview and wait
  for the user's reply. Don't try to get around the gate.
