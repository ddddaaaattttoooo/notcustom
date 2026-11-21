package dev.notcustom.app.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.DisplayMetrics

data class AnimScales(
    val window: Float = 1f,
    val transition: Float = 1f,
    val animator: Float = 1f
)

data class PhoneSettings(
    val screenTimeoutMs: Int = 30_000,
    val autoRotate: Boolean = true,
    val touchSounds: Boolean = true,
    val touchVibration: Boolean = true
)

data class Density(val physical: Int, val overrideDpi: Int?) {
    val effective: Int get() = overrideDpi ?: physical
}

/**
 * System tweaks through Android's own settings API. Shizuku is only a fallback:
 * animations need WRITE_SECURE_SETTINGS (granted once over adb, or by Shizuku),
 * font size needs "Modify system settings", and density has no app API at all.
 */
object Tweaks {
    fun canWriteSecure(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun canWriteSystem(context: Context): Boolean = Settings.System.canWrite(context)

    /** Shizuku runs as the shell user, which may grant this app WRITE_SECURE_SETTINGS for good. */
    suspend fun grantSecureWithShizuku(context: Context) {
        Shell.exec("pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}")
    }

    fun animScales(context: Context): AnimScales {
        val resolver = context.contentResolver
        return AnimScales(
            Settings.Global.getFloat(resolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f),
            Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f),
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        )
    }

    suspend fun setAnimScales(context: Context, scales: AnimScales) {
        val values = listOf(
            Settings.Global.WINDOW_ANIMATION_SCALE to scales.window,
            Settings.Global.TRANSITION_ANIMATION_SCALE to scales.transition,
            Settings.Global.ANIMATOR_DURATION_SCALE to scales.animator
        )
        if (canWriteSecure(context)) {
            values.forEach { (key, value) -> Settings.Global.putFloat(context.contentResolver, key, value) }
        } else {
            values.forEach { (key, value) -> Shell.exec("settings put global $key $value") }
        }
    }

    fun fontScale(context: Context): Float =
        Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, 1f)

    suspend fun setFontScale(context: Context, value: Float) {
        if (canWriteSystem(context)) {
            Settings.System.putFloat(context.contentResolver, Settings.System.FONT_SCALE, value)
        } else {
            Shell.exec("settings put system font_scale $value")
        }
    }

    fun phone(context: Context): PhoneSettings {
        val resolver = context.contentResolver
        return PhoneSettings(
            Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000),
            Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1,
            Settings.System.getInt(resolver, Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1,
            Settings.System.getInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1
        )
    }

    suspend fun setPhone(context: Context, phone: PhoneSettings) {
        listOf(
            Settings.System.SCREEN_OFF_TIMEOUT to phone.screenTimeoutMs,
            Settings.System.ACCELEROMETER_ROTATION to if (phone.autoRotate) 1 else 0,
            Settings.System.SOUND_EFFECTS_ENABLED to if (phone.touchSounds) 1 else 0,
            Settings.System.HAPTIC_FEEDBACK_ENABLED to if (phone.touchVibration) 1 else 0
        ).forEach { (key, value) ->
            if (canWriteSystem(context)) Settings.System.putInt(context.contentResolver, key, value)
            else Shell.exec("settings put system $key $value")
        }
        // Like Android's own Sounds screen: the setting alone doesn't load or drop the click sounds.
        context.getSystemService(android.media.AudioManager::class.java)?.let {
            if (phone.touchSounds) it.loadSoundEffects() else it.unloadSoundEffects()
        }
    }

    /** Needs Shizuku for the override; without it, guesses the override from the current density. */
    suspend fun density(context: Context, shizuku: Boolean): Density {
        if (!shizuku) {
            val physical = DisplayMetrics.DENSITY_DEVICE_STABLE
            val current = context.resources.displayMetrics.densityDpi
            return Density(physical, current.takeIf { it != physical })
        }
        val output = Shell.exec("wm density")
        var physical = 0
        var overrideDpi: Int? = null
        output.lineSequence().forEach { line ->
            val value = line.substringAfter(':', "").trim().toIntOrNull()
            when {
                line.startsWith("Physical density") -> if (value != null) physical = value
                line.startsWith("Override density") -> overrideDpi = value
            }
        }
        return Density(if (physical == 0) 420 else physical, overrideDpi)
    }

    suspend fun setDensity(dpi: Int) {
        Shell.exec("wm density $dpi")
    }

    suspend fun resetDensity() {
        Shell.exec("wm density reset")
    }

    /** Applies each part on its own, so a missing density permission doesn't block the rest. */
    suspend fun apply(context: Context, snapshot: Snapshot, shizuku: Boolean) {
        val results = listOf(
            runCatching {
                setAnimScales(context, AnimScales(snapshot.window, snapshot.transition, snapshot.animator))
            },
            runCatching { setFontScale(context, snapshot.fontScale) },
            // Density changes only when a preset saved one: never reset the user's own Display size.
            runCatching { if (shizuku && snapshot.density != null) setDensity(snapshot.density) }
        )
        results.firstOrNull { it.isFailure }?.exceptionOrNull()?.let { throw it }
    }

    /** Added to every font weight system-wide (Android 12+ "Bold text" uses 300). */
    fun fontWeight(context: Context): Int = context.resources.configuration.fontWeightAdjustment
        .takeIf { it != android.content.res.Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED } ?: 0

    suspend fun setFontWeight(context: Context, value: Int) {
        if (canWriteSecure(context)) {
            Settings.Secure.putInt(context.contentResolver, "font_weight_adjustment", value)
        } else {
            Shell.exec("settings put secure font_weight_adjustment $value")
        }
    }

    suspend fun resetAll(context: Context, shizuku: Boolean) {
        val weight = runCatching { setFontWeight(context, 0) }
        apply(context, Snapshot(1f, 1f, 1f, 1f, null), shizuku)
        weight.getOrThrow()
    }
}
