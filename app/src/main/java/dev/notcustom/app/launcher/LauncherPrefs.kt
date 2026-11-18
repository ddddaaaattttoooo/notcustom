package dev.notcustom.app.launcher

import android.content.ComponentName
import android.content.Context

class LauncherPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("notcustom_home", Context.MODE_PRIVATE)

    var initialized: Boolean
        get() = prefs.getBoolean("initialized", false)
        set(value) = prefs.edit().putBoolean("initialized", value).apply()

    var columns: Int
        get() = prefs.getInt("columns", 4).coerceIn(3, 6)
        set(value) = prefs.edit().putInt("columns", value.coerceIn(3, 6)).apply()

    var iconSize: Int
        get() = prefs.getInt("icon_size", 56).coerceIn(40, 76)
        set(value) = prefs.edit().putInt("icon_size", value.coerceIn(40, 76)).apply()

    var showLabels: Boolean
        get() = prefs.getBoolean("labels", true)
        set(value) = prefs.edit().putBoolean("labels", value).apply()

    var showSearch: Boolean
        get() = prefs.getBoolean("search", true)
        set(value) = prefs.edit().putBoolean("search", value).apply()

    var use24HourClock: Boolean
        get() = prefs.getBoolean("clock_24_hour", true)
        set(value) = prefs.edit().putBoolean("clock_24_hour", value).apply()

    var atAGlanceStyle: AtAGlanceStyle
        get() = runCatching {
            AtAGlanceStyle.valueOf(prefs.getString("at_a_glance_style", null) ?: AtAGlanceStyle.CLOCK.name)
        }.getOrDefault(AtAGlanceStyle.CLOCK)
        set(value) = prefs.edit().putString("at_a_glance_style", value.name).apply()

    var monochrome: Boolean
        get() = prefs.getBoolean("monochrome", false)
        set(value) = prefs.edit().putBoolean("monochrome", value).apply()

    var drawerMode: DrawerMode
        get() = runCatching {
            DrawerMode.valueOf(prefs.getString("drawer_mode", null) ?: DrawerMode.GRID.name)
        }.getOrDefault(DrawerMode.GRID)
        set(value) = prefs.edit().putString("drawer_mode", value.name).apply()

    var pageCount: Int
        get() = prefs.getInt("page_count", 1).coerceIn(1, MAX_HOME_PAGES)
        set(value) = prefs.edit().putInt("page_count", value.coerceIn(1, MAX_HOME_PAGES)).apply()

    var currentPage: Int
        get() = prefs.getInt("current_page", 0).coerceIn(0, MAX_HOME_PAGES - 1)
        set(value) = prefs.edit().putInt("current_page", value.coerceIn(0, MAX_HOME_PAGES - 1)).apply()

    var iconPack: String?
        get() = prefs.getString("icon_pack", null)
        set(value) = prefs.edit().putString("icon_pack", value).apply()

    var pinned: List<ComponentName>
        get() = decode(prefs.getString("pinned", null))
        set(value) = prefs.edit().putString("pinned", encode(value)).apply()

    var pinPositions: Map<ComponentName, Int>
        get() = prefs.getString("pin_positions", null).orEmpty()
            .split(";")
            .mapNotNull { entry ->
                val split = entry.lastIndexOf('@')
                if (split < 0) null else {
                    val component = ComponentName.unflattenFromString(entry.substring(0, split))
                    val position = entry.substring(split + 1).toIntOrNull()
                    if (component != null && position != null &&
                        position in -MAX_DOCK_ITEMS..(MAX_HOME_PAGES * HOME_ROWS * 6 - 1)
                    ) component to position else null
                }
            }.toMap()
        set(value) = prefs.edit().putString(
            "pin_positions",
            value.entries.joinToString(";") { "${it.key.flattenToString()}@${it.value}" }
        ).apply()

    var dock: List<ComponentName>
        get() = decode(prefs.getString("dock", null))
        set(value) = prefs.edit().putString("dock", encode(value.take(5))).apply()

    var hidden: Set<String>
        get() = prefs.getStringSet("hidden", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("hidden", value).apply()

    var widgets: List<Int>
        get() = prefs.getString("widgets", null).orEmpty()
            .split(",")
            .mapNotNull { it.toIntOrNull() }
        set(value) = prefs.edit().putString("widgets", value.joinToString(",")).apply()

    var widgetSizes: Map<Int, WidgetSize>
        get() = prefs.getString("widget_sizes", null).orEmpty()
            .split(";")
            .mapNotNull { entry ->
                val parts = entry.split(':')
                if (parts.size != 3) return@mapNotNull null
                val id = parts[0].toIntOrNull() ?: return@mapNotNull null
                val columns = parts[1].toIntOrNull() ?: return@mapNotNull null
                val rows = parts[2].toIntOrNull() ?: return@mapNotNull null
                id to WidgetSize(columns, rows)
            }.toMap()
        set(value) = prefs.edit().putString(
            "widget_sizes",
            value.entries.joinToString(";") {
                "${it.key}:${it.value.columns}:${it.value.rows}"
            }
        ).apply()

    fun config(): LauncherConfig = LauncherConfig(
        columns = columns,
        iconSize = iconSize,
        showLabels = showLabels,
        showSearch = showSearch,
        use24HourClock = use24HourClock,
        atAGlanceStyle = atAGlanceStyle,
        monochrome = monochrome
    )

    fun clearLayout() {
        prefs.edit()
            .remove("pinned")
            .remove("pin_positions")
            .remove("dock")
            .remove("hidden")
            .remove("page_count")
            .remove("current_page")
            .putBoolean("initialized", false)
            .apply()
    }

    private fun encode(list: List<ComponentName>): String =
        list.joinToString(";") { it.flattenToString() }

    private fun decode(raw: String?): List<ComponentName> =
        raw.orEmpty()
            .split(";")
            .mapNotNull { it.takeIf { value -> value.isNotBlank() } }
            .mapNotNull { ComponentName.unflattenFromString(it) }
}
