# Shizuku is loaded reflectively and uses Binder/AIDL internals.
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class dev.rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**
-dontwarn dev.rikka.shizuku.**

# Local AIDL shell service and its generated Binder stubs.
-keep class dev.notcustom.app.IShellService { *; }
-keep class dev.notcustom.app.IShellService$* { *; }
-keep class dev.notcustom.app.ShellService { *; }
-keep class dev.notcustom.app.ShellService$* { *; }

# Android components referenced from the manifest.
-keep class dev.notcustom.app.MainActivity { *; }
-keep class dev.notcustom.app.launcher.LauncherActivity { *; }
-keep class dev.notcustom.app.launcher.NotCustomAccessibilityService { *; }
-keep class dev.notcustom.app.launcher.NotCustomAccessibilityService$* { *; }
-keep class dev.notcustom.app.NotCustomTileService { *; }
-keep class rikka.shizuku.ShizukuProvider { *; }

-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
