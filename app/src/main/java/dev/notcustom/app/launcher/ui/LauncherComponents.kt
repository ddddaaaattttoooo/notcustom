package dev.notcustom.app.launcher.ui

import android.graphics.Rect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.notcustom.app.launcher.LauncherApp
import dev.notcustom.app.launcher.LauncherConfig
import dev.notcustom.app.launcher.LauncherViewModel
import dev.notcustom.app.ui.theme.Muted
import dev.notcustom.app.ui.theme.NothingRed
import dev.notcustom.app.ui.theme.Paper
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.hypot

@Composable
fun DotGlyph(
    modifier: Modifier = Modifier,
    tint: Color = Paper,
    center: Color = NothingRed
) {
    Canvas(modifier) {
        val step = size.width / 4f
        val radius = step * 0.2f
        for (row in 0..2) {
            for (col in 0..2) {
                drawCircle(
                    color = if (row == 1 && col == 1) center else tint,
                    radius = radius,
                    center = Offset(step * (col + 1), step * (row + 1))
                )
            }
        }
    }
}

@Composable
fun BrandMark(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val base = if (onClick != null) modifier.clickable { onClick() } else modifier
    Column(base, horizontalAlignment = Alignment.End) {
        DotGlyph(
            Modifier.size(30.dp),
            center = NothingRed.copy(alpha = 0.9f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "NOTCUSTOM",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
            color = Color.White.copy(alpha = 0.55f)
        )
    }
}

@Composable
fun ClockHeader(onOpenSettings: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(15_000)
        }
    }
    val time = remember(now) { now.format(DateTimeFormatter.ofPattern("HH:mm")) }
    val date = remember(now) {
        now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
            .uppercase(Locale.getDefault())
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                time,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 46.sp,
                letterSpacing = 2.sp,
                color = Paper
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(24.dp).height(2.dp).background(NothingRed))
                Spacer(Modifier.width(8.dp))
                Text(
                    date,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
        }
        BrandMark(onClick = onOpenSettings)
    }
}

@Composable
fun AppIcon(bitmap: ImageBitmap?, sizeDp: Int, monochrome: Boolean) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(percent = 24))
            .background(Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = if (monochrome) {
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                } else {
                    null
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(
    app: LauncherApp,
    config: LauncherConfig,
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    fromHome: Boolean = true,
    iconPackKey: String? = null,
    isReturning: Boolean = false,
    isDragging: Boolean = false,
    dragOffset: Offset = Offset.Zero,
    onDragStart: (() -> Unit)? = null,
    onDrag: ((Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onPosition: ((String, Rect?) -> Unit)? = null
) {
    val icon by produceState<ImageBitmap?>(
        initialValue = viewModel.cachedIcon(app),
        app.component,
        iconPackKey
    ) {
        value = viewModel.loadIcon(app)
    }
    val interaction = remember { MutableInteractionSource() }
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnPosition by rememberUpdatedState(onPosition)
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "iconScale")
    val returnScale by animateFloatAsState(
        targetValue = if (isReturning) 1.04f else 1f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "returnScale"
    )
    var iconBounds by remember { mutableStateOf<Rect?>(null) }
    val view = LocalView.current

    DisposableEffect(app.key) {
        onDispose { currentOnPosition?.invoke(app.key, null) }
    }

    val cellModifier = if (fromHome && onDragStart != null && onDrag != null && onDragEnd != null) {
        modifier
            .zIndex(if (isDragging) 2f else 0f)
            .graphicsLayer {
                translationX = if (isDragging) dragOffset.x else 0f
                translationY = if (isDragging) dragOffset.y else 0f
                scaleX = if (isDragging) 1.06f else 1f
                scaleY = if (isDragging) 1.06f else 1f
                alpha = if (isDragging) 0.92f else 1f
            }
            .pointerInput(app.key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { currentDragStart?.invoke() },
                    onDragEnd = { currentDragEnd?.invoke() },
                    onDragCancel = { currentDragEnd?.invoke() },
                    onDrag = { change, amount ->
                        change.consume()
                        currentDrag?.invoke(amount)
                    }
                )
            }
            .clickable(
                interactionSource = interaction,
                indication = null
            ) { viewModel.launch(app, iconBounds, fromHome, view) }
    } else {
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { viewModel.launch(app, iconBounds, fromHome, view) },
                onLongClick = { viewModel.showMenu(app) }
            )
    }

    Column(
        cellModifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale * returnScale
                    scaleY = scale * returnScale
                }
                .onGloballyPositioned { coordinates ->
                    if (fromHome) {
                        // ActivityOptions expects coordinates relative to its source view.
                        val bounds = coordinates.boundsInRoot()
                        iconBounds = Rect(
                            bounds.left.roundToInt(),
                            bounds.top.roundToInt(),
                            bounds.right.roundToInt(),
                            bounds.bottom.roundToInt()
                        )
                    }
                    currentOnPosition?.invoke(app.key, coordinates.boundsInRoot().let { bounds ->
                        Rect(
                            bounds.left.roundToInt(), bounds.top.roundToInt(),
                            bounds.right.roundToInt(), bounds.bottom.roundToInt()
                        )
                    })
                }
        ) {
            AppIcon(icon, config.iconSize, config.monochrome)
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                app.label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AppGrid(
    apps: List<LauncherApp>,
    config: LauncherConfig,
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier,
    iconPackKey: String? = null
) {
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(config.columns),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 8.dp)
    ) {
        items(apps.size, key = { apps[it].key }) { index ->
            AppCell(apps[index], config, viewModel, iconPackKey = iconPackKey)
        }
    }
}

