package dev.notcustom.app.launcher.ui

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Rect
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.notcustom.app.launcher.HOME_ROWS
import dev.notcustom.app.launcher.HOME_GRID_ROWS
import dev.notcustom.app.launcher.MAX_DOCK_ITEMS
import dev.notcustom.app.launcher.MAX_HOME_PAGES
import dev.notcustom.app.launcher.LauncherApp
import dev.notcustom.app.launcher.LauncherConfig
import dev.notcustom.app.launcher.LauncherViewModel
import dev.notcustom.app.launcher.WidgetPlacement
import dev.notcustom.app.launcher.WidgetSize
import dev.notcustom.app.launcher.ReturnTransition
import dev.notcustom.app.launcher.isShortcut
import dev.notcustom.app.launcher.toItem
import dev.notcustom.app.ui.theme.Muted
import dev.notcustom.app.ui.theme.NothingRed
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun LauncherRoot(viewModel: LauncherViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appsByComponent = remember(state.apps, state.pinnedShortcuts, state.folders) {
        state.apps.groupBy { it.component }.mapValues { (_, list) ->
            list.firstOrNull { it.user == android.os.Process.myUserHandle() } ?: list.first()
        } + state.pinnedShortcuts.associateBy { it.component } +
            state.folders.values.map { it.toItem() }.associateBy { it.component }
    }
    val pinned = remember(state.pinned, state.pinPositions, appsByComponent) {
        state.pinned.mapNotNull { appsByComponent[it] }
            .sortedBy { state.pinPositions[it.component] ?: Int.MAX_VALUE }
    }
    val dockApps = remember(state.dock, appsByComponent) {
        state.dock.mapNotNull { appsByComponent[it] }
    }
    val visibleApps = remember(state.apps, state.hidden) {
        state.apps.filter { it.packageName !in state.hidden }
    }
    val iconKey = state.iconPack.orEmpty() + "#" + state.iconRevision
    val returningKey = state.returning?.app?.key
    val context = LocalContext.current
    val dropBounds = remember { mutableMapOf<HomeDropAction, Rect>() }
    val drawerDropBounds = remember { mutableMapOf<HomeDropAction, Rect>() }
    var pagerOffset by remember { mutableFloatStateOf(0f) }
    val wallpaper by produceState<ImageBitmap?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { loadWallpaper(context) }
    }

    val widgetConfig = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onWidgetResult(result.resultCode, result.data)
    }
    val installWidget: (AppWidgetProviderInfo) -> Unit = { info ->
        viewModel.prepareWidget(info)
    }
    LaunchedEffect(state.widgetConfigRequest) {
        state.widgetConfigRequest?.let { intent ->
            runCatching { widgetConfig.launch(intent) }
                .onFailure { viewModel.onWidgetConfigFailed() }
            viewModel.clearWidgetConfigRequest()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    BackHandler(enabled = state.drawerOpen) { viewModel.closeDrawer() }
    BackHandler(enabled = state.openFolder != null) { viewModel.closeFolder() }

    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 64.dp.toPx() }
    val homeSlotBounds = remember { mutableStateMapOf<Int, Rect>() }
    val dockSlotBounds = remember { mutableStateMapOf<Int, Rect>() }
    var homeDragActive by remember { mutableStateOf(false) }
    var homeDrag by remember { mutableStateOf<HomeDrag?>(null) }
    var widgetDrag by remember { mutableStateOf<WidgetDrag?>(null) }
    var drawerDrag by remember { mutableStateOf<DrawerDrag?>(null) }
    var selectedWidgetId by remember { mutableStateOf<Int?>(null) }
    val widgetPressGate = remember { WidgetPressGate() }
    val homeEditing = homeDrag != null || widgetDrag != null
    val haptics = LocalHapticFeedback.current
    // Buzz on pickup, and tick whenever the drag snaps to a new slot or drop target.
    val picked = homeDrag != null || widgetDrag != null || drawerDrag != null
    LaunchedEffect(picked) {
        if (picked) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val dragTarget = homeDrag?.let { listOf(it.hoverSlot, it.hoverDockSlot, it.actionTarget) }
        ?: drawerDrag?.let { listOf(it.hoverSlot, it.hoverDockSlot, it.actionTarget) }
        ?: widgetDrag?.let { listOf(it.hoverRemove) }
    var lastDragTarget by remember { mutableStateOf<List<Any?>?>(null) }
    LaunchedEffect(dragTarget) {
        if (dragTarget != null && lastDragTarget != null && dragTarget != lastDragTarget) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastDragTarget = dragTarget
    }
    val homeScale by animateFloatAsState(
        targetValue = if (homeEditing) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "homeEditScale"
    )
    // Narrow enough that the rightmost column stays droppable; pages only flip after a dwell.
    val edgeThreshold = with(density) { 28.dp.toPx() }
    val drawerDragActive = drawerDrag?.active == true
    val homeDragActiveState = rememberUpdatedState(homeDragActive)

    LaunchedEffect(selectedWidgetId, state.widgets) {
        if (selectedWidgetId != null && selectedWidgetId !in state.widgets) {
            selectedWidgetId = null
        }
    }
    LaunchedEffect(
        state.drawerOpen,
        state.homeMenuOpen,
        state.settingsOpen,
        state.widgetPickerOpen,
        state.menuApp,
        widgetDrag
    ) {
        if (state.drawerOpen || state.homeMenuOpen || state.settingsOpen ||
            state.widgetPickerOpen || state.menuApp != null || widgetDrag != null
        ) {
            selectedWidgetId = null
        }
    }
    BackHandler(enabled = selectedWidgetId != null) { selectedWidgetId = null }

    LaunchedEffect(homeDrag, homeDrag?.edgeDirection, state.currentPage, state.pageCount) {
        val drag = homeDrag ?: return@LaunchedEffect
        if (drag.edgeDirection == 0 || drag.edgeConsumed || drag.actionTarget != null) {
            return@LaunchedEffect
        }
        delay(420)
        if (homeDrag !== drag || drag.edgeDirection == 0 || drag.edgeConsumed) {
            return@LaunchedEffect
        }
        val target = state.currentPage + drag.edgeDirection
        drag.edgeConsumed = true
        when {
            target in 0 until state.pageCount -> viewModel.setCurrentPage(target)
            target >= state.pageCount && state.pageCount < MAX_HOME_PAGES -> viewModel.addPage()
        }
    }

    LaunchedEffect(widgetDrag, widgetDrag?.edgeDirection, state.currentPage, state.pageCount) {
        val drag = widgetDrag ?: return@LaunchedEffect
        if (drag.hoverRemove || drag.edgeDirection == 0 || drag.edgeConsumed) {
            return@LaunchedEffect
        }
        delay(420)
        if (widgetDrag !== drag || drag.hoverRemove || drag.edgeDirection == 0 ||
            drag.edgeConsumed
        ) {
            return@LaunchedEffect
        }
        val target = state.currentPage + drag.edgeDirection
        drag.edgeConsumed = true
        when {
            target in 0 until state.pageCount -> viewModel.setCurrentPage(target)
            target >= state.pageCount && state.pageCount < MAX_HOME_PAGES -> viewModel.addPage()
        }
    }

    val updateHomeDrag: (Offset) -> Unit = { delta ->
        homeDrag?.let { drag ->
            drag.offset += delta
            drag.travelled = maxOf(drag.travelled, drag.offset.getDistance())
            val pageSize = state.config.columns * HOME_ROWS
            val x = drag.origin.exactCenterX() + drag.offset.x
            val y = drag.origin.exactCenterY() + drag.offset.y
            val action = drag.actions.firstOrNull { action ->
                dropBounds[action]?.contains(x.roundToInt(), y.roundToInt()) == true
            }
            if (action != drag.actionTarget) drag.actionTarget = action
            if (action != null) {
                drag.hoverSlot = -1
                drag.hoverDockSlot = -1
                drag.edgeDirection = 0
                drag.edgeConsumed = false
            } else {
                val dockTarget = dockSlotBounds.entries.firstOrNull {
                    it.value.contains(x.roundToInt(), y.roundToInt())
                }?.key ?: -1
                val direction = if (dockTarget >= 0) 0 else when {
                    x < edgeThreshold && state.currentPage > 0 -> -1
                    x > rootSize.width - edgeThreshold && state.pageCount < MAX_HOME_PAGES -> 1
                    else -> 0
                }
                if (direction != drag.edgeDirection) {
                    drag.edgeDirection = direction
                    drag.edgeConsumed = false
                }
                val target = if (dockTarget >= 0) {
                    -1
                } else {
                    homeSlotBounds.entries.firstOrNull {
                        it.key / pageSize == viewModel.state.value.currentPage &&
                            it.value.contains(x.roundToInt(), y.roundToInt())
                    }?.key ?: -1
                }
                if (target != drag.hoverSlot) drag.hoverSlot = target
                if (dockTarget != drag.hoverDockSlot) drag.hoverDockSlot = dockTarget
            }
        }
    }

    val updateWidgetTarget: (WidgetDrag) -> Unit = { drag ->
        val current = viewModel.state.value
        val size = current.widgetSizes[drag.id] ?: WidgetSize(1, 1)
        val page = current.currentPage
        val columns = current.config.columns
        val pageSize = columns * HOME_ROWS
        val x = drag.origin.exactCenterX() + drag.offset.x
        val y = drag.origin.exactCenterY() + drag.offset.y
        val slots = homeSlotBounds.entries.filter { it.key / pageSize == page }
        val anchor = slots.firstOrNull {
            it.value.contains(x.roundToInt(), y.roundToInt())
        } ?: slots.minByOrNull {
            hypot(
                (it.value.exactCenterX() - x).toDouble(),
                (it.value.exactCenterY() - y).toDouble()
            )
        }
        if (drag.hoverRemove || anchor == null) {
            drag.targetPage = -1
        } else {
            val inPage = anchor.key % pageSize
            val anchorColumn = inPage % columns
            val anchorRow = inPage / columns
            val column = (anchorColumn - (size.columns - 1) / 2)
                .coerceIn(0, (columns - size.columns).coerceAtLeast(0))
            val row = (anchorRow - (size.rows - 1) / 2)
                .coerceIn(0, (HOME_GRID_ROWS - size.rows).coerceAtLeast(0))
            drag.targetPage = page
            drag.targetColumn = column
            drag.targetRow = row
            drag.targetValid = viewModel.widgetPlacementFree(drag.id, page, column, row)
        }
    }

    LaunchedEffect(widgetDrag, state.currentPage) {
        val drag = widgetDrag ?: return@LaunchedEffect
        delay(80)
        updateWidgetTarget(drag)
    }

    val onDrawerAppDrag: (Offset) -> Unit = { delta ->
                    drawerDrag?.let { drag ->
                        drag.offset += delta
                        if (!drag.active && drag.offset.getDistance() > swipeThreshold * 0.25f) {
                            drag.active = true
                        }
                        if (drag.active) {
                            val x = drag.origin.exactCenterX() + drag.offset.x
                            val y = drag.origin.exactCenterY() + drag.offset.y
                            val action = drawerDropBounds.entries.firstOrNull {
                                it.value.contains(x.roundToInt(), y.roundToInt())
                            }?.key
                            if (action != drag.actionTarget) drag.actionTarget = action
                            if (action != null) {
                                drag.hoverDockSlot = -1
                                drag.hoverSlot = -1
                            } else {
                                val pageSize = state.config.columns * HOME_ROWS
                                drag.hoverDockSlot = dockSlotBounds.entries.firstOrNull {
                                    it.value.contains(x.roundToInt(), y.roundToInt())
                                }?.key ?: -1
                                drag.hoverSlot = homeSlotBounds.entries.firstOrNull {
                                    it.key / pageSize == state.currentPage &&
                                        it.value.contains(x.roundToInt(), y.roundToInt())
                                }?.key ?: -1
                            }
                        }
                    }
                }
    val onDrawerAppDragEnd: () -> Unit = {
                    val drag = drawerDrag
                    if (drag != null && drag.active) {
                        val x = drag.origin.exactCenterX() + drag.offset.x
                        val y = drag.origin.exactCenterY() + drag.offset.y
                        val action = drag.actionTarget
                        val direction = when {
                            x < edgeThreshold && state.currentPage > 0 -> -1
                            x > rootSize.width - edgeThreshold -> 1
                            else -> 0
                        }
                        val dockTarget = dockSlotBounds.entries.firstOrNull {
                            it.value.contains(x.roundToInt(), y.roundToInt())
                        }?.key
                        // A folder app leaves its folder only once it lands somewhere new.
                        if (drag.fromFolder && action == null) viewModel.extractFromFolder(drag.app)
                        if (action != null) {
                            when (action) {
                                HomeDropAction.Uninstall -> {
                                    viewModel.uninstall(drag.app)
                                    viewModel.closeDrawer()
                                }
                                HomeDropAction.Remove -> viewModel.extractFromFolder(drag.app)
                                HomeDropAction.Cancel -> Unit
                                else -> Unit
                            }
                        } else if (dockTarget != null) {
                            viewModel.addToDock(drag.app, dockTarget)
                        } else if (direction != 0) {
                            viewModel.addToAdjacentPage(drag.app, direction)
                        } else {
                            val pageSize = state.config.columns * HOME_ROWS
                            val target = homeSlotBounds.entries.firstOrNull {
                                it.key / pageSize == state.currentPage &&
                                    it.value.contains(x.roundToInt(), y.roundToInt())
                            }?.key
                            if (target != null) {
                                viewModel.addToSlot(drag.app, target)
                            } else {
                                viewModel.addToPage(drag.app, state.currentPage)
                            }
                        }
                        viewModel.closeDrawer()
                    } else if (drag != null) {
                        viewModel.showMenu(drag.app)
                    }
                    if (drag?.fromFolder == true && drag.active) viewModel.closeFolder()
                    drawerDrag = null
                }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
            .pointerInput(
                state.drawerOpen,
                state.homeMenuOpen,
                state.settingsOpen,
                state.widgetPickerOpen,
                state.menuApp,
                state.openFolder,
                widgetDrag,
                swipeThreshold
            ) {
                if (state.drawerOpen || state.homeMenuOpen || state.settingsOpen ||
                    state.widgetPickerOpen || state.menuApp != null || widgetDrag != null ||
                    state.openFolder != null
                ) {
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    var vertical = 0f
                    var horizontal = 0f
                    var triggered = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUp()) break
                        if (widgetPressGate.down) break
                        val delta = change.positionChange()
                        vertical += delta.y
                        horizontal += abs(delta.x)
                        if (!triggered && homeDragActiveState.value) break
                        if (!triggered && abs(vertical) > swipeThreshold &&
                            abs(vertical) > horizontal * 1.15f
                        ) {
                            triggered = true
                            if (vertical < 0f) {
                                viewModel.openDrawer()
                            } else {
                                viewModel.openNotificationsFromHome()
                            }
                        }
                        if (triggered) change.consume()
                    }
                }
            }
            .pointerInput(
                state.drawerOpen,
                state.homeMenuOpen,
                state.settingsOpen,
                state.widgetPickerOpen,
                state.menuApp,
                state.openFolder,
                widgetDrag
            ) {
                if (state.drawerOpen || state.homeMenuOpen || state.settingsOpen ||
                    state.widgetPickerOpen || state.menuApp != null || widgetDrag != null ||
                    state.openFolder != null
                ) {
                    return@pointerInput
                }
                detectTapGestures(
                    onTap = { selectedWidgetId = null },
                    onDoubleTap = { viewModel.lockScreen() },
                    onLongPress = {
                        if (!widgetPressGate.down) viewModel.openHomeMenu()
                    }
                )
            }
    ) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (state.pageCount > 1) {
                            val fraction = (pagerOffset / (state.pageCount - 1)).coerceIn(0f, 1f)
                            translationX = -(fraction - 0.5f) * size.width * 0.10f
                        }
                        scaleX = 1.06f
                        scaleY = 1.06f
                    }
            )
        }
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
                .graphicsLayer {
                    scaleX = homeScale
                    scaleY = homeScale
                }
        ) {
            Spacer(Modifier.height(8.dp))
            AtAGlanceHeader(
                style = state.config.atAGlanceStyle,
                use24HourClock = state.config.use24HourClock,
                showEvents = state.config.showEvents,
                onOpenSettings = viewModel::openSettings
            )
            Spacer(Modifier.height(20.dp))
            HomePager(
                apps = pinned,
                widgets = state.widgets,
                widgetSizes = state.widgetSizes,
                widgetPlacements = state.widgetPlacements,
                selectedWidgetId = selectedWidgetId,
                widgetPressGate = widgetPressGate,
                onWidgetSelect = { selectedWidgetId = it },
                onWidgetResize = viewModel::resizeWidget,
                onWidgetDragStart = { id, origin ->
                    widgetDrag = WidgetDrag(id, origin, state.currentPage)
                },
                onWidgetDrag = { delta ->
                    widgetDrag?.let { drag ->
                        drag.offset += delta
                        val x = drag.origin.exactCenterX() + drag.offset.x
                        val y = drag.origin.exactCenterY() + drag.offset.y
                        drag.hoverRemove = dropBounds[HomeDropAction.Remove]
                            ?.contains(x.roundToInt(), y.roundToInt()) == true
                        val direction = if (drag.hoverRemove) 0 else when {
                            x < edgeThreshold && state.currentPage > 0 -> -1
                            x > rootSize.width - edgeThreshold &&
                                state.pageCount < MAX_HOME_PAGES -> 1
                            else -> 0
                        }
                        if (direction != drag.edgeDirection) {
                            drag.edgeDirection = direction
                            drag.edgeConsumed = false
                        }
                        updateWidgetTarget(drag)
                    }
                },
                onWidgetDragEnd = {
                    widgetDrag?.let { drag ->
                        if (drag.hoverRemove) {
                            viewModel.removeWidget(drag.id)
                        } else {
                            val current = viewModel.state.value
                            val size = current.widgetSizes[drag.id] ?: WidgetSize(1, 1)
                            val fallback = current.widgetPlacements[drag.id]
                                ?: WidgetPlacement(0, 0, current.currentPage)
                            var page = drag.targetPage
                            if (page < 0) page = current.currentPage
                            val column = if (drag.targetPage == page) {
                                drag.targetColumn
                            } else {
                                fallback.column.coerceIn(
                                    0,
                                    (current.config.columns - size.columns).coerceAtLeast(0)
                                )
                            }
                            val row = if (drag.targetPage == page) {
                                drag.targetRow
                            } else {
                                fallback.row.coerceIn(
                                    0,
                                    (HOME_GRID_ROWS - size.rows).coerceAtLeast(0)
                                )
                            }
                            if (!viewModel.moveWidget(drag.id, column, row, page)) {
                                viewModel.showMessage("Not enough space there.")
                            }
                        }
                    }
                    widgetDrag = null
                    selectedWidgetId = null
                },
                onWidgetDragCancel = { widgetDrag = null },
                config = state.config,
                viewModel = viewModel,
                returningKey = returningKey,
                pinPositions = state.pinPositions,
                iconPackKey = iconKey,
                pageCount = state.pageCount,
                currentPage = state.currentPage,
                widgetDrag = widgetDrag,
                slotBounds = homeSlotBounds,
                drawerHoverSlot = drawerDrag?.takeIf { it.active }?.hoverSlot?.takeIf { it >= 0 },
                showSlotDots = drawerDrag?.active == true,
                onPageOffset = { pagerOffset = it },
                homeDrag = homeDrag,
                onHomeDragStart = { app, page, origin ->
                    homeDrag = HomeDrag(app, origin, page, homeDropActions(viewModel, app))
                    homeDragActive = true
                },
                onHomeDrag = updateHomeDrag,
                onHomeDragEnd = {
                    homeDrag?.let { drag ->
                        val action = drag.actionTarget
                        val moved = drag.travelled > 24f
                        if (action != null) {
                            when (action) {
                                HomeDropAction.Remove -> viewModel.unpin(drag.app)
                                HomeDropAction.Uninstall -> viewModel.uninstall(drag.app)
                                HomeDropAction.AppInfo -> viewModel.appInfo(drag.app)
                                HomeDropAction.Cancel -> Unit
                            }
                        } else if (moved) {
                            when {
                                drag.hoverDockSlot >= 0 ->
                                    viewModel.movePinnedToSlot(drag.app, -(drag.hoverDockSlot + 1))
                                drag.edgeConsumed -> {
                                    val pageSize = state.config.columns * HOME_ROWS
                                    val landingPage = viewModel.state.value.currentPage
                                    val x = drag.origin.exactCenterX() + drag.offset.x
                                    val y = drag.origin.exactCenterY() + drag.offset.y
                                    val pageSlots = homeSlotBounds.entries.filter {
                                        it.key / pageSize == landingPage
                                    }
                                    val target = pageSlots.firstOrNull {
                                        it.value.contains(x.roundToInt(), y.roundToInt())
                                    }?.key ?: pageSlots.minByOrNull {
                                        hypot((it.value.exactCenterX() - x).toDouble(),
                                            (it.value.exactCenterY() - y).toDouble())
                                    }?.key
                                    if (target != null) viewModel.movePinnedToSlot(drag.app, target)
                                }
                                drag.hoverSlot >= 0 ->
                                    viewModel.movePinnedToSlot(drag.app, drag.hoverSlot)
                                else -> {
                                    val pageSize = state.config.columns * HOME_ROWS
                                    val landingPage = viewModel.state.value.currentPage
                                    val x = drag.origin.exactCenterX() + drag.offset.x
                                    val y = drag.origin.exactCenterY() + drag.offset.y
                                    val target = homeSlotBounds.entries
                                        .filter { it.key / pageSize == landingPage }
                                        .minByOrNull {
                                            hypot(
                                                (it.value.exactCenterX() - x).toDouble(),
                                                (it.value.exactCenterY() - y).toDouble()
                                            )
                                        }?.key
                                    if (target != null) {
                                        viewModel.movePinnedToSlot(drag.app, target)
                                    } else {
                                        viewModel.movePinnedToCurrentPage(drag.app)
                                    }
                                }
                            }
                        } else {
                            viewModel.showMenu(drag.app)
                        }
                    }
                    homeDrag = null
                    homeDragActive = false
                },
                onHomeDragCancel = {
                    homeDrag = null
                    homeDragActive = false
                },
                modifier = Modifier.weight(1f)
            )
            if (state.config.showSearch) {
                SearchPill { viewModel.openDrawer() }
                Spacer(Modifier.height(6.dp))
            }
            DockRow(
                apps = dockApps,
                pinPositions = state.pinPositions,
                config = state.config,
                viewModel = viewModel,
                iconPackKey = iconKey,
                slotBounds = dockSlotBounds,
                highlightDockIndex = drawerDrag?.takeIf { it.active }?.hoverDockSlot?.takeIf { it >= 0 },
                homeDrag = homeDrag,
                dragging = homeDrag != null || drawerDrag != null,
                onDragStart = { app, origin ->
                    homeDrag = HomeDrag(app, origin, state.currentPage, homeDropActions(viewModel, app))
                    homeDragActive = true
                },
                onDrag = updateHomeDrag,
                onDragEnd = {
                    homeDrag?.let { drag ->
                        val action = drag.actionTarget
                        if (action != null) {
                            when (action) {
                                HomeDropAction.Remove -> viewModel.unpin(drag.app)
                                HomeDropAction.Uninstall -> viewModel.uninstall(drag.app)
                                HomeDropAction.AppInfo -> viewModel.appInfo(drag.app)
                                HomeDropAction.Cancel -> Unit
                            }
                        } else if (drag.travelled <= 24f) {
                            viewModel.showMenu(drag.app)
                        } else {
                            when {
                                drag.hoverDockSlot >= 0 ->
                                    viewModel.movePinnedToSlot(drag.app, -(drag.hoverDockSlot + 1))
                                drag.edgeConsumed -> {
                                    val pageSize = state.config.columns * HOME_ROWS
                                    val landingPage = viewModel.state.value.currentPage
                                    val x = drag.origin.exactCenterX() + drag.offset.x
                                    val y = drag.origin.exactCenterY() + drag.offset.y
                                    val target = homeSlotBounds.entries
                                        .filter { it.key / pageSize == landingPage }
                                        .minByOrNull {
                                            hypot((it.value.exactCenterX() - x).toDouble(),
                                                (it.value.exactCenterY() - y).toDouble())
                                        }?.key
                                    if (target != null) viewModel.movePinnedToSlot(drag.app, target)
                                }
                                drag.hoverSlot >= 0 -> viewModel.movePinnedToSlot(drag.app, drag.hoverSlot)
                            }
                        }
                    }
                    homeDrag = null
                    homeDragActive = false
                },
                onDragCancel = {
                    homeDrag = null
                    homeDragActive = false
                }
            )
        }

        val drawerHiddenOffset =
            if (rootSize.height > 0) rootSize.height.toFloat() + 240f else 10000f
        val drawerSlide by animateFloatAsState(
            targetValue = if (state.drawerOpen) 0f else drawerHiddenOffset,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "drawerSlide"
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = drawerSlide }
        ) {
            AppDrawer(
                visibleApps,
                state,
                viewModel,
                dragActive = drawerDragActive,
                onAppDragStart = { app, origin ->
                    drawerDrag = DrawerDrag(app, origin)
                },
                onAppDrag = onDrawerAppDrag,
                onAppDragEnd = onDrawerAppDragEnd,
                onAppDragCancel = { drawerDrag = null }
            )
        }

        drawerDrag?.takeIf { it.active }?.let { drag ->
            HomeDropBar(
                actions = listOf(HomeDropAction.Cancel) +
                    (if (drag.fromFolder) listOf(HomeDropAction.Remove) else emptyList()) +
                    if (viewModel.canUninstall(drag.app)) listOf(HomeDropAction.Uninstall) else emptyList(),
                hovered = drag.actionTarget,
                onBounds = { action, rect -> drawerDropBounds[action] = rect }
            )
            val x = drag.origin.exactCenterX() + drag.offset.x
            val direction = when {
                x < edgeThreshold && state.currentPage > 0 -> -1
                x > rootSize.width - edgeThreshold -> 1
                else -> 0
            }
            if (direction != 0) {
                EdgeDropIndicator(direction, state.currentPage, state.pageCount)
            }
            DrawerDragGhost(drag, state.config, viewModel, iconKey)
        }

        homeDrag?.let { drag ->
            HomeDropBar(drag.actions, drag.actionTarget) { action, rect ->
                dropBounds[action] = rect
            }
            HomeDragGhost(drag, state.config, viewModel, iconKey)
        }

        widgetDrag?.let { drag ->
            HomeDropBar(
                actions = listOf(HomeDropAction.Remove),
                hovered = if (drag.hoverRemove) HomeDropAction.Remove else null,
                onBounds = { action, rect -> dropBounds[action] = rect }
            )
            if (drag.edgeDirection != 0 && !drag.hoverRemove) {
                EdgeDropIndicator(drag.edgeDirection, state.currentPage, state.pageCount)
            }
            WidgetDragGhost(drag)
        }

        if (homeDrag != null || widgetDrag != null) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(80.dp)
                    .fillMaxHeight()
                    .systemGestureExclusion()
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(80.dp)
                    .fillMaxHeight()
                    .systemGestureExclusion()
            )
        }

        state.returning?.let { transition ->
            ReturnGhost(transition, rootSize, viewModel, iconKey)
        }

        state.openFolder?.let { id ->
            state.folders[id]?.let { folder ->
                FolderOverlay(
                    folder, appsByComponent, state.config, viewModel, iconKey,
                    hidden = drawerDrag?.fromFolder == true && drawerDrag?.active == true,
                    onAppDragStart = { app, origin -> drawerDrag = DrawerDrag(app, origin, fromFolder = true) },
                    onAppDrag = onDrawerAppDrag,
                    onAppDragEnd = onDrawerAppDragEnd,
                    onAppDragCancel = { drawerDrag = null }
                )
            }
        }

        state.menuApp?.let { app ->
            AppMenuSheet(app, state, viewModel)
        }

        if (state.homeMenuOpen) {
            HomeMenuSheet(state, viewModel)
        }

        if (state.settingsOpen) {
            LauncherSettingsScreen(state, viewModel)
        }

        if (state.widgetPickerOpen) {
            WidgetPickerSheet(
                state = state,
                viewModel = viewModel,
                onInstall = installWidget,
                slotBounds = homeSlotBounds,
                page = state.currentPage,
                columns = state.config.columns
            )
        }
    }
}

