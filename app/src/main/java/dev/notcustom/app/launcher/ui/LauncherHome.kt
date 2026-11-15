package dev.notcustom.app.launcher.ui

import android.graphics.Rect
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetProviderInfo
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.notcustom.app.launcher.LauncherApp
import dev.notcustom.app.launcher.LauncherConfig
import dev.notcustom.app.launcher.LauncherViewModel
import dev.notcustom.app.launcher.ReturnTransition
import dev.notcustom.app.ui.theme.Muted
import dev.notcustom.app.ui.theme.NothingRed
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun LauncherRoot(viewModel: LauncherViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appsByComponent = remember(state.apps) { state.apps.associateBy { it.component } }
    val pinned = remember(state.pinned, state.pinPositions, appsByComponent) {
        state.pinned.mapNotNull { appsByComponent[it] }
            .sortedBy { state.pinPositions[it.component] ?: Int.MAX_VALUE }
    }
    val homeSlotCount = remember(state.config.columns, state.pinPositions) {
        val columns = state.config.columns
        val needed = maxOf(columns * 3, (state.pinPositions.values.maxOrNull() ?: -1) + 1)
        ((needed + columns - 1) / columns) * columns
    }
    val dock = remember(state.dock, appsByComponent) {
        state.dock.mapNotNull { appsByComponent[it] }
    }
    val visibleApps = remember(state.apps, state.hidden) {
        state.apps.filter { it.packageName !in state.hidden }
    }
    val returningKey = state.returning?.app?.key
    val context = LocalContext.current

    val widgetConfig = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onWidgetConfigured(result.resultCode)
    }
    val installWidget: (AppWidgetProviderInfo) -> Unit = { info ->
        val intent = viewModel.prepareWidget(info)
        if (intent != null) {
            runCatching { widgetConfig.launch(intent) }
                .onFailure { viewModel.onWidgetConfigFailed() }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    BackHandler(enabled = state.drawerOpen) { viewModel.closeDrawer() }

    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 64.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
            .pointerInput(state.drawerOpen) {
                if (state.drawerOpen) return@pointerInput
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        total += dragAmount
                        if (total > swipeThreshold) {
                            viewModel.openNotificationsFromHome()
                            total = 0f
                            change.consume()
                        } else if (total < -swipeThreshold) {
                            viewModel.openDrawer()
                            total = 0f
                            change.consume()
                        }
                    }
                )
            }
            .pointerInput(
                state.drawerOpen,
                state.homeMenuOpen,
                state.settingsOpen,
                state.widgetPickerOpen,
                state.menuApp
            ) {
                if (state.drawerOpen || state.homeMenuOpen || state.settingsOpen ||
                    state.widgetPickerOpen || state.menuApp != null
                ) {
                    return@pointerInput
                }
                detectTapGestures(
                    onDoubleTap = { viewModel.lockScreen() },
                    onLongPress = { viewModel.openHomeMenu() }
                )
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.50f),
                            Color.Black.copy(alpha = 0.16f),
                            Color.Black.copy(alpha = 0.62f)
                        )
                    )
                )
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(NothingRed.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(size.width * 0.85f, size.height * 0.06f),
                            radius = size.width * 0.9f
                        )
                    )
                }
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            ClockHeader(onOpenSettings = viewModel::openSettings)
            Spacer(Modifier.height(20.dp))
            HomeGrid(
                pinned,
                state.widgets,
                state.config,
                viewModel,
                returningKey,
                pinPositions = state.pinPositions,
                iconPackKey = state.iconPack,
                slotCount = homeSlotCount,
                scrollable = state.widgets.isNotEmpty() ||
                    homeSlotCount > state.config.columns * 3,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(state.drawerOpen) {
                        if (state.drawerOpen) return@pointerInput
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                total += dragAmount
                                if (total > swipeThreshold) {
                                    viewModel.openNotificationsFromHome()
                                    total = 0f
                                    change.consume()
                                } else if (total < -swipeThreshold) {
                                    viewModel.openDrawer()
                                    total = 0f
                                    change.consume()
                                }
                            }
                        )
                    }
            ) {
                Column {
                    if (state.config.showSearch) {
                        SearchPill { viewModel.openDrawer() }
                        Spacer(Modifier.height(12.dp))
                    }
                    DockRow(dock, state.config, viewModel, returningKey, state.iconPack)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        AnimatedVisibility(
            visible = state.drawerOpen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(260, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(150)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(120))
        ) {
            AppDrawer(visibleApps, state, viewModel)
        }

        state.returning?.let { transition ->
            ReturnGhost(transition, rootSize, viewModel, state.iconPack)
        }

        state.menuApp?.let { app ->
            AppMenuSheet(app, state, viewModel)
        }

        if (state.homeMenuOpen) {
            HomeMenuSheet(viewModel)
        }

        if (state.settingsOpen) {
            LauncherSettingsSheet(state, viewModel)
        }

        if (state.widgetPickerOpen) {
            WidgetPickerSheet(viewModel, installWidget)
        }
    }
}

