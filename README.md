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
  data/ThemeRepository.kt      – persists theme and personalisation (SharedPreferences)
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

For a command-line build, use JDK 17, Gradle 8.7, Android SDK platform 34,
and Build Tools 34.0.0. Set `ANDROID_HOME` to the SDK directory, then run:

```sh
gradle assembleDebug testDebugUnitTest lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
CI runs these checks for pull requests and publishes an APK release on `main`.

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

Open **Personalise** to save a display name for the time-based greeting, choose
a preset, or adjust the accent, background, clock visibility, and grid density.
Choose Solid, Aurora, or Dusk wallpaper, or import an image using the system
picker (up to 20 MB). Imported images are copied to private app storage and
remain available after restarting. TVs without a document picker can use the
built-in wallpapers. Theme and wallpaper changes apply immediately; use
**Save name** to apply an edited name.

Use the D-pad and OK/Enter to browse and open apps. Focused controls have an
accent border. Settings scrolls automatically as controls receive focus.

## 5. Manage apps safely

Open **Manage Apps**, select user-installed apps, and choose **Uninstall** in
the fixed bar above the app grid. The Menu key jumps to that action from any
row when apps are selected. Review the names, then choose **Continue**.
Android asks for confirmation for each app; cancelling a removal stops the
remaining queue. **Stop queue**, Back, or returning home also stops pending
requests (an already-open Android confirmation must still be answered).
System apps, updated system apps, and this launcher cannot be selected.
The app list refreshes after returning from Android. Recreating the activity
stops the batch rather than replaying destructive requests.

### Device smoke check

On an Android TV, verify D-pad focus and scrolling, launch a TV app and a
sideloaded app, save a name/theme/wallpaper and restart, then select two
**disposable user apps** in Manage Apps. Confirm the first removal and cancel
the second. Verify the first disappears, the second remains, and protected
apps never become selectable. Unit tests cover queue sequencing, cancellation,
protection checks, and preference persistence; TV-specific installer behaviour
still requires a device check.

## Notes

- Package visibility uses scoped `<queries>` for TV and phone launcher entry
  points. `REQUEST_DELETE_PACKAGES` allows asking Android to uninstall apps;
  it does not grant silent deletion.
- This installs as a normal app. No root, no bootloader unlock, no
  firmware changes — uninstalling it or switching the default launcher
  back fully reverts your TV to how it was before.