@Composable
private fun DockRow(
    apps: List<LauncherApp>,
    pinPositions: Map<android.content.ComponentName, Int>,
    config: LauncherConfig,
    viewModel: LauncherViewModel,
    iconPackKey: String?,
    slotBounds: MutableMap<Int, Rect>,
    highlightDockIndex: Int?,
    homeDrag: HomeDrag?,
    dragging: Boolean,
    onDragStart: (LauncherApp, Rect) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val appByDockIndex = remember(apps, pinPositions) {
        pinPositions.entries.filter { it.value < 0 }
            .associate { (-it.value - 1) to apps.firstOrNull { app -> app.component == it.key } }
    }
    val dockConfig = remember(config) { config.copy(iconSize = config.iconSize.coerceAtMost(48), showLabels = false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Spread docked apps evenly; empty drop slots only appear while something is being dragged.
        val shown = (0 until MAX_DOCK_ITEMS).filter { dragging || appByDockIndex[it] != null }
        shown.forEach { index -> key(index) {
            val app = appByDockIndex[index]
            DisposableEffect(index) { onDispose { slotBounds.remove(index) } }
            Box(
                Modifier
                    .weight(1f)
                    .height(66.dp)
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot()
                        slotBounds[index] = Rect(
                            bounds.left.roundToInt(), bounds.top.roundToInt(),
                            bounds.right.roundToInt(), bounds.bottom.roundToInt()
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (app == null) {
                    if (homeDrag != null || highlightDockIndex == index) {
                        val active = highlightDockIndex == index
                        Box(
                            Modifier.fillMaxSize().border(
                                if (active) 2.dp else 1.dp,
                                if (active) NothingRed else NothingRed.copy(alpha = .45f),
                                RoundedCornerShape(16.dp)
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            DotGlyph(Modifier.size(14.dp), tint = Color.White.copy(alpha = .1f))
                        }
                    }
                } else {
                    AppCell(
                        app = app,
                        config = dockConfig,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                        showLabel = false,
                        fromHome = true,
                        iconPackKey = iconPackKey,
                        isDragging = homeDrag?.app?.key == app.key,
                        sourceHidden = homeDrag?.app?.key == app.key,
                        onDragStart = { origin ->
                            onDragStart(app, origin ?: slotBounds[index]?.let { Rect(it) } ?: return@AppCell)
                        },
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel
                    )
                }
            }
        } }
    }
}

@Composable
private fun HomePager(
    apps: List<LauncherApp>,
    widgets: List<Int>,
    widgetSizes: Map<Int, WidgetSize>,
    widgetPlacements: Map<Int, WidgetPlacement>,
    selectedWidgetId: Int?,
    widgetPressGate: WidgetPressGate,
    onWidgetSelect: (Int?) -> Unit,
    onWidgetResize: (Int, Int, Int) -> Unit,
    onWidgetDragStart: (Int, Rect) -> Unit,
    onWidgetDrag: (Offset) -> Unit,
    onWidgetDragEnd: () -> Unit,
    onWidgetDragCancel: () -> Unit,
    config: LauncherConfig,
    viewModel: LauncherViewModel,
    returningKey: String?,
    pinPositions: Map<android.content.ComponentName, Int>,
    iconPackKey: String?,
    pageCount: Int,
    currentPage: Int,
    widgetDrag: WidgetDrag?,
    slotBounds: MutableMap<Int, Rect>,
    drawerHoverSlot: Int?,
    showSlotDots: Boolean,
    onPageOffset: (Float) -> Unit,
    homeDrag: HomeDrag?,
    onHomeDragStart: (LauncherApp, Int, Rect) -> Unit,
    onHomeDrag: (Offset) -> Unit,
    onHomeDragEnd: () -> Unit,
    onHomeDragCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeCount = pageCount.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = currentPage.coerceIn(0, safeCount - 1),
        pageCount = { safeCount }
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { viewModel.setCurrentPage(it) }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }
            .collect(onPageOffset)
    }
    LaunchedEffect(currentPage, safeCount) {
        val target = currentPage.coerceIn(0, safeCount - 1)
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }
    val beyond = (homeDrag?.sourcePage ?: widgetDrag?.sourcePage)?.let {
        (kotlin.math.abs(pagerState.currentPage - it) + 1)
            .coerceAtMost(safeCount - 1)
    } ?: 0
    Column(modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = beyond,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { page ->
            HomePage(
                page = page,
                apps = apps,
                widgets = widgets,
                widgetSizes = widgetSizes,
                widgetPlacements = widgetPlacements,
                selectedWidgetId = selectedWidgetId,
                widgetPressGate = widgetPressGate,
                onWidgetSelect = onWidgetSelect,
                onWidgetResize = onWidgetResize,
                onWidgetDragStart = onWidgetDragStart,
                onWidgetDrag = onWidgetDrag,
                onWidgetDragEnd = onWidgetDragEnd,
                onWidgetDragCancel = onWidgetDragCancel,
                config = config,
                viewModel = viewModel,
                returningKey = returningKey,
                pinPositions = pinPositions,
                iconPackKey = iconPackKey,
                slotBounds = slotBounds,
                drawerHoverSlot = drawerHoverSlot,
                showSlotDots = showSlotDots,
                pageCount = safeCount,
                widgetDrag = widgetDrag,
                homeDrag = homeDrag,
                onHomeDragStart = onHomeDragStart,
                onHomeDrag = onHomeDrag,
                onHomeDragEnd = onHomeDragEnd,
                onHomeDragCancel = onHomeDragCancel
            )
        }
        PageDots(
            pageCount = safeCount,
            currentPage = pagerState.currentPage,
            onSelect = viewModel::setCurrentPage
        )
    }
}

@Composable
private fun HomePage(
    page: Int,
    pageCount: Int,
    apps: List<LauncherApp>,
    widgets: List<Int>,
    widgetSizes: Map<Int, WidgetSize>,
    widgetPlacements: Map<Int, WidgetPlacement>,
    selectedWidgetId: Int?,
    widgetPressGate: WidgetPressGate,
    onWidgetSelect: (Int?) -> Unit,
    onWidgetResize: (Int, Int, Int) -> Unit,
    onWidgetDragStart: (Int, Rect) -> Unit,
    onWidgetDrag: (Offset) -> Unit,
    onWidgetDragEnd: () -> Unit,
    onWidgetDragCancel: () -> Unit,
    config: LauncherConfig,
    viewModel: LauncherViewModel,
    returningKey: String?,
    pinPositions: Map<android.content.ComponentName, Int>,
    iconPackKey: String?,
    slotBounds: MutableMap<Int, Rect>,
    drawerHoverSlot: Int?,
    showSlotDots: Boolean,
    widgetDrag: WidgetDrag?,
    homeDrag: HomeDrag?,
    onHomeDragStart: (LauncherApp, Int, Rect) -> Unit,
    onHomeDrag: (Offset) -> Unit,
    onHomeDragEnd: () -> Unit,
    onHomeDragCancel: () -> Unit
) {
    val pageSize = config.columns * HOME_ROWS
    val pageStart = page * pageSize
    val pageWidgets = remember(widgets, widgetPlacements, page) {
        widgets.filter { (widgetPlacements[it]?.page ?: 0) == page }
    }
    val pageWidgetSizes = remember(pageWidgets, widgetSizes, config.columns) {
        pageWidgets.associateWith { id ->
            val size = widgetSizes[id] ?: WidgetSize(1, 1)
            WidgetSize(
                size.columns.coerceIn(1, config.columns),
                size.rows.coerceIn(1, HOME_GRID_ROWS)
            )
        }
    }
    val pageWidgetPlacements = remember(
        pageWidgets,
        widgetPlacements,
        pageWidgetSizes,
        config.columns
    ) {
        pageWidgets.associateWith { id ->
            val size = pageWidgetSizes[id] ?: WidgetSize(1, 1)
            val placement = widgetPlacements[id] ?: WidgetPlacement(0, 0)
            WidgetPlacement(
                placement.column.coerceIn(
                    0,
                    (config.columns - size.columns).coerceAtLeast(0)
                ),
                placement.row.coerceIn(
                    0,
                    (HOME_GRID_ROWS - size.rows).coerceAtLeast(0)
                )
            )
        }
    }
    val appsBySlot = remember(apps, pinPositions, pageStart, pageSize) {
        apps.filter { (pinPositions[it.component] ?: -1) in pageStart until pageStart + pageSize }
            .associateBy { pinPositions[it.component] ?: -1 }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gapX = 4.dp
        val gapY = 10.dp
        val rowHeight = ((maxHeight - 42.dp) / HOME_GRID_ROWS).coerceAtLeast(60.dp)
        val slotHeight = rowHeight
        val cellWidth = (maxWidth - gapX * (config.columns - 1)) / config.columns
        LazyVerticalGrid(
            columns = GridCells.Fixed(config.columns), modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(gapX), verticalArrangement = Arrangement.spacedBy(gapY),
            contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)
        ) {
            items(config.columns * HOME_GRID_ROWS, key = { "home-slot-${pageStart + it}" }) { offset ->
                val slot = pageStart + offset
                val app = appsBySlot[slot]
                val isDropTarget = (homeDrag?.hoverSlot == slot && homeDrag.app.key != app?.key) ||
                    drawerHoverSlot == slot
                // Dropping an app on an app/folder merges them, so preview a folder instead of a box.
                // Drawer drags (homeDrag == null) are always real apps.
                val mergeTarget = isDropTarget && app != null && !app.component.isShortcut &&
                    homeDrag?.app?.isApp != false
                DisposableEffect(slot) { onDispose { slotBounds.remove(slot) } }
                Box(Modifier.fillMaxWidth().height(slotHeight)
                    .then(
                        when {
                            mergeTarget -> Modifier.background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
                                .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
                            isDropTarget -> Modifier.border(2.dp, NothingRed, RoundedCornerShape(18.dp))
                            else -> Modifier
                        }
                    )
                    .onGloballyPositioned { c -> val b = c.boundsInRoot(); slotBounds[slot] = Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt()) },
                    contentAlignment = Alignment.Center) {
                    if (app == null) {
                        Box(Modifier.fillMaxWidth().height(slotHeight), contentAlignment = Alignment.Center) {
                            if (homeDrag != null || showSlotDots) {
                                DotGlyph(
                                    Modifier.size(18.dp),
                                    tint = Color.White.copy(alpha = .10f),
                                    center = NothingRed.copy(alpha = .20f)
                                )
                            }
                        }
                    } else {
                        AppCell(app, config, viewModel, modifier = Modifier.fillMaxWidth(), showLabel = config.showLabels,
                            fromHome = true, iconPackKey = iconPackKey, isReturning = returningKey == app.key,
                            isDragging = homeDrag?.app?.key == app.key, sourceHidden = homeDrag?.app?.key == app.key,
                            dragOffset = { Offset.Zero },
                            onDragStart = { origin -> onHomeDragStart(app, page, origin ?: slotBounds[slot]?.let { Rect(it) } ?: return@AppCell) },
                            onDrag = onHomeDrag, onDragEnd = onHomeDragEnd, onDragCancel = onHomeDragCancel)
                    }
                }
            }
        }
        pageWidgets.forEach { id ->
            val size = pageWidgetSizes[id] ?: WidgetSize(1, 1)
            val placement = pageWidgetPlacements[id] ?: WidgetPlacement(0, 0)
            val x = cellWidth * placement.column + gapX * placement.column
            val y = 4.dp + rowHeight * placement.row + gapY * placement.row
            Box(Modifier.offset(x = x, y = y)) {
                WidgetView(
                    id = id,
                    host = viewModel.widgetHost,
                    size = size,
                    rowHeight = rowHeight,
                    cellWidth = cellWidth,
                    maxColumns = config.columns,
                    maxRows = HOME_GRID_ROWS,
                    selected = selectedWidgetId == id,
                    pressGate = widgetPressGate,
                    onSelect = onWidgetSelect,
                    onResize = onWidgetResize,
                    onDragStart = { origin -> onWidgetDragStart(id, origin) },
                    onDrag = onWidgetDrag,
                    onDragEnd = onWidgetDragEnd,
                    onDragCancel = onWidgetDragCancel
                )
            }
        }
        val preview = widgetDrag
        if (preview != null && preview.targetPage == page) {
            val size = widgetSizes[preview.id] ?: WidgetSize(1, 1)
            val width = cellWidth * size.columns + gapX * (size.columns - 1)
            val height = rowHeight * size.rows + gapY * (size.rows - 1)
            val x = cellWidth * preview.targetColumn + gapX * preview.targetColumn
            val y = 4.dp + rowHeight * preview.targetRow + gapY * preview.targetRow
            Box(
                Modifier
                    .offset(x = x, y = y)
                    .size(width, height)
                    .border(
                        2.dp,
                        if (preview.targetValid) Color.White else NothingRed,
                        RoundedCornerShape(18.dp)
                    )
                    .background(
                        (if (preview.targetValid) Color.White else NothingRed)
                            .copy(alpha = 0.08f),
                        RoundedCornerShape(18.dp)
                    )
                    .zIndex(30f)
            )
        }
        if (appsBySlot.isEmpty() && pageWidgets.isEmpty() && homeDrag == null) {
            Text(
                if (page == 0) "Swipe up for apps. Long-press the home screen for widgets and settings."
                else "Empty page. Drag apps here from the drawer, or use − / + below.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(28.dp)
            )
        }
        if (homeDrag?.sourcePage == page && homeDrag.edgeDirection != 0) EdgeDropIndicator(homeDrag.edgeDirection, page, pageCount)
    }
}

