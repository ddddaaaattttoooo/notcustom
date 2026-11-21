package dev.notcustom.app.launcher.ui

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.activity.compose.BackHandler
import dev.notcustom.app.launcher.isDefaultHome
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import android.content.Intent
import android.content.pm.ShortcutInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.notcustom.app.BuildConfig
import dev.notcustom.app.MainActivity
import dev.notcustom.app.launcher.LauncherApp
import dev.notcustom.app.launcher.AtAGlanceStyle
import dev.notcustom.app.launcher.LauncherState
import dev.notcustom.app.launcher.LauncherViewModel
import dev.notcustom.app.launcher.MAX_HOME_PAGES
import dev.notcustom.app.launcher.homeRoleIntent
import dev.notcustom.app.launcher.homeSettingsIntent
import dev.notcustom.app.ui.theme.Ink
import dev.notcustom.app.ui.theme.Line
import dev.notcustom.app.ui.theme.Muted
import dev.notcustom.app.ui.theme.NothingRed
import dev.notcustom.app.ui.theme.Ok
import dev.notcustom.app.ui.theme.Paper
import dev.notcustom.app.ui.theme.Surface0
import dev.notcustom.app.ui.theme.Surface1
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMenuSheet(
    app: LauncherApp,
    state: LauncherState,
    viewModel: LauncherViewModel
) {
    ModalBottomSheet(
        onDismissRequest = viewModel::dismissMenu,
        containerColor = Surface0,
        contentColor = Paper,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        contentWindowInsets = { WindowInsets(0) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        DarkSheetSystemBars()
        val icon by produceState<ImageBitmap?>(initialValue = null, app.component, state.iconPack) {
            value = viewModel.loadIcon(app)
        }
        val pinned = state.pinned.contains(app.component)
        val inFolder = remember(app, state.folders) { viewModel.folderOf(app) }
        var movePageMenuOpen by remember { mutableStateOf(false) }

        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app.folderId != null) {
                    FolderIcon(app, 44, false, viewModel, state.iconPack)
                } else {
                    AppIcon(icon, 44, false)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Paper
                    )
                    Text(
                        when {
                            app.folderId != null -> "Folder · ${app.folderApps.size} apps"
                            app.shortcutId != null -> "Shortcut · ${app.packageName}"
                            else -> app.packageName
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            if (inFolder != null) {
                MenuAction("REMOVE FROM FOLDER") { viewModel.removeFromFolder(app) }
            } else if (app.folderId != null) {
                MenuAction("OPEN FOLDER") { viewModel.openFolder(app.folderId) }
                MenuAction("REMOVE FOLDER FROM HOME") { viewModel.unpin(app) }
            } else {
                MenuAction(if (pinned) "REMOVE FROM HOME" else "ADD TO HOME") {
                    if (pinned) viewModel.unpin(app) else viewModel.pin(app)
                }
            }
            if (inFolder == null) Box {
                MenuAction(if (pinned) "MOVE TO PAGE" else "ADD TO PAGE") {
                    movePageMenuOpen = true
                }
                DropdownMenu(
                    expanded = movePageMenuOpen,
                    onDismissRequest = { movePageMenuOpen = false }
                ) {
                    repeat(state.pageCount) { page ->
                        DropdownMenuItem(
                            text = { Text("PAGE ${page + 1}") },
                            onClick = {
                                movePageMenuOpen = false
                                if (pinned) viewModel.moveToPage(app, page)
                                else viewModel.addToPage(app, page)
                            }
                        )
                    }
                }
            }
            MenuAction("HOME SETTINGS") {
                viewModel.dismissMenu()
                viewModel.openSettings()
            }
            if (app.isApp && viewModel.widgetChoices(app.packageName).isNotEmpty()) {
                MenuAction("ADD WIDGET") { viewModel.openWidgetPicker(app.packageName) }
            }
            if (app.isApp) MenuAction("HIDE APP") { viewModel.hide(app) }
            if (app.folderId == null) MenuAction("APP INFO") { viewModel.appInfo(app) }
            if (viewModel.canUninstall(app)) {
                MenuAction("UNINSTALL") { viewModel.uninstall(app) }
            }
            val shortcuts by produceState(
                initialValue = emptyList<ShortcutInfo>(),
                app.key
            ) {
                value = if (app.isApp) viewModel.shortcuts(app) else emptyList()
            }
            if (shortcuts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("SHORTCUTS", style = MaterialTheme.typography.labelSmall, color = Muted)
                Spacer(Modifier.height(4.dp))
                shortcuts.forEach { shortcut ->
                    ShortcutRow(app, shortcut, viewModel)
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(
    app: LauncherApp,
    shortcut: ShortcutInfo,
    viewModel: LauncherViewModel
) {
    val icon by produceState<ImageBitmap?>(initialValue = null, app.key, shortcut.id) {
        value = viewModel.shortcutIcon(shortcut)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { viewModel.launchShortcut(app, shortcut) }
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(icon, 30, false)
        Spacer(Modifier.width(12.dp))
        Text(
            shortcut.shortLabel?.toString()?.takeIf { it.isNotBlank() }
                ?: shortcut.longLabel?.toString().orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = Paper,
            maxLines = 1
        )
    }
}

@Composable
private fun AccessRow(
    title: String,
    enabled: Boolean,
    readyText: String,
    needText: String,
    onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(7.dp)
                .height(7.dp)
                .background(if (enabled) Ok else NothingRed, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(
                if (enabled) readyText else needText,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) Muted else Paper
            )
        }
        TextButton(onClick = onClick) {
            Text(
                if (enabled) "SETTINGS" else "ENABLE",
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) Muted else NothingRed
            )
        }
    }
}

@Composable
private fun MenuAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Paper,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMenuSheet(state: LauncherState, viewModel: LauncherViewModel) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = viewModel::closeHomeMenu,
        containerColor = Surface0,
        contentColor = Paper,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        contentWindowInsets = { WindowInsets(0) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        DarkSheetSystemBars()
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HOME", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(24.dp).height(2.dp).background(NothingRed))
            }
            Spacer(Modifier.height(8.dp))
            MenuAction("ADD WIDGET") { viewModel.openWidgetPicker() }
            MenuAction("ADD PAGE") { viewModel.addPage() }
            if (state.pageCount > 1) {
                MenuAction("REMOVE PAGE") { viewModel.removeCurrentPage() }
            }
            MenuAction(
                if (state.config.showLabels) "HIDE APP NAMES" else "SHOW APP NAMES"
            ) { viewModel.setShowLabels(!state.config.showLabels) }
            MenuAction("WALLPAPER & STYLE") {
                viewModel.closeHomeMenu()
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_SET_WALLPAPER)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            MenuAction("HOME SETTINGS") {
                viewModel.closeHomeMenu()
                viewModel.openSettings()
            }
            MenuAction("APP DRAWER") {
                viewModel.closeHomeMenu()
                viewModel.openDrawer()
            }
            MenuAction("TURN SCREEN OFF") {
                viewModel.closeHomeMenu()
                viewModel.lockScreen()
            }
        }
    }
}

