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
- **Home** (`ui/screens/HomeScreen.kt`): a cinematic **spotlight** on top —
  full-screen show backdrop (`ui/components/Spotlight.kt`
  `SpotlightBackdrop`, drawn by LauncherRoot behind everything, Ken Burns
  drift + crossfade) or, for apps, a drifting banner card. It follows focus
  and auto-rotates every 8 s after 9 s idle (toggle in Layout & Grid). Below:
  **"Popular on <service>"** rows for installed streaming apps (TMDB, see
  below), Favourite Apps, Recently Opened. Without a TMDB key the spotlight
  rotates favourite/recent app banners and a tip card explains the key.
- **Content rows** (`data/ContentRepository.kt`): TMDB `discover` filtered
  by watch provider in region IN (provider IDs matched at runtime by name:
  netflix, amazonprimevideo, hotstar, jiocinema, sonyliv, zee5, appletv,
  sunnxt → known TV package names). Cached 6 h in SharedPreferences; Coil
  caches images. OK on a show sends `ACTION_SEARCH` with the title to that
  app (`AppRepository.openTitle`), falling back to just opening the app.
  Key = `TMDB_API_KEY` GitHub Actions secret if set, else `tmdbApiKey` in
  `gradle.properties` (committed) → `BuildConfig.TMDB_API_KEY` (v3 key →
  `api_key` param; long v4 token → Bearer header).
- **Living background** (`ui/components/LivingBackground.kt`): one Canvas
  over the wallpaper — drifting light pools tinted by `Ambient.color` (the
  spotlight image's average colour, also used for focus glows), rising
  motes, and live weather from Open-Meteo (rain, storm + lightning, clouds,
  fog, snow, sun glow by day, twinkling stars at night). Frame clock only
  ticks while the activity is STARTED. Toggle in Layout & Grid.
- **Home extras**: spotlight buttons Open / Trailer (TMDB videos → YouTube
  TV app, `AppRepository.openYouTube`) / My List; **mood chips** (`Mood`
  enum in ContentRepository → per-service discover with genre/language/
  runtime filters, session-cached); **My List** row; first content row is
  **"New on your apps"** (recent films + TV with episodes airing).
- **Search** (sidebar item, `ui/screens/SearchScreen.kt`): BasicTextField +
  voice (`RecognizerIntent`), installed-app matches plus TMDB `search/multi`
  with per-title watch providers in IN → "On your apps" / "Not on your apps".
- **My List** (`data/MyListRepository.kt`): hold OK on any show card (Home,
  search) or the spotlight button; JSON in SharedPreferences.
- **Kids mode** (`data/KidsRepository.kt`, `ui/screens/KidsScreen.kt`):
  Settings › Kids mode — allowed apps, 4-digit PIN (`PinPad`, shown by
  LauncherRoot over everything), daily limit. When active, LauncherRoot
  renders `KidsHome` instead of the normal layout. Time = from launching an
  app on the kids home until MainActivity.onResume (`endSession`); apps
  lock at the kids home when time's up (running apps aren't killed).
- **Navigation sounds** (`data/SoundFx.kt`): synthesised WAVs via SoundPool,
  played from `MainActivity.dispatchKeyEvent`; toggle in Layout & Grid.
- **Screensaver** (`dream/LauncherDream.kt`, plain Views): cached backdrops
  from `content_cache` with Ken Burns + crossfade, clock, date, last
  weather (`weather_cache` prefs written by rememberWeather).
- **Phone companion** (`data/CompanionServer.kt`): raw ServerSocket on port
  8765 in the launcher process, token-protected (`?t=`), serves one HTML
  page + JSON API (apps, icon, launch, search, open, mylist, save).
  Settings › Phone companion shows URL + QR (zxing). On/off + new token.
- **Home button override** (`home/HomeOverrideService.kt`): this TV has
  no "default home app" setting, so an AccessibilityService (window-state
  events only, no content) watches for any other HOME-handling package
  (queried + known TV launcher list) coming to the front and starts
  MainActivity with CATEGORY_HOME (→ homeTicks → Home screen). 700 ms
  debounce. Settings › Home button: one on/off toggle ("Home button opens
  this launcher"; off = pause pref, first time opens Accessibility
  settings), "Open the TV's own home screen" (one-time `bypass` pref,
  cleared when any non-launcher window comes to front), detected stock
  launcher.
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
- **Settings**: (scrollable; header shows "Build N"; first row = Check for
  updates, then Home button) Theme & Appearance, App Management, Layout & Grid, Remote &
  Navigation, Clock & Date Format, Kids mode, Phone companion, Diagnostics,
  About (shows build number). Layout & Grid also has Living background,
  Rotate Home spotlight, Navigation sounds and the Screensaver shortcut.
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

- `MainActivity.kt` — creates repositories (apps, settings, kids, content,
  My List, companion server, sounds), refreshes app list and ends kids
  sessions on resume, plays navigation sounds in `dispatchKeyEvent`.
- `ui/LauncherRoot.kt` — wallpaper + sidebar + top bar + screen switching,
  Back handling, app-options dialog wiring.
