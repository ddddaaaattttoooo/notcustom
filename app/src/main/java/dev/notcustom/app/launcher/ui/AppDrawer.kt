package dev.notcustom.app.launcher.ui

import android.graphics.Rect
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.notcustom.app.launcher.DrawerMode
import dev.notcustom.app.launcher.LauncherApp
import dev.notcustom.app.launcher.LauncherState
import dev.notcustom.app.launcher.LauncherViewModel
import dev.notcustom.app.ui.theme.Line
import dev.notcustom.app.ui.theme.Muted
import dev.notcustom.app.ui.theme.NothingRed
import dev.notcustom.app.ui.theme.Paper
import dev.notcustom.app.ui.theme.Surface0
import kotlin.math.roundToInt

@Composable
fun AppDrawer(
    apps: List<LauncherApp>,
    state: LauncherState,
    viewModel: LauncherViewModel,
    dragActive: Boolean = false,
    onAppDragStart: ((LauncherApp, Rect) -> Unit)? = null,
    onAppDrag: ((Offset) -> Unit)? = null,
    onAppDragEnd: (() -> Unit)? = null,
    onAppDragCancel: (() -> Unit)? = null
) {
    val filtered = remember(apps, state.search) {
        if (state.search.isBlank()) {
            apps
        } else {
            apps.filter { it.label.contains(state.search, ignoreCase = true) }
        }
    }
    var openFolder by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.drawerOpen) { if (!state.drawerOpen) openFolder = null }
    BackHandler(enabled = openFolder != null) { openFolder = null }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val dismissThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    var dismissDrag by remember { mutableFloatStateOf(0f) }
    val pullDownToExit = remember(gridState, listState, state.drawerMode, dismissThreshold, viewModel) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag) {
                    if (available.y < 0f) dismissDrag = 0f
                    val atTop = if (state.drawerMode == DrawerMode.LIST) {
                        listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                    } else {
                        gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
                    }
                    if (atTop && available.y > 0f) {
                        dismissDrag += available.y
                        if (dismissDrag > dismissThreshold) {
                            dismissDrag = 0f
                            viewModel.pullDownFromDrawer()
                        }
                        return Offset(0f, available.y)
                    }
                }
                return Offset.Zero
            }
        }
    }

    Surface(
        color = Color(0xFF0A0A0B),
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (dragActive) 0f else 1f }
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(dismissThreshold, viewModel) {
                            var headerPull = 0f
                            detectVerticalDragGestures(
                                onDragStart = { headerPull = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    headerPull += dragAmount
                                    if (headerPull > dismissThreshold) {
                                        headerPull = 0f
                                        viewModel.pullDownFromDrawer()
                                    }
                                }
                            )
                        }
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SearchField(state.search, viewModel::setSearch, Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = viewModel::closeDrawer) {
                                Text(
                                    "CLOSE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Muted
                                )
                            }
                            TextButton(onClick = {
                                viewModel.closeDrawer()
                                viewModel.openSettings()
                            }) {
                                Text(
                                    "SETTINGS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Muted
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            DrawerMode.entries.forEach { mode ->
                                ModeChip(
                                    mode,
                                    state.drawerMode == mode,
                                    Modifier.weight(1f)
                                ) { viewModel.setDrawerMode(mode) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Long-press and drag onto Home. Long-press without moving for options.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                if (filtered.isEmpty()) {
                    var emptyPull by remember { mutableFloatStateOf(0f) }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(dismissThreshold, viewModel) {
                                detectVerticalDragGestures(
                                    onDragStart = { emptyPull = 0f },
                                    onVerticalDrag = { _, dragAmount ->
                                        emptyPull += dragAmount
                                        if (emptyPull > dismissThreshold) {
                                            emptyPull = 0f
                                            viewModel.pullDownFromDrawer()
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No apps found",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    when (state.drawerMode) {
                        DrawerMode.LIST -> LazyColumn(
                            modifier = Modifier.weight(1f).nestedScroll(pullDownToExit),
                            state = listState,
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listItems(filtered, key = { it.key }, contentType = { "app" }) { app ->
                                DrawerListRow(
                                    app = app,
                                    viewModel = viewModel,
                                    iconPackKey = state.iconPack,
                                    onDragStart = { a, bounds -> onAppDragStart?.invoke(a, bounds) },
                                    onDrag = { onAppDrag?.invoke(it) },
                                    onDragEnd = { onAppDragEnd?.invoke() },
                                    onDragCancel = { onAppDragCancel?.invoke() }
                                )
                            }
                        }

                        DrawerMode.GRID -> LazyVerticalGrid(
                            columns = GridCells.Fixed(state.config.columns),
                            modifier = Modifier.weight(1f).nestedScroll(pullDownToExit),
                            state = gridState,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            gridItems(filtered, key = { it.key }, contentType = { "app" }) { app ->
                                AppCell(
                                    app,
                                    state.config,
                                    viewModel,
                                    showLabel = state.config.showLabels,
                                    fromHome = false,
                                    dragEnabled = true,
                                    iconPackKey = state.iconPack,
                                    onDragStart = { bounds ->
                                        bounds?.let { onAppDragStart?.invoke(app, it) }
                                    },
                                    onDrag = { onAppDrag?.invoke(it) },
                                    onDragEnd = { onAppDragEnd?.invoke() },
                                    onDragCancel = { onAppDragCancel?.invoke() }
                                )
                            }
                        }

                        DrawerMode.SMART -> {
                            if (state.search.isNotBlank()) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier.weight(1f).nestedScroll(pullDownToExit),
                                    state = gridState,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 16.dp)
                                ) {
                                    gridItems(filtered, key = { it.key }, contentType = { "app" }) { app ->
                                        AppCell(
                                            app,
                                            state.config,
                                            viewModel,
                                            showLabel = true,
                                            fromHome = false,
                                            dragEnabled = true,
                                            iconPackKey = state.iconPack,
                                            onDragStart = { bounds ->
                                                bounds?.let { onAppDragStart?.invoke(app, it) }
                                            },
                                            onDrag = { onAppDrag?.invoke(it) },
                                            onDragEnd = { onAppDragEnd?.invoke() },
                                            onDragCancel = { onAppDragCancel?.invoke() }
                                        )
                                    }
                                }
                            } else {
                                val groups = remember(filtered) { smartGroups(filtered) }
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier.weight(1f).nestedScroll(pullDownToExit),
                                    state = gridState,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 16.dp)
                                ) {
                                    groups.forEach { (category, group) ->
                                        item(key = "smart-$category", contentType = "smart") {
                                            SmartCategoryCard(
                                                category = category,
                                                apps = group,
                                                state = state,
                                                viewModel = viewModel,
                                                onOpenFolder = { openFolder = category },
                                                onAppDragStart = onAppDragStart,
                                                onAppDrag = onAppDrag,
                                                onAppDragEnd = onAppDragEnd,
                                                onAppDragCancel = onAppDragCancel
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            openFolder?.let { folder ->
                val group = remember(filtered, folder) {
                    smartGroups(filtered).firstOrNull { it.first == folder }?.second.orEmpty()
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFA0A0A0B))
                        .pointerInput(folder) {
                            detectTapGestures { openFolder = null }
                        }
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                folder.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = Paper,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${group.size} APPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = Muted
                            )
                            Spacer(Modifier.width(10.dp))
                            TextButton(onClick = { openFolder = null }) {
                                Text("CLOSE", style = MaterialTheme.typography.labelSmall, color = Muted)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(state.config.columns),
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            gridItems(group, key = { it.key }, contentType = { "app" }) { app ->
                                AppCell(
                                    app,
                                    state.config,
                                    viewModel,
                                    showLabel = state.config.showLabels,
                                    fromHome = false,
                                    dragEnabled = true,
                                    iconPackKey = state.iconPack,
                                    onDragStart = { bounds ->
                                        bounds?.let { onAppDragStart?.invoke(app, it) }
                                    },
                                    onDrag = { onAppDrag?.invoke(it) },
                                    onDragEnd = { onAppDragEnd?.invoke() },
                                    onDragCancel = { onAppDragCancel?.invoke() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(
    mode: DrawerMode,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (selected) NothingRed else Line)
    ) {
        Text(
            when (mode) {
                DrawerMode.GRID -> "GRID"
                DrawerMode.LIST -> "LIST"
                DrawerMode.SMART -> "SMART"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Paper else Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        )
    }
}

@Composable
private fun DrawerListRow(
    app: LauncherApp,
    viewModel: LauncherViewModel,
    iconPackKey: String?,
    onDragStart: (LauncherApp, Rect) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val icon by produceState<ImageBitmap?>(initialValue = viewModel.cachedIcon(app), app.key, iconPackKey) {
        value = viewModel.loadIcon(app)
    }
    var bounds by remember(app.key) { mutableStateOf(Rect()) }
    var suppressClickUntil by remember(app.key) { mutableStateOf(0L) }
    val currentStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentEnd by rememberUpdatedState(onDragEnd)
    val currentCancel by rememberUpdatedState(onDragCancel)
    Row(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val b = coordinates.boundsInRoot()
                bounds = Rect(
                    b.left.roundToInt(),
                    b.top.roundToInt(),
                    b.right.roundToInt(),
                    b.bottom.roundToInt()
                )
            }
            .clip(RoundedCornerShape(16.dp))
            .background(Surface0.copy(alpha = 0.78f))
            .pointerInput(app.key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        suppressClickUntil = SystemClock.uptimeMillis() + 800
                        currentStart(app, bounds)
                    },
                    onDragEnd = {
                        suppressClickUntil = SystemClock.uptimeMillis() + 800
                        currentEnd()
                    },
                    onDragCancel = {
                        suppressClickUntil = SystemClock.uptimeMillis() + 800
                        currentCancel()
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        currentDrag(amount)
                    }
                )
            }
            .clickable {
                if (SystemClock.uptimeMillis() >= suppressClickUntil) {
                    viewModel.launch(app, fromHome = false)
                }
            }
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(icon, 42, false)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyMedium, color = Paper)
            Text(
                app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DotGlyph(Modifier.size(12.dp), tint = Muted, center = NothingRed)
    }
}

@Composable
private fun SmartCategoryCard(
    category: String,
    apps: List<LauncherApp>,
    state: LauncherState,
    viewModel: LauncherViewModel,
    onOpenFolder: () -> Unit,
    onAppDragStart: ((LauncherApp, Rect) -> Unit)?,
    onAppDrag: ((Offset) -> Unit)?,
    onAppDragEnd: (() -> Unit)?,
    onAppDragCancel: (() -> Unit)?
) {
    val miniConfig = remember(state.config) {
        state.config.copy(iconSize = 38, showLabels = false)
    }
    Surface(
        color = Surface0.copy(alpha = 0.86f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Paper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    apps.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingRed
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(2) { column ->
                            val index = row * 2 + column
                            val app = apps.getOrNull(index)
                            if (index < 3 && app != null) {
                                AppCell(
                                    app = app,
                                    config = miniConfig,
                                    viewModel = viewModel,
                                    modifier = Modifier.weight(1f),
                                    showLabel = false,
                                    fromHome = false,
                                    dragEnabled = true,
                                    iconPackKey = state.iconPack,
                                    onDragStart = { bounds ->
                                        bounds?.let { onAppDragStart?.invoke(app, it) }
                                    },
                                    onDrag = { onAppDrag?.invoke(it) },
                                    onDragEnd = { onAppDragEnd?.invoke() },
                                    onDragCancel = { onAppDragCancel?.invoke() }
                                )
                            } else {
                                FolderTile(
                                    apps = apps,
                                    viewModel = viewModel,
                                    iconPackKey = state.iconPack,
                                    modifier = Modifier.weight(1f),
                                    onClick = onOpenFolder
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderTile(
    apps: List<LauncherApp>,
    viewModel: LauncherViewModel,
    iconPackKey: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val hidden = (apps.size - 3).coerceAtLeast(0)
    Column(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(26.dp)) {
            apps.take(3).forEachIndexed { index, app ->
                MiniAppIcon(
                    app = app,
                    viewModel = viewModel,
                    iconPackKey = iconPackKey,
                    modifier = Modifier
                        .offset(x = (index * 6).dp, y = (index * 2).dp)
                        .size(22.dp)
                        .zIndex((3 - index).toFloat())
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            if (hidden > 0) "+$hidden" else "ALL",
            style = MaterialTheme.typography.labelSmall,
            color = if (hidden > 0) NothingRed else Muted,
            maxLines = 1
        )
    }
}

@Composable
private fun MiniAppIcon(
    app: LauncherApp,
    viewModel: LauncherViewModel,
    iconPackKey: String?,
    modifier: Modifier = Modifier
) {
    val icon by produceState<ImageBitmap?>(initialValue = viewModel.cachedIcon(app), app.key, iconPackKey) {
        value = viewModel.loadIcon(app)
    }
    AppIcon(icon, 22, false, modifier)
}

private val SMART_ORDER = listOf(
    "Communication", "Social", "Entertainment", "Games", "Tools", "Productivity",
    "Finance", "Photography", "Shopping", "Travel", "System", "Other"
)

private fun smartGroups(apps: List<LauncherApp>): List<Pair<String, List<LauncherApp>>> =
    apps.groupBy { it.category }
        .toList()
        .sortedBy { (category, _) ->
            SMART_ORDER.indexOf(category).let { if (it < 0) SMART_ORDER.size else it }
        }

@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        cursorBrush = SolidColor(Paper),
        textStyle = TextStyle(
            color = Paper,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace
        ),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DotGlyph(
                    Modifier.size(14.dp),
                    tint = Color.White.copy(alpha = 0.7f),
                    center = Color.White.copy(alpha = 0.7f)
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            "Search apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.45f)
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}
