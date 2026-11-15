package dev.notcustom.app.launcher

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName

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

data class LauncherConfig(
    val columns: Int = 4,
    val iconSize: Int = 56,
    val showLabels: Boolean = true,
    val showSearch: Boolean = true,
    val monochrome: Boolean = false
)

enum class DrawerMode {
    GRID,
    LIST,
    SMART
}
