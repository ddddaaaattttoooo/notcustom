package dev.notcustom.app.ui

import android.provider.Settings
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.notcustom.app.BuildConfig
import dev.notcustom.app.launcher.isDefaultHome
import dev.notcustom.app.launcher.ui.DotGlyph
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.notcustom.app.core.AnimScales
import dev.notcustom.app.launcher.LauncherActivity
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
import dev.notcustom.app.ui.theme.Warn
import kotlin.math.roundToInt

private data class AnimPreset(val label: String, val scale: Float)

private val animPresets = listOf(
    AnimPreset("OFF", 0f),
    AnimPreset("SNAP", 0.25f),
    AnimPreset("BRISK", 0.5f),
    AnimPreset("STOCK", 1f),
    AnimPreset("SMOOTH", 1.25f),
    AnimPreset("CINEMA", 2f),
    AnimPreset("ULTRA", 3f)
)

@Composable
fun NotCustomApp(viewModel: NotCustomViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isHome by remember { mutableStateOf(isDefaultHome(context)) }
    LifecycleResumeEffect(Unit) {
        isHome = isDefaultHome(context)
        // A permission granted in Settings shows up as soon as the user comes back.
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(36.dp))
            Hero()
            Spacer(Modifier.height(32.dp))

            Message(state.message) { viewModel.clearMessage() }

            HomeCard(context, isHome) { isHome = isDefaultHome(context) }
            Spacer(Modifier.height(14.dp))
            ReadyContent(state, viewModel)
            Spacer(Modifier.height(14.dp))
            SetupCard(state.shizuku, context, viewModel)

            Spacer(Modifier.height(16.dp))
            AboutCard()
            Spacer(Modifier.height(28.dp))
        }
        // Keeps scrolled cards from running under the status bar icons.
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        )
    }
}

@Composable
private fun Hero() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(96.dp)
                .background(Surface0, RoundedCornerShape(30.dp))
                .border(1.dp, Line, RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center
        ) {
            DotGlyph(Modifier.size(60.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "NOTCUSTOM",
            style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 6.sp),
            color = Paper
        )
        Spacer(Modifier.height(6.dp))
        Text("MAKE HOME YOURS", style = MaterialTheme.typography.labelSmall, color = Muted)
        Spacer(Modifier.height(14.dp))
        Pill("v${BuildConfig.VERSION_NAME}", Muted)
    }
}

@Composable
private fun Pill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = CircleShape,
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ShizukuPill(shizuku: ShizukuState) = when (shizuku) {
    ShizukuState.READY -> Pill("READY", Ok)
    ShizukuState.NOT_INSTALLED -> Pill("NOT INSTALLED", Muted)
    ShizukuState.NOT_RUNNING -> Pill("SERVICE OFF", Warn)
    ShizukuState.PERMISSION_DENIED -> Pill("NO ACCESS", Warn)
}

