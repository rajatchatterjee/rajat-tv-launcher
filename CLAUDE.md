# Rajat's TV Launcher — project log

This file is read automatically at the start of every Claude Code session in
this repo. It exists so work can resume across sessions without re-explaining
context. **Keep it updated whenever a commit changes what the app does or how
it's built** — add a dated entry under "History" and update "Current state"
if the summary changes.

## What this is

A custom Android TV home-screen launcher built for Rajat's OnePlus TV.
Not a Play Store app — sideloaded, personal use only.

## Current state

- **App name**: "Rajat's TV Launcher"
- **Screens**: Home (greeting, clock, date, weather chip, quick-launch row,
  left nav rail) and a separate full-screen App Drawer (full app grid +
  bulk-uninstall selection mode).
- **Themes**: 8 presets — Midnight, Sunset, Forest, Minimal Light, Crimson
  (default), Obsidian, Neon, Pure White. Customizable further in Settings
  (accent/background color, clock toggle, grid column count). Background is
  a gradient that shifts slightly with time of day, blended with the active
  theme.
- **Bulk uninstall**: select multiple apps in the Drawer, confirm each via
  the system's uninstall dialog in sequence (Android has no silent bulk
  uninstall API).
- **Weather widget**: Open-Meteo (free, no API key), using device location
  (`ACCESS_COARSE_LOCATION`, requested at runtime) with Pune, Maharashtra as
  a fallback if permission is denied or no fix is available.
- **Storage**: SharedPreferences (not DataStore — see History, this mattered).

## Build & deploy pipeline

- CI: `.github/workflows/build.yml` — builds a debug APK on every push to
  `main`, and publishes it as a **GitHub Release** (not just an Actions
  artifact — artifacts need a GitHub login and resolve to Azure blob storage
  URLs that aren't reachable from every network; a Release asset is a plain
  public `github.com` link).
- **Getting the APK onto the TV**: this TV has no working wireless/network
  ADB debugging option, and its USB port doesn't support device mode to a
  computer (USB config always reverts to "Charging"). The working method is:
  install the **Downloader** app (by AFTVnews) on the TV from the Play
  Store, then paste in the direct Release download URL from this repo's
  Releases page.
- Repo: `github.com/rajatchatterjee/rajat-tv-launcher`

## History

- **2026-10-30**: Initial project scaffold — Compose-based Android TV
  launcher, theme system (DataStore-backed at first), app grid, settings
  screen. Pushed to GitHub, CI set up.
- **2026-10-30**: Fixed CI (`android-actions/setup-android` tried to install
  a removed legacy `tools` package — switched to accepting SDK licenses
  directly, since `ubuntu-latest` runners already ship the SDK). Fixed a
  wrong `toBitmap()` import (core-ktx, not Compose).
- **2026-10-30**: Sideload troubleshooting — TV has no wireless/network ADB
  option and USB device mode doesn't work on this unit. Switched to
  Downloader-app + GitHub Release install method. Along the way: replaced
  DataStore with plain SharedPreferences (DataStore's native `.so` lock file
  isn't guaranteed to match every TV's CPU architecture — this or the
  `QUERY_ALL_PACKAGES` permission was suspected of causing a silent "App not
  installed" failure); replaced `QUERY_ALL_PACKAGES` with narrower
  `<queries>` intent declarations (some Android TV builds reject sideloaded
  APKs requesting the broad permission); added `REQUEST_DELETE_PACKAGES`
  permission (required for the uninstall-confirmation intent to do anything
  at all — this is what was actually breaking bulk uninstall).
- **2026-10-30**: Personalization pass — renamed app to "Rajat's TV
  Launcher", added Crimson (red/black) theme as default, added a
  personalized greeting + date next to the clock.
- **2026-10-30**: Visual redesign — split the single app-grid screen into a
  clean Home + separate App Drawer; added Obsidian, Neon, and Pure White
  themes; redesigned app tiles (icon badge, elevation, real D-pad focus
  handling — previously declared but never wired up); made the Settings
  preset row horizontally scrollable.
- **2026-10-30**: Second visual pass (in progress) — added a persistent
  left nav rail (Home / All apps / Settings), a weather widget (Open-Meteo +
  device location, Pune fallback), a soft time-of-day background gradient,
  a glow effect behind app icons, and staggered entrance animations for
  tiles.

## Known gotchas for future work

- This TV's Developer Options only exposes USB debugging, and that doesn't
  actually work (USB config always snaps back to "Charging" — likely a
  hardware/firmware limitation on this specific TV, not a cable issue,
  though an untested known-good data cable could still be worth trying).
  **Don't assume ADB will work when debugging future issues** — plan on the
  Downloader-app install path.
- `material-icons-extended` is a dependency specifically to avoid guessing
  wrong about which icons are in the smaller `material-icons-core` set
  (already got bitten once by `Icons.Filled.Apps` not being in core).
- Icon name collisions: anything named `List` clashes with Kotlin's
  `List<T>` — avoid importing `Icons.Filled.List` unaliased in a file that
  also uses generic `List<T>` types (this file does, heavily).