/** Settings pages, Pixel style: a list on the first screen, one page per topic. */
private enum class SettingsPage(val title: String, val summary: String) {
    MAIN("Settings", ""),
    HOME("Home screen", "Grid, icon size, pages, labels and search bar"),
    ICONS("Icons", "Icon pack, themed and monochrome icons"),
    GLANCE("At a Glance", "Clock, battery, alarm and calendar events"),
    DRAWER("App drawer", "Hidden apps"),
    WIDGETS("Widgets", "Add, remove or clear widgets"),
    ACCESS("Gestures & access", "Double-tap to lock and notification dots"),
    BACKUP("Backup & reset", "Save, restore or reset your layout"),
    ABOUT("About", "Version and credits")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LauncherSettingsScreen(
    state: LauncherState,
    viewModel: LauncherViewModel
) {
    val context = LocalContext.current
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportLayout) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importLayout) }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    val calendarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.setShowEvents(granted) }
    val hiddenApps = remember(state.hidden, state.apps) {
        state.apps.filter { it.packageName in state.hidden }
            .distinctBy { it.packageName }
    }
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    val back = { if (page == SettingsPage.MAIN) viewModel.closeSettings() else page = SettingsPage.MAIN }
    LaunchedEffect(Unit) { viewModel.checkForUpdates() }

    BackHandler(onBack = back)
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            // Swallow touches so Home underneath never reacts.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Ink)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = back) {
                    Text("←", style = MaterialTheme.typography.titleMedium, color = Paper)
                }
            }
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState != SettingsPage.MAIN
                    (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
                },
                label = "settings page"
            ) { current ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(current.title, style = MaterialTheme.typography.displaySmall, color = Paper)
                    Spacer(Modifier.height(24.dp))
                    when (current) {
                        SettingsPage.MAIN -> {
                            val update = state.update
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Surface1)
                                    .border(1.dp, Line, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("SOFTWARE UPDATE", style = MaterialTheme.typography.labelSmall, color = Muted)
                                    Text(
                                        when {
                                            update.downloading -> "DOWNLOADING ${update.latest.orEmpty()}…"
                                            update.checking -> "CHECKING…"
                                            update.available -> "VERSION ${update.latest.orEmpty()} AVAILABLE"
                                            update.message != null -> update.message
                                            else -> "NOT CHECKED"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (update.available) NothingRed else Paper
                                    )
                                }
                                if (update.available) {
                                    Button(
                                        onClick = viewModel::installUpdate,
                                        enabled = !update.downloading,
                                        shape = RoundedCornerShape(50),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NothingRed,
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("INSTALL", style = MaterialTheme.typography.labelSmall)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { viewModel.checkForUpdates(force = true) },
                                        enabled = !update.checking && !update.downloading,
                                        shape = RoundedCornerShape(50),
                                        border = BorderStroke(1.dp, Line),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Text("CHECK", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            if (update.available) {
                                Text(
                                    "Downloads from GitHub, then opens Android's installer. Beta builds are debug-signed; if Android refuses the update, uninstall the old copy first.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Muted,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                            if (!isDefaultHome(context)) {
                                SettingsRow("Set as default home", "NotCustom isn't your home screen yet", highlight = true) {
                                    roleLauncher.launch(homeRoleIntent(context) ?: homeSettingsIntent())
                                }
                            }
                            SettingsPage.entries.drop(1).forEach { target ->
                                SettingsRow(target.title, target.summary) { page = target }
                            }
                            SettingsRow("System tweaks", "Animations, fonts, screen timeout and more") {
                                viewModel.closeSettings()
                                context.startActivity(
                                    Intent(context, MainActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                        SettingsPage.HOME -> {
                            SettingSlider(
                                label = "COLUMNS",
                                value = state.config.columns.toFloat(),
                                range = 3f..6f,
                                steps = 3
                            ) { viewModel.setColumns(it.roundToInt()) }

                            SettingSlider(
                                label = "ICON SIZE",
                                value = state.config.iconSize.toFloat(),
                                range = 40f..76f,
                                steps = 9
                            ) { viewModel.setIconSize(it.roundToInt()) }

                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("HOME PAGES", style = MaterialTheme.typography.labelSmall, color = Muted)
                                    Text(
                                        "${state.pageCount} total  ·  page ${state.currentPage + 1}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Paper
                                    )
                                }
                                PageSheetButton("−", enabled = state.pageCount > 1) {
                                    viewModel.removeCurrentPage()
                                }
                                Spacer(Modifier.width(8.dp))
                                PageSheetButton("+", enabled = state.pageCount < MAX_HOME_PAGES) {
                                    viewModel.addPage()
                                }
                            }
                            SwitchRow("APP LABELS", state.config.showLabels) { viewModel.setShowLabels(it) }
                            SwitchRow("SEARCH BAR", state.config.showSearch) { viewModel.setShowSearch(it) }
                        }
                        SettingsPage.ICONS -> {
                            var iconPackMenuOpen by remember { mutableStateOf(false) }
                            val selectedPackLabel = state.iconPacks
                                .firstOrNull { it.packageName == state.iconPack }
                                ?.label ?: "SYSTEM DEFAULT"
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("ICON PACK", style = MaterialTheme.typography.labelSmall, color = Muted)
                                    Text(selectedPackLabel, style = MaterialTheme.typography.bodySmall, color = Paper)
                                }
                                Box {
                                    OutlinedButton(
                                        onClick = { iconPackMenuOpen = true },
                                        shape = RoundedCornerShape(50),
                                        border = BorderStroke(1.dp, Line)
                                    ) {
                                        Text("CHANGE", style = MaterialTheme.typography.labelSmall)
                                    }
                                    DropdownMenu(
                                        expanded = iconPackMenuOpen,
                                        onDismissRequest = { iconPackMenuOpen = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("SYSTEM DEFAULT") },
                                            onClick = {
                                                iconPackMenuOpen = false
                                                viewModel.setIconPack(null)
                                            }
                                        )
                                        state.iconPacks.forEach { pack ->
                                            DropdownMenuItem(
                                                text = { Text(pack.label) },
                                                onClick = {
                                                    iconPackMenuOpen = false
                                                    viewModel.setIconPack(pack.packageName)
                                                }
                                            )
                                        }
                                        if (state.iconPacks.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("No compatible packs found") },
                                                enabled = false,
                                                onClick = { }
                                            )
                                        }
                                    }
                                }
                            }
                            SwitchRow("THEMED ICONS", state.config.themedIcons) { viewModel.setThemedIcons(it) }
                            Text(
                                "Like Pixel: apps that support it get a light-on-dark icon. Android 13 or newer, system icons only.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted
                            )
                            SwitchRow("MONOCHROME ICONS", state.config.monochrome) { viewModel.setMonochrome(it) }
                        }
                        SettingsPage.GLANCE -> {
                            var atAGlanceMenuOpen by remember { mutableStateOf(false) }
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("AT A GLANCE", style = MaterialTheme.typography.labelSmall, color = Muted)
                                    Text(
                                        when (state.config.atAGlanceStyle) {
                                            AtAGlanceStyle.CLOCK -> "CLOCK + DATE"
                                            AtAGlanceStyle.BATTERY -> "BATTERY STATUS"
                                            AtAGlanceStyle.NEXT_ALARM -> "NEXT ALARM"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Paper
                                    )
                                }
                                Box {
                                    OutlinedButton(
                                        onClick = { atAGlanceMenuOpen = true },
                                        shape = RoundedCornerShape(50),
                                        border = BorderStroke(1.dp, Line)
                                    ) {
                                        Text("CHANGE", style = MaterialTheme.typography.labelSmall)
                                    }
                                    DropdownMenu(
                                        expanded = atAGlanceMenuOpen,
                                        onDismissRequest = { atAGlanceMenuOpen = false }
                                    ) {
                                        listOf(
                                            AtAGlanceStyle.CLOCK to "CLOCK + DATE",
                                            AtAGlanceStyle.BATTERY to "BATTERY STATUS",
                                            AtAGlanceStyle.NEXT_ALARM to "NEXT ALARM"
                                        ).forEach { (style, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = {
                                                    atAGlanceMenuOpen = false
                                                    viewModel.setAtAGlanceStyle(style)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            SwitchRow("24-HOUR CLOCK", state.config.use24HourClock) { viewModel.setUse24HourClock(it) }
                            SwitchRow("CALENDAR EVENTS", state.config.showEvents) { on ->
                                val granted = context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) ==
                                    android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (on && !granted) calendarLauncher.launch(android.Manifest.permission.READ_CALENDAR)
                                else viewModel.setShowEvents(on)
                            }
                            Text(
                                "Shows your next event in the coming 24 hours under the date. Read on the phone only, never uploaded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted
                            )
                        }
                        SettingsPage.DRAWER -> {
                            Text("HIDDEN APPS", style = MaterialTheme.typography.labelSmall, color = Muted)
                            Spacer(Modifier.height(8.dp))
                            if (hiddenApps.isEmpty()) {
                                Text("None", style = MaterialTheme.typography.bodySmall, color = Muted)
                            } else {
                                hiddenApps.forEach { app ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            app.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Paper,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { viewModel.unhide(app.packageName) }) {
                                            Text("RESTORE", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                        SettingsPage.WIDGETS -> {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("WIDGETS", style = MaterialTheme.typography.labelSmall, color = Muted)
                                TextButton(onClick = viewModel::openWidgetPicker) {
                                    Text("ADD", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (state.widgets.isEmpty()) {
                                Text("None", style = MaterialTheme.typography.bodySmall, color = Muted)
                            } else {
                                state.widgets.forEach { id ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            viewModel.widgetLabel(id),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Paper,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { viewModel.removeWidget(id) }) {
                                            Text("REMOVE", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                OutlinedButton(
                                    onClick = viewModel::clearWidgets,
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, Line)
                                ) {
                                    Text("CLEAR ALL WIDGETS", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        SettingsPage.ACCESS -> {
                            AccessRow(
                                title = "GESTURE ACCESS",
                                enabled = state.accessibilityEnabled,
                                readyText = "Double-tap screen-off is ready.",
                                needText = "Enable for double-tap screen-off.",
                                onClick = viewModel::openAccessibilitySettings
                            )
                            Spacer(Modifier.height(6.dp))
                            AccessRow(
                                title = "NOTIFICATION DOTS",
                                enabled = state.notificationDotsEnabled,
                                readyText = "Apps with notifications show a red dot.",
                                needText = "Allow notification access to show dots on icons.",
                                onClick = viewModel::openNotificationDotSettings
                            )
                            Spacer(Modifier.height(10.dp))

                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Blocked by \"Restricted setting\"? Allow it in App Info.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Muted,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = viewModel::openAppInfo) {
                                    Text(
                                        "APP INFO",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Paper
                                    )
                                }
                            }
                        }
                        SettingsPage.BACKUP -> {
                            Text(
                                "A backup keeps your pages, dock, folders, hidden apps and settings. Widgets need adding again after a restore.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted
                            )
                            Spacer(Modifier.height(16.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { backupLauncher.launch("notcustom-layout.json") },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink)
                                ) {
                                    Text("BACK UP LAYOUT", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, Line)
                                ) {
                                    Text("RESTORE LAYOUT", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = viewModel::resetLayout,
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, Line)
                                ) {
                                    Text("RESET LAYOUT", style = MaterialTheme.typography.labelSmall, color = NothingRed)
                                }
                            }
                        }
                        SettingsPage.ABOUT -> {
                            Text(
                                "NotCustom Home · ${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("CREDITS", style = MaterialTheme.typography.labelSmall, color = Muted)
                            Text(
                                "ddddaaaattttoooo",
                                style = MaterialTheme.typography.titleMedium,
                                color = Paper
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "No root. Nothing Launcher is untouched - this is a separate home screen " +
                                    "you can switch to or away from anytime.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, summary: String, highlight: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (highlight) NothingRed else Paper)
            if (summary.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(summary, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = Muted)
    }
}

@Composable
private fun PageSheetButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, Line),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(38.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    var local by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(
                local.roundToInt().toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Paper
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local) },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Paper,
                activeTrackColor = Paper,
                inactiveTrackColor = Line
            )
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink,
                checkedTrackColor = Paper,
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = Line
            )
        )
    }
}

/** M3 sheets follow the system theme; NotCustom is always dark, so keep both system bars dark too. */
@Composable
private fun DarkSheetSystemBars() {
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window ?: return@SideEffect
        window.isNavigationBarContrastEnforced = false
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightNavigationBars = false
            isAppearanceLightStatusBars = false
        }
    }
}