@Composable
private fun StepCard(
    number: String,
    title: String,
    subtitle: String,
    status: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = Surface0,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(number, style = MaterialTheme.typography.labelSmall, color = NothingRed)
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Paper,
                    modifier = Modifier.weight(1f)
                )
                status()
            }
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun Message(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    Surface(
        color = Surface1,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = Paper,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("OK", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ReadyContent(state: UiState, viewModel: NotCustomViewModel) {
    FeelCard(state, viewModel)
    Spacer(Modifier.height(14.dp))
    PhoneCard(state, viewModel)
    Spacer(Modifier.height(14.dp))
    DisplayCard(state, viewModel)
    Spacer(Modifier.height(14.dp))
    PresetCard(state, viewModel)
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = Surface0,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Paper)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeelCard(state: UiState, viewModel: NotCustomViewModel) {
    SectionCard("FEEL", "system animation speed") {
        if (!state.canSecure) {
            val context = LocalContext.current
            Locked(
                "Android only lets an app change animation speed after a one-time grant. " +
                    "From a computer, run:\n\nadb shell pm grant ${context.packageName} " +
                    "android.permission.WRITE_SECURE_SETTINGS\n\nOr connect Shizuku below once. " +
                    "You can also change it by hand in Developer options."
            ) {
                SecondaryButton("DEVELOPER OPTIONS") {
                    openSettings(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                }
            }
            return@SectionCard
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            animPresets.forEach { preset ->
                val selected = state.anim.window == preset.scale &&
                    state.anim.transition == preset.scale &&
                    state.anim.animator == preset.scale
                ChoiceChip(preset.label, selected) {
                    viewModel.setAnim(AnimScales(preset.scale, preset.scale, preset.scale))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        ScaleSlider("WINDOW", state.anim.window) {
            viewModel.setAnim(state.anim.copy(window = it))
        }
        ScaleSlider("TRANSITION", state.anim.transition) {
            viewModel.setAnim(state.anim.copy(transition = it))
        }
        ScaleSlider("ANIMATOR", state.anim.animator) {
            viewModel.setAnim(state.anim.copy(animator = it))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Android only exposes speed multipliers without root, not custom curves.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted
        )
    }
}

private val screenTimeouts = listOf(
    "15S" to 15_000, "30S" to 30_000, "1M" to 60_000,
    "2M" to 120_000, "5M" to 300_000, "10M" to 600_000
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhoneCard(state: UiState, viewModel: NotCustomViewModel) {
    val context = LocalContext.current
    val phone = state.phone
    SectionCard("PHONE", "screen, rotation and touch feedback") {
        if (!state.canSystem) {
            Locked("Allow NotCustom to modify system settings to change these from here.") {
                PrimaryButton("ALLOW") {
                    openSettings(
                        context,
                        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                    )
                }
            }
            return@SectionCard
        }
        Text("SCREEN TIMEOUT", style = MaterialTheme.typography.labelSmall, color = Muted)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            screenTimeouts.forEach { (label, ms) ->
                ChoiceChip(label, phone.screenTimeoutMs == ms) {
                    viewModel.setPhone(phone.copy(screenTimeoutMs = ms))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("TOGGLES", style = MaterialTheme.typography.labelSmall, color = Muted)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChoiceChip("AUTO-ROTATE", phone.autoRotate) {
                viewModel.setPhone(phone.copy(autoRotate = !phone.autoRotate))
            }
            ChoiceChip("TOUCH SOUNDS", phone.touchSounds) {
                viewModel.setPhone(phone.copy(touchSounds = !phone.touchSounds))
            }
            ChoiceChip("TOUCH VIBRATION", phone.touchVibration) {
                viewModel.setPhone(phone.copy(touchVibration = !phone.touchVibration))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplayCard(state: UiState, viewModel: NotCustomViewModel) {
    val density = state.density
    val physical = density?.physical ?: 420
    val tight = (physical * 0.80f).roundToInt()
    val compact = (physical * 0.92f).roundToInt()
    val roomy = (physical * 1.08f).roundToInt()
    val huge = (physical * 1.20f).roundToInt()

    val context = LocalContext.current
    SectionCard("DISPLAY", "density and text scale") {
        Text("DENSITY", style = MaterialTheme.typography.labelSmall, color = Muted)
        Spacer(Modifier.height(8.dp))
        if (!state.canDensity) {
            Locked("Android has no app setting for density. Use Display size in Settings, or connect Shizuku below.") {
                SecondaryButton("DISPLAY SIZE") {
                    openSettings(context, Intent(Settings.ACTION_DISPLAY_SETTINGS))
                }
            }
        } else FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChoiceChip("TIGHT", density?.overrideDpi == tight) {
                viewModel.setDensity(tight)
            }
            ChoiceChip("COMPACT", density?.overrideDpi == compact) {
                viewModel.setDensity(compact)
            }
            ChoiceChip("DEFAULT", density?.overrideDpi == null) {
                viewModel.setDensity(null)
            }
            ChoiceChip("ROOMY", density?.overrideDpi == roomy) {
                viewModel.setDensity(roomy)
            }
            ChoiceChip("XL", density?.overrideDpi == huge) {
                viewModel.setDensity(huge)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.canDensity) Text(
            "Current: ${density?.effective ?: "-"} dpi",
            style = MaterialTheme.typography.bodySmall,
            color = Muted
        )

        Spacer(Modifier.height(18.dp))
        Text("FONT WEIGHT", style = MaterialTheme.typography.labelSmall, color = Muted)
        Spacer(Modifier.height(8.dp))
        if (!state.canSecure) {
            Text(
                "Needs the same one-time grant as animation speed (see FEEL).",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        } else {
            var weight by remember(state.fontWeight) { mutableStateOf(state.fontWeight.toFloat()) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("THIN", style = MaterialTheme.typography.labelSmall, color = Muted)
                Text(
                    "${400 + weight.roundToInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Paper
                )
                Text("BOLD", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Slider(
                value = weight,
                onValueChange = { weight = it },
                onValueChangeFinished = { viewModel.setFontWeight(weight.roundToInt()) },
                valueRange = -300f..300f,
                steps = 11,
                colors = SliderDefaults.colors(
                    thumbColor = Paper,
                    activeTrackColor = Paper,
                    inactiveTrackColor = Line
                )
            )
        }

        Spacer(Modifier.height(18.dp))
        Text("FONT SIZE", style = MaterialTheme.typography.labelSmall, color = Muted)
        Spacer(Modifier.height(8.dp))
        if (!state.canSystem) {
            Spacer(Modifier.height(8.dp))
            Locked("Allow NotCustom to modify system settings to change text size from here.") {
                PrimaryButton("ALLOW") {
                    openSettings(
                        context,
                        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                    )
                }
            }
            return@SectionCard
        }
        var font by remember(state.fontScale) { mutableStateOf(state.fontScale) }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("SYSTEM TEXT", style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(
                "${(font * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = Paper
            )
        }
        Slider(
            value = font,
            onValueChange = { font = it },
            onValueChangeFinished = { viewModel.setFontScale(font) },
            valueRange = 0.7f..1.6f,
            steps = 35,
            colors = SliderDefaults.colors(
                thumbColor = Paper,
                activeTrackColor = Paper,
                inactiveTrackColor = Line
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetCard(state: UiState, viewModel: NotCustomViewModel) {
    SectionCard("PRESETS", "save the current feel, reapply in one tap") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = viewModel::saveSnapshot,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Paper,
                    contentColor = Ink
                )
            ) {
                Text("SAVE", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = viewModel::applySnapshot,
                enabled = state.hasSnapshot,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Line)
            ) {
                Text("APPLY", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = viewModel::resetAll,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Line)
            ) {
                Text(
                    "RESET",
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingRed
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Add the NotCustom tile to Quick Settings to apply your saved preset without opening the app.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupCard(
    shizuku: ShizukuState,
    context: Context,
    viewModel: NotCustomViewModel
) {
    StepCard(
        "02",
        "SYSTEM TWEAKS",
        "optional · density, and animation access in one tap",
        status = { ShizukuPill(shizuku) }
    ) {
        val body = when (shizuku) {
            ShizukuState.NOT_INSTALLED ->
                "Not needed for text size. Shizuku adds density and grants animation access once. Install it, enable Wireless debugging in Developer options, then start the Shizuku service."
            ShizukuState.NOT_RUNNING ->
                "Shizuku is installed but its service is off. Open Shizuku and start it over Wireless debugging."
            ShizukuState.PERMISSION_DENIED ->
                "Shizuku is running. Grant NotCustom access so it can apply system tweaks."
            ShizukuState.READY ->
                "Connected through Shizuku. Animation access is granted, so it keeps working after Shizuku stops."
        }
        Text(body, style = MaterialTheme.typography.bodySmall, color = Muted)
        if (shizuku == ShizukuState.READY) return@StepCard
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (shizuku) {
                ShizukuState.NOT_INSTALLED -> PrimaryButton("GET SHIZUKU") {
                    openUrl(context, "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
                }
                ShizukuState.NOT_RUNNING -> PrimaryButton("OPEN SHIZUKU") {
                    openPackage(context, "moe.shizuku.privileged.api")
                }
                ShizukuState.PERMISSION_DENIED -> {
                    PrimaryButton("GRANT ACCESS", viewModel::requestPermission)
                    SecondaryButton("OPEN SHIZUKU") { openPackage(context, "moe.shizuku.privileged.api") }
                }
                ShizukuState.READY -> Unit
            }
            SecondaryButton("REFRESH", viewModel::refresh)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Shizuku stops on reboot. Start it again and NotCustom reconnects automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Locked(text: String, buttons: @Composable () -> Unit) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Muted)
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
}

private fun openSettings(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
        .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Paper)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AboutCard() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text("ABOUT", style = MaterialTheme.typography.labelSmall, color = Muted)
        Spacer(Modifier.height(8.dp))
        Text(
            "NotCustom Home runs on any Android 12+ phone with no special access. System tweaks go " +
                "through Shizuku and only write reversible Settings values and a display-density " +
                "override: no root, no system files, nothing that can brick a phone.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeCard(context: Context, isHome: Boolean, onRoleResult: () -> Unit) {
    val role = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onRoleResult() }
    val openHome = { context.startActivity(Intent(context, LauncherActivity::class.java)) }
    StepCard(
        "01",
        "HOME SCREEN",
        if (isHome) "NotCustom is your home screen" else "make NotCustom your home screen",
        status = { if (isHome) Pill("ACTIVE", Ok) else Pill("NOT SET", Warn) }
    ) {
        Text(
            "Pages, folders, widgets, dock, smart drawer, icon packs and notification dots. " +
                "No root, no Shizuku needed.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isHome) {
                PrimaryButton("OPEN HOME", openHome)
            } else {
                PrimaryButton("SET AS HOME") {
                    role.launch(homeRoleIntent(context) ?: homeSettingsIntent())
                }
                SecondaryButton("PREVIEW", openHome)
            }
            SecondaryButton("HOME SETTINGS") {
                context.startActivity(
                    Intent(context, LauncherActivity::class.java)
                        .putExtra(LauncherActivity.EXTRA_OPEN_SETTINGS, true)
                )
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) Paper else Color.Transparent,
        contentColor = if (selected) Ink else Paper,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (selected) Paper else Line)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ScaleSlider(label: String, value: Float, onDone: (Float) -> Unit) {
    var local by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(formatScale(local), style = MaterialTheme.typography.labelSmall, color = Paper)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onDone(local) },
            valueRange = 0f..3f,
            steps = 59,
            colors = SliderDefaults.colors(
                thumbColor = Paper,
                activeTrackColor = Paper,
                inactiveTrackColor = Line
            )
        )
    }
}

private fun formatScale(value: Float): String =
    if (value == 0f) "OFF" else "${(value * 100).roundToInt()}%"

private fun openUrl(context: Context, url: String) {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun openPackage(context: Context, pkg: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        ?: Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
        )
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