- `ui/screens/` — Home, Apps, Favourites, LiveTv, Settings, AppOptionsDialog.
- `ui/components/` — Controls (buttons/chips/rows), AppTiles, Chrome
  (sidebar/top bar/weather), Wallpaper, TvModifiers.
- `data/` — AppRepository, SettingsRepository, BulkUninstallController,
  WeatherRepository, ContentRepository (TMDB), MyListRepository,
  KidsRepository, CompanionServer, Updater, Diagnostics, SoundFx.
  `dream/` — LauncherDream. `model/` — LauncherApp, LauncherTheme, AppCategory.
- `ui/screens/` also has SearchScreen and KidsScreen (KidsHome, PinPad,
  KidsSettingsContent); `ui/components/` has Spotlight and LivingBackground.

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
- **2026-11-01**: "Alive" Home redesign — spotlight hero with full-screen
  show backdrops (focus-follow + idle rotation), TMDB "Popular on …" rows
  for installed streaming apps, living background with ambient colour and
  live weather, tiles lift and glow in the ambient colour. Added Coil
  (`io.coil-kt:coil-compose:2.6.0`), `buildConfig = true`, CI passes the
  `TMDB_API_KEY` secret. New settings: Living background, Rotate Home
  spotlight.
- **2026-11-02**: Rajat couldn't set a default launcher on the TV (no
  option). Added the Home button override accessibility service + Settings
  › Home button page, `<queries>` for HOME intents. Then (same day) a
  single on/off toggle and an "Open the TV's own home screen" button.
- **2026-11-02**: On this TV the generic `ACTION_ACCESSIBILITY_SETTINGS`
  shows "No applications can handle this action". `openAccessibilitySettings`
  now tries `com.android.tv.settings` accessibility activities by name,
  only starts intents that resolve (skipping resolver stubs), and falls
  back to a toast with the manual path (Device Preferences › Accessibility).
- **2026-11-01**: Settings home is now scrollable (9 rows overflowed the
  screen, so About was unreachable-looking) and starts with a "Check for
  updates" row plus "Build N" under the title.
- **2026-11-01**: Fixed D-pad on Home (Rajat: "pressing down also takes it
  right"). The focus-lift `graphicsLayer` (scale + translateY) sat *before*
  the focus modifiers on ContentCard/AppCard, so focus search used the
  lifted bounds and the same-row neighbour qualified as "below". Focus/click
  modifiers now come before `graphicsLayer`.
- **2026-11-01**: "Do all" feature batch (builds 17–19): universal search
  with voice and where-to-watch, My List, trailers, mood chips, "New on
  your apps" row (content cache layout bumped to `v2:` signature), kids
  mode with PIN and daily limit, navigation sounds, screensaver
  (DreamService), phone companion web page with QR (zxing
  `com.google.zxing:core:3.5.3`). Destination enum gained SEARCH (sidebar
  index = enum ordinal). New manifest entries: RECOGNIZE_SPEECH and
  YouTube TV `<queries>`, DreamService. None of this was tested on the TV
  yet (Rajat away) — expect follow-up fixes.
- **2026-11-01**: Rajat's TMDB v3 key committed in `gradle.properties`
  (`tmdbApiKey`) — Claude sessions can't set Actions secrets (proxy blocks
  the secrets API). Env/secret `TMDB_API_KEY` still overrides it.
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
- **Preview rows of other apps are not readable**: verified against AOSP —
  TvProvider's `ACCESS_ALL_EPG_DATA` is `signature|privileged`, so even ADB
  `pm grant` can't give it to a sideloaded launcher. That's why content
  comes from TMDB. Settings › Diagnostics shows what this TV actually allows.
- **TMDB key is public**: committed in `gradle.properties` of this public
  repo and inside the public APK. Fine for a
  free read-only TMDB key; never put anything sensitive in BuildConfig.
- `ACTION_SEARCH` deep-linking into streaming apps is best effort — not
  every app handles it; check on the TV if a service opens oddly.
- **Companion server lives in the launcher process**: if Android kills
  the launcher while another app is open, the phone page stops answering
  until Home is shown again. A foreground service would fix it if needed.
- **Kids time limits can't stop an app mid-play** without usage-access or
  device-owner powers; enforcement happens at the kids home.
- **Screensaver selection** depends on the TV's settings exposing a
  screen-saver picker; Google TV builds may only offer Ambient mode.
- **Never put a translating `graphicsLayer` before focus modifiers** on a
  focusable: Compose focus search uses the transformed bounds, so a
  lifted/shifted tile breaks Up/Down navigation. Order: focusRequester →
  onFocusChanged → tvClickable → graphicsLayer → shadow/clip.
- **Default home app can't be set on this TV** — rely on the accessibility
  override. On Android 13+ sideloaded apps may need "Allow restricted
  settings" in App info before the accessibility toggle is enabled.
- `SettingsRow`'s last parameter is `onClick` (trailing lambda); `trailing`
  (the right-hand widget) comes before it and is passed by name.
