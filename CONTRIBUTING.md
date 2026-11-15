# Contributing

Thanks for helping improve NotCustom. Please open an issue before large changes so the scope can be discussed.

## Build

- JDK 17
- Android SDK Platform 34
- `./gradlew assembleDebug` to build a debug APK

Keep changes focused, preserve the separate Nothing Launcher, and avoid adding network access or permissions unless the feature needs them and the change is explained. Include screenshots for visible UI changes when possible.

## Pull requests and merge requests

1. Describe the user-visible change and any permission or device-specific behavior.
2. Build the affected variant and report the result.
3. Include device model and Android version for device-dependent changes.
4. Never include signing keys, private logs, personal data, or generated build files.
