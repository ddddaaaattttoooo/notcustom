package dev.notcustom.app.core

data class AnimScales(
    val window: Float = 1f,
    val transition: Float = 1f,
    val animator: Float = 1f
)

data class Density(val physical: Int, val overrideDpi: Int?) {
    val effective: Int get() = overrideDpi ?: physical
}

object Tweaks {
    private suspend fun getGlobal(key: String): String =
        Shell.exec("settings get global $key")

    private suspend fun putGlobal(key: String, value: String) {
        Shell.exec("settings put global $key $value")
    }

    suspend fun animScales(): AnimScales {
        val window = getGlobal("window_animation_scale").toFloatOrNull() ?: 1f
        val transition = getGlobal("transition_animation_scale").toFloatOrNull() ?: 1f
        val animator = getGlobal("animator_duration_scale").toFloatOrNull() ?: 1f
        return AnimScales(window, transition, animator)
    }

    suspend fun setAnimScales(scales: AnimScales) {
        putGlobal("window_animation_scale", scales.window.toString())
        putGlobal("transition_animation_scale", scales.transition.toString())
        putGlobal("animator_duration_scale", scales.animator.toString())
    }

    suspend fun fontScale(): Float =
        Shell.exec("settings get system font_scale").toFloatOrNull() ?: 1f

    suspend fun setFontScale(value: Float) {
        Shell.exec("settings put system font_scale $value")
    }

    suspend fun density(): Density {
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

    suspend fun resetAll() {
        setAnimScales(AnimScales())
        setFontScale(1f)
        resetDensity()
    }
}
