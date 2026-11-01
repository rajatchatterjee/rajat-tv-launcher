# Rajat's TV Launcher

A custom Android TV home screen launcher, built for OnePlus TV (Android TV OS).
A cinematic Home with a spotlight that follows what you highlight, real
show artwork and "Popular on Netflix / Prime / JioHotstar" rows (TMDB), a
living background with live weather, universal search with "where to
watch", My List, trailers, mood filters, kids mode, a screensaver, a phone
companion page, self-updates from GitHub — plus the sidebar, app grid,
favourites, Live TV and bulk uninstall.

## What's in here

```
app/src/main/java/com/mytvlauncher/app/
  MainActivity.kt                   – entry point
  ui/LauncherRoot.kt                – wallpaper, sidebar, top bar, screen switching
  ui/screens/                       – Home, Apps, Favourites, Live TV, Settings, app options menu
  ui/components/                    – buttons/chips/rows, app cards, sidebar/top bar, wallpapers, remote handling
  data/AppRepository.kt             – finds/launches apps, remembers recently opened
  data/ContentRepository.kt         – TMDB rows, moods, search + where-to-watch, trailers
  data/MyListRepository.kt          – My List watchlist
  data/KidsRepository.kt            – kids mode allowed apps, PIN, daily time
  data/CompanionServer.kt           – phone companion web server (port 8765)
  data/Updater.kt                   – self-update from GitHub Releases
  data/SoundFx.kt                   – navigation sounds
  dream/LauncherDream.kt            – screensaver
  data/SettingsRepository.kt        – all user settings (SharedPreferences)
  data/BulkUninstallController.kt   – walks the uninstall confirmation dialog across a selection
  model/                            – app, theme and category models
```

## 1. Build it

CI builds this automatically on every push (see `.github/workflows/build.yml`)
and publishes the APK as a GitHub Release — check the **Releases** page for
the latest `app-debug.apk`, no local setup needed.

To build locally instead:
1. Install **Android Studio** (free, from developer.android.com).
2. Open this folder as a project — "Open" not "Import".
3. Let Gradle sync (first time will download dependencies, a few minutes).
4. Build the APK: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
   The output lands in `app/build/outputs/apk/debug/app-debug.apk`.

## 2. Install it on your OnePlus TV

The most reliable method, if your TV's Developer Options don't expose
wireless/network ADB debugging (common on some Android TV builds) and the
USB port doesn't support device mode to a computer:

1. On the TV, install **Downloader** (by AFTVnews) from the Play Store.
   (Builds are signed with a fixed key, so each new build installs over the
   previous one and keeps your settings.)
2. Grab the direct download link for `app-debug.apk` from this repo's
   **Releases** page.
3. Open Downloader on the TV, paste in that URL, let it fetch and install.

If ADB does work for you:
```
adb connect <TV_IP_ADDRESS>:5555
adb install app-debug.apk
```

## 3. Set it as your home screen

1. On the TV: **Settings → Apps → See all apps → Rajat's TV Launcher → Set
   as default** (exact wording varies by TV software version), **or**
2. Press the **Home** button on the remote — Android TV will prompt you
   to choose a launcher if more than one is installed. Pick "Rajat's TV
   Launcher" and choose "Always".

To revert any time: same menu, select the original OnePlus launcher
instead — nothing about the TV itself is modified, so this is fully
reversible.

## 4. Using it

- **OK** opens an app. **Hold OK** (or press **Menu**) for app options:
  add/remove favourite, move, add to Live TV, uninstall, app info.
- **Favourites → Edit Order**: OK picks an app up, arrows move it, OK drops it.
- **Settings → Theme & Appearance**: wallpaper, accent colour, Blur/Dim/Solid
  background, clock on/off. Layout & Grid, Clock & Date Format and About
  (shows the installed build number) are there too.
- **Bulk uninstall**: Apps → **Select** (or Settings → App Management), tick
  apps, **Delete**. Android requires confirming each uninstall individually,
  so you'll see the system dialog once per app. System apps can't be selected.

### New features (Nov 2026)

- **Home spotlight**: highlight anything and the big banner switches to it;
  leave the remote alone and it rotates. Buttons: Open, Trailer, My List.
- **Mood chips** above the rows: Funny, Thriller, Family night, Action,
  Sci-fi, Romance, Hindi, Under 2 hours.
- **Search** (sidebar): type or press the mic. Results show which of *your*
  apps stream each title; OK opens it there. Hold OK on any show to add it
  to **My List** (its own row on Home).
- **Kids mode**: Settings › Kids mode — tick allowed apps, set a 4-digit
  PIN and an optional daily limit, then Start. Leaving needs the PIN.
  Time counts from when a kid opens an app until they come back home;
  when it runs out, apps lock on the kids home.
- **Screensaver**: Settings › Layout & Grid › Screensaver opens the TV's
  screen-saver settings; choose "Rajat's TV Launcher".
- **Phone companion**: Settings › Phone companion shows a QR code. Scan it
  on the same Wi-Fi to search, play on TV, launch apps and see My List.
- **Updates**: an "Update · build N" button appears in the top bar when a
  new build is on GitHub (or Settings › About › Check for updates).

## Notes

- Uses `<queries>` intent declarations (not the broader `QUERY_ALL_PACKAGES`
  permission) to see every launchable app — some Android TV builds reject
  sideloaded APKs that request the broader permission.
- `REQUEST_DELETE_PACKAGES` permission is required for the bulk-uninstall
  flow to trigger the system confirmation dialog at all.
- Theme storage uses plain SharedPreferences rather than Jetpack DataStore,
  since DataStore bundles a native library that isn't guaranteed to match
  every Android TV's CPU architecture.
- This installs as a normal app. No root, no bootloader unlock, no
  firmware changes — uninstalling it or switching the default launcher
  back fully reverts your TV to how it was before.
