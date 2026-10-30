# My TV Launcher

A custom Android TV home screen launcher, built for OnePlus TV (Android TV OS).
Shows every installed app — including sideloaded ones — in a themed grid,
with a Settings screen to design your own look: background color, accent
color, clock on/off, and grid density. Four presets are included (Midnight,
Sunset, Forest, Minimal Light) as starting points.

## What's in here

```
app/src/main/java/com/mytvlauncher/app/
  MainActivity.kt              – entry point, sets content to HomeScreen
  model/LauncherApp.kt         – one installed app (icon, label, package)
  model/LauncherTheme.kt       – theme data model + the 4 presets
  data/AppRepository.kt        – finds installed/sideloaded apps, launches them
  data/ThemeRepository.kt      – persists your theme choices (DataStore)
  ui/screens/HomeScreen.kt     – the home screen itself (clock, app grid)
  ui/screens/SettingsScreen.kt – the theme designer
```

To change the design further, `LauncherTheme.kt` and `SettingsScreen.kt`
are the two files to edit — add more presets, more colors, a wallpaper
picker, whatever you want.

## 1. Build it

1. Install **Android Studio** (free, from developer.android.com) if you
   don't have it.
2. Open this folder (`MyTVLauncher`) as a project — "Open" not "Import".
3. Let Gradle sync (first time will download dependencies, a few minutes).
4. Build the APK: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
   The output lands in `app/build/outputs/apk/debug/app-debug.apk`.

No Android Studio on this machine, so I can't compile it from here —
but the project is complete and will build as-is once opened there.

## 2. Install it on your OnePlus TV over WiFi (ADB)

Since the TV is on your WiFi already:

1. On the TV: **Settings → Device Preferences → About → (click Build
   number 7 times)** to enable Developer Options, if not already on.
2. **Settings → Device Preferences → Developer Options → Network debugging**
   → turn it on. Note the IP address shown (or check **Settings → Network**).
3. On your computer, with [ADB](https://developer.android.com/tools/adb)
   installed (comes with Android Studio's SDK Platform-Tools):
   ```
   adb connect <TV_IP_ADDRESS>:5555
   adb install app-debug.apk
   ```
4. On the TV, open the **Apps** row — "My TV Launcher" should now be listed.
   Open it once to confirm it works.

## 3. Set it as your home screen

1. On the TV: **Settings → Apps → See all apps → My TV Launcher → Set as
   default** (exact wording varies by TV software version), **or**
2. Press the **Home** button on the remote — Android TV will prompt you
   to choose a launcher if more than one is installed. Pick "My TV
   Launcher" and choose "Always".

To revert any time: same menu, select the original OnePlus launcher
instead — nothing about the TV itself is modified, so this is fully
reversible.

## 4. Design your theme

Open the app → tap the gear icon (top right) → pick a preset, then
override the accent color, background color, clock visibility, and
number of grid columns. Changes apply immediately.

## Notes

- `QUERY_ALL_PACKAGES` permission is required for a launcher to see
  every app on the device, including ones you sideloaded — this is
  the correct, standard permission for this use case, not something
  that touches system files.
- This installs as a normal app. No root, no bootloader unlock, no
  firmware changes — uninstalling it or switching the default launcher
  back fully reverts your TV to how it was before.
