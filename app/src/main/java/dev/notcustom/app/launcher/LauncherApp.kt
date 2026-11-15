package dev.notcustom.app.launcher

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import androidx.compose.runtime.Immutable

@Immutable
data class LauncherApp(
    val component: ComponentName,
    val label: String,
    val packageName: String
) {
    val key: String get() = component.flattenToString()
}

data class WidgetChoice(
    val info: AppWidgetProviderInfo,
    val appLabel: String,
    val label: String,
    val sizeLabel: String
)

enum class AtAGlanceStyle {
    CLOCK,
    BATTERY,
    NEXT_ALARM
}

@Immutable
data class LauncherConfig(
    val columns: Int = 4,
    val iconSize: Int = 56,
    val showLabels: Boolean = true,
    val showSearch: Boolean = true,
    val use24HourClock: Boolean = true,
    val atAGlanceStyle: AtAGlanceStyle = AtAGlanceStyle.CLOCK,
    val monochrome: Boolean = false
)

const val HOME_ROWS = 5
const val HOME_GRID_ROWS = HOME_ROWS - 1
const val MAX_DOCK_ITEMS = 5
const val MAX_HOME_PAGES = 12

enum class DrawerMode {
    GRID,
    LIST,
    SMART
}
