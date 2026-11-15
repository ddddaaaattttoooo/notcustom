package dev.notcustom.app.launcher.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        val icon by produceState<ImageBitmap?>(initialValue = null, app.component, state.iconPack) {
            value = viewModel.loadIcon(app)
        }
        val pinned = state.pinned.contains(app.component)
        var movePageMenuOpen by remember { mutableStateOf(false) }

        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(icon, 44, false)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Paper
                    )
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            MenuAction(if (pinned) "REMOVE FROM HOME" else "ADD TO HOME") {
                if (pinned) viewModel.unpin(app) else viewModel.pin(app)
            }
            Box {
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
            MenuAction("HIDE APP") { viewModel.hide(app) }
            MenuAction("APP INFO") { viewModel.appInfo(app) }
            MenuAction("UNINSTALL") { viewModel.uninstall(app) }
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
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LauncherSettingsSheet(
    state: LauncherState,
    viewModel: LauncherViewModel
) {
    val context = LocalContext.current
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    val hiddenApps = remember(state.hidden, state.apps) {
        state.apps.filter { it.packageName in state.hidden }
            .distinctBy { it.packageName }
    }

    ModalBottomSheet(
        onDismissRequest = viewModel::closeSettings,
        containerColor = Surface0,
        contentColor = Paper,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HOME SETTINGS", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                Text(
                    "BETA",
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingRed
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "NotCustom Home · ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
            Spacer(Modifier.height(18.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(7.dp)
                        .height(7.dp)
                        .background(
                            if (state.accessibilityEnabled) Ok else NothingRed,
                            CircleShape
                        )
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "GESTURE ACCESS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted
                    )
                    Text(
                        if (state.accessibilityEnabled) {
                            "Double-tap screen-off and swipe-down notifications are ready."
                        } else {
                            "Enable for double-tap screen-off and swipe-down notifications."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.accessibilityEnabled) Muted else Paper
                    )
                }
                TextButton(onClick = viewModel::openAccessibilitySettings) {
                    Text(
                        if (state.accessibilityEnabled) "SETTINGS" else "ENABLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.accessibilityEnabled) Muted else NothingRed
                    )
                }
            }
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

            var iconPackMenuOpen by remember { mutableStateOf(false) }
            var atAGlanceMenuOpen by remember { mutableStateOf(false) }
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

            SwitchRow("APP LABELS", state.config.showLabels) { viewModel.setShowLabels(it) }
            SwitchRow("SEARCH BAR", state.config.showSearch) { viewModel.setShowSearch(it) }
            SwitchRow("24-HOUR CLOCK", state.config.use24HourClock) { viewModel.setUse24HourClock(it) }
            SwitchRow("MONOCHROME ICONS", state.config.monochrome) { viewModel.setMonochrome(it) }

            Spacer(Modifier.height(18.dp))
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

            Spacer(Modifier.height(18.dp))
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

            Spacer(Modifier.height(18.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        roleLauncher.launch(homeRoleIntent(context) ?: homeSettingsIntent())
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Paper,
                        contentColor = Ink
                    )
                ) {
                    Text("SET AS HOME", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = viewModel::resetLayout,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Line)
                ) {
                    Text("RESET LAYOUT", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = {
                        viewModel.closeSettings()
                        context.startActivity(
                            Intent(context, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Line)
                ) {
                    Text("OPEN TWEAKS", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("CREDITS", style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(
                "ddddaaaatttoooo",
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
