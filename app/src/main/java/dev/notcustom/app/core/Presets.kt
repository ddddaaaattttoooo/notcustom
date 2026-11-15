package dev.notcustom.app.core

import android.content.Context

data class Snapshot(
    val window: Float,
    val transition: Float,
    val animator: Float,
    val fontScale: Float,
    val density: Int?
)

object Presets {
    private const val FILE = "notcustom"
    private const val KEY = "snapshot"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(context: Context, snapshot: Snapshot) {
        val encoded = listOf(
            snapshot.window,
            snapshot.transition,
            snapshot.animator,
            snapshot.fontScale,
            snapshot.density ?: -1
        ).joinToString(",")
        prefs(context).edit().putString(KEY, encoded).apply()
    }

    fun load(context: Context): Snapshot? {
        val raw = prefs(context).getString(KEY, null) ?: return null
        val parts = raw.split(",")
        if (parts.size != 5) return null
        val density = parts[4].toIntOrNull() ?: -1
        return Snapshot(
            window = parts[0].toFloatOrNull() ?: 1f,
            transition = parts[1].toFloatOrNull() ?: 1f,
            animator = parts[2].toFloatOrNull() ?: 1f,
            fontScale = parts[3].toFloatOrNull() ?: 1f,
            density = if (density <= 0) null else density
        )
    }

    fun exists(context: Context): Boolean =
        prefs(context).contains(KEY)
}
