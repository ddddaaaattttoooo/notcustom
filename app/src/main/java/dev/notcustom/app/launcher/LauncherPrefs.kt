package dev.notcustom.app.launcher

import android.content.ComponentName
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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
            AtAGlanceStyle.valueOf(
                prefs.getString("at_a_glance_style", null) ?: AtAGlanceStyle.CLOCK.name
            )
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
        get() = decodeComponents(prefs.getString("pinned", null))
        set(value) = prefs.edit().putString("pinned", encodeComponents(value)).apply()

    var pinPositions: Map<ComponentName, Int>
        get() {
            val raw = prefs.getString("pin_positions", null) ?: return emptyMap()
            return runCatching {
                if (raw.trimStart().startsWith("[")) decodePinPositionsJson(raw)
                else decodePinPositionsLegacy(raw)
            }.getOrDefault(emptyMap())
        }
        set(value) {
            val array = JSONArray()
            value.forEach { (component, position) ->
                array.put(
                    JSONObject()
                        .put("component", component.flattenToString())
                        .put("position", position)
                )
            }
            prefs.edit().putString("pin_positions", array.toString()).apply()
        }

    var dock: List<ComponentName>
        get() = decodeComponents(prefs.getString("dock", null)).take(MAX_DOCK_ITEMS)
        set(value) = prefs.edit().putString(
            "dock",
            encodeComponents(value.take(MAX_DOCK_ITEMS))
        ).apply()

    var hidden: Set<String>
        get() = prefs.getStringSet("hidden", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("hidden", value).apply()

    var widgets: List<Int>
        get() {
            val raw = prefs.getString("widgets", null) ?: return emptyList()
            return runCatching {
                if (raw.trimStart().startsWith("[")) decodeWidgetsJson(raw)
                else raw.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it >= 0 }
            }.getOrDefault(emptyList())
        }
        set(value) {
            val array = JSONArray()
            value.forEach { array.put(it) }
            prefs.edit().putString("widgets", array.toString()).apply()
        }

    var widgetSizes: Map<Int, WidgetSize>
        get() {
            val raw = prefs.getString("widget_sizes", null) ?: return emptyMap()
            return runCatching {
                if (raw.trimStart().startsWith("[")) decodeWidgetSizesJson(raw)
                else decodeWidgetSizesLegacy(raw)
            }.getOrDefault(emptyMap())
        }
        set(value) {
            val array = JSONArray()
            value.forEach { (id, size) ->
                array.put(
                    JSONObject()
                        .put("id", id)
                        .put("columns", size.columns)
                        .put("rows", size.rows)
                )
            }
            prefs.edit().putString("widget_sizes", array.toString()).apply()
        }

    var widgetPlacements: Map<Int, WidgetPlacement>
        get() {
            val raw = prefs.getString("widget_placements", null) ?: return emptyMap()
            return runCatching {
                if (raw.trimStart().startsWith("[")) decodeWidgetPlacementsJson(raw)
                else decodeWidgetPlacementsLegacy(raw)
            }.getOrDefault(emptyMap())
        }
        set(value) {
            val array = JSONArray()
            value.forEach { (id, placement) ->
                array.put(
                    JSONObject()
                        .put("id", id)
                        .put("column", placement.column)
                        .put("row", placement.row)
                        .put("page", placement.page)
                )
            }
            prefs.edit().putString("widget_placements", array.toString()).apply()
        }

    var folders: List<HomeFolder>
        get() {
            val raw = prefs.getString("folders", null) ?: return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val apps = item.optJSONArray("apps") ?: JSONArray()
                    HomeFolder(
                        id = id,
                        name = item.optString("name", "Folder"),
                        apps = (0 until apps.length()).mapNotNull {
                            ComponentName.unflattenFromString(apps.optString(it, ""))
                        }
                    )
                }
            }.getOrDefault(emptyList())
        }
        set(value) {
            val array = JSONArray()
            value.forEach { folder ->
                array.put(
                    JSONObject()
                        .put("id", folder.id)
                        .put("name", folder.name)
                        .put("apps", JSONArray().apply {
                            folder.apps.forEach { put(it.flattenToString()) }
                        })
                )
            }
            prefs.edit().putString("folders", array.toString()).apply()
        }

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
            .remove("folders")
            .remove("page_count")
            .remove("current_page")
            .putBoolean("initialized", false)
            .apply()
    }

    private fun encodeComponents(list: List<ComponentName>): String {
        val array = JSONArray()
        list.forEach { array.put(it.flattenToString()) }
        return array.toString()
    }

    private fun decodeComponents(raw: String?): List<ComponentName> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            if (raw.trimStart().startsWith("[")) {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { index ->
                    ComponentName.unflattenFromString(array.optString(index, ""))
                }
            } else {
                raw.split(";")
                    .mapNotNull { it.takeIf { value -> value.isNotBlank() } }
                    .mapNotNull { ComponentName.unflattenFromString(it) }
            }
        }.getOrDefault(emptyList())
    }

    private fun decodePinPositionsJson(raw: String): Map<ComponentName, Int> {
        val array = JSONArray(raw)
        val result = LinkedHashMap<ComponentName, Int>()
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val component = ComponentName.unflattenFromString(
                entry.optString("component", "")
            ) ?: continue
            val position = entry.optInt("position", Int.MIN_VALUE)
            if (position in -MAX_DOCK_ITEMS..(MAX_HOME_PAGES * HOME_ROWS * 6 - 1)) {
                result[component] = position
            }
        }
        return result
    }

    private fun decodePinPositionsLegacy(raw: String): Map<ComponentName, Int> {
        val result = LinkedHashMap<ComponentName, Int>()
        raw.split(";").forEach { entry ->
            val split = entry.lastIndexOf('@')
            if (split < 0) return@forEach
            val component = ComponentName.unflattenFromString(entry.substring(0, split))
                ?: return@forEach
            val position = entry.substring(split + 1).toIntOrNull() ?: return@forEach
            if (position in -MAX_DOCK_ITEMS..(MAX_HOME_PAGES * HOME_ROWS * 6 - 1)) {
                result[component] = position
            }
        }
        return result
    }

    private fun decodeWidgetsJson(raw: String): List<Int> {
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            array.optInt(index, Int.MIN_VALUE).takeIf { it >= 0 }
        }
    }

    private fun decodeWidgetSizesJson(raw: String): Map<Int, WidgetSize> {
        val array = JSONArray(raw)
        val result = LinkedHashMap<Int, WidgetSize>()
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val id = entry.optInt("id", -1)
            val columns = entry.optInt("columns", 0)
            val rows = entry.optInt("rows", 0)
            if (id >= 0 && columns in 1..6 && rows in 1..HOME_ROWS) {
                result[id] = WidgetSize(columns, rows)
            }
        }
        return result
    }

    private fun decodeWidgetSizesLegacy(raw: String): Map<Int, WidgetSize> {
        val result = LinkedHashMap<Int, WidgetSize>()
        raw.split(";").forEach { entry ->
            val parts = entry.split(':')
            if (parts.size != 3) return@forEach
            val id = parts[0].toIntOrNull() ?: return@forEach
            val columns = parts[1].toIntOrNull() ?: return@forEach
            val rows = parts[2].toIntOrNull() ?: return@forEach
            if (id >= 0 && columns in 1..6 && rows in 1..HOME_ROWS) {
                result[id] = WidgetSize(columns, rows)
            }
        }
        return result
    }

    private fun decodeWidgetPlacementsJson(raw: String): Map<Int, WidgetPlacement> {
        val array = JSONArray(raw)
        val result = LinkedHashMap<Int, WidgetPlacement>()
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val id = entry.optInt("id", -1)
            val column = entry.optInt("column", -1)
            val row = entry.optInt("row", -1)
            val page = entry.optInt("page", 0)
            if (id >= 0 && column in 0 until 6 && row in 0 until HOME_ROWS &&
                page in 0 until MAX_HOME_PAGES
            ) {
                result[id] = WidgetPlacement(column, row, page)
            }
        }
        return result
    }

    private fun decodeWidgetPlacementsLegacy(raw: String): Map<Int, WidgetPlacement> {
        val result = LinkedHashMap<Int, WidgetPlacement>()
        raw.split(";").forEach { entry ->
            val parts = entry.split(':')
            if (parts.size != 3 && parts.size != 4) return@forEach
            val id = parts[0].toIntOrNull() ?: return@forEach
            val column = parts[1].toIntOrNull() ?: return@forEach
            val row = parts[2].toIntOrNull() ?: return@forEach
            val page = if (parts.size == 4) parts[3].toIntOrNull() ?: 0 else 0
            if (id >= 0 && column in 0 until 6 && row in 0 until HOME_ROWS &&
                page in 0 until MAX_HOME_PAGES
            ) {
                result[id] = WidgetPlacement(column, row, page)
            }
        }
        return result
    }
}
