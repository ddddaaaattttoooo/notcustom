# NotCustom

NotCustom is an Android app with two parts: a small set of Nothing OS tweaks and a separate, customizable home screen replacement with Nothing-inspired styling.

It is not affiliated with Nothing. Nothing Launcher is left untouched.

## Status

Beta: `0.3.7-beta` (versionCode 10). Expect rough edges and breaking changes.

## Features

### NotCustom Home

- Nothing-inspired home screen with a separate HOME activity
- App drawer with GRID, LIST, and SMART modes
- Free-position app placement with saved positions
- Dock, pinned apps, hidden apps, and configurable columns/icon size/labels
- Custom widget picker with categories, search, and previews
- Icon pack support, including `appfilter.xml` mappings
- Home gestures:
  - Swipe up: open the app drawer
  - Swipe down on home: open the notification shade
  - Double-tap home background: turn off the screen
  - Long-press home: home menu

### Tweaks

- FEEL and DISPLAY presets
- Density and font-scale controls
- Quick Settings tile
- About screen

System-level tweaks use Shizuku. The home screen gestures do not require Shizuku.

## Requirements

- Android 12 or newer (minSdk 31, targetSdk 34)
- Tested on a Nothing Phone 3a Lite running Nothing OS 4.x
- JDK 17 and Android SDK 34 to build
- Shizuku only for protected system tweaks
- Accessibility access only for screen-off and notification-shade gestures

## Build

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

The debug APK is written to `app/build/outputs/apk/debug/`, and the release APK to `app/build/outputs/apk/release/`.

The current release build is signed with the Android debug key so it can be installed over local test builds. Use your own release keystore before distributing builds.

## Install

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Then:

1. Open NotCustom Home and set it as the default home app.
2. Open Home Settings and enable Gesture Access if you want double-tap screen-off and swipe-down notifications.
3. On sideloaded builds, Android may require **App Info > Allow restricted settings** before the accessibility service can be enabled.

## Permissions and privacy

- The accessibility service only performs global actions: lock the screen and open the notification shade. It does not read screen content.
- The app does not request the `INTERNET` permission.
- Launcher settings, pinned apps, and widget IDs are stored locally on the device.
- Shizuku is optional and is only used for protected system settings.

## No root

NotCustom does not require root. It does not modify Nothing Launcher and does not add Glyph features.

## Project layout

- `app/src/main/java/dev/notcustom/app/launcher/`: home screen, drawer, widgets, gestures, icon loading
- `app/src/main/java/dev/notcustom/app/core/`: Shizuku, shell, tweaks, presets
- `app/src/main/java/dev/notcustom/app/ui/`: tweaks app UI
- `app/src/main/res/xml/accessibility_service_config.xml`: accessibility service configuration

## License

MIT. See [LICENSE](LICENSE).