private enum class HomeDropAction(val label: String) {
    Remove("REMOVE"),
    Uninstall("UNINSTALL"),
    AppInfo("APP INFO"),
    Cancel("CANCEL")
}

private fun homeDropActions(viewModel: LauncherViewModel, app: LauncherApp): List<HomeDropAction> =
    if (app.folderId != null) {
        listOf(HomeDropAction.Remove)
    } else if (viewModel.canUninstall(app)) {
        listOf(HomeDropAction.Remove, HomeDropAction.Uninstall)
    } else {
        listOf(HomeDropAction.Remove, HomeDropAction.AppInfo)
    }

@Composable
private fun HomeDropBar(
    actions: List<HomeDropAction>,
    hovered: HomeDropAction?,
    onBounds: (HomeDropAction, Rect) -> Unit
) {
    val density = LocalDensity.current
    val padding = with(density) { 22.dp.toPx() }.roundToInt()
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEach { action ->
            val selected = hovered == action
            Box(
                Modifier
                    .padding(horizontal = 5.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (selected) NothingRed else Color.Black.copy(alpha = 0.80f))
                    .border(
                        1.dp,
                        if (selected) NothingRed else Color.White.copy(alpha = 0.22f),
                        RoundedCornerShape(24.dp)
                    )
                    .onGloballyPositioned { c ->
                        val b = c.boundsInRoot()
                        onBounds(
                            action,
                            Rect(
                                b.left.roundToInt() - padding,
                                b.top.roundToInt() - padding,
                                b.right.roundToInt() + padding,
                                b.bottom.roundToInt() + padding
                            )
                        )
                    }
                    .padding(horizontal = 20.dp, vertical = 11.dp)
            ) {
                Text(
                    action.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.88f)
                )
            }
        }
    }
}

