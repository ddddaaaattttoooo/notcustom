package dev.notcustom.app.launcher.ui

import android.app.WallpaperManager
import android.graphics.Rect
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetProviderInfo
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import dev.notcustom.app.launcher.ReturnTransition
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
    val appsByComponent = remember(state.apps) {
        state.apps.groupBy { it.component }.mapValues { (_, list) ->
            list.firstOrNull { it.user == android.os.Process.myUserHandle() } ?: list.first()
        }
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
    val returningKey = state.returning?.app?.key
    val context = LocalContext.current
    val dropBounds = remember { mutableMapOf<HomeDropAction, Rect>() }
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

    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 64.dp.toPx() }
    val homeSlotBounds = remember { mutableStateMapOf<Int, Rect>() }
    val dockSlotBounds = remember { mutableStateMapOf<Int, Rect>() }
    var homeDragActive by remember { mutableStateOf(false) }
    var homeDrag by remember { mutableStateOf<HomeDrag?>(null) }
    var widgetDrag by remember { mutableStateOf<WidgetDrag?>(null) }
    var drawerDrag by remember { mutableStateOf<DrawerDrag?>(null) }
    val homeEditing = homeDrag != null || widgetDrag != null
    val homeScale by animateFloatAsState(
        targetValue = if (homeEditing) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "homeEditScale"
    )
    val edgeThreshold = with(density) { 64.dp.toPx() }
    val drawerDragActive = drawerDrag?.active == true
    val homeDragActiveState = rememberUpdatedState(homeDragActive)

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

    val updateHomeDrag: (Offset) -> Unit = { delta ->
        homeDrag?.let { drag ->
            drag.offset += delta
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
                val target = if (direction != 0 || dockTarget >= 0) {
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
                widgetDrag,
                swipeThreshold
            ) {
                if (state.drawerOpen || state.homeMenuOpen || state.settingsOpen ||
                    state.widgetPickerOpen || state.menuApp != null || widgetDrag != null
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
                widgetDrag
            ) {
                if (state.drawerOpen || state.homeMenuOpen || state.settingsOpen ||
                    state.widgetPickerOpen || state.menuApp != null || widgetDrag != null
                ) {
                    return@pointerInput
                }
                detectTapGestures(
                    onDoubleTap = { viewModel.lockScreen() },
                    onLongPress = { viewModel.openHomeMenu() }
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
                onOpenSettings = viewModel::openSettings
            )
            Spacer(Modifier.height(20.dp))
            HomePager(
                apps = pinned,
                widgets = state.widgets,
                onWidgetDragStart = { id, origin -> widgetDrag = WidgetDrag(id, origin) },
                onWidgetDrag = { delta ->
                    widgetDrag?.let { drag ->
                        drag.offset += delta
                        val x = drag.origin.exactCenterX() + drag.offset.x
                        val y = drag.origin.exactCenterY() + drag.offset.y
                        drag.hoverRemove = dropBounds[HomeDropAction.Remove]
                            ?.contains(x.roundToInt(), y.roundToInt()) == true
                    }
                },
                onWidgetDragEnd = {
                    widgetDrag?.let { drag ->
                        if (drag.hoverRemove) viewModel.removeWidget(drag.id)
                    }
                    widgetDrag = null
                },
                onWidgetDragCancel = { widgetDrag = null },
                config = state.config,
                viewModel = viewModel,
                returningKey = returningKey,
                pinPositions = state.pinPositions,
                iconPackKey = state.iconPack,
                pageCount = state.pageCount,
                currentPage = state.currentPage,
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
                        val moved = drag.offset.getDistance() > 24f
                        if (action != null) {
                            when (action) {
                                HomeDropAction.Remove -> viewModel.unpin(drag.app)
                                HomeDropAction.Uninstall -> viewModel.uninstall(drag.app)
                                HomeDropAction.AppInfo -> viewModel.appInfo(drag.app)
                            }
                        } else if (moved) {
                            when {
                                drag.edgeDirection != 0 && !drag.edgeConsumed ->
                                    viewModel.movePinnedToAdjacentPage(drag.app, drag.edgeDirection)
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
                iconPackKey = state.iconPack,
                slotBounds = dockSlotBounds,
                highlightDockIndex = drawerDrag?.takeIf { it.active }?.hoverDockSlot?.takeIf { it >= 0 },
                homeDrag = homeDrag,
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
                            }
                        } else if (drag.offset.getDistance() <= 24f) {
                            viewModel.showMenu(drag.app)
                        } else {
                            when {
                                drag.hoverDockSlot >= 0 ->
                                    viewModel.movePinnedToSlot(drag.app, -(drag.hoverDockSlot + 1))
                                drag.edgeDirection != 0 && !drag.edgeConsumed ->
                                    viewModel.movePinnedToAdjacentPage(drag.app, drag.edgeDirection)
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
                onAppDrag = { delta ->
                    drawerDrag?.let { drag ->
                        drag.offset += delta
                        if (!drag.active && drag.offset.getDistance() > swipeThreshold * 0.25f) {
                            drag.active = true
                        }
                        if (drag.active) {
                            val x = drag.origin.exactCenterX() + drag.offset.x
                            val y = drag.origin.exactCenterY() + drag.offset.y
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
                },
                onAppDragEnd = {
                    val drag = drawerDrag
                    if (drag != null && drag.active) {
                        val x = drag.origin.exactCenterX() + drag.offset.x
                        val y = drag.origin.exactCenterY() + drag.offset.y
                        val direction = when {
                            x < edgeThreshold && state.currentPage > 0 -> -1
                            x > rootSize.width - edgeThreshold -> 1
                            else -> 0
                        }
                        val dockTarget = dockSlotBounds.entries.firstOrNull {
                            it.value.contains(x.roundToInt(), y.roundToInt())
                        }?.key
                        if (dockTarget != null) {
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
                    drawerDrag = null
                },
                onAppDragCancel = { drawerDrag = null }
            )
        }

        drawerDrag?.takeIf { it.active }?.let { drag ->
            val x = drag.origin.exactCenterX() + drag.offset.x
            val direction = when {
                x < edgeThreshold && state.currentPage > 0 -> -1
                x > rootSize.width - edgeThreshold -> 1
                else -> 0
            }
            if (direction != 0) {
                EdgeDropIndicator(direction, state.currentPage, state.pageCount)
            }
            DrawerDragGhost(drag, state.config, viewModel, state.iconPack)
        }

        homeDrag?.let { drag ->
            HomeDropBar(drag.actions, drag.actionTarget) { action, rect ->
                dropBounds[action] = rect
            }
            HomeDragGhost(drag, state.config, viewModel, state.iconPack)
        }

        widgetDrag?.let { drag ->
            HomeDropBar(
                actions = listOf(HomeDropAction.Remove),
                hovered = if (drag.hoverRemove) HomeDropAction.Remove else null,
                onBounds = { action, rect -> dropBounds[action] = rect }
            )
            WidgetDragGhost(drag)
        }



        state.returning?.let { transition ->
            ReturnGhost(transition, rootSize, viewModel, state.iconPack)
        }

        state.menuApp?.let { app ->
            AppMenuSheet(app, state, viewModel)
        }

        if (state.homeMenuOpen) {
            HomeMenuSheet(state, viewModel)
        }

        if (state.settingsOpen) {
            LauncherSettingsSheet(state, viewModel)
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
        repeat(MAX_DOCK_ITEMS) { index ->
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
        }
    }
}

@Composable
private fun HomePager(
    apps: List<LauncherApp>,
    widgets: List<Int>,
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
    val beyond = homeDrag?.let {
        (kotlin.math.abs(pagerState.currentPage - it.sourcePage) + 1)
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
            onSelect = viewModel::setCurrentPage,
            onAdd = viewModel::addPage,
            onRemove = viewModel::removeCurrentPage
        )
    }
}

@Composable
private fun HomePage(
    page: Int,
    pageCount: Int,
    apps: List<LauncherApp>,
    widgets: List<Int>,
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
    homeDrag: HomeDrag?,
    onHomeDragStart: (LauncherApp, Int, Rect) -> Unit,
    onHomeDrag: (Offset) -> Unit,
    onHomeDragEnd: () -> Unit,
    onHomeDragCancel: () -> Unit
) {
    val pageSize = config.columns * HOME_ROWS
    val pageStart = page * pageSize
    val pageWidgets = if (page == 0) widgets else emptyList()
    val appsBySlot = remember(apps, pinPositions, pageStart, pageSize) {
        apps.filter { (pinPositions[it.component] ?: -1) in pageStart until pageStart + pageSize }
            .associateBy { pinPositions[it.component] ?: -1 }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val slotHeight = ((maxHeight - 42.dp) / HOME_GRID_ROWS).coerceAtLeast(60.dp)
        LazyVerticalGrid(
            columns = GridCells.Fixed(config.columns), modifier = Modifier.fillMaxSize(),
            userScrollEnabled = pageWidgets.isNotEmpty() || config.iconSize > 64,
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)
        ) {
            pageWidgets.forEach { id ->
                item(key = "widget-$id", span = { GridItemSpan(maxLineSpan) }) {
                    WidgetView(
                        id = id,
                        host = viewModel.widgetHost,
                        onDragStart = { origin -> onWidgetDragStart(id, origin) },
                        onDrag = onWidgetDrag,
                        onDragEnd = onWidgetDragEnd,
                        onDragCancel = onWidgetDragCancel
                    )
                }
            }
            items(config.columns * HOME_GRID_ROWS, key = { "home-slot-${pageStart + it}" }) { offset ->
                val slot = pageStart + offset
                val app = appsBySlot[slot]
                val isDropTarget = (homeDrag?.hoverSlot == slot && homeDrag.app.key != app?.key) ||
                    drawerHoverSlot == slot
                DisposableEffect(slot) { onDispose { slotBounds.remove(slot) } }
                Box(Modifier.fillMaxWidth().height(slotHeight)
                    .then(if (isDropTarget) Modifier.border(2.dp, NothingRed, RoundedCornerShape(18.dp)) else Modifier)
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
    AppInfo("APP INFO")
}

private fun homeDropActions(viewModel: LauncherViewModel, app: LauncherApp): List<HomeDropAction> =
    if (viewModel.canUninstall(app)) {
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
                                b.left.roundToInt(),
                                b.top.roundToInt(),
                                b.right.roundToInt(),
                                b.bottom.roundToInt()
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
}

private class WidgetDrag(val id: Int, val origin: Rect) {
    var offset by mutableStateOf(Offset.Zero)
    var hoverRemove by mutableStateOf(false)
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
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PageStepButton("−", enabled = pageCount > 1, onClick = onRemove)
        Spacer(Modifier.width(8.dp))
        Text(
            "${currentPage + 1}/$pageCount",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.72f)
        )
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
        Spacer(Modifier.width(8.dp))
        PageStepButton("+", enabled = pageCount < MAX_HOME_PAGES, onClick = onAdd)
    }
}

@Composable
private fun PageStepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (enabled) Color.White.copy(alpha = 0.10f)
                else Color.White.copy(alpha = 0.04f)
            )
            .border(
                1.dp,
                if (enabled) Color.White.copy(alpha = 0.22f)
                else Color.White.copy(alpha = 0.08f),
                CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) Color.White.copy(alpha = 0.85f)
            else Color.White.copy(alpha = 0.25f)
        )
    }
}

@Composable
private fun WidgetView(
    id: Int,
    host: AppWidgetHost,
    onDragStart: (Rect) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val context = LocalContext.current
    var bounds by remember(id) { mutableStateOf(Rect()) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
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
                    var dragging = false
                    var finished = false
                    var total = Offset.Zero
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUp()) {
                                if (dragging) {
                                    onDragEnd()
                                    finished = true
                                }
                                break
                            }
                            val delta = change.positionChange()
                            if (!dragging) {
                                total += delta
                                if (total.getDistance() > viewConfiguration.touchSlop) break
                                if (change.uptimeMillis - down.uptimeMillis >=
                                    viewConfiguration.longPressTimeoutMillis
                                ) {
                                    dragging = true
                                    onDragStart(bounds)
                                }
                            } else {
                                change.consume()
                                onDrag(delta)
                            }
                        }
                    } finally {
                        if (dragging && !finished) onDragCancel()
                    }
                }
            }
    ) {
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
            modifier = Modifier.fillMaxWidth()
        )
    }
}


private class DrawerDrag(val app: LauncherApp, val origin: Rect) {
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


private fun loadWallpaper(context: android.content.Context): ImageBitmap? = runCatching {
    val manager = WallpaperManager.getInstance(context)
    val drawable = manager.drawable ?: return@runCatching null
    val metrics = context.resources.displayMetrics
    drawable.toBitmap(
        width = metrics.widthPixels.coerceAtMost(1440),
        height = metrics.heightPixels.coerceAtMost(3200)
    ).asImageBitmap()
}.getOrNull()
