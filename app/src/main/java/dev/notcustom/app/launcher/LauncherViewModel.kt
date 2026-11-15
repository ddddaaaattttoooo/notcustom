package dev.notcustom.app.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.app.Application
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.view.View
import androidx.compose.ui.graphics.ImageBitmap
import dev.notcustom.app.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.notcustom.app.core.Shell
import dev.notcustom.app.core.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.math.roundToInt

data class LauncherState(
    val loading: Boolean = true,
    val apps: List<LauncherApp> = emptyList(),
    val pinned: List<ComponentName> = emptyList(),
    val pinPositions: Map<ComponentName, Int> = emptyMap(),
    val pageCount: Int = 1,
    val currentPage: Int = 0,
    val iconPacks: List<IconPackInfo> = emptyList(),
    val iconPack: String? = null,
    val dock: List<ComponentName> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val config: LauncherConfig = LauncherConfig(),
    val drawerMode: DrawerMode = DrawerMode.GRID,
    val drawerOpen: Boolean = false,
    val search: String = "",
    val menuApp: LauncherApp? = null,
    val settingsOpen: Boolean = false,
    val homeMenuOpen: Boolean = false,
    val widgets: List<Int> = emptyList(),
    val widgetPickerOpen: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val returning: ReturnTransition? = null,
    val message: String? = null
)