private class HomeDrag(
    val app: LauncherApp,
    val origin: Rect,
    val sourcePage: Int,
    val actions: List<HomeDropAction>
) {
    var offset by mutableStateOf(Offset.Zero)
    var hoverSlot by mutableIntStateOf(-1)
    var hoverDockSlot by mutableIntStateOf(-1)
    var edgeDirection by mutableIntStateOf(0)
    var edgeConsumed by mutableStateOf(false)
    var actionTarget by mutableStateOf<HomeDropAction?>(null)
    /** Farthest the finger got from the start; a drag that returns home is still a drag, not a menu press. */
    var travelled = 0f
}

private class WidgetDrag(val id: Int, val origin: Rect, val sourcePage: Int) {
    var offset by mutableStateOf(Offset.Zero)
    var hoverRemove by mutableStateOf(false)
    var edgeDirection by mutableIntStateOf(0)
    var edgeConsumed by mutableStateOf(false)
    var targetPage by mutableIntStateOf(-1)
    var targetColumn by mutableIntStateOf(0)
    var targetRow by mutableIntStateOf(0)
    var targetValid by mutableStateOf(false)
}

private class WidgetPressGate {
    var down = false
}

private class WidgetTouchLayer(context: Context) : FrameLayout(context) {
    var onLongPress: (() -> Unit)? = null
    var onDragStart: ((Float, Float) -> Unit)? = null
    var onDrag: ((Float, Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null
    var onDragCancel: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var longPressed = false
    private var dragging = false
    private val longPress = Runnable {
        if (!longPressed) {
            longPressed = true
            parent?.requestDisallowInterceptTouchEvent(true)
            onLongPress?.invoke()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(longPress)
                downX = ev.x
                downY = ev.y
                longPressed = false
                dragging = false
                postDelayed(longPress, longPressTimeout)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!longPressed) {
                    if (hypot((ev.x - downX).toDouble(), (ev.y - downY).toDouble()) >
                        touchSlop
                    ) {
                        removeCallbacks(longPress)
                    }
                    return false
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                return longPressed
            }
        }
        return longPressed
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!longPressed) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    if (hypot((ev.x - downX).toDouble(), (ev.y - downY).toDouble()) <=
                        touchSlop
                    ) return true
                    dragging = true
                    // Screen coordinates: Home zooms out while dragging, so local deltas would overshoot.
                    lastX = ev.rawX - (ev.x - downX)
                    lastY = ev.rawY - (ev.y - downY)
                    onDragStart?.invoke(downX, downY)
                }
                val dx = ev.rawX - lastX
                val dy = ev.rawY - lastY
                lastX = ev.rawX
                lastY = ev.rawY
                if (dx != 0f || dy != 0f) onDrag?.invoke(dx, dy)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) onDragEnd?.invoke()
                reset()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) onDragCancel?.invoke()
                reset()
                return true
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        reset()
        super.onDetachedFromWindow()
    }

    private fun reset() {
        removeCallbacks(longPress)
        longPressed = false
        dragging = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }
}

