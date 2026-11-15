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
                prefs.pinned = pinned
                prefs.dock = dock
                val pinPositions = normalizePinPositions(pinned)
                _state.update {
                    it.copy(
                        loading = false,
                        apps = apps,
                        pinned = pinned,
                        pinPositions = pinPositions,
                        dock = dock,
                        hidden = hidden,
                        widgets = widgets,
                        config = prefs.config(),
                        drawerMode = prefs.drawerMode,
                        iconPacks = iconPacks,
                        iconPack = iconPack
                    )
                }
                val byComponent = apps.associateBy { it.component }
                val preload = ((pinned + dock).mapNotNull { byComponent[it] } + apps)
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
            @Suppress("DEPRECATION")
            ActivityOptions.makeScaleUpAnimation(
                sourceView,
                bounds.left,
                bounds.top,
                bounds.width(),
                bounds.height()
            ).toBundle()
        } else {
            null
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
        val updated = (_state.value.pinned + app.component).distinct()
        val positions = _state.value.pinPositions.toMutableMap()
        if (app.component !in positions) {
            var next = 0
            while (next in positions.values) next++
            positions[app.component] = next
        }
        prefs.pinned = updated
        prefs.pinPositions = positions
        _state.update { it.copy(pinned = updated, pinPositions = positions, menuApp = null) }
    }

    fun unpin(app: LauncherApp) {
        val updated = _state.value.pinned - app.component
        val positions = _state.value.pinPositions - app.component
        prefs.pinned = updated
        prefs.pinPositions = positions
        _state.update { it.copy(pinned = updated, pinPositions = positions, menuApp = null) }
    }

    fun movePinnedToSlot(app: LauncherApp, targetSlot: Int) {
        if (targetSlot !in 0..MAX_PIN_SLOT) return
        val positions = _state.value.pinPositions.toMutableMap()
        val sourceSlot = positions[app.component] ?: return
        if (sourceSlot == targetSlot) return
        val occupying = positions.entries.firstOrNull {
            it.key != app.component && it.value == targetSlot
        }?.key
        positions[app.component] = targetSlot
        if (occupying != null) positions[occupying] = sourceSlot
        prefs.pinPositions = positions
        _state.update { it.copy(pinPositions = positions) }
    }

    fun addDock(app: LauncherApp) {
        val current = _state.value.dock
        if (current.contains(app.component) || current.size >= 5) return
        val updated = current + app.component
        prefs.dock = updated
        _state.update { it.copy(dock = updated, menuApp = null) }
    }

    fun removeDock(app: LauncherApp) {
        val updated = _state.value.dock - app.component
        prefs.dock = updated
        _state.update { it.copy(dock = updated, menuApp = null) }
    }

    fun moveDock(app: LauncherApp, target: LauncherApp) {
        val updated = _state.value.dock.toMutableList()
        val fromIndex = updated.indexOf(app.component)
        val targetIndex = updated.indexOf(target.component)
        if (fromIndex < 0 || targetIndex < 0 || fromIndex == targetIndex) return
        updated.add(targetIndex, updated.removeAt(fromIndex))
        prefs.dock = updated
        _state.update { it.copy(dock = updated) }
    }

    fun hide(app: LauncherApp) {
        val hidden = _state.value.hidden + app.packageName
        val pinned = _state.value.pinned.filter { it.packageName != app.packageName }
        val pinPositions = _state.value.pinPositions.filterKeys { it.packageName != app.packageName }
        val dock = _state.value.dock.filter { it.packageName != app.packageName }
        prefs.hidden = hidden
        prefs.pinned = pinned
        prefs.pinPositions = pinPositions
        prefs.dock = dock
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
        refreshConfig()
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
        val pinned = prefs.pinned
        val pinPositions = normalizePinPositions(pinned)
        _state.update {
            it.copy(
                pinned = pinned,
                pinPositions = pinPositions,
                dock = prefs.dock,
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
        val stored = prefs.pinPositions.filter { (component, slot) ->
            component in pinned && slot in 0..MAX_PIN_SLOT
        }
        val result = LinkedHashMap<ComponentName, Int>()
        val used = mutableSetOf<Int>()
        pinned.forEach { component ->
            val position = stored[component]
            if (position != null && used.add(position)) {
                result[component] = position
            } else {
                var next = 0
                while (next in used) next++
                result[component] = next
                used += next
            }
        }
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
        private const val MAX_PIN_SLOT = 59

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
