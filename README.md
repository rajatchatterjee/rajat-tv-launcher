# Rajat's TV Launcher

A custom Android TV home screen launcher, built for OnePlus TV (Android TV OS).
Shows every installed app — including sideloaded ones — in a themed grid,
with a Settings screen to design your own look: background color, accent
color, clock on/off, and grid density. Five presets are included (Midnight,
Sunset, Forest, Minimal Light, Crimson — the default) as starting points.
The home screen also greets you by name and shows the date next to the clock.

## What's in here

```
app/src/main/java/com/mytvlauncher/app/
  MainActivity.kt                  – entry point, sets content to HomeScreen
  model/LauncherApp.kt             – one installed app (icon, label, package)
  model/LauncherTheme.kt           – theme data model + the 5 presets
  data/AppRepository.kt            – finds installed/sideloaded apps, launches/uninstalls them
  data/ThemeRepository.kt          – persists your theme choices (SharedPreferences)
  data/BulkUninstallController.kt  – walks the uninstall confirmation dialog across a selection
  ui/screens/HomeScreen.kt         – the home screen itself (clock, greeting, app grid, bulk select)
  ui/screens/SettingsScreen.kt     – the theme designer
```

To change the design further, `LauncherTheme.kt` and `SettingsScreen.kt`
are the two files to edit — add more presets, more colors, a wallpaper
picker, whatever you want.

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

## 4. Design your theme

Open the app → tap the gear icon (top right) → pick a preset, then
override the accent color, background color, clock visibility, and
number of grid columns. Changes apply immediately.

## 5. Bulk uninstall apps

Tap the trash icon (top right) to enter selection mode, tap apps to check
them off (only non-system apps are selectable), then tap **Uninstall**.
Android requires confirming each uninstall individually — there's no
silent bulk-delete API for a regular app — so you'll see the system's
"Uninstall this app?" dialog once per selected app; just keep confirming
until the queue finishes.

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