@Composable
private fun WidgetDragGhost(drag: WidgetDrag) {
    val density = LocalDensity.current
    val width = with(density) { drag.origin.width().toDp() }
    val height = with(density) { drag.origin.height().toDp() }
    Box(
        Modifier
            .offset {
                IntOffset(
                    (drag.origin.left + drag.offset.x).roundToInt(),
                    (drag.origin.top + drag.offset.y).roundToInt()
                )
            }
            .size(width, height)
            .zIndex(40f)
            .border(2.dp, NothingRed, RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
    )
}

@Composable
private fun HomeDragGhost(drag: HomeDrag, config: LauncherConfig, viewModel: LauncherViewModel, iconPackKey: String?) {
    val icon by produceState<ImageBitmap?>(initialValue = viewModel.cachedIcon(drag.app), drag.app.component, iconPackKey) { value = viewModel.loadIcon(drag.app) }
    val density = LocalDensity.current
    val width = with(density) { drag.origin.width().toDp() }
    val height = with(density) { drag.origin.height().toDp() }
    Box(Modifier.offset { IntOffset((drag.origin.left + drag.offset.x).roundToInt(), (drag.origin.top + drag.offset.y).roundToInt()) }
        .size(width, height).zIndex(40f).border(2.dp, NothingRed, RoundedCornerShape(18.dp))
        .background(Color.Black.copy(alpha = .35f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
        AppIcon(icon, config.iconSize, config.monochrome)
    }
}

@Composable
private fun EdgeDropIndicator(direction: Int, page: Int, pageCount: Int) {
    val target = page + direction
    val label = if (target >= pageCount) "NEW PAGE" else "PAGE ${target + 1}"
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 14.dp),
        contentAlignment = if (direction > 0) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            Modifier
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
                .border(1.dp, NothingRed, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (direction > 0) "›" else "‹",
                style = MaterialTheme.typography.titleLarge,
                color = NothingRed
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun PageDots(
    pageCount: Int,
    currentPage: Int,
    onSelect: (Int) -> Unit
) {
    // Page add/remove lives in the Home long-press menu; dots only, like stock launchers.
    Row(
        Modifier
            .fillMaxWidth()
            .height(20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pageCount < 2) return@Row
        repeat(pageCount) { page ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (page == currentPage) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (page == currentPage) NothingRed else Color.White.copy(alpha = 0.28f)
                    )
                    .clickable { onSelect(page) }
            )
        }
    }
}

@Composable
private fun WidgetView(
    id: Int,
    host: AppWidgetHost,
    size: WidgetSize,
    rowHeight: Dp,
    cellWidth: Dp,
    maxColumns: Int,
    maxRows: Int,
    selected: Boolean,
    pressGate: WidgetPressGate,
    onSelect: (Int?) -> Unit,
    onResize: (Int, Int, Int) -> Unit,
    onDragStart: (Rect) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val cellWidthPx = with(density) { cellWidth.toPx() }.coerceAtLeast(1f)
    val rowHeightPx = with(density) { rowHeight.toPx() }.coerceAtLeast(1f)
    var bounds by remember(id) { mutableStateOf(Rect()) }
    var hostView by remember(id) { mutableStateOf<AppWidgetHostView?>(null) }
    var preview by remember(id, size) { mutableStateOf(size) }
    val previewWidth = cellWidth * preview.columns + 4.dp * (preview.columns - 1)
    val previewHeight = rowHeight * preview.rows + 10.dp * (preview.rows - 1)
    val currentSize = rememberUpdatedState(size)
    val currentOnSelect = rememberUpdatedState(onSelect)
    val currentOnDragStart = rememberUpdatedState(onDragStart)
    val currentOnDrag = rememberUpdatedState(onDrag)
    val currentOnDragEnd = rememberUpdatedState(onDragEnd)
    val currentOnDragCancel = rememberUpdatedState(onDragCancel)

    Box(
        Modifier
            .width(previewWidth)
            .height(previewHeight)
            .padding(vertical = 4.dp)
            .border(
                2.dp,
                if (selected) NothingRed else Color.Transparent,
                RoundedCornerShape(18.dp)
            )
            .onGloballyPositioned { c ->
                val b = c.boundsInRoot()
                bounds = Rect(
                    b.left.roundToInt(),
                    b.top.roundToInt(),
                    b.right.roundToInt(),
                    b.bottom.roundToInt()
                )
            }
            .pointerInput(id) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    pressGate.down = true
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                        }
                    } finally {
                        pressGate.down = false
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                val layer = WidgetTouchLayer(ctx).apply {
                    this.onLongPress = { currentOnSelect.value(id) }
                    this.onDragStart = { x, y ->
                        val width = bounds.width().coerceAtLeast(1)
                        val height = bounds.height().coerceAtLeast(1)
                        val touch = Offset(bounds.left + x, bounds.top + y)
                        currentOnDragStart.value(
                            Rect(
                                (touch.x - width / 2f).roundToInt(),
                                (touch.y - height / 2f).roundToInt(),
                                (touch.x + width / 2f).roundToInt(),
                                (touch.y + height / 2f).roundToInt()
                            )
                        )
                    }
                    this.onDrag = { dx, dy -> currentOnDrag.value(Offset(dx, dy)) }
                    this.onDragEnd = { currentOnDragEnd.value() }
                    this.onDragCancel = { currentOnDragCancel.value() }
                }
                val child = runCatching {
                    val info = AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id)
                    host.createView(ctx, id, info).also { hostView = it }
                }.getOrElse {
                    hostView = null
                    View(ctx)
                }
                child.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                layer.addView(child)
                layer
            },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { measured ->
                    val view = hostView ?: return@onSizeChanged
                    val width = with(density) {
                        measured.width.toDp().value.roundToInt()
                    }.coerceAtLeast(1)
                    val height = with(density) {
                        measured.height.toDp().value.roundToInt()
                    }.coerceAtLeast(1)
                    val options = Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
                    }
                    runCatching {
                        view.updateAppWidgetSize(options, width, height, width, height)
                    }
                }
        )
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "${preview.columns}×${preview.rows}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(NothingRed)
                    .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                    .pointerInput(id, cellWidthPx, rowHeightPx, maxColumns, maxRows) {
                        var start = currentSize.value
                        var dragged = Offset.Zero
                        detectDragGestures(
                            onDragStart = {
                                start = currentSize.value
                                dragged = Offset.Zero
                                onSelect(id)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragged += amount
                                val columns = (start.columns +
                                    (dragged.x / cellWidthPx).roundToInt())
                                    .coerceIn(1, maxColumns)
                                val rows = (start.rows +
                                    (dragged.y / rowHeightPx).roundToInt())
                                    .coerceIn(1, maxRows)
                                preview = WidgetSize(columns, rows)
                            },
                            onDragEnd = {
                                onResize(id, preview.columns, preview.rows)
                            },
                            onDragCancel = {
                                preview = currentSize.value
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(16.dp)) {
                    val stroke = 2.dp.toPx()
                    val color = Color.White
                    val w = this.size.width
                    val h = this.size.height
                    drawLine(
                        color,
                        Offset(w * 0.25f, h * 0.75f),
                        Offset(w * 0.75f, h * 0.25f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color,
                        Offset(w * 0.55f, h * 0.75f),
                        Offset(w * 0.75f, h * 0.55f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

private class DrawerDrag(
    val app: LauncherApp,
    val origin: Rect,
    val fromFolder: Boolean = false
) {
    var actionTarget by mutableStateOf<HomeDropAction?>(null)
    var offset by mutableStateOf(Offset.Zero)
    var active by mutableStateOf(false)
    var hoverSlot by mutableIntStateOf(-1)
    var hoverDockSlot by mutableIntStateOf(-1)
}

@Composable
private fun DrawerDragGhost(
    drag: DrawerDrag,
    config: LauncherConfig,
    viewModel: LauncherViewModel,
    iconPackKey: String?
) {
    val icon by produceState<ImageBitmap?>(
        initialValue = viewModel.cachedIcon(drag.app),
        drag.app.component,
        iconPackKey
    ) {
        value = viewModel.loadIcon(drag.app)
    }
    val density = LocalDensity.current
    val width = with(density) { drag.origin.width().toDp() }
    val height = with(density) { drag.origin.height().toDp() }
    Box(
        Modifier
            .offset {
                IntOffset(
                    (drag.origin.left + drag.offset.x).roundToInt(),
                    (drag.origin.top + drag.offset.y).roundToInt()
                )
            }
            .size(width, height)
            .zIndex(30f)
            .border(2.dp, NothingRed, RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(icon, config.iconSize, config.monochrome)
    }
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
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    val bounds = transition.bounds
    if (bounds.width() <= 0 || bounds.height() <= 0) return
    if (screenSize.width <= 0 || screenSize.height <= 0) return
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
                val iconScale = 2.8f + (1f - 2.8f) * p
                scaleX = iconScale
                scaleY = iconScale
                translationX = (screenSize.width / 2f - targetCx) * (1f - p)
                translationY = (screenSize.height / 2f - targetCy) * (1f - p)
                alpha = 0.72f * (1f - p)
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


// Without READ_WALLPAPER_INTERNAL this throws on newer Android; runCatching returns null and the
// system wallpaper behind the window (windowShowWallpaper) is shown instead, just without parallax.
@android.annotation.SuppressLint("MissingPermission")
private fun loadWallpaper(context: android.content.Context): ImageBitmap? = runCatching {
    val manager = WallpaperManager.getInstance(context)
    val drawable = manager.drawable ?: return@runCatching null
    val metrics = context.resources.displayMetrics
    drawable.toBitmap(
        width = metrics.widthPixels.coerceAtMost(1440),
        height = metrics.heightPixels.coerceAtMost(3200)
    ).asImageBitmap()
}.getOrNull()
