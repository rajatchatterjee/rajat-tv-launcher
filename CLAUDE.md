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

Built to match the "Rajat TV Launcher" UI/UX mockup sheet (8 screens).

- **App name**: "Rajat's TV Launcher"
- **Layout**: left sidebar (crown logo, Home / Apps / Favourites / Live TV /
  Settings), top-right bar (date · time, weather, Wi-Fi/Ethernet/offline
  icon, settings gear), full-screen wallpaper behind everything.
- **Home**: "Good Evening, Rajat" greeting, Favourite Apps row, Recently
  Opened row (stands in for the mockup's "Continue Watching" — see gotchas).
- **Apps**: grid (4–8 columns) with category chips All / Video / Music /
  Games / Tools / Other (`model/AppCategory.kt`: system category + known
  package/keyword lists) and a "+ Add App" tile that opens Play Store.
  "Select" mode = bulk delete: tick apps, Delete (n), confirm each in the
  system dialog. System apps are dimmed/unselectable.
- **App options menu**: hold OK (or press Menu) on any app → icon, version,
  APK size, install date; Open, Add/Remove Favourites, Move, Add/Remove Live
  TV, Uninstall (user apps only), App Info.
- **Favourites**: user-ordered; "Edit Order": OK picks up, arrows move, OK
  drops. Seeded on first run with YouTube/Netflix/Prime/Hotstar/Spotify/
  JioCinema if installed.
- **Live TV**: auto-detected channel/news/sports apps (JioTV, Xstream, Tata
  Play, ZEE5, SonyLIV, news apps…) + manual add/remove; chips Recent / News /
  Sports / Entertainment.
- **Settings**: Theme & Appearance, App Management, Layout & Grid, Remote &
  Navigation, Clock & Date Format, Diagnostics, About (shows build number).
- **Self-update** (`data/Updater.kt`): checks this repo's latest GitHub
  Release on resume (every 3 h) and from Settings › About; when a higher
  `build-N` exists an "Update · build N" pill appears in the top bar. It
  downloads the APK into a PackageInstaller session and Android shows one
  Install confirmation. Needs "Install unknown apps" allowed for the
  launcher once (it opens that screen and resumes after). Downloader is now
  only needed for a fresh install.
- **Diagnostics** (`data/Diagnostics.kt`): on-TV checks for developer
  options / USB debugging / wireless debugging flags, adb TCP port props, a
  localhost:5555 probe, the TV's IP, shortcuts into hidden developer
  settings, and whether the TV provider exposes other apps' channels /
  preview programs / Watch Next to this launcher.
- **Themes**: 5 scenic wallpapers drawn in code (`ui/components/Wallpaper.kt`,
  no image files) — Cinematic Sunset (default), Aurora, Minimal Dark, Ocean
  Blue, Forest — plus the 8 older colour themes. Accent colour (6 choices),
  background style Blur / Dim / Solid (real blur only on Android 12+; older
  TVs get a heavier scrim), show-clock toggle.
- **Remote handling**: `ui/components/TvModifiers.kt` `tvClickable` — OK
  click, OK-hold long-press, Menu key. Back never exits the launcher; the
  remote's Home button returns to the Home screen.
- **Weather**: Open-Meteo (no API key), coarse location with Pune fallback,
  refreshed every 30 min.
- **Storage**: SharedPreferences only (`data/SettingsRepository.kt`,
  usage/recents in `data/AppRepository.kt`). Not DataStore — see History.

## Code map

- `MainActivity.kt` — creates repositories, refreshes app list on resume.
- `ui/LauncherRoot.kt` — wallpaper + sidebar + top bar + screen switching,
  Back handling, app-options dialog wiring.
- `ui/screens/` — Home, Apps, Favourites, LiveTv, Settings, AppOptionsDialog.
- `ui/components/` — Controls (buttons/chips/rows), AppTiles, Chrome
  (sidebar/top bar/weather), Wallpaper, TvModifiers.
- `data/` — AppRepository, SettingsRepository, BulkUninstallController,
  WeatherRepository. `model/` — LauncherApp, LauncherTheme, AppCategory.

## Build & deploy pipeline

- CI: `.github/workflows/build.yml` — builds a debug APK on every push to
  `main`, and publishes it as a **GitHub Release** (not just an Actions
  artifact — artifacts need a GitHub login and resolve to Azure blob storage
  URLs that aren't reachable from every network; a Release asset is a plain
  public `github.com` link).
- **Updates after the first install**: the launcher updates itself from
  the latest Release (see Self-update above) — no Downloader needed.
- **Getting the APK onto the TV (fresh install)**: this TV has no working wireless/network
  ADB debugging option, and its USB port doesn't support device mode to a
  computer (USB config always reverts to "Charging"). The working method is:
  install the **Downloader** app (by AFTVnews) on the TV from the Play
  Store, then paste in the direct Release download URL from this repo's
  Releases page.
- Repo: `github.com/rajatchatterjee/rajat-tv-launcher`
- **Signing**: debug builds are signed with the committed
  `keystore/debug.keystore` (password/alias: `android` / `androiddebugkey`),
  so each new Release installs *over* the previous one and keeps settings.
- **Versioning**: `versionCode` = CI run number, `versionName` = `1.<run>`;
  matches the Release tag `build-<run>` and is shown in Settings › About.
- **No local Android build in Claude sessions**: the sandbox can't reach
  Google Maven / Gradle distribution servers, so GitHub Actions is the
  compiler. Push, then watch the run via
  `gh api repos/rajatchatterjee/rajat-tv-launcher/actions/runs`.

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
- **2026-10-30**: Built out the full mockup sheet (8 screens). New:
  labelled sidebar with Favourites + Live TV, top bar with network icon,
  scenic code-drawn wallpapers (Cinematic Sunset default) with Blur/Dim/Solid
  and accent picker, Apps category chips + "+ Add App", Select & bulk delete
  as its own mode, long-press app options menu, Favourites with D-pad
  reordering, Live TV section, Settings split into sub-pages (Layout & Grid,
  Remote & Navigation, Clock & Date Format, About). App icons/banners are
  now converted to bitmaps once on a background thread; the app list
  refreshes in place instead of re-running setContent on every resume.
  Replaced ThemeRepository with SettingsRepository (old pref keys kept) and
  removed SettingsActivity (settings now live inside MainActivity).
  Build: committed a fixed debug signing key and run-number versionCode —
  previously every CI build had a different random debug key, so new builds
  could not install over old ones. **One-time**: uninstall the old build
  before installing the first build signed with the new key.

- **2026-11-01**: Added Settings › Diagnostics to verify ADB status and
  preview-row access on the TV itself (Rajat wanted to double-check the "no
  ADB" conclusion). Manifest gained `READ_EPG_DATA` (normal permission) and
  `<queries>` entries for the `android.media.tv` provider and the settings
  packages. Background: AOSP's TvProvider declares `ACCESS_ALL_EPG_DATA` as
  `signature|privileged`, so even working ADB couldn't `pm grant` it — the
  diagnostics confirm what *this* OEM build actually does.
- **2026-11-01**: Added in-app self-update from GitHub Releases
  (`REQUEST_INSTALL_PACKAGES` permission, `UpdateStatusReceiver` for
  PackageInstaller status, top-bar update pill, About › Check for updates).
  Silent updates are impossible for a non-system app; one Install press is
  the minimum. The first build containing the updater still has to be
  sideloaded via Downloader.

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
- **"Continue Watching" isn't possible for a third-party launcher**: reading
  other apps' Watch Next programs (TvContract.WatchNextPrograms) needs a
  system/privileged permission. The Home row shows *Recently Opened* apps
  (tracked by this launcher's own launches) instead.
- Lazy rows/grids may not have composed their items when a LaunchedEffect
  runs — use `requestFocusWhenReady()` (retries) rather than a bare
  `requestFocus()`, which throws if the node isn't attached.
- `SettingsRow`'s last parameter is `onClick` (trailing lambda); `trailing`
  (the right-hand widget) comes before it and is passed by name.
