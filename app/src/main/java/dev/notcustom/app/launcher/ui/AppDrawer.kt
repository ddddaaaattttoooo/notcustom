package dev.notcustom.app.launcher.ui

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.notcustom.app.launcher.LauncherApp
import dev.notcustom.app.launcher.LauncherState
import dev.notcustom.app.launcher.LauncherViewModel
import dev.notcustom.app.launcher.DrawerMode
import dev.notcustom.app.ui.theme.Muted
import dev.notcustom.app.ui.theme.Paper
import dev.notcustom.app.ui.theme.Line
import dev.notcustom.app.ui.theme.NothingRed
import dev.notcustom.app.ui.theme.Surface0
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow

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
        color = Color(0xF70A0A0B),
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (dragActive) 0f else 1f }
    ) {
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
                            DrawerListRow(app, viewModel, state.iconPack)
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
                        val groups = remember(filtered) { groupApps(filtered) }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(state.config.columns),
                            modifier = Modifier.weight(1f).nestedScroll(pullDownToExit),
                            state = gridState,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            groups.forEach { (category, group) ->
                                item(
                                    key = "category-$category",
                                    span = { GridItemSpan(maxLineSpan) },
                                    contentType = "header"
                                ) {
                                    Text(
                                        "$category  ·  ${group.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Muted,
                                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                    )
                                }
                                gridItems(group, key = { "${category}-${it.key}" }, contentType = { "app" }) { app ->
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerListRow(app: LauncherApp, viewModel: LauncherViewModel, iconPackKey: String?) {
    val icon by produceState<ImageBitmap?>(initialValue = viewModel.cachedIcon(app), app.key, iconPackKey) {
        value = viewModel.loadIcon(app)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface0.copy(alpha = 0.78f))
            .combinedClickable(
                onClick = { viewModel.launch(app, fromHome = false) },
                onLongClick = { viewModel.showMenu(app) }
            )
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

private fun groupApps(apps: List<LauncherApp>): List<Pair<String, List<LauncherApp>>> {
    val categories = listOf(
        "Communication", "Social", "Productivity", "Media", "Travel", "Shopping",
        "Finance", "Tools", "Games", "Other"
    )
    return apps.groupBy(::categoryForApp)
        .toSortedMap(compareBy<String> { category ->
            categories.indexOf(category).let { if (it < 0) Int.MAX_VALUE else it }
        })
        .map { it.key to it.value }
}

private fun categoryForApp(app: LauncherApp): String {
    val text = "${app.label} ${app.packageName}".lowercase()
    fun has(vararg terms: String) = terms.any { text.contains(it) }
    return when {
        has("phone", "dialer", "contact", "message", "sms", "gmail", "mail", "whatsapp", "telegram") -> "Communication"
        has("instagram", "facebook", "tiktok", "snapchat", "reddit", "twitter", "social") -> "Social"
        has("calendar", "docs", "sheets", "drive", "office", "note", "task", "notion", "work") -> "Productivity"
        has("camera", "photo", "gallery", "youtube", "music", "spotify", "video", "netflix", "podcast") -> "Media"
        has("maps", "navigation", "travel", "uber", "bolt", "transit", "booking", "airline") -> "Travel"
        has("shop", "store", "amazon", "retail", "marketplace") -> "Shopping"
        has("bank", "wallet", "pay", "finance", "payment") -> "Finance"
        has("settings", "calculator", "clock", "file", "browser", "recorder", "utility") -> "Tools"
        has("game") -> "Games"
        else -> "Other"
    }
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
