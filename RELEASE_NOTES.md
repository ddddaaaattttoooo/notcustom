## NotCustom 0.3.27-beta

- Large widgets follow your finger while dragging and can be dropped on REMOVE again
- Dragging an app into the rightmost column no longer throws it onto the next page
- Removing a page also removes its folders, so their apps can be added to Home again
- Fresh screenshots on the project pages

## NotCustom 0.3.26-beta

- Folders: drop an app onto another app on Home to make a folder, rename it, and drag apps back out of an open folder just like on Home
- Notification dots on app icons (turn on in Home Settings and allow notification access)
- Swipe down on Home opens the notification shade without the accessibility service
- Apps can pin shortcuts to Home ("Add to Home screen" from Chrome, Contacts and others)
- Hold an app without moving to open its menu, on Home and in the drawer; long-press inside a folder keeps the folder open behind the menu
- Uninstall from drag-to-uninstall and from the app menu works on Android 9+ instead of being silently ignored
- App menus and settings sheets open fully and stay dark over the navigation bar, even when the phone uses a light system theme
- New setup screen: app logo, a home screen step that shows whether NotCustom is your default, and optional Shizuku tweaks
- One logo everywhere: the app icon now matches the dot grid used in the app, the home menu button and the project page
- New widgets are added at the size their app asks for instead of a single cell, shrinking only if the page is too full
- SMART drawer categories with four or fewer apps no longer show repeated ALL tiles
- Home shows page dots only; add or remove pages from the Home long-press menu, so a stray tap can't delete a page
- Docked apps are spread evenly; empty dock slots only appear while dragging
- Pressing Back on Home no longer reloads the launcher with a blank screen
- Dragging an app away and back no longer opens its menu

## NotCustom 0.3.25-beta

- Long-press a widget to pick it up again; moving your finger keeps the usual drag-to-remove flow
- The resize frame no longer cancels the drag gesture when it appears
- The corner resize handle is larger and easier to grab
- Changing a widget's size updates its Android layout so clock and calendar widgets reflow correctly

## NotCustom 0.3.24-beta

- Resize any Home widget: long-press it to select, then drag the corner handle to change its width and height in grid cells
- Widget sizes are remembered per widget, including after a restart
- Widgets now start at a sensible cell size instead of stretching across the whole Home screen
- Home no longer scrolls when a large widget is added; grid rows shrink to keep every page on one screen
- Tap outside a selected widget or press Back to deselect it
- Dragging a selected widget still removes it, and normal widget taps continue to work

## NotCustom 0.3.23-beta

- Release APKs now use a stable signing key, so updates install over older versions instead of failing with a package conflict
- SMART drawer puts TBC, BOG, and Keepz under Finance
- Taobao, Pinduoduo, 1688, Goofish, Trendyol, AliExpress, Alibaba.com, and KiwiPost go under Shopping
- Steam goes under Games
- Category matching now checks package IDs and localized labels, so Chinese-named shopping apps no longer fall into Other

## NotCustom 0.3.22-beta

- Widgets bind reliably on any phone: if the system needs permission, the standard bind prompt appears, then the widget's configuration screen still opens
- Widgets pinned from other apps (for example Appbar) are accepted through Android's confirm-pin request
- Long-press any widget in the picker and drag it onto Home; the sheet fades away and Home shows the target slots
- Dragging apps out of GRID, LIST, and SMART drawers now highlights the exact Home slot and dock position before release
- The app list refreshes immediately after installs, updates, and removals, including icon changes
- Work profile and cloned apps are listed with profile badges and launch in the correct profile
- POP-UP VIEW in the app menu opens an app in a centered freeform window on devices that support freeform
- SMART drawer now matches Nothing OS: category cards in a two-column grid, three apps launch directly, and the folder tile opens the rest
- Categories use Android's own app categories plus package and name heuristics
- App menus show the app's dynamic shortcuts
- Wallpaper pager parallax, spring-based animations, and a pre-composed drawer that opens without first-open jank

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
