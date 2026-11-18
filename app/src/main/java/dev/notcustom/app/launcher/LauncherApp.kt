package dev.notcustom.app.launcher

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.os.Process
import android.os.UserHandle
import androidx.compose.runtime.Immutable

@Immutable
data class LauncherApp(
    val component: ComponentName,
    val label: String,
    val packageName: String,
    val user: UserHandle = Process.myUserHandle(),
    val category: String = "Other",
    val system: Boolean = false,
    /** Set for a pinned deep shortcut placed on Home (e.g. Chrome "Add to Home screen"). */
    val shortcutId: String? = null,
    /** Set for a Home folder. */
    val folderId: String? = null,
    /** Folder contents, carried on the item so the folder icon redraws when they change. */
    val folderApps: List<ComponentName> = emptyList()
) {
    val key: String get() = "${component.flattenToString()}#${user.hashCode()}"
    val isApp: Boolean get() = shortcutId == null && folderId == null
}

/** Folder on Home or in the dock. [apps] are real app components, in display order. */
@Immutable
data class HomeFolder(val id: String, val name: String, val apps: List<ComponentName>)

// Shortcuts and folders occupy Home slots through synthetic components, so every slot,
// dock and drag path treats them like apps without a second placement model.
const val SHORTCUT_PACKAGE = "notcustom.shortcut"
const val FOLDER_PACKAGE = "notcustom.folder"

fun shortcutComponent(packageName: String, shortcutId: String) =
    ComponentName(SHORTCUT_PACKAGE, "$packageName/$shortcutId")

fun folderComponent(id: String) = ComponentName(FOLDER_PACKAGE, id)

val ComponentName.isFolder: Boolean get() = packageName == FOLDER_PACKAGE
val ComponentName.isShortcut: Boolean get() = packageName == SHORTCUT_PACKAGE

fun HomeFolder.toItem() = LauncherApp(
    component = folderComponent(id),
    label = name,
    packageName = FOLDER_PACKAGE,
    folderId = id,
    folderApps = apps
)

data class WidgetChoice(
    val info: AppWidgetProviderInfo,
    val appLabel: String,
    val label: String,
    val sizeLabel: String
)

@Immutable
data class WidgetSize(val columns: Int, val rows: Int)

@Immutable
data class WidgetPlacement(val column: Int, val row: Int, val page: Int = 0)

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