@Composable
fun SearchPill(onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        color = Color.White.copy(alpha = 0.10f),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DotGlyph(
                Modifier.size(16.dp),
                tint = Color.White.copy(alpha = 0.7f),
                center = NothingRed
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Search apps",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
fun DockRow(
    apps: List<LauncherApp>,
    config: LauncherConfig,
    viewModel: LauncherViewModel,
    returningKey: String? = null,
    iconPackKey: String? = null
) {
    if (apps.isEmpty()) return
    val cellBounds = remember { mutableStateMapOf<String, Rect>() }
    var draggedApp by remember { mutableStateOf<LauncherApp?>(null) }
    var dragOrigin by remember { mutableStateOf<Rect?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    androidx.compose.material3.Surface(
        color = Color.White.copy(alpha = 0.10f),
        shape = RoundedCornerShape(30.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            apps.forEach { app ->
                AppCell(
                    app,
                    config,
                    viewModel,
                    modifier = Modifier.weight(1f),
                    showLabel = false,
                    iconPackKey = iconPackKey,
                    isReturning = returningKey == app.key,
                    isDragging = draggedApp?.key == app.key,
                    dragOffset = dragOffset,
                    onDragStart = {
                        draggedApp = app
                        dragOrigin = cellBounds[app.key]?.let { Rect(it) }
                        dragOffset = Offset.Zero
                    },
                    onDrag = { dragOffset += it },
                    onDragEnd = {
                        val bounds = dragOrigin
                        if (bounds != null && hypot(
                                dragOffset.x.toDouble(), dragOffset.y.toDouble()
                            ) > 24.0
                        ) {
                            val x = bounds.exactCenterX() + dragOffset.x
                            val y = bounds.exactCenterY() + dragOffset.y
                            val targetKey = cellBounds.entries
                                .filter { it.key != app.key }
                                .minByOrNull {
                                    hypot(
                                        (it.value.exactCenterX() - x).toDouble(),
                                        (it.value.exactCenterY() - y).toDouble()
                                    )
                                }?.key
                            apps.firstOrNull { it.key == targetKey }?.let { target ->
                                viewModel.moveDock(app, target)
                            }
                        }
                        draggedApp = null
                        dragOrigin = null
                        dragOffset = Offset.Zero
                    },
                    onPosition = { key, rect ->
                        if (rect == null) cellBounds.remove(key) else cellBounds[key] = rect
                    }
                )
            }
        }
    }
}
