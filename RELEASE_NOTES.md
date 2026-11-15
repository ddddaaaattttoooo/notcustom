## NotCustom 0.3.21-beta

- Check for NotCustom updates from Home Settings (GitHub first, GitLab as fallback)
- Install an available update from inside the launcher; the APK downloads and opens Android's installer
- Uninstall is only offered for apps that can actually be removed; system apps and device-administrator apps show APP INFO instead
- Automatic update check runs shortly after the launcher starts

## NotCustom 0.3.20-beta

- Remove an app or widget the mainstream way: pick it up and drop it on the REMOVE target at the top of the screen
- Apps can be dropped on UNINSTALL while still holding them; system apps that cannot be uninstalled show APP INFO instead
- Uninstall is hidden for apps that cannot be removed in the app long-press menu too
- Widgets can be dragged to REMOVE instead of tapping a button
- Releasing an app without moving it still opens its usual options menu

## NotCustom 0.3.19-beta

- Add widgets for a specific app from its long-press menu; the picker filters to just that app's widgets
- Picking up or long-pressing an app or widget on Home now shows REMOVE (and UNINSTALL for apps) actions
- Home screen gently scales back while editing a placement
- Release asset label in the GitLab pipeline is now "Download NotCustom APK"

## NotCustom 0.3.18-beta

This beta brings the Home screen and dock together while keeping the dock compact and fixed along the bottom edge.

- Persistent bottom dock with drag-and-drop between dock, Home pages, and the app drawer
- More usable Home placement area and page switching while dragging
- Clock header choices: clock/date, battery status, or next alarm
- 12-hour and 24-hour clock formats
- App launch and return animation improvements
- Shizuku shell execution updates for supported system tweaks

### Requirements

- Android 12 or newer
- The launcher is intended for Android phones from different manufacturers; verified so far on a Nothing Phone 3a Lite running Nothing OS 4.x.
- Shizuku is optional and only needed for protected system tweaks.

### Install or update

Download the APK attached to this release and install it on the phone. Android can update an existing copy only when its signing key matches.

This beta is signed with an Android debug key. Android may reject an update if the installed copy was signed with a different key. Uninstalling first removes saved launcher settings; a stable release-signing key is needed for seamless updates between releases.