@Composable
private fun HomeGrid(
    apps: List<LauncherApp>,
    widgets: List<Int>,
    config: LauncherConfig,
    viewModel: LauncherViewModel,
    returningKey: String?,
    pinPositions: Map<android.content.ComponentName, Int>,
    iconPackKey: String?,
    slotCount: Int,
    scrollable: Boolean,
    modifier: Modifier = Modifier
) {
    val slotBounds = remember { mutableStateMapOf<Int, Rect>() }
    val appsBySlot = remember(apps, pinPositions) {
        apps.associateBy { pinPositions[it.component] ?: 0 }
    }
    var draggedApp by remember { mutableStateOf<LauncherApp?>(null) }
    var dragOriginBounds by remember { mutableStateOf<Rect?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    if (apps.isEmpty() && widgets.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Swipe up for apps. Long-press the home screen for widgets and settings.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(28.dp)
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(config.columns),
        modifier = modifier.fillMaxWidth(),
        userScrollEnabled = scrollable,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)
    ) {
        widgets.forEach { id ->
            item(key = "widget-$id", span = { GridItemSpan(maxLineSpan) }) {
                WidgetView(id, viewModel.widgetHost)
            }
        }
        if (apps.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Long-press the home screen for widgets and settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(slotCount, key = { "home-slot-$it" }) { slot ->
                val app = appsBySlot[slot]
                val cellHeight = config.iconSize.dp + if (config.showLabels) 34.dp else 18.dp
                DisposableEffect(slot) {
                    onDispose { slotBounds.remove(slot) }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = cellHeight)
                        .onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInRoot()
                            slotBounds[slot] = Rect(
                                bounds.left.roundToInt(), bounds.top.roundToInt(),
                                bounds.right.roundToInt(), bounds.bottom.roundToInt()
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (app == null) {
                        Box(
                            Modifier.fillMaxWidth().height(cellHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            if (draggedApp != null) {
                                DotGlyph(
                                    Modifier.size(18.dp),
                                    tint = Color.White.copy(alpha = 0.10f),
                                    center = NothingRed.copy(alpha = 0.20f)
                                )
                            }
                        }
                    } else {
                        AppCell(
                            app,
                            config,
                            viewModel,
                            modifier = Modifier.fillMaxWidth(),
                            fromHome = true,
                            iconPackKey = iconPackKey,
                            isReturning = returningKey == app.key,
                            isDragging = draggedApp?.key == app.key,
                            dragOffset = dragOffset,
                            onDragStart = {
                                draggedApp = app
                                dragOriginBounds = slotBounds[slot]?.let { Rect(it) }
                                dragOffset = Offset.Zero
                            },
                            onDrag = { dragOffset += it },
                            onDragEnd = {
                                val bounds = dragOriginBounds
                                if (bounds != null && hypot(
                                        dragOffset.x.toDouble(), dragOffset.y.toDouble()
                                    ) > 24.0
                                ) {
                                    val x = (bounds.exactCenterX() + dragOffset.x).roundToInt()
                                    val y = (bounds.exactCenterY() + dragOffset.y).roundToInt()
                                    val targetSlot = slotBounds.entries.firstOrNull {
                                        it.value.contains(x, y)
                                    }?.key ?: slotBounds.entries.minByOrNull {
                                        hypot(
                                            (it.value.exactCenterX() - x).toDouble(),
                                            (it.value.exactCenterY() - y).toDouble()
                                        )
                                    }?.key
                                    if (targetSlot != null) {
                                        viewModel.movePinnedToSlot(app, targetSlot)
                                    }
                                }
                                draggedApp = null
                                dragOriginBounds = null
                                dragOffset = Offset.Zero
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetView(id: Int, host: AppWidgetHost) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            runCatching {
                val info = android.appwidget.AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id)
                host.createView(ctx, id, info).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
            }.getOrElse {
                View(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun ReturnGhost(
    transition: ReturnTransition,
    screenSize: IntSize,
    viewModel: LauncherViewModel,
    iconPackKey: String?
) {
    val icon by produceState<ImageBitmap?>(initialValue = null, transition.app.component, iconPackKey) {
        value = viewModel.loadIcon(transition.app)
    }
    val progress = remember(transition) { Animatable(0f) }
    LaunchedEffect(transition) {
        progress.snapTo(0f)
        progress.animateTo(
            1f,
            tween(durationMillis = 260, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
        )
    }
    val bounds = transition.bounds
    if (bounds.width() <= 0 || bounds.height() <= 0) return
    if (screenSize.width <= 0 || screenSize.height <= 0) return
    val cover = max(
        screenSize.width.toFloat() / bounds.width(),
        screenSize.height.toFloat() / bounds.height()
    )
    val targetCx = bounds.left + bounds.width() / 2f
    val targetCy = bounds.top + bounds.height() / 2f
    val density = LocalDensity.current

    Box(
        Modifier
            .offset { IntOffset(bounds.left, bounds.top) }
            .size(
                with(density) { bounds.width().toDp() },
                with(density) { bounds.height().toDp() }
            )
            .graphicsLayer {
                val p = progress.value
                scaleX = cover + (1f - cover) * p
                scaleY = cover + (1f - cover) * p
                translationX = (screenSize.width / 2f - targetCx) * (1f - p)
                translationY = (screenSize.height / 2f - targetCy) * (1f - p)
                alpha = 0.55f + 0.45f * p
            }
    ) {
        if (icon != null) {
            Image(
                bitmap = icon!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
