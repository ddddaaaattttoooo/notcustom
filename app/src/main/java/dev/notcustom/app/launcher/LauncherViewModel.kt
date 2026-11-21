package dev.notcustom.app.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.view.View
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import dev.notcustom.app.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.notcustom.app.core.Shell
import dev.notcustom.app.core.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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
    val widgetSizes: Map<Int, WidgetSize> = emptyMap(),
    val widgetPlacements: Map<Int, WidgetPlacement> = emptyMap(),
    val folders: Map<String, HomeFolder> = emptyMap(),
    val pinnedShortcuts: List<LauncherApp> = emptyList(),
    val openFolder: String? = null,
    val widgetPickerOpen: Boolean = false,
    val widgetPickerPackage: String? = null,
    val widgetConfigRequest: Intent? = null,
    val iconRevision: Int = 0,
    val accessibilityEnabled: Boolean = false,
    val notificationDotsEnabled: Boolean = false,
    val returning: ReturnTransition? = null,
    val update: UpdateState = UpdateState(),
    val message: String? = null
)

data class UpdateState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val checked: Boolean = false,
    val available: Boolean = false,
    val latest: String? = null,
    val apkUrl: String? = null,
    val message: String? = null
)

private data class ReleaseUpdate(val version: String, val apkUrl: String)

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
    private val launcherApps = application.getSystemService(LauncherApps::class.java)
    private val _state = MutableStateFlow(LauncherState())
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    private var awaitingReturn = false
    private var lastLaunch: LaunchRecord? = null
    private var pendingConfigId: Int? = null
    private var pendingBindId: Int? = null
    private var reloadJob: Job? = null
    private var pendingPinRequest: Intent? = null

    private val appCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = scheduleReload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = scheduleReload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = scheduleReload()
        override fun onPackagesAvailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) = scheduleReload()
        override fun onPackagesUnavailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) = scheduleReload()
    }

    private data class LaunchRecord(
        val app: LauncherApp,
        val bounds: Rect?,
        val fromHome: Boolean
    )

    init {
        runCatching { launcherApps.registerCallback(appCallback) }
        refreshAccessibility()
        reload()
        viewModelScope.launch {
            delay(4000)
            checkForUpdates()
        }
    }

    override fun onCleared() {
        runCatching { launcherApps.unregisterCallback(appCallback) }
        super.onCleared()
    }

    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(250)
            icons.clear()
            reload()
        }
    }

    fun reload() {
        viewModelScope.launch {
            val appsJob = async(Dispatchers.IO) { loadApps() }
            val shortcutsJob = async(Dispatchers.IO) { loadPinnedShortcuts() }
            val iconPacksJob = async(Dispatchers.IO) { icons.availablePacks() }
            val apps = appsJob.await()
            val shortcuts = shortcutsJob.await()
            val iconPacks = iconPacksJob.await()
            val iconPack = prefs.iconPack?.takeIf { selected ->
                iconPacks.any { it.packageName == selected }
            }
            prefs.iconPack = iconPack
            icons.setSelectedPack(iconPack)
            icons.setThemed(prefs.themedIcons)
            val widgets = loadWidgets()
            val widgetSizes = syncWidgetSizes(widgets, prefs.columns)
            val widgetPlacements = syncWidgetPlacements(widgets, widgetSizes, prefs.columns, prefs.pinPositions)
            if (apps.isNotEmpty()) {
                if (!prefs.initialized) applyDefaults(apps)
                val hidden = prefs.hidden
                val validApps = apps.filter { it.packageName !in hidden }.map { it.component }.toSet()
                val folders = cleanFolders(validApps)
                val valid = validApps + shortcuts.map { it.component } +
                    folders.map { folderComponent(it.id) }
                val pinned = prefs.pinned.filter { it in valid }
                val dock = prefs.dock.filter { it in valid }
                val allPinned = (pinned + dock).distinct()
                val pinPositions = normalizePinPositions(allPinned)
                val unifiedPinned = pinPositions.entries.sortedBy { it.value }.map { it.key }
                val config = prefs.config()
                val pageCount = maxOf(
                    prefs.pageCount,
                    neededPages(pinPositions, config.columns),
                    (widgetPlacements.values.maxOfOrNull { it.page + 1 } ?: 1),
                    1
                ).coerceAtMost(MAX_HOME_PAGES)
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
                        folders = folders.associateBy { f -> f.id },
                        pinnedShortcuts = shortcuts,
                        openFolder = it.openFolder?.takeIf { id -> folders.any { f -> f.id == id } },
                        widgets = widgets,
                        widgetSizes = widgetSizes,
                        widgetPlacements = widgetPlacements,
                        config = config,
                        drawerMode = prefs.drawerMode,
                        iconPacks = iconPacks,
                        iconPack = iconPack,
                        iconRevision = it.iconRevision + 1
                    )
                }
                val byComponent = primaryByComponent(apps)
                val preload = ((unifiedPinned).mapNotNull { byComponent[it] } + apps)
                    .distinctBy { it.key }
                preload.forEach { viewModelScope.launch { icons.load(it) } }
                pendingPinRequest?.let { pending ->
                    pendingPinRequest = null
                    handlePinRequest(pending)
                }
            } else {
                _state.update {
                    it.copy(
                        loading = false,
                        widgets = widgets,
                        widgetSizes = widgetSizes,
                        widgetPlacements = widgetPlacements,
                        iconPacks = iconPacks,
                        iconPack = iconPack,
                        iconRevision = it.iconRevision + 1
                    )
                }
            }
        }
    }

    private val shortcutIcons = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

    suspend fun loadIcon(app: LauncherApp): ImageBitmap? = when {
        app.folderId != null -> null
        app.shortcutId != null -> shortcutIcons[app.key]
            ?: loadShortcutIcon(app)?.also { shortcutIcons[app.key] = it }
        else -> icons.load(app)
    }

    fun cachedIcon(app: LauncherApp): ImageBitmap? =
        if (app.isApp) icons.peek(app.key) else shortcutIcons[app.key]

    private suspend fun loadShortcutIcon(app: LauncherApp): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val id = app.shortcutId ?: return@withContext null
            runCatching {
                val query = LauncherApps.ShortcutQuery()
                    .setPackage(app.packageName)
                    .setShortcutIds(listOf(id))
                    .setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                    )
                val info = launcherApps.getShortcuts(query, app.user)?.firstOrNull()
                    ?: return@runCatching null
                val density = getApplication<Application>().resources.displayMetrics.densityDpi
                launcherApps.getShortcutBadgedIconDrawable(info, density)
                    ?.toBitmap(168, 168)
                    ?.asImageBitmap()
            }.getOrNull()
        }

    private fun shortcutItem(info: ShortcutInfo) = LauncherApp(
        component = shortcutComponent(info.`package`, info.id),
        label = info.shortLabel?.toString()?.takeIf { it.isNotBlank() }
            ?: info.longLabel?.toString()?.takeIf { it.isNotBlank() }
            ?: info.`package`,
        packageName = info.`package`,
        user = info.userHandle,
        shortcutId = info.id
    )

    /** Shortcuts this launcher pinned; only works while NotCustom is the default Home. */
    private fun loadPinnedShortcuts(): List<LauncherApp> = runCatching {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty()
            .filter { it.isEnabled }
            .map(::shortcutItem)
    }.getOrDefault(emptyList())

    /** Drops vanished apps from folders; a folder left with one app turns back into that app. */
    private fun cleanFolders(validApps: Set<ComponentName>): List<HomeFolder> {
        val saved = prefs.folders
        val positions = prefs.pinPositions.toMutableMap()
        var positionsChanged = false
        val kept = mutableListOf<HomeFolder>()
        saved.forEach { folder ->
            val apps = folder.apps.filter { it in validApps }.distinct()
            val component = folderComponent(folder.id)
            val slot = positions[component] ?: return@forEach
            if (apps.size >= 2) {
                kept += folder.copy(apps = apps)
            } else {
                positions.remove(component)
                apps.firstOrNull()?.let { if (it !in positions) positions[it] = slot }
                positionsChanged = true
            }
        }
        if (kept != saved) prefs.folders = kept
        if (positionsChanged) writePlacements(positions)
        return kept
    }

    fun openFolder(id: String) {
        _state.update { it.copy(openFolder = id, menuApp = null, homeMenuOpen = false) }
    }

    fun closeFolder() {
        _state.update { it.copy(openFolder = null) }
    }

    fun renameFolder(id: String, name: String) {
        val folder = _state.value.folders[id] ?: return
        val clean = name.trim().take(40).ifEmpty { "Folder" }
        if (clean == folder.name) return
        val folders = _state.value.folders + (id to folder.copy(name = clean))
        prefs.folders = folders.values.toList()
        _state.update { it.copy(folders = folders) }
    }

    fun appFor(component: ComponentName): LauncherApp? =
        _state.value.apps.firstOrNull { it.component == component && it.user == Process.myUserHandle() }
            ?: _state.value.apps.firstOrNull { it.component == component }

    fun folderOf(app: LauncherApp): HomeFolder? =
        _state.value.folders.values.firstOrNull { app.component in it.apps }

    /** Takes an app out of its folder without placing it; a folder left with one app dissolves. */
    fun extractFromFolder(app: LauncherApp) {
        val current = _state.value
        val folder = folderOf(app) ?: return
        val folderComp = folderComponent(folder.id)
        val positions = current.pinPositions.toMutableMap()
        val remaining = folder.apps - app.component
        val folders = current.folders.toMutableMap()
        if (remaining.size >= 2) {
            folders[folder.id] = folder.copy(apps = remaining)
        } else {
            folders.remove(folder.id)
            val folderSlot = positions.remove(folderComp)
            val last = remaining.firstOrNull()
            if (last != null && folderSlot != null) positions[last] = folderSlot
        }
        prefs.folders = folders.values.toList()
        val (pinned, dock) = writePlacements(positions)
        _state.update {
            it.copy(folders = folders, pinned = pinned, dock = dock, pinPositions = positions)
        }
    }

    /** Moves an app out of its folder to the first free slot on the current page. */
    fun removeFromFolder(app: LauncherApp) {
        val current = _state.value
        val folder = folderOf(app) ?: return
        val folderComp = folderComponent(folder.id)
        val folderSlot = current.pinPositions[folderComp] ?: return
        val positions = current.pinPositions.toMutableMap()
        val remaining = folder.apps - app.component
        val folders = current.folders.toMutableMap()
        if (remaining.size >= 2) {
            folders[folder.id] = folder.copy(apps = remaining)
        } else {
            folders.remove(folder.id)
            positions.remove(folderComp)
            remaining.firstOrNull()?.let { positions[it] = folderSlot }
        }
        if (app.component !in positions) {
            val page = current.currentPage
            val pageSize = current.config.columns * HOME_ROWS
            val slot = freeSlotAtOrAfter(positions, page * pageSize, current.config.columns)
            if (slot == null) {
                _state.update { it.copy(message = "No free space on Home for ${app.label}.") }
                return
            }
            positions[app.component] = slot
        }
        prefs.folders = folders.values.toList()
        val (pinned, dock) = writePlacements(positions)
        _state.update {
            it.copy(
                folders = folders,
                pinned = pinned,
                dock = dock,
                pinPositions = positions,
                menuApp = null,
                openFolder = it.openFolder?.takeIf { id -> id in folders }
            )
        }
    }

    /**
     * Standard launcher drop: an app dropped on an app makes a folder; on a folder it joins it.
     * Returns false when the drop is not a merge (caller falls back to its move/swap).
     */
    private fun mergeIntoFolder(app: LauncherApp, targetSlot: Int): Boolean {
        if (!app.isApp) return false
        val current = _state.value
        val occupant = current.pinPositions.entries
            .firstOrNull { it.value == targetSlot && it.key != app.component }?.key ?: return false
        if (occupant.isShortcut) return false
        val positions = current.pinPositions.toMutableMap()
        positions.remove(app.component)
        val folders = current.folders.toMutableMap()
        if (occupant.isFolder) {
            val existing = folders[occupant.className] ?: return false
            folders[existing.id] = existing.copy(apps = (existing.apps + app.component).distinct())
        } else {
            val other = current.apps.firstOrNull { it.component == occupant }
            val id = java.util.UUID.randomUUID().toString().take(8)
            val name = if (other != null && other.category == app.category &&
                app.category != "Other"
            ) app.category else "Folder"
            folders[id] = HomeFolder(id, name, listOf(occupant, app.component))
            positions.remove(occupant)
            positions[folderComponent(id)] = targetSlot
        }
        prefs.folders = folders.values.toList()
        val (pinned, dock) = writePlacements(positions)
        _state.update {
            it.copy(folders = folders, pinned = pinned, dock = dock, pinPositions = positions, menuApp = null)
        }
        return true
    }

    fun launch(
        app: LauncherApp,
        sourceBounds: Rect? = null,
        fromHome: Boolean = false,
        sourceView: View? = null
    ) {
        app.folderId?.let { openFolder(it); return }
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
        val shortcutId = app.shortcutId
        var launched = runCatching {
            if (shortcutId != null) {
                launcherApps.startShortcut(app.packageName, shortcutId, bounds, opts, app.user)
            } else {
                launcherApps.startMainActivity(app.component, app.user, bounds, opts)
            }
        }.isSuccess
        if (shortcutId != null && !launched) {
            _state.update { it.copy(message = "Could not open that shortcut.") }
        }
        if (!launched && shortcutId == null && app.user == Process.myUserHandle()) {
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
        _state.update {
            it.copy(drawerOpen = false, menuApp = null, search = "", homeMenuOpen = false, openFolder = null)
        }
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
        val dots = NotificationDotService.isEnabled(getApplication())
        _state.update { it.copy(accessibilityEnabled = enabled, notificationDotsEnabled = dots) }
    }

    fun openAppInfo() {
        val context = getApplication<Application>()
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openNotificationDotSettings() {
        val context = getApplication<Application>()
        runCatching { context.startActivity(NotificationDotService.settingsIntent(context)) }
            .onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
    }

    fun openAccessibilitySettings() {
        NotCustomAccessibilityService.openAccessibilitySettings(getApplication())
    }

    fun pullDownFromDrawer() {
        _state.update { it.copy(drawerOpen = false, search = "") }
    }

    fun openNotificationsFromHome() {
        val enabled = NotCustomAccessibilityService.isEnabled(getApplication())
        val opened = (enabled && NotCustomAccessibilityService.openNotifications()) ||
            expandStatusBar()
        _state.update {
            it.copy(
                accessibilityEnabled = enabled,
                message = if (opened) null else "Could not open notifications. Enable Gesture Access."
            )
        }
    }

    /** Same call stock launchers use; needs only the normal EXPAND_STATUS_BAR permission. */
    private fun expandStatusBar(): Boolean = runCatching {
        val statusBar = getApplication<Application>().getSystemService("statusbar") ?: return false
        statusBar.javaClass.getMethod("expandNotificationsPanel").invoke(statusBar)
        true
    }.getOrDefault(false)

    /** Home button: close overlays; a second press while already on Home goes to page 1. */
    fun onHomePressed(alreadyOnHome: Boolean) {
        val current = _state.value
        val overlayOpen = current.drawerOpen || current.menuApp != null || current.homeMenuOpen ||
            current.settingsOpen || current.widgetPickerOpen || current.openFolder != null
        _state.update {
            it.copy(
                drawerOpen = false,
                search = "",
                menuApp = null,
                homeMenuOpen = false,
                settingsOpen = false,
                widgetPickerOpen = false,
                widgetPickerPackage = null,
                openFolder = null
            )
        }
        if (alreadyOnHome && !overlayOpen) setCurrentPage(0)
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
        val removedPage = current.currentPage
        val start = removedPage * pageSize
        val end = start + pageSize
        val pageApps = current.pinPositions.filterValues { it in start until end }.keys
        val shifted = current.pinPositions
            .filterKeys { it !in pageApps }
            .mapValues { (_, slot) -> if (slot >= end) slot - pageSize else slot }
        val removedWidgets = current.widgets.filter {
            current.widgetPlacements[it]?.page == removedPage
        }
        removedWidgets.forEach { runCatching { widgetHost.deleteAppWidgetId(it) } }
        val widgets = current.widgets - removedWidgets.toSet()
        val widgetSizes = current.widgetSizes - removedWidgets.toSet()
        val placements = current.widgetPlacements
            .filterKeys { it !in removedWidgets }
            .mapValues { (_, placement) ->
                if (placement.page > removedPage) placement.copy(page = placement.page - 1)
                else placement
            }
        val pageCount = current.pageCount - 1
        val currentPage = current.currentPage.coerceIn(0, pageCount - 1)
        // Folders on the removed page go with it; otherwise their apps stay stuck "in" a hidden folder.
        val folders = current.folders.filterKeys { folderComponent(it) !in pageApps }
        if (folders.size != current.folders.size) prefs.folders = folders.values.toList()
        val (pinned, dock) = writePlacements(shifted)
        prefs.pageCount = pageCount
        prefs.currentPage = currentPage
        prefs.widgets = widgets
        prefs.widgetSizes = widgetSizes
        prefs.widgetPlacements = placements
        _state.update {
            it.copy(
                pinPositions = shifted,
                folders = folders,
                openFolder = it.openFolder?.takeIf { id -> id in folders },
                pinned = pinned,
                dock = dock,
                pageCount = pageCount,
                currentPage = currentPage,
                widgets = widgets,
                widgetSizes = widgetSizes,
                widgetPlacements = placements,
                message = when {
                    pageApps.isEmpty() && removedWidgets.isEmpty() -> "Page removed"
                    removedWidgets.isEmpty() -> "Page removed; its apps removed from Home"
                    pageApps.isEmpty() -> "Page removed; its widgets removed from Home"
                    else -> "Page removed; its apps and widgets removed from Home"
                }
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
        val current = _state.value
        val positions = current.pinPositions - app.component
        val folders = app.folderId?.let { current.folders - it } ?: current.folders
        if (app.folderId != null) prefs.folders = folders.values.toList()
        val shortcuts = if (app.shortcutId != null) {
            val remaining = current.pinnedShortcuts.filter { it.component != app.component }
            runCatching {
                launcherApps.pinShortcuts(
                    app.packageName,
                    remaining.filter { it.packageName == app.packageName }.mapNotNull { it.shortcutId },
                    app.user
                )
            }
            remaining
        } else current.pinnedShortcuts
        val (pinned, dock) = writePlacements(positions)
        _state.update {
            it.copy(
                pinned = pinned,
                dock = dock,
                pinPositions = positions,
                folders = folders,
                pinnedShortcuts = shortcuts,
                openFolder = it.openFolder?.takeIf { id -> id in folders },
                menuApp = null
            )
        }
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
        if (targetSlot >= 0 && slotHasWidget(targetSlot, current.config.columns)) {
            _state.update { it.copy(message = "That space is taken by a widget.") }
            return
        }
        val occupying = positions.entries.firstOrNull {
            it.key != app.component && it.value == targetSlot
        }?.key
        if (occupying != null && mergeIntoFolder(app, targetSlot)) return
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
        if (slotHasWidget(targetSlot, current.config.columns)) {
            _state.update { it.copy(message = "That space is taken by a widget.") }
            return
        }
        val positions = current.pinPositions.toMutableMap()
        val occupying = positions.entries.firstOrNull {
            it.key != app.component && it.value == targetSlot
        }?.key
        if (occupying != null && mergeIntoFolder(app, targetSlot)) return
        if (occupying != null) {
            val freeForOccupant = freeSlotAtOrAfter(
                positions.filterValues { it != targetSlot },
                targetSlot + 1,
                current.config.columns
            )
            if (freeForOccupant == null) {
                _state.update { it.copy(message = "No free space. Remove an app or a page first.") }
                return
            }
            positions[occupying] = freeForOccupant
        }
        val target = if (occupying == null) {
            freeSlotAtOrAfter(positions, targetSlot, current.config.columns)
        } else {
            targetSlot
        }
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
        if (startSlot !in used && !slotHasWidget(startSlot, columns)) return startSlot
        val pageEnd = page * pageSize + columns * HOME_GRID_ROWS
        for (slot in startSlot + 1 until pageEnd) {
            if (slot !in used && !slotHasWidget(slot, columns)) return slot
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
        if (slotHasWidget(targetSlot, current.config.columns)) {
            _state.update { it.copy(message = "That space is taken by a widget.") }
            return
        }
        val positions = current.pinPositions.toMutableMap()
        val occupying = positions.entries.firstOrNull { it.value == targetSlot }?.key
        if (occupying != null && mergeIntoFolder(app, targetSlot)) return
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
        if (!canUninstall(app)) {
            appInfo(app)
            return
        }
        val intent = Intent(
            Intent.ACTION_DELETE,
            Uri.parse("package:${app.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Intent.EXTRA_USER, app.user)
        val opened = runCatching {
            getApplication<Application>().startActivity(intent)
        }.isSuccess
        if (!opened) {
            _state.update { it.copy(message = "Could not open the uninstaller.") }
        }
        dismissMenu()
    }

    fun canUninstall(app: LauncherApp): Boolean {
        if (!app.isApp) return false
        if (app.user != Process.myUserHandle()) return false
        val context = getApplication<Application>()
        val info = runCatching {
            context.packageManager.getApplicationInfo(app.packageName, 0)
        }.getOrNull() ?: return false
        if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return false
        if ((info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) return false
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (dpm?.activeAdmins?.any { it.packageName == app.packageName } == true) return false
        return true
    }

    fun checkForUpdates(force: Boolean = false) {
        val update = _state.value.update
        if (update.checking || update.downloading) return
        if (update.checked && !force) return
        _state.update { it.copy(update = it.update.copy(checking = true, message = null)) }
        viewModelScope.launch {
            val context = getApplication<Application>()
            val current = installedVersion(context)
            val release = withContext(Dispatchers.IO) { fetchLatestRelease() }
            if (release == null) {
                _state.update {
                    it.copy(
                        update = it.update.copy(
                            checking = false,
                            checked = true,
                            message = "Could not check GitHub or GitLab for updates."
                        )
                    )
                }
                return@launch
            }
            val latest = release.version.removePrefix("v").removePrefix("V")
            val newer = isNewerVersion(latest, current)
            _state.update {
                it.copy(
                    update = UpdateState(
                        checking = false,
                        checked = true,
                        available = newer,
                        latest = latest,
                        apkUrl = release.apkUrl,
                        message = if (newer) null else "NotCustom is up to date."
                    )
                )
            }
        }
    }

    fun installUpdate() {
        val update = _state.value.update
        val url = update.apkUrl
        if (url.isNullOrBlank() || update.downloading) return
        _state.update { it.copy(update = it.update.copy(downloading = true, message = null)) }
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { downloadApk(url) }
            val context = getApplication<Application>()
            if (file == null) {
                _state.update {
                    it.copy(
                        update = it.update.copy(
                            downloading = false,
                            message = "Update download failed. Try again."
                        )
                    )
                }
                return@launch
            }
            val launched = runCatching {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.updates",
                    file
                )
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                )
            }.isSuccess
            _state.update {
                it.copy(
                    update = it.update.copy(
                        downloading = false,
                        message = if (launched) {
                            "Follow the installer prompt to update."
                        } else {
                            "Allow NotCustom to install unknown apps, then try again."
                        }
                    )
                )
            }
        }
    }

    private fun installedVersion(context: Application): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0"

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val a = versionParts(latest)
        val b = versionParts(current)
        for (i in 0 until 3) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun versionParts(value: String): List<Int> =
        Regex("\\d+").findAll(value).map { it.value.toIntOrNull() ?: 0 }.take(3).toList()

    private fun fetchLatestRelease(): ReleaseUpdate? {
        fetchGithubRelease()?.let { return it }
        return fetchGitlabRelease()
    }

    private fun fetchGithubRelease(): ReleaseUpdate? = runCatching {
        val releases = JSONArray(
            httpGet("https://api.github.com/repos/ddddaaaattttoooo/notcustom/releases?per_page=10")
        )
        (0 until releases.length()).asSequence()
            .map { releases.getJSONObject(it) }
            .filter { !it.optBoolean("draft") }
            .mapNotNull { release ->
                val tag = release.optString("tag_name").trim()
                val assets = release.optJSONArray("assets") ?: JSONArray()
                val apk = (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                val url = apk?.optString("browser_download_url").orEmpty()
                if (tag.isBlank() || url.isBlank()) null else ReleaseUpdate(tag, url)
            }
            .firstOrNull()
    }.getOrNull()

    private fun fetchGitlabRelease(): ReleaseUpdate? = runCatching {
        val releases = JSONArray(
            httpGet("https://gitlab.com/api/v4/projects/88960787/releases?per_page=1")
        )
        val release = releases.optJSONObject(0) ?: return@runCatching null
        val tag = release.optString("tag_name").trim()
        val links = release.optJSONObject("assets")?.optJSONArray("links") ?: JSONArray()
        val url = (0 until links.length())
            .map { links.getJSONObject(it).optString("url") }
            .firstOrNull { it.endsWith(".apk", ignoreCase = true) || it.contains("/packages/generic/") }
            .orEmpty()
        if (tag.isBlank() || url.isBlank()) null else ReleaseUpdate(tag, url)
    }.getOrNull()

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("User-Agent", "NotCustom")
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadApk(url: String): File? = runCatching {
        val dir = File(getApplication<Application>().cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "NotCustom-update.apk")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.setRequestProperty("User-Agent", "NotCustom")
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        target
    }.getOrNull()

    fun setColumns(value: Int) {
        val old = prefs.columns
        prefs.columns = value
        val columns = prefs.columns
        if (old == columns) return
        // Slots encode page/row/column with the column count, so re-encode them or icons jump.
        prefs.pinPositions = remapSlots(prefs.pinPositions, old, columns)
        reload()
    }

    private fun remapSlots(
        positions: Map<ComponentName, Int>,
        oldColumns: Int,
        newColumns: Int
    ): Map<ComponentName, Int> {
        val oldPage = oldColumns * HOME_ROWS
        val newPage = newColumns * HOME_ROWS
        val result = LinkedHashMap<ComponentName, Int>()
        val used = mutableSetOf<Int>()
        positions.filterValues { it < 0 }.forEach { (component, slot) ->
            result[component] = slot
            used += slot
        }
        val overflow = mutableListOf<Pair<ComponentName, Int>>()
        positions.entries.filter { it.value >= 0 }.sortedBy { it.value }.forEach { (component, slot) ->
            val page = slot / oldPage
            val inPage = slot % oldPage
            val row = inPage / oldColumns
            val column = inPage % oldColumns
            val target = page * newPage + row * newColumns + column
            if (column < newColumns && row < HOME_GRID_ROWS && used.add(target)) {
                result[component] = target
            } else {
                overflow += component to page
            }
        }
        overflow.forEach { (component, startPage) ->
            val slot = (startPage until MAX_HOME_PAGES).asSequence()
                .flatMap { page ->
                    (page * newPage until page * newPage + newColumns * HOME_GRID_ROWS).asSequence()
                }
                .firstOrNull { it !in used }
            if (slot != null) {
                result[component] = slot
                used += slot
            }
        }
        return result
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

    fun setShowEvents(value: Boolean) {
        prefs.showEvents = value
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

    fun setThemedIcons(value: Boolean) {
        prefs.themedIcons = value
        icons.setThemed(value)
        _state.update { it.copy(config = prefs.config(), iconRevision = it.iconRevision + 1) }
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
        val byComponent = primaryByComponent(apps)
        val preload = ((_state.value.pinned + _state.value.dock).mapNotNull { byComponent[it] } + apps)
            .distinctBy { it.key }
        preload.forEach { viewModelScope.launch { icons.load(it) } }
    }

    fun startWidgets() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { widgetHost.startListening() }
        }
    }

    fun stopWidgets() {
        runCatching { widgetHost.stopListening() }
    }

    fun openWidgetPicker(packageName: String? = null) {
        _state.update {
            it.copy(
                widgetPickerOpen = true,
                widgetPickerPackage = packageName,
                homeMenuOpen = false,
                settingsOpen = false,
                menuApp = null
            )
        }
    }

    fun closeWidgetPicker() {
        _state.update { it.copy(widgetPickerOpen = false, widgetPickerPackage = null) }
    }

    fun widgetChoices(packageName: String? = null): List<WidgetChoice> {
        val context = getApplication<Application>()
        val pm = context.packageManager
        return runCatching {
            widgetManager.getInstalledProviders()
                .filter { packageName == null || it.provider.packageName == packageName }
                .mapNotNull { info ->
                    val appLabel = runCatching {
                        pm.getApplicationLabel(
                            pm.getApplicationInfo(info.provider.packageName, 0)
                        ).toString()
                    }.getOrDefault(info.provider.packageName)
                    val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                        ?: appLabel
                    val cells = widgetCells(info)
                    WidgetChoice(info, appLabel, label, "${cells.columns}×${cells.rows}")
                }
                .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
        }.getOrDefault(emptyList())
    }

    fun prepareWidget(info: AppWidgetProviderInfo) {
        val id = runCatching { widgetHost.allocateAppWidgetId() }.getOrNull()
        if (id == null) {
            _state.update { it.copy(message = "Could not allocate a widget slot.") }
            return
        }
        val allowed = runCatching {
            widgetManager.bindAppWidgetIdIfAllowed(id, info.provider)
        }.getOrDefault(false)
        if (allowed) {
            configureOrCommit(id, info)
            return
        }
        pendingBindId = id
        _state.update {
            it.copy(
                widgetPickerOpen = false,
                widgetPickerPackage = null,
                widgetConfigRequest = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            )
        }
    }

    fun onWidgetResult(resultCode: Int, data: Intent?) {
        pendingBindId?.let { allocated ->
            pendingBindId = null
            if (resultCode == Activity.RESULT_OK) {
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, allocated)
                    ?.takeIf { it >= 0 } ?: allocated
                configureOrCommit(id, widgetManager.getAppWidgetInfo(id))
            } else {
                runCatching { widgetHost.deleteAppWidgetId(allocated) }
                _state.update {
                    it.copy(message = "Widget permission was not granted. Set NotCustom as Home and try again.")
                }
            }
            return
        }
        val id = pendingConfigId ?: return
        pendingConfigId = null
        if (resultCode == Activity.RESULT_OK) {
            commitWidget(id)
        } else {
            runCatching { widgetHost.deleteAppWidgetId(id) }
        }
    }

    fun clearWidgetConfigRequest() {
        _state.update { it.copy(widgetConfigRequest = null) }
    }

    fun handlePinRequest(intent: Intent?) {
        if (intent == null) return
        // A pin request can arrive in a fresh activity before the layout loads; mutating the
        // empty initial state would then overwrite the saved Home layout. Defer until loaded.
        if (_state.value.loading) {
            pendingPinRequest = intent
            return
        }
        val request = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull() ?: return
        if (request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            val info = request.shortcutInfo
            val accepted = info != null && runCatching { request.accept() }.getOrDefault(false)
            if (!accepted || info == null) {
                _state.update { it.copy(message = "Could not add that shortcut.") }
                return
            }
            val item = shortcutItem(info)
            _state.update { state ->
                state.copy(
                    pinnedShortcuts = state.pinnedShortcuts.filter { it.component != item.component } + item
                )
            }
            pin(item)
            return
        }
        if (request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET) return
        val info = runCatching { request.getAppWidgetProviderInfo(getApplication()) }.getOrNull()
            ?: return
        val id = runCatching { widgetHost.allocateAppWidgetId() }.getOrNull()
        if (id == null) {
            _state.update { it.copy(message = "Could not allocate a widget slot.") }
            return
        }
        val accepted = runCatching {
            request.accept(Bundle().apply {
                putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            })
        }.getOrDefault(false)
        if (!accepted) {
            runCatching { widgetHost.deleteAppWidgetId(id) }
            _state.update { it.copy(message = "Could not pin that widget.") }
            return
        }
        configureOrCommit(id, info)
    }

    private fun configureOrCommit(id: Int, info: AppWidgetProviderInfo?) {
        val configure = info?.configure
        if (configure != null) {
            pendingConfigId = id
            _state.update {
                it.copy(
                    widgetPickerOpen = false,
                    widgetPickerPackage = null,
                    widgetConfigRequest = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                        .setComponent(configure)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                )
            }
        } else {
            commitWidget(id)
        }
    }

    suspend fun shortcuts(app: LauncherApp): List<ShortcutInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val query = LauncherApps.ShortcutQuery()
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
                .setPackage(app.packageName)
                .setActivity(app.component)
            launcherApps.getShortcuts(query, app.user).orEmpty()
        }.getOrDefault(emptyList())
            .filter { it.isEnabled }
            .take(4)
    }

    suspend fun shortcutIcon(shortcut: ShortcutInfo): ImageBitmap? = withContext(Dispatchers.IO) {
        val density = getApplication<Application>().resources.displayMetrics.densityDpi
        runCatching {
            launcherApps.getShortcutIconDrawable(shortcut, density)
                ?.toBitmap(112, 112)
                ?.asImageBitmap()
        }.getOrNull()
    }

    fun launchShortcut(app: LauncherApp, shortcut: ShortcutInfo) {
        val launched = runCatching {
            launcherApps.startShortcut(app.packageName, shortcut.id, null, null, app.user)
        }.isSuccess
        _state.update { it.copy(menuApp = null) }
        if (!launched) _state.update { it.copy(message = "Could not open that shortcut.") }
    }

    fun launchPopup(app: LauncherApp) {
        val context = getApplication<Application>()
        val metrics = context.resources.displayMetrics
        val width = (metrics.widthPixels * 0.75f).roundToInt()
        val height = (metrics.heightPixels * 0.55f).roundToInt()
        val left = ((metrics.widthPixels - width) / 2).coerceAtLeast(0)
        val top = ((metrics.heightPixels - height) / 2).coerceAtLeast(0)
        val options = ActivityOptions.makeBasic().apply {
            setLaunchBounds(Rect(left, top, left + width, top + height))
            runCatching {
                ActivityOptions::class.java
                    .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                    .invoke(this, 5)
            }
        }
        val launched = runCatching {
            launcherApps.startMainActivity(app.component, app.user, null, options.toBundle())
        }.isSuccess
        _state.update { it.copy(menuApp = null) }
        if (!launched) {
            launch(app)
            _state.update { it.copy(message = "Pop-up view is not available for ${app.label}.") }
        }
    }

    fun onWidgetConfigFailed() {
        pendingConfigId?.let { runCatching { widgetHost.deleteAppWidgetId(it) } }
        pendingConfigId = null
        pendingBindId?.let { runCatching { widgetHost.deleteAppWidgetId(it) } }
        pendingBindId = null
        _state.update { it.copy(message = "Could not add that widget.") }
    }

    private fun commitWidget(id: Int) {
        val current = _state.value
        val updated = (current.widgets + id).distinct()
        val preferred = current.widgetSizes[id] ?: defaultWidgetSize(id)
        val page = current.currentPage.coerceIn(0, (current.pageCount - 1).coerceAtLeast(0))
        // Try the widget's own size first, then smaller spans until it fits around existing items.
        val candidates = (preferred.columns downTo 1).flatMap { c ->
            (preferred.rows downTo 1).map { r -> WidgetSize(c, r) }
        }.sortedByDescending { it.columns * it.rows }
        var size = preferred
        var placement: WidgetPlacement? = null
        for (candidate in candidates) {
            placement = findWidgetPlacement(
                id = id,
                size = candidate,
                columns = current.config.columns,
                page = page,
                placements = current.widgetPlacements,
                sizes = current.widgetSizes + (id to candidate),
                positions = current.pinPositions
            )
            if (placement != null) {
                size = candidate
                break
            }
        }
        val sizes = current.widgetSizes + (id to size)
        if (placement == null) {
            runCatching { widgetHost.deleteAppWidgetId(id) }
            _state.update {
                it.copy(
                    widgetPickerOpen = false,
                    widgetPickerPackage = null,
                    message = "No room on page ${page + 1} for that widget."
                )
            }
            return
        }
        val placements = current.widgetPlacements + (id to placement)
        prefs.widgets = updated
        prefs.widgetSizes = sizes
        prefs.widgetPlacements = placements
        prefs.currentPage = page
        _state.update {
            it.copy(
                widgets = updated,
                widgetSizes = sizes,
                widgetPlacements = placements,
                currentPage = page,
                widgetPickerOpen = false,
                widgetPickerPackage = null,
                message = "Widget added to page ${page + 1}"
            )
        }
    }

    fun resizeWidget(id: Int, columns: Int, rows: Int) {
        val current = _state.value
        if (id !in current.widgets) return
        val maxColumns = current.config.columns
        val size = WidgetSize(
            columns.coerceIn(1, maxColumns),
            rows.coerceIn(1, HOME_GRID_ROWS)
        )
        if (current.widgetSizes[id] == size) return
        val existing = current.widgetPlacements[id] ?: WidgetPlacement(0, 0)
        val placed = clampWidgetPlacement(existing, size, maxColumns)
        if (!widgetPlacementFree(id, placed, size, current)) {
            _state.update { it.copy(message = "That widget size does not fit there.") }
            return
        }
        val sizes = current.widgetSizes + (id to size)
        val placements = current.widgetPlacements + (id to placed)
        prefs.widgetSizes = sizes
        prefs.widgetPlacements = placements
        _state.update { it.copy(widgetSizes = sizes, widgetPlacements = placements) }
    }

    fun widgetPlacementFree(id: Int, page: Int, column: Int, row: Int): Boolean {
        val current = _state.value
        if (id !in current.widgets) return false
        val size = current.widgetSizes[id] ?: WidgetSize(1, 1)
        val placement = clampWidgetPlacement(
            WidgetPlacement(column, row, page),
            size,
            current.config.columns
        )
        return widgetPlacementFree(id, placement, size, current)
    }

    fun moveWidget(id: Int, column: Int, row: Int, page: Int): Boolean {
        val current = _state.value
        if (id !in current.widgets) return false
        val size = current.widgetSizes[id] ?: WidgetSize(1, 1)
        val placement = clampWidgetPlacement(
            WidgetPlacement(column, row, page),
            size,
            current.config.columns
        )
        if (current.widgetPlacements[id] == placement) return true
        if (!widgetPlacementFree(id, placement, size, current)) return false
        val pageCount = maxOf(current.pageCount, placement.page + 1)
            .coerceAtMost(MAX_HOME_PAGES)
        val placements = current.widgetPlacements + (id to placement)
        prefs.widgetPlacements = placements
        prefs.pageCount = pageCount
        prefs.currentPage = placement.page
        _state.update {
            it.copy(
                widgetPlacements = placements,
                pageCount = pageCount,
                currentPage = placement.page
            )
        }
        return true
    }

    fun removeWidget(id: Int) {
        runCatching { widgetHost.deleteAppWidgetId(id) }
        val updated = _state.value.widgets - id
        val sizes = _state.value.widgetSizes - id
        val placements = _state.value.widgetPlacements - id
        prefs.widgets = updated
        prefs.widgetSizes = sizes
        prefs.widgetPlacements = placements
        _state.update {
            it.copy(widgets = updated, widgetSizes = sizes, widgetPlacements = placements)
        }
    }

    fun clearWidgets() {
        _state.value.widgets.forEach { runCatching { widgetHost.deleteAppWidgetId(it) } }
        prefs.widgets = emptyList()
        prefs.widgetSizes = emptyMap()
        prefs.widgetPlacements = emptyMap()
        _state.update {
            it.copy(widgets = emptyList(), widgetSizes = emptyMap(), widgetPlacements = emptyMap())
        }
    }

    fun exportLayout(uri: android.net.Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!
                        .bufferedWriter().use { it.write(prefs.exportLayout()) }
                }.isSuccess
            }
            showMessage(if (ok) "Layout backed up" else "Couldn't save the backup")
        }
    }

    fun importLayout(uri: android.net.Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = getApplication<Application>().contentResolver.openInputStream(uri)!!
                        .bufferedReader().use { it.readText() }
                    prefs.importLayout(raw)
                }.isSuccess
            }
            if (ok) reload()
            showMessage(if (ok) "Layout restored" else "Not a NotCustom backup")
        }
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
                folders = emptyMap(),
                openFolder = null,
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

    private fun syncWidgetSizes(widgets: List<Int>, columns: Int): Map<Int, WidgetSize> {
        val saved = prefs.widgetSizes.filterKeys { it in widgets }.toMutableMap()
        var changed = saved.size != prefs.widgetSizes.size
        widgets.forEach { id ->
            val size = saved[id]
            if (size == null) {
                saved[id] = defaultWidgetSize(id)
                changed = true
            } else if (size.columns > columns) {
                saved[id] = size.copy(columns = columns)
                changed = true
            }
        }
        if (changed) prefs.widgetSizes = saved
        return saved
    }

    private fun defaultWidgetSize(id: Int): WidgetSize =
        widgetManager.getAppWidgetInfo(id)?.let(::widgetCells) ?: WidgetSize(1, 1)

    /** The provider's preferred cell span (Android 12+ targetCell*), else derived from its min size. */
    private fun widgetCells(info: AppWidgetProviderInfo): WidgetSize {
        // minWidth/minHeight are px; Android sizes n cells as 70n - 30 dp.
        val density = getApplication<Application>().resources.displayMetrics.density
        val cols = info.targetCellWidth.takeIf { it > 0 }
            ?: kotlin.math.ceil((info.minWidth / density + 30f) / 70f).toInt()
        val rows = info.targetCellHeight.takeIf { it > 0 }
            ?: kotlin.math.ceil((info.minHeight / density + 30f) / 70f).toInt()
        return WidgetSize(
            cols.coerceIn(1, _state.value.config.columns),
            rows.coerceIn(1, HOME_GRID_ROWS)
        )
    }

    private fun syncWidgetPlacements(
        widgets: List<Int>,
        sizes: Map<Int, WidgetSize>,
        columns: Int,
        positions: Map<ComponentName, Int>
    ): Map<Int, WidgetPlacement> {
        val saved = prefs.widgetPlacements
        val placements = LinkedHashMap<Int, WidgetPlacement>()
        var changed = saved.keys.any { it !in widgets }
        widgets.forEach { id ->
            val size = sizes[id] ?: WidgetSize(1, 1)
            val existing = saved[id]?.let { clampWidgetPlacement(it, size, columns) }
            var placement = existing?.takeIf {
                widgetPlacementFreeAt(id, it, size, columns, placements, sizes, positions)
            }
            if (placement == null) {
                changed = true
                val preferred = existing?.page ?: 0
                val pages = listOf(preferred) + (0 until MAX_HOME_PAGES).filter { it != preferred }
                placement = pages.firstNotNullOfOrNull { page ->
                    findWidgetPlacement(
                        id = id,
                        size = size,
                        columns = columns,
                        page = page,
                        placements = placements,
                        sizes = sizes,
                        positions = positions
                    )
                }
            }
            if (placement == null) placement = WidgetPlacement(0, 0, 0)
            if (placement != saved[id]) changed = true
            placements[id] = placement
        }
        if (changed) prefs.widgetPlacements = placements
        return placements
    }

    private fun findWidgetPlacement(
        id: Int,
        size: WidgetSize,
        columns: Int,
        page: Int,
        placements: Map<Int, WidgetPlacement> = _state.value.widgetPlacements,
        sizes: Map<Int, WidgetSize> = _state.value.widgetSizes,
        positions: Map<ComponentName, Int> = _state.value.pinPositions
    ): WidgetPlacement? {
        val occupied = widgetOccupiedCells(placements, sizes, page, id) +
            appOccupiedCells(positions, columns, page)
        val maxColumn = (columns - size.columns).coerceAtLeast(0)
        val maxRow = (HOME_GRID_ROWS - size.rows).coerceAtLeast(0)
        for (row in 0..maxRow) {
            for (column in 0..maxColumn) {
                val cells = widgetCells(WidgetPlacement(column, row, page), size)
                if (cells.none { it in occupied }) return WidgetPlacement(column, row, page)
            }
        }
        return null
    }

    private fun widgetPlacementFree(
        id: Int,
        placement: WidgetPlacement,
        size: WidgetSize,
        state: LauncherState
    ): Boolean {
        val cells = widgetCells(placement, size)
        if (cells.any {
                it.first !in 0 until state.config.columns ||
                    it.second !in 0 until HOME_GRID_ROWS
            }
        ) return false
        val occupied = widgetOccupiedCells(
            state.widgetPlacements, state.widgetSizes, placement.page, id
        ) + appOccupiedCells(state.pinPositions, state.config.columns, placement.page)
        return cells.none { it in occupied }
    }

    private fun widgetPlacementFreeAt(
        id: Int,
        placement: WidgetPlacement,
        size: WidgetSize,
        columns: Int,
        placements: Map<Int, WidgetPlacement>,
        sizes: Map<Int, WidgetSize>,
        positions: Map<ComponentName, Int>
    ): Boolean {
        val cells = widgetCells(placement, size)
        if (cells.any {
                it.first !in 0 until columns || it.second !in 0 until HOME_GRID_ROWS
            }
        ) return false
        val occupied = widgetOccupiedCells(placements, sizes, placement.page, id) +
            appOccupiedCells(positions, columns, placement.page)
        return cells.none { it in occupied }
    }

    private fun widgetOccupiedCells(
        placements: Map<Int, WidgetPlacement>,
        sizes: Map<Int, WidgetSize>,
        page: Int,
        excludeId: Int
    ): Set<Pair<Int, Int>> = buildSet {
        placements.forEach { (id, placement) ->
            if (id == excludeId || placement.page != page) return@forEach
            val size = sizes[id] ?: WidgetSize(1, 1)
            addAll(widgetCells(placement, size))
        }
    }

    private fun appOccupiedCells(
        positions: Map<ComponentName, Int>,
        columns: Int,
        page: Int
    ): Set<Pair<Int, Int>> {
        val pageSize = columns * HOME_ROWS
        return buildSet {
            positions.values.forEach { slot ->
                if (slot < 0 || slot / pageSize != page) return@forEach
                val inPage = slot % pageSize
                val row = inPage / columns
                if (row < HOME_GRID_ROWS) add((inPage % columns) to row)
            }
        }
    }

    private fun slotHasWidget(slot: Int, columns: Int): Boolean {
        if (slot < 0) return false
        val pageSize = columns * HOME_ROWS
        val page = slot / pageSize
        val inPage = slot % pageSize
        val row = inPage / columns
        if (row >= HOME_GRID_ROWS) return false
        val cell = (inPage % columns) to row
        val state = _state.value
        return state.widgets.any { id ->
            val placement = state.widgetPlacements[id] ?: return@any false
            val size = state.widgetSizes[id] ?: WidgetSize(1, 1)
            placement.page == page && cell in widgetCells(placement, size)
        }
    }

    private fun clampWidgetPlacement(
        placement: WidgetPlacement,
        size: WidgetSize,
        columns: Int
    ): WidgetPlacement = WidgetPlacement(
        placement.column.coerceIn(0, (columns - size.columns).coerceAtLeast(0)),
        placement.row.coerceIn(0, (HOME_GRID_ROWS - size.rows).coerceAtLeast(0)),
        placement.page.coerceIn(0, MAX_HOME_PAGES - 1)
    )

    private fun widgetCells(
        placement: WidgetPlacement,
        size: WidgetSize
    ): List<Pair<Int, Int>> = buildList {
        for (row in placement.row until placement.row + size.rows) {
            for (column in placement.column until placement.column + size.columns) {
                add(column to row)
            }
        }
    }

    private fun firstFreeSlot(
        positions: Map<ComponentName, Int>,
        page: Int,
        columns: Int
    ): Int? {
        val pageSize = columns * HOME_ROWS
        val start = page * pageSize
        val used = positions.values.toSet()
        return (start until start + columns * HOME_GRID_ROWS)
            .firstOrNull { it !in used && !slotHasWidget(it, columns) }
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

    private fun primaryByComponent(apps: List<LauncherApp>): Map<ComponentName, LauncherApp> =
        apps.groupBy { it.component }.mapValues { (_, list) ->
            list.firstOrNull { it.user == Process.myUserHandle() } ?: list.first()
        }

    private fun loadApps(): List<LauncherApp> {
        val context = getApplication<Application>()
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val userManager = context.getSystemService(UserManager::class.java)
        val users = runCatching { userManager?.userProfiles }.getOrNull().orEmpty()
            .ifEmpty { listOf(Process.myUserHandle()) }
        val collator = Collator.getInstance()
        val result = ArrayList<LauncherApp>()
        val categoryCache = HashMap<String, Pair<String, Boolean>>()
        users.forEach { user ->
            runCatching {
                launcherApps.getActivityList(null, user).forEach { info ->
                    val component = info.componentName ?: return@forEach
                    // NotCustom itself is reached from Home settings, not as an app icon.
                    if (component.packageName == context.packageName) return@forEach
                    val label = info.label?.toString()?.takeIf { it.isNotBlank() }
                        ?: component.packageName
                    val cached = categoryCache[component.packageName]
                    val appInfo: ApplicationInfo? = if (cached != null) {
                        null
                    } else {
                        runCatching {
                            launcherApps.getApplicationInfo(component.packageName, 0, user)
                        }.getOrNull()
                    }
                    val system = cached?.second
                        ?: (((appInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0)
                    val category = cached?.first
                        ?: appInfo?.let { categoryFor(it, label, component.packageName, system) }
                        ?: "Other"
                    if (cached == null) categoryCache[component.packageName] = category to system
                    result += LauncherApp(
                        component = component,
                        label = label,
                        packageName = component.packageName,
                        user = user,
                        category = category,
                        system = system
                    ).also { icons.remember(it.key, info) }
                }
            }
        }
        return result.distinctBy { it.key }.sortedWith { a, b ->
            val byLabel = collator.compare(a.label, b.label)
            if (byLabel != 0) byLabel else a.user.hashCode().compareTo(b.user.hashCode())
        }
    }

    private fun categoryFor(
        info: ApplicationInfo,
        label: String,
        packageName: String,
        system: Boolean
    ): String {
        val text = "$label $packageName".lowercase()
        fun has(vararg terms: String) = terms.any { text.contains(it) }
        if (system && has(
                "settings", "systemui", "system ui", "provider", "server", "service",
                "sim", "telecom", "nfc", "bluetooth", "installer", "launcher", "shell"
            )
        ) {
            return "System"
        }
        val pkg = packageName.lowercase()
        fun pkgIs(vararg prefixes: String) =
            prefixes.any { pkg == it || pkg.startsWith("$it.") }
        if (pkgIs("ge.bog.mobilebank", "com.icomvision.bsc.tbc") ||
            pkg.contains("keepz") ||
            label.equals("BOG", ignoreCase = true) ||
            label.startsWith("TBC Bank", ignoreCase = true) ||
            label.contains("Keepz", ignoreCase = true)
        ) {
            return "Finance"
        }
        if (pkgIs(
                "com.taobao", "com.tmall", "com.jingdong", "com.xunmeng.pinduoduo",
                "com.alibaba.wireless", "com.alibaba.aliexpresshd",
                "com.alibaba.intl.android.apps.poseidon"
            ) || pkg == "trendyol.com" || pkg.startsWith("trendyol.") ||
            pkg.contains("kiwipost") || label.contains("KiwiPost", ignoreCase = true) ||
            has("淘宝", "天猫", "京东", "拼多多", "闲鱼", "1688")
        ) {
            return "Shopping"
        }
        if (pkgIs("com.valvesoftware") || label.equals("Steam", ignoreCase = true)) {
            return "Games"
        }
        return when (info.category) {
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_GAME -> "Games"
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> "Entertainment"
            ApplicationInfo.CATEGORY_IMAGE -> "Photography"
            ApplicationInfo.CATEGORY_NEWS -> "Social"
            ApplicationInfo.CATEGORY_MAPS -> "Travel"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Tools"
            else -> when {
                has("phone", "dialer", "contact", "message", "sms", "mms", "gmail", "mail",
                    "whatsapp", "telegram", "signal") -> "Communication"
                has("instagram", "facebook", "tiktok", "snapchat", "reddit", "twitter",
                    "threads", "mastodon", "social") -> "Social"
                has("bank", "wallet", "pay", "finance", "revolut", "monzo", "cash") -> "Finance"
                has("shop", "store", "amazon", "ebay", "etsy", "retail", "marketplace",
                    "allegro", "taobao", "tmall", "jingdong", "pinduoduo", "aliexpress",
                    "trendyol", "idlefish", "goofish", "alibaba", "1688",
                    "淘宝", "天猫", "京东", "拼多多", "闲鱼") -> "Shopping"
                has("maps", "navigation", "travel", "uber", "bolt", "transit", "booking",
                    "airline", "flight", "train") -> "Travel"
                has("camera", "photo", "gallery", "lens", "scanner") -> "Photography"
                has("calendar", "docs", "sheet", "drive", "office", "note", "task", "notion",
                    "slack", "teams", "work") -> "Productivity"
                has("settings", "calculator", "clock", "file", "browser", "recorder",
                    "utility", "vpn", "keyboard", "tools") -> "Tools"
                has("youtube", "music", "spotify", "video", "netflix", "podcast", "tv",
                    "player", "audio") -> "Entertainment"
                has("game", "steam") -> "Games"
                else -> "Other"
            }
        }
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
