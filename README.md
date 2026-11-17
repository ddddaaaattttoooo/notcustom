<p align="center">
  <img src="assets/notcustom-banner.svg" alt="NotCustom — Make home yours" width="760" />
</p>

<p align="center">ANDROID 12+ &nbsp;•&nbsp; NO ROOT &nbsp;•&nbsp; MIT LICENSE</p>

<p align="center">
  <a href="https://github.com/ddddaaaattttoooo/notcustom/releases/tag/v0.3.23-beta"><img alt="Download NotCustom 0.3.23-beta" src="https://img.shields.io/badge/Download-NotCustom%200.3.23--beta-ed1c24?style=for-the-badge&logo=android&logoColor=white" /></a>
</p>

NotCustom is an Android launcher with a flexible Home screen, app drawer, widgets, and optional Shizuku-powered system tweaks. It takes visual inspiration from Nothing OS while keeping Nothing Launcher intact.

> Not affiliated with Nothing Technology Limited. NotCustom does not require root and does not add Glyph features.

## Install

### Download the APK

Download the APK from [GitHub Releases](https://github.com/ddddaaaattttoooo/notcustom/releases/tag/v0.3.23-beta).

### Install on your phone

1. Open the release page on your Android phone and download `NotCustom-…-release.apk`.
2. Open the downloaded file. If Android asks, allow your browser or Files app to install unknown apps, then confirm **Install**.
3. Press Home and choose **NotCustom Home**. Choose **Always** if you want it as your default launcher. You can change this later in Android's Default apps settings.
4. Optional: open Home Settings to enable accessibility access for screen-off and notification-shade gestures. Android may require **App info → Allow restricted settings** for a sideloaded build.

To update, install the newer APK over the existing app. Beta APKs use Android's debug signing key, so Android may reject an update if the two APKs were signed with different keys. Uninstalling first removes NotCustom's saved layout and settings. A stable release-signing key is needed before promising seamless updates between every release.

## Compatibility

- **Android 12 or newer** is required. NotCustom Home uses standard Android launcher APIs and is intended to work on Android phones from different manufacturers, including Nothing, Pixel, and Samsung devices.
- **Verified device:** Nothing Phone 3a Lite running Nothing OS 4.x. Other brands and Android versions have not all been tested; drawer behavior, widgets, gestures, and launcher selection can vary by manufacturer.
- **System tweaks are device-dependent.** The optional FEEL/DISPLAY tweaks require Shizuku and may not be supported on every Android build. The launcher works without Shizuku.

## Features

### Home screen

- Multiple pages with drag-and-drop app placement
- A compact dock fixed at the bottom, editable by dragging apps between the dock and Home
- Pick up an app or widget and drop it on REMOVE, or on UNINSTALL while still holding it; system and device-admin apps show APP INFO instead
- Drawer in GRID, LIST, or SMART mode
- App labels, icon size, columns, icon packs, and monochrome icons
- Clock with 12/24-hour format, battery status, or next alarm in the header
- Android app widgets, a searchable widget picker, and per-app widget adding from the long-press menu
- Swipe up for the drawer; optional gestures for notifications and screen-off

### Optional system tools

- FEEL and DISPLAY presets, density and font-scale controls, and a Quick Settings tile
- Shizuku support for protected settings; no root required

## Updates

Home Settings checks GitHub Releases for a newer beta and falls back to GitLab. When one is available, **INSTALL** downloads the APK and opens Android's installer. Beta builds use the debug signing key, so Android may reject an update over a differently signed copy.

## Permissions and privacy

- Internet access is used only to check for and download NotCustom updates from GitHub or GitLab. There is no analytics, tracking, or background upload. Battery and next-alarm header options read on-device information.
- Accessibility access is optional and used only for requested global actions such as opening notifications or locking the screen. It does not read screen content.
- Shizuku is optional and used only for system-level tweaks.
- Launcher preferences, app placements, and widget IDs are stored locally.

## Build from source

Requirements: JDK 17 and Android SDK Platform 34.

```bash
git clone https://github.com/ddddaaaattttoooo/notcustom.git
cd notcustom
./gradlew assembleDebug
```

The APK is created under `app/build/outputs/apk/debug/`. Release builds are signed with the Android debug key for local testing; configure your own signing key before distributing a production build.

## Help and contributions

Found a bug? Open a [GitHub issue](https://github.com/ddddaaaattttoooo/notcustom/issues). Please include your phone model, Android version, NotCustom version, and steps to reproduce. Don't include private logs or personal data.

Merge requests and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for the build and review workflow.

## License

NotCustom is released under the [MIT License](LICENSE).
