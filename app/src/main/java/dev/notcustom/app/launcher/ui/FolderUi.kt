package dev.notcustom.app.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.notcustom.app.launcher.HomeFolder
import dev.notcustom.app.launcher.LauncherApp
import dev.notcustom.app.launcher.LauncherConfig
import dev.notcustom.app.launcher.LauncherViewModel
import dev.notcustom.app.ui.theme.Muted
import dev.notcustom.app.ui.theme.NothingRed
import dev.notcustom.app.ui.theme.Paper
import dev.notcustom.app.ui.theme.Surface0

/** 2x2 preview of a folder's first apps, drawn inside the normal icon footprint. */
@Composable
fun FolderIcon(
    folder: LauncherApp,
    sizeDp: Int,
    monochrome: Boolean,
    viewModel: LauncherViewModel,
    iconPackKey: String?
) {
    val previews = remember(folder.folderApps) {
        folder.folderApps.take(4).mapNotNull { viewModel.appFor(it) }
    }
    val mini = (sizeDp * 0.36f).dp
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(percent = 28))
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(percent = 28)),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            previews.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { app -> MiniIcon(app, mini, monochrome, viewModel, iconPackKey) }
                }
            }
        }
    }
}

@Composable
private fun MiniIcon(
    app: LauncherApp,
    size: androidx.compose.ui.unit.Dp,
    monochrome: Boolean,
    viewModel: LauncherViewModel,
    iconPackKey: String?
) {
    val icon by produceState<ImageBitmap?>(viewModel.cachedIcon(app), app.key, iconPackKey) {
        value = viewModel.loadIcon(app)
    }
    Box(Modifier.size(size)) {
        AppIcon(icon, size.value.toInt(), monochrome)
    }
}

/** Open folder: centred panel over a dimmed Home. Tap outside or Back closes it. */
@Composable
fun FolderOverlay(
    folder: HomeFolder,
    appsByComponent: Map<android.content.ComponentName, LauncherApp>,
    config: LauncherConfig,
    viewModel: LauncherViewModel,
    iconPackKey: String?,
    hidden: Boolean = false,
    onAppDragStart: (LauncherApp, android.graphics.Rect) -> Unit = { _, _ -> },
    onAppDrag: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    onAppDragEnd: () -> Unit = {},
    onAppDragCancel: () -> Unit = {}
) {
    val apps = remember(folder.apps, appsByComponent) {
        folder.apps.mapNotNull { appsByComponent[it] }
    }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(folder.id) {
        enter.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        )
    }
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    val focus = LocalFocusManager.current
    val columns = config.columns.coerceAtMost(4)
    Box(
        Modifier
            .fillMaxSize()
            // Stay composed while an app is dragged out, or its gesture would be cancelled.
            .graphicsLayer { alpha = if (hidden) 0f else enter.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                viewModel.renameFolder(folder.id, name)
                viewModel.closeFolder()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val s = 0.86f + 0.14f * enter.value
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(30.dp))
                .background(Surface0.copy(alpha = 0.96f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(30.dp))
                // Swallow taps on the panel so they don't close the folder.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { focus.clearFocus() }
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = Paper,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(NothingRed),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    viewModel.renameFolder(folder.id, name)
                    focus.clearFocus()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) viewModel.renameFolder(folder.id, name) }
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.width(24.dp).height(2.dp).background(NothingRed))
            Spacer(Modifier.height(14.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(apps, key = { it.key }) { app ->
                    AppCell(
                        app = app,
                        config = config,
                        viewModel = viewModel,
                        showLabel = true,
                        fromHome = false,
                        dragEnabled = true,
                        iconPackKey = iconPackKey,
                        onDragStart = { bounds -> bounds?.let { onAppDragStart(app, it) } },
                        onDrag = onAppDrag,
                        onDragEnd = onAppDragEnd,
                        onDragCancel = onAppDragCancel
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.Text(
                "Hold an app to drag it out, or for options",
                style = MaterialTheme.typography.labelSmall,
                color = Muted
            )
        }
    }
}