data class ReturnTransition(
    val app: LauncherApp,
    val bounds: Rect,
    val nonce: Long = System.nanoTime()
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = LauncherPrefs(application)
    private val icons = IconLoader(application)
    private val widgetManager = AppWidgetManager.getInstance(application)
    val widgetHost = AppWidgetHost(application, WIDGET_HOST_ID)
    private val _state = MutableStateFlow(LauncherState())
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    private var awaitingReturn = false
    private var lastLaunch: LaunchRecord? = null
    private var pendingConfigId: Int? = null

    private data class LaunchRecord(
        val app: LauncherApp,
        val bounds: Rect?,
        val fromHome: Boolean
    )

    init {
        refreshAccessibility()
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { loadApps() }
            val iconPacks = withContext(Dispatchers.IO) { icons.availablePacks() }
            val iconPack = prefs.iconPack?.takeIf { selected ->
                iconPacks.any { it.packageName == selected }
            }
            prefs.iconPack = iconPack
            icons.setSelectedPack(iconPack)
            val widgets = loadWidgets()
            if (apps.isNotEmpty()) {
                if (!prefs.initialized) applyDefaults(apps)
                val valid = apps.map { it.component }.toSet()
                val hidden = prefs.hidden
                val pinned = prefs.pinned.filter { it in valid && it.packageName !in hidden }
                val dock = prefs.dock.filter { it in valid && it.packageName !in hidden }
                val allPinned = (pinned + dock).distinct()
                val pinPositions = normalizePinPositions(allPinned)
                val unifiedPinned = pinPositions.entries.sortedBy { it.value }.map { it.key }
                val config = prefs.config()
                val pageCount = maxOf(
                    prefs.pageCount,
                    neededPages(pinPositions, config.columns),
                    1
                )
                val currentPage = prefs.currentPage.coerceIn(0, pageCount - 1)
                prefs.pageCount = pageCount
                prefs.currentPage = currentPage
                _state.update {
                    it.copy(
                        loading = false,
                        apps = apps,
                        pinned = unifiedPinned,
                        pinPositions = pinPositions,
                        pageCount = pageCount,
                        currentPage = currentPage,
                        dock = pinPositions.entries.filter { it.value < 0 }
                            .sortedByDescending { it.value }.map { it.key },
                        hidden = hidden,
                        widgets = widgets,
                        config = config,
                        drawerMode = prefs.drawerMode,
                        iconPacks = iconPacks,
                        iconPack = iconPack
                    )
                }
                val byComponent = apps.associateBy { it.component }
                val preload = ((unifiedPinned).mapNotNull { byComponent[it] } + apps)
                    .distinctBy { it.key }
                viewModelScope.launch { preload.forEach { icons.load(it) } }
            } else {
                _state.update {
                    it.copy(
                        loading = false,
                        widgets = widgets,
                        iconPacks = iconPacks,
                        iconPack = iconPack
                    )
                }
            }
        }
    }

    suspend fun loadIcon(app: LauncherApp): ImageBitmap? = icons.load(app)

    fun cachedIcon(app: LauncherApp): ImageBitmap? = icons.peek(app.key)

    fun launch(
        app: LauncherApp,
        sourceBounds: Rect? = null,
        fromHome: Boolean = false,
        sourceView: View? = null
    ) {
        val context = getApplication<Application>()
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val bounds = sourceBounds?.takeIf { it.width() > 0 && it.height() > 0 }
        val opts = if (bounds != null && sourceView != null) {
            ActivityOptions.makeScaleUpAnimation(
                sourceView,
                bounds.left,
                bounds.top,
                bounds.width(),
                bounds.height()
            ).toBundle()
        } else {
            ActivityOptions.makeCustomAnimation(
                context,
                R.anim.app_launch_enter,
                R.anim.launcher_launch_exit
            ).toBundle()
        }
        var launched = runCatching {
            launcherApps.startMainActivity(app.component, Process.myUserHandle(), bounds, opts)
        }.isSuccess
        if (!launched) {
            launched = runCatching {
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(app.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (opts != null) context.startActivity(intent, opts) else context.startActivity(intent)
            }.isSuccess
        }
        if (launched) {
            awaitingReturn = true
            lastLaunch = LaunchRecord(app, bounds, fromHome)
        }
        _state.update { it.copy(drawerOpen = false, menuApp = null, search = "", homeMenuOpen = false) }
    }

    fun onActivityResumed() {
        refreshAccessibility()
        if (!awaitingReturn) return
        awaitingReturn = false
        val record = lastLaunch ?: return
        lastLaunch = null
        if (!record.fromHome || record.bounds == null) return
        _state.update { it.copy(returning = ReturnTransition(record.app, record.bounds)) }
        viewModelScope.launch {
            delay(300)
            _state.update { it.copy(returning = null) }
        }
    }

    fun refreshAccessibility() {
        val enabled = NotCustomAccessibilityService.isEnabled(getApplication())
        _state.update { it.copy(accessibilityEnabled = enabled) }
    }

    fun openAppInfo() {
        val context = getApplication<Application>()
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openAccessibilitySettings() {
        NotCustomAccessibilityService.openAccessibilitySettings(getApplication())
    }

    fun pullDownFromDrawer() {
        _state.update { it.copy(drawerOpen = false, search = "") }
    }

    fun openNotificationsFromHome() {
        val enabled = NotCustomAccessibilityService.isEnabled(getApplication())
        val opened = enabled && NotCustomAccessibilityService.openNotifications()
        _state.update {
            it.copy(
                accessibilityEnabled = enabled,
                message = when {
                    !enabled -> "Enable Gesture Access for swipe-down notifications."
                    !opened -> "Could not open notifications. Re-enable Gesture Access."
                    else -> null
                }
            )
        }
    }

    fun lockScreen() {
        if (NotCustomAccessibilityService.lockScreen()) return
        if (ShizukuManager.hasPermission()) {
            viewModelScope.launch {
                val ok = runCatching { Shell.exec("input keyevent 26") }.isSuccess
                if (!ok) showScreenOffAccessMessage()
            }
        } else {
            showScreenOffAccessMessage()
        }
    }

    private fun showScreenOffAccessMessage() {
        _state.update {
            it.copy(message = "Enable Gesture Access in Home Settings for double-tap screen-off.")
        }
    }

    fun openDrawer() {
        _state.update { it.copy(drawerOpen = true, homeMenuOpen = false) }
    }

    fun closeDrawer() {
        _state.update { it.copy(drawerOpen = false, search = "") }
    }

    fun setSearch(value: String) {
        _state.update { it.copy(search = value) }
    }

    fun showMenu(app: LauncherApp) {
        _state.update { it.copy(menuApp = app, homeMenuOpen = false) }
    }

    fun dismissMenu() {
        _state.update { it.copy(menuApp = null) }
    }

    fun openHomeMenu() {
        _state.update { it.copy(homeMenuOpen = true, menuApp = null) }
    }

    fun closeHomeMenu() {
        _state.update { it.copy(homeMenuOpen = false) }
    }

    fun openSettings() {
        _state.update { it.copy(settingsOpen = true, homeMenuOpen = false, menuApp = null) }
    }

    fun closeSettings() {
        _state.update { it.copy(settingsOpen = false) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun showMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    fun pin(app: LauncherApp) {
        val current = _state.value
        val positions = current.pinPositions.toMutableMap()
        var pageCount = current.pageCount
        var page = current.currentPage.coerceIn(0, pageCount - 1)
        if (app.component !in positions) {
            var target = firstFreeSlot(positions, page, current.config.columns)
            if (target == null) {
                if (pageCount >= MAX_HOME_PAGES) {
                    _state.update {
                        it.copy(message = "No free space. Remove an app or a page first.")
                    }
                    return
                }
                page = pageCount
                pageCount++
                target = firstFreeSlot(positions, page, current.config.columns)
            }
            positions[app.component] = target ?: return
        }
        val (updated, dock) = writePlacements(positions)
        prefs.pageCount = pageCount
        prefs.currentPage = page
        _state.update {
            it.copy(
                pinned = updated,
                dock = dock,
                pinPositions = positions,
                pageCount = pageCount,
                currentPage = page,
                menuApp = null,
                message = "Added to page ${page + 1}"
            )
        }
    }

    fun addPage() {
        val current = _state.value
        if (current.pageCount >= MAX_HOME_PAGES) {
            _state.update { it.copy(message = "Maximum ${MAX_HOME_PAGES} pages.") }
            return
        }
        val pageCount = current.pageCount + 1
        val page = pageCount - 1
        prefs.pageCount = pageCount
        prefs.currentPage = page
        _state.update {
            it.copy(pageCount = pageCount, currentPage = page, message = "Page ${page + 1} added")
        }
    }

    fun setCurrentPage(page: Int) {
        val current = _state.value
        val clamped = page.coerceIn(0, current.pageCount - 1)
        if (clamped == current.currentPage) return
        prefs.currentPage = clamped
        _state.update { it.copy(currentPage = clamped) }
    }

    fun removeCurrentPage() {
        val current = _state.value
        if (current.pageCount <= 1) {
            _state.update { it.copy(message = "Keep at least one page.") }
            return
        }
        val pageSize = current.config.columns * HOME_ROWS
        val start = current.currentPage * pageSize
        val end = start + pageSize
        val pageApps = current.pinPositions.filterValues { it in start until end }.keys
        val shifted = current.pinPositions
            .filterKeys { it !in pageApps }
            .mapValues { (_, slot) -> if (slot >= end) slot - pageSize else slot }
        val pageCount = current.pageCount - 1
        val currentPage = current.currentPage.coerceIn(0, pageCount - 1)
        val (pinned, dock) = writePlacements(shifted)
        prefs.pageCount = pageCount
        prefs.currentPage = currentPage
        _state.update {
            it.copy(
                pinPositions = shifted,
                pinned = pinned,
                dock = dock,
                pageCount = pageCount,
                currentPage = currentPage,
                message = if (pageApps.isEmpty()) "Page removed" else "Page removed; its apps removed from Home"
            )
        }
    }

    fun moveToPage(app: LauncherApp, page: Int) {
        val current = _state.value
        if (page !in 0 until current.pageCount) return
        val positions = current.pinPositions.toMutableMap()
        val target = firstFreeSlot(positions, page, current.config.columns)
        if (target == null) {
            _state.update { it.copy(message = "Page ${page + 1} is full.") }
            return
        }
        positions[app.component] = target
        val (pinned, dock) = writePlacements(positions)
        _state.update {
            it.copy(
                pinned = pinned,
                dock = dock,
                pinPositions = positions,
                currentPage = page,
                menuApp = null,
                message = "Moved to page ${page + 1}"
            )
        }
    }

    fun addToPage(app: LauncherApp, page: Int) {
        val current = _state.value
        if (page !in 0 until current.pageCount) return
        val positions = current.pinPositions.toMutableMap()
        val target = firstFreeSlot(positions, page, current.config.columns)
        if (target == null) {
            _state.update { it.copy(message = "Page ${page + 1} is full.") }
            return
        }
        positions[app.component] = target
        val (updated, dock) = writePlacements(positions)
        prefs.currentPage = page
        _state.update {
            it.copy(
                pinned = updated,
                dock = dock,
                pinPositions = positions,
                currentPage = page,
                menuApp = null,
                message = "Added to page ${page + 1}"
            )
        }
    }

    fun unpin(app: LauncherApp) {
        val positions = _state.value.pinPositions - app.component
        val (pinned, dock) = writePlacements(positions)
        _state.update { it.copy(pinned = pinned, dock = dock, pinPositions = positions, menuApp = null) }
    }

    fun movePinnedToSlot(app: LauncherApp, targetSlot: Int) {
        if (targetSlot !in -MAX_DOCK_ITEMS..MAX_PIN_SLOT) return
        val current = _state.value
        val pageSize = current.config.columns * HOME_ROWS
        val targetPage = if (targetSlot < 0) current.currentPage else targetSlot / pageSize
        if (targetSlot >= 0 &&
            (targetPage >= MAX_HOME_PAGES || targetSlot % pageSize >= current.config.columns * HOME_GRID_ROWS)
        ) return
        val pageCount = if (targetSlot < 0) current.pageCount else maxOf(
            current.pageCount,
            targetPage + 1
        ).coerceAtMost(MAX_HOME_PAGES)
        val positions = current.pinPositions.toMutableMap()
        val sourceSlot = positions[app.component] ?: return
        if (sourceSlot == targetSlot) return
        val occupying = positions.entries.firstOrNull {
            it.key != app.component && it.value == targetSlot
        }?.key
        positions[app.component] = targetSlot
        if (occupying != null) positions[occupying] = sourceSlot
        val (pinned, dock) = writePlacements(positions)
        prefs.pageCount = pageCount
        prefs.currentPage = targetPage
        _state.update {
            it.copy(
                pinned = pinned,
                dock = dock,
                pinPositions = positions,
                pageCount = pageCount,
                currentPage = targetPage
            )
        }
    }

    fun movePinnedToCurrentPage(app: LauncherApp) {
        val current = _state.value
        val target = firstFreeSlot(current.pinPositions, current.currentPage, current.config.columns)
        if (target == null) {
            _state.update { it.copy(message = "This page is full. Add a page or make room.") }
            return
        }
        val positions = current.pinPositions.toMutableMap()
        positions[app.component] = target
        val (pinned, dock) = writePlacements(positions)
        _state.update { it.copy(pinned = pinned, dock = dock, pinPositions = positions) }
    }

    fun addToSlot(app: LauncherApp, targetSlot: Int) {
        val current = _state.value
        if (targetSlot < 0 ||
            targetSlot / (current.config.columns * HOME_ROWS) >= MAX_HOME_PAGES ||
            targetSlot % (current.config.columns * HOME_ROWS) >= current.config.columns * HOME_GRID_ROWS
        ) {
            return
        }
        if (app.component in current.pinPositions) {
            movePinnedToSlot(app, targetSlot)
            return
        }
        val positions = current.pinPositions.toMutableMap()
        val target = freeSlotAtOrAfter(positions, targetSlot, current.config.columns)
        if (target == null) {
            _state.update { it.copy(message = "No free space. Remove an app or a page first.") }
            return
        }
        positions[app.component] = target
        val page = target / (current.config.columns * HOME_ROWS)
        val pageCount = maxOf(current.pageCount, page + 1).coerceAtMost(MAX_HOME_PAGES)
        val (updated, dock) = writePlacements(positions)
        prefs.pageCount = pageCount
        prefs.currentPage = page
        _state.update {
            it.copy(
                pinned = updated,
                dock = dock,
                pinPositions = positions,
                pageCount = pageCount,
                currentPage = page,
                menuApp = null,
                message = "Added to page ${page + 1}"
            )
        }
    }

    fun addToAdjacentPage(app: LauncherApp, direction: Int) {
        if (direction == 0) return
        if (app.component in _state.value.pinPositions) {
            movePinnedToAdjacentPage(app, direction)
            return
        }
        val current = _state.value
        val pageSize = current.config.columns * HOME_ROWS
        val firstPage = (current.currentPage + direction).coerceAtLeast(0)
        var page = firstPage
        var pageCount = current.pageCount
        var target: Int? = null
        while (page in 0 until MAX_HOME_PAGES) {
            if (page >= pageCount) pageCount = page + 1
            target = firstFreeSlot(current.pinPositions, page, current.config.columns)
            if (target != null) break
            page += direction
        }
        if (target == null) {
            _state.update { it.copy(message = "No free space. Remove an app or a page first.") }
            return
        }
        val positions = current.pinPositions.toMutableMap()
        positions[app.component] = target
        val (updated, dock) = writePlacements(positions)
        prefs.pageCount = pageCount
        prefs.currentPage = page
        _state.update {
            it.copy(
                pinned = updated,
                dock = dock,
                pinPositions = positions,
                pageCount = pageCount,
                currentPage = page,
                menuApp = null,
                message = "Added to page ${page + 1}"
            )
        }
    }

    fun movePinnedToAdjacentPage(app: LauncherApp, direction: Int) {
        if (direction == 0) return
        val current = _state.value
        val pageSize = current.config.columns * HOME_ROWS
        val sourceSlot = current.pinPositions[app.component] ?: return
        val sourcePage = if (sourceSlot < 0) current.currentPage else sourceSlot / pageSize
        var page = (sourcePage + direction).coerceAtLeast(0)
        if (page == sourcePage) {
            _state.update { it.copy(message = "Already on the first page.") }
            return
        }
        if (page >= MAX_HOME_PAGES) {
            _state.update { it.copy(message = "Maximum ${MAX_HOME_PAGES} pages.") }
            return
        }
        var pageCount = current.pageCount
        var target: Int? = null
        while (page in 0 until MAX_HOME_PAGES) {
            if (page >= pageCount) pageCount = page + 1
            target = firstFreeSlot(current.pinPositions, page, current.config.columns)
            if (target != null) break
            page += direction
        }
        if (target == null) {
            _state.update { it.copy(message = "No free space on the ${if (direction > 0) "next" else "previous"} page.") }
            return
        }
        val positions = current.pinPositions.toMutableMap()
        positions[app.component] = target
        val (pinned, dock) = writePlacements(positions)
        prefs.pageCount = pageCount
        prefs.currentPage = page
        _state.update {
            it.copy(
                pinned = pinned,
                dock = dock,
                pinPositions = positions,
                pageCount = pageCount,
                currentPage = page,
                message = "Moved to page ${page + 1}"
            )
        }
    }

    private fun freeSlotAtOrAfter(
        positions: Map<ComponentName, Int>,
        startSlot: Int,
        columns: Int
    ): Int? {
        val pageSize = columns * HOME_ROWS
        var page = startSlot / pageSize
        if (page >= MAX_HOME_PAGES) return null
        val used = positions.values.toSet()
        if (startSlot !in used) return startSlot
        val pageEnd = page * pageSize + columns * HOME_GRID_ROWS
        for (slot in startSlot + 1 until pageEnd) {
            if (slot !in used) return slot
        }
        page++
        while (page < MAX_HOME_PAGES) {
            val free = firstFreeSlot(positions, page, columns)
            if (free != null) return free
            page++
        }
        return null
    }

    fun addDock(app: LauncherApp) {
        val occupied = _state.value.pinPositions.values.filter { it < 0 }.toSet()
        val index = (0 until MAX_DOCK_ITEMS).firstOrNull { -(it + 1) !in occupied }
        if (index == null && app.component !in _state.value.pinPositions) {
            _state.update { it.copy(message = "Dock is full. Move an icon out first.") }
            return
        }
        addToDock(app, index ?: 0)
    }

    fun addToDock(app: LauncherApp, dockIndex: Int) {
        if (dockIndex !in 0 until MAX_DOCK_ITEMS) return
        val current = _state.value
        val targetSlot = -(dockIndex + 1)
        if (app.component in current.pinPositions) {
            movePinnedToSlot(app, targetSlot)
            return
        }
        val positions = current.pinPositions.toMutableMap()
        val occupying = positions.entries.firstOrNull { it.value == targetSlot }?.key
        if (occupying != null) {
            val freeDock = (0 until MAX_DOCK_ITEMS)
                .map { -(it + 1) }
                .firstOrNull { it !in positions.values && it != targetSlot }
            val homeSlot = if (freeDock == null) {
                firstFreeSlot(positions, current.currentPage, current.config.columns)
            } else null
            val replacement = freeDock ?: homeSlot
            if (replacement == null) {
                _state.update { it.copy(message = "No free space to move the dock icon.") }
                return
            }
            positions[occupying] = replacement
        }
        positions[app.component] = targetSlot
        val (pinned, dock) = writePlacements(positions)
        _state.update { it.copy(pinned = pinned, dock = dock, pinPositions = positions, menuApp = null) }
    }

    fun removeDock(app: LauncherApp) = unpin(app)

    fun moveDock(app: LauncherApp, target: LauncherApp) {
        val targetSlot = _state.value.pinPositions[target.component] ?: return
        if (targetSlot < 0) movePinnedToSlot(app, targetSlot)
    }

    fun movePinnedToDock(app: LauncherApp) {
        val current = _state.value
        if ((current.pinPositions[app.component] ?: 0) < 0) return
        val occupied = current.pinPositions.values.filter { it < 0 }.toSet()
        val dockSlot = (-1 downTo -MAX_DOCK_ITEMS).firstOrNull { it !in occupied }
        if (dockSlot == null) {
            _state.update { it.copy(message = "Dock is full. Move an icon out first.") }
            return
        }
        movePinnedToSlot(app, dockSlot)
    }

    fun moveDockToHomeSlot(app: LauncherApp, slot: Int) = movePinnedToSlot(app, slot)

    fun hide(app: LauncherApp) {
        val hidden = _state.value.hidden + app.packageName
        val pinPositions = _state.value.pinPositions.filterKeys { it.packageName != app.packageName }
        prefs.hidden = hidden
        val (pinned, dock) = writePlacements(pinPositions)
        _state.update {
            it.copy(hidden = hidden, pinned = pinned, pinPositions = pinPositions, dock = dock, menuApp = null)
        }
    }

    fun unhide(packageName: String) {
        val hidden = _state.value.hidden - packageName
        prefs.hidden = hidden
        _state.update { it.copy(hidden = hidden) }
    }

    fun appInfo(app: LauncherApp) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${app.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
        dismissMenu()
    }

    fun uninstall(app: LauncherApp) {
        val intent = Intent(
            Intent.ACTION_DELETE,
            Uri.parse("package:${app.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
        dismissMenu()
    }

    fun setColumns(value: Int) {
        prefs.columns = value
        val current = _state.value
        val config = prefs.config()
        val pageCount = maxOf(
            current.pageCount,
            neededPages(current.pinPositions, config.columns)
        ).coerceAtMost(MAX_HOME_PAGES)
        val currentPage = current.currentPage.coerceIn(0, pageCount - 1)
        prefs.pageCount = pageCount
        prefs.currentPage = currentPage
        _state.update {
            it.copy(
                config = config,
                pageCount = pageCount,
                currentPage = currentPage
            )
        }
    }

    fun setIconSize(value: Int) {
        prefs.iconSize = value
        refreshConfig()
    }

    fun setShowLabels(value: Boolean) {
        prefs.showLabels = value
        refreshConfig()
    }

    fun setShowSearch(value: Boolean) {
        prefs.showSearch = value
        refreshConfig()
    }

    fun setUse24HourClock(value: Boolean) {
        prefs.use24HourClock = value
        refreshConfig()
    }

    fun setAtAGlanceStyle(value: AtAGlanceStyle) {
        prefs.atAGlanceStyle = value
        refreshConfig()
    }

    fun setMonochrome(value: Boolean) {
        prefs.monochrome = value
        refreshConfig()
    }

    fun setDrawerMode(value: DrawerMode) {
        prefs.drawerMode = value
        _state.update { it.copy(drawerMode = value) }
    }

    fun setIconPack(packageName: String?) {
        val selected = packageName?.takeIf { candidate ->
            _state.value.iconPacks.any { it.packageName == candidate }
        }
        prefs.iconPack = selected
        icons.setSelectedPack(selected)
        _state.update { it.copy(iconPack = selected) }
        val apps = _state.value.apps
        val byComponent = apps.associateBy { it.component }
        val preload = ((_state.value.pinned + _state.value.dock).mapNotNull { byComponent[it] } + apps)
            .distinctBy { it.key }
        viewModelScope.launch { preload.forEach { icons.load(it) } }
    }

    fun startWidgets() {
        runCatching { widgetHost.startListening() }
    }

    fun stopWidgets() {
        runCatching { widgetHost.stopListening() }
    }

    fun openWidgetPicker() {
        _state.update {
            it.copy(widgetPickerOpen = true, homeMenuOpen = false, settingsOpen = false)
        }
    }

    fun closeWidgetPicker() {
        _state.update { it.copy(widgetPickerOpen = false) }
    }

    fun widgetChoices(): List<WidgetChoice> {
        val context = getApplication<Application>()
        val pm = context.packageManager
        return runCatching {
            widgetManager.getInstalledProviders().mapNotNull { info ->
                val appLabel = runCatching {
                    pm.getApplicationLabel(
                        pm.getApplicationInfo(info.provider.packageName, 0)
                    ).toString()
                }.getOrDefault(info.provider.packageName)
                val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                    ?: appLabel
                val cols = (info.minWidth / 70f).roundToInt().coerceAtLeast(1)
                val rows = (info.minHeight / 70f).roundToInt().coerceAtLeast(1)
                WidgetChoice(info, appLabel, label, "$cols×$rows")
            }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
        }.getOrDefault(emptyList())
    }

    fun prepareWidget(info: AppWidgetProviderInfo): Intent? {
        val id = runCatching { widgetHost.allocateAppWidgetId() }.getOrNull()
        if (id == null) {
            _state.update { it.copy(message = "Could not allocate a widget slot.") }
            return null
        }
        val allowed = runCatching {
            widgetManager.bindAppWidgetIdIfAllowed(id, info.provider)
        }.getOrDefault(false)
        if (!allowed) {
            runCatching { widgetHost.deleteAppWidgetId(id) }
            _state.update {
                it.copy(
                    widgetPickerOpen = false,
                    message = "Set NotCustom Home as your home app, then add the widget again."
                )
            }
            return null
        }
        if (info.configure != null) {
            pendingConfigId = id
            _state.update { it.copy(widgetPickerOpen = false) }
            return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(info.configure)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        }
        commitWidget(id)
        return null
    }

    fun onWidgetConfigured(resultCode: Int) {
        val id = pendingConfigId ?: return
        pendingConfigId = null
        if (resultCode == Activity.RESULT_OK) {
            commitWidget(id)
        } else {
            runCatching { widgetHost.deleteAppWidgetId(id) }
        }
    }

    fun onWidgetConfigFailed() {
        pendingConfigId?.let { runCatching { widgetHost.deleteAppWidgetId(it) } }
        pendingConfigId = null
        _state.update { it.copy(message = "Could not configure that widget.") }
    }

    private fun commitWidget(id: Int) {
        val updated = (_state.value.widgets + id).distinct()
        prefs.widgets = updated
        _state.update { it.copy(widgets = updated, widgetPickerOpen = false) }
    }

    fun removeWidget(id: Int) {
        runCatching { widgetHost.deleteAppWidgetId(id) }
        val updated = _state.value.widgets - id
        prefs.widgets = updated
        _state.update { it.copy(widgets = updated) }
    }

    fun clearWidgets() {
        _state.value.widgets.forEach { runCatching { widgetHost.deleteAppWidgetId(it) } }
        prefs.widgets = emptyList()
        _state.update { it.copy(widgets = emptyList()) }
    }

    fun widgetLabel(id: Int): String = runCatching {
        widgetManager.getAppWidgetInfo(id)
            ?.loadLabel(getApplication<Application>().packageManager)
            ?.toString()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Widget"

    fun resetLayout() {
        prefs.clearLayout()
        val apps = _state.value.apps
        if (apps.isNotEmpty()) applyDefaults(apps)
        val pinPositions = normalizePinPositions(prefs.pinned + prefs.dock)
        val pinned = pinPositions.entries.sortedBy { it.value }.map { it.key }
        prefs.pageCount = 1
        prefs.currentPage = 0
        _state.update {
            it.copy(
                pinned = pinned,
                pinPositions = pinPositions,
                pageCount = 1,
                currentPage = 0,
                dock = pinPositions.entries.filter { it.value < 0 }
                    .sortedByDescending { it.value }.map { it.key },
                hidden = prefs.hidden,
                config = prefs.config()
            )
        }
    }

    private fun refreshConfig() {
        _state.update { it.copy(config = prefs.config()) }
    }

    private fun loadWidgets(): List<Int> {
        val ids = prefs.widgets.filter { widgetManager.getAppWidgetInfo(it) != null }
        if (ids != prefs.widgets) prefs.widgets = ids
        return ids
    }

    private fun firstFreeSlot(
        positions: Map<ComponentName, Int>,
        page: Int,
        columns: Int
    ): Int? {
        val pageSize = columns * HOME_ROWS
        val start = page * pageSize
        val used = positions.values.toSet()
        return (start until start + columns * HOME_GRID_ROWS).firstOrNull { it !in used }
    }

    private fun writePlacements(
        positions: Map<ComponentName, Int>
    ): Pair<List<ComponentName>, List<ComponentName>> {
        val home = positions.entries.filter { it.value >= 0 }.sortedBy { it.value }.map { it.key }
        val dock = positions.entries.filter { it.value < 0 }
            .sortedByDescending { it.value }.map { it.key }
        prefs.pinned = home
        prefs.dock = dock
        prefs.pinPositions = positions
        return positions.entries.sortedBy { it.value }.map { it.key } to dock
    }

    private fun neededPages(positions: Map<ComponentName, Int>, columns: Int): Int {
        val highest = positions.values.filter { it >= 0 }.maxOrNull() ?: -1
        if (highest < 0) return 1
        val pageSize = columns * HOME_ROWS
        return ((highest + 1) + pageSize - 1) / pageSize
    }

    private fun applyDefaults(apps: List<LauncherApp>) {
        val byPackage = apps.groupBy { it.packageName }
        fun pick(candidates: List<String>): List<LauncherApp> =
            candidates.flatMap { pkg -> byPackage[pkg]?.take(1).orEmpty() }

        val dock = pick(DOCK_CANDIDATES).distinctBy { it.component }.take(4).toMutableList()
        if (dock.size < 4) {
            apps.filter { app -> dock.none { it.component == app.component } }
                .take(4 - dock.size)
                .forEach { dock.add(it) }
        }
        val dockSet = dock.map { it.component }.toSet()
        val pinned = pick(PINNED_CANDIDATES)
            .filter { it.component !in dockSet }
            .distinctBy { it.component }
            .take(12)
            .toMutableList()
        if (pinned.isEmpty()) {
            apps.filter { it.component !in dockSet }.take(8).forEach { pinned.add(it) }
        }
        prefs.dock = dock.map { it.component }
        prefs.pinned = pinned.map { it.component }
        prefs.pinPositions = emptyMap()
        prefs.initialized = true
    }

    private fun normalizePinPositions(pinned: List<ComponentName>): Map<ComponentName, Int> {
        val columns = prefs.columns
        val pageSize = columns * HOME_ROWS
        val homeCapacity = columns * HOME_GRID_ROWS
        val stored = prefs.pinPositions.filter { (component, slot) ->
            component in pinned && slot in -MAX_DOCK_ITEMS..MAX_PIN_SLOT
        }
        val result = LinkedHashMap<ComponentName, Int>()
        val used = mutableSetOf<Int>()
        val legacyDock = prefs.dock.filter { it in pinned }.take(MAX_DOCK_ITEMS)
        val alreadyDocked = stored.filterValues { it < 0 }.entries
            .sortedByDescending { it.value }
            .map { it.key }
        val oldBottomRow = if (legacyDock.isEmpty() && alreadyDocked.isEmpty()) {
            stored.entries
                .filter { it.value in (homeCapacity until pageSize) }
                .sortedBy { it.value }
                .map { it.key }
                .take(MAX_DOCK_ITEMS)
        } else emptyList()
        val dockOrder = (legacyDock + alreadyDocked + oldBottomRow).distinct().take(MAX_DOCK_ITEMS)
        dockOrder.forEachIndexed { index, component ->
            val slot = -(index + 1)
            result[component] = slot
            used += slot
        }

        fun firstFreeHomeSlot(startPage: Int): Int? {
            for (page in startPage until MAX_HOME_PAGES) {
                val start = page * pageSize
                val free = (start until start + homeCapacity).firstOrNull { it !in used }
                if (free != null) return free
            }
            return null
        }

        val dockSet = dockOrder.toSet()
        val overflow = mutableListOf<Pair<ComponentName, Int>>()
        stored.entries.sortedBy { it.value }.forEach { (component, slot) ->
            if (component in dockSet || slot < 0) return@forEach
            val page = slot / pageSize
            val pageOffset = slot % pageSize
            if (pageOffset < homeCapacity && used.add(slot)) {
                result[component] = slot
            } else {
                overflow += component to page
            }
        }
        overflow.forEach { (component, page) ->
            firstFreeHomeSlot(page)?.let { slot -> result[component] = slot; used += slot }
        }
        pinned.filter { it !in result }.forEach { component ->
            firstFreeHomeSlot(0)?.let { slot -> result[component] = slot; used += slot }
        }
        val home = result.entries.filter { it.value >= 0 }.sortedBy { it.value }.map { it.key }
        val dock = result.entries.filter { it.value < 0 }
            .sortedByDescending { it.value }.map { it.key }
        prefs.pinned = home
        prefs.dock = dock
        prefs.pinPositions = result
        return result
    }

    private fun loadApps(): List<LauncherApp> {
        val launcherApps = getApplication<Application>()
            .getSystemService(LauncherApps::class.java)
        val collator = Collator.getInstance()
        return launcherApps
            .getActivityList(null, Process.myUserHandle())
            .mapNotNull { info ->
                val component = info.componentName ?: return@mapNotNull null
                val label = info.label?.toString()?.takeIf { it.isNotBlank() }
                    ?: component.packageName
                LauncherApp(component, label, component.packageName)
            }
            .distinctBy { it.component }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    companion object {
        private const val WIDGET_HOST_ID = 1024
        private const val MAX_PIN_SLOT = HOME_ROWS * 6 * MAX_HOME_PAGES - 1

        private val DOCK_CANDIDATES = listOf(
            "com.google.android.dialer",
            "com.android.dialer",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.google.android.GoogleCamera",
            "com.nothing.camera",
            "com.android.camera2"
        )

        private val PINNED_CANDIDATES = listOf(
            "com.android.settings",
            "com.android.vending",
            "com.google.android.apps.photos",
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.google.android.calculator",
            "com.android.calculator2",
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.google.android.gm",
            "com.google.android.youtube",
            "com.google.android.apps.maps"
        )
    }
}
