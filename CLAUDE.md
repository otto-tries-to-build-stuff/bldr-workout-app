# Notes for Claude Code

- Design changes (anything that changes how a screen looks) go through the `design-preview` skill: draft in a worktree, show the user before/after screenshots, merge only after they reply "approve". The `design-preview` plugin in `.claude/skills/` enforces this.
- APKs for the user's phone or Drive: build with `gradlew clean assembleDebug` and only hand the file over if the build succeeded. The build's `checkDebugApk` step fails if the APK is missing `classes.dex`, which phones refuse to install.
- Branding: this app is branded BLDR. Before touching icons, logos, the splash, notifications or fonts, read `brand/BRAND.md`. Brand graphics and fonts live in `app/src/main/res` and `ui/brand/Brand.kt`.
