package dev.notcustom.app.launcher.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.BatteryManager
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.notcustom.app.launcher.LauncherApp
import dev.notcustom.app.launcher.NotificationDotService
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import dev.notcustom.app.launcher.LauncherConfig
import dev.notcustom.app.launcher.LauncherViewModel
import dev.notcustom.app.launcher.AtAGlanceStyle
import dev.notcustom.app.ui.theme.Muted
import dev.notcustom.app.ui.theme.NothingRed
import dev.notcustom.app.ui.theme.Paper
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

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
fun AtAGlanceHeader(
    style: AtAGlanceStyle,
    use24HourClock: Boolean,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(15_000)
        }
    }
    val time = remember(now, use24HourClock) {
        now.format(DateTimeFormatter.ofPattern(if (use24HourClock) "HH:mm" else "h:mm a", Locale.getDefault()))
    }
    val date = remember(now) {
        now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
            .uppercase(Locale.getDefault())
    }
    val battery by produceState(initialValue = "--%" to "BATTERY STATUS", style) {
        if (style == AtAGlanceStyle.BATTERY) {
            while (true) {
                value = readBatteryStatus(context)
                delay(30_000)
            }
        }
    }
    val nextAlarm by produceState<LocalDateTime?>(initialValue = null, style) {
        if (style == AtAGlanceStyle.NEXT_ALARM) {
            while (true) {
                value = readNextAlarm(context)
                delay(30_000)
            }
        }
    }
    val headline = when (style) {
        AtAGlanceStyle.CLOCK -> time
        AtAGlanceStyle.BATTERY -> battery.first
        AtAGlanceStyle.NEXT_ALARM -> nextAlarm?.format(
            DateTimeFormatter.ofPattern(if (use24HourClock) "HH:mm" else "h:mm a", Locale.getDefault())
        ) ?: "--:--"
    }
    val supportingText = when (style) {
        AtAGlanceStyle.CLOCK -> date
        AtAGlanceStyle.BATTERY -> "${battery.second}  ·  $date"
        AtAGlanceStyle.NEXT_ALARM -> nextAlarm?.format(
            DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
        )?.uppercase(Locale.getDefault()) ?: "NO UPCOMING ALARM"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                headline,
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
                    supportingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
        }
        BrandMark(onClick = onOpenSettings)
    }
}

private fun readBatteryStatus(context: Context): Pair<String, String> {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else -1
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val label = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
        BatteryManager.BATTERY_STATUS_FULL -> "FULLY CHARGED"
        else -> "BATTERY STATUS"
    }
    return (if (percent >= 0) "$percent%" else "--%") to label
}

private fun readNextAlarm(context: Context): LocalDateTime? = runCatching {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    alarmManager.nextAlarmClock?.triggerTime?.let { triggerTime ->
        Instant.ofEpochMilli(triggerTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }
}.getOrNull()

@Composable
fun AppIcon(
    bitmap: ImageBitmap?,
    sizeDp: Int,
    monochrome: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
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
    sourceHidden: Boolean = false,
    dragOffset: () -> Offset = { Offset.Zero },
    dragEnabled: Boolean = fromHome,
    onDragStart: ((Rect?) -> Unit)? = null,
    onDrag: ((Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
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
    val currentDragCancel by rememberUpdatedState(onDragCancel)
    val currentOnPosition by rememberUpdatedState(onPosition)
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iconScale"
    )
    val returnScale by animateFloatAsState(
        targetValue = if (isReturning) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "returnScale"
    )
    var iconBounds by remember { mutableStateOf<Rect?>(null) }
    var cellBounds by remember { mutableStateOf<Rect?>(null) }
    var cellScale by remember { mutableStateOf(1f) }
    var suppressClickUntil by remember { mutableStateOf(0L) }
    val view = LocalView.current

    DisposableEffect(app.key) {
        onDispose { currentOnPosition?.invoke(app.key, null) }
    }

    val cellModifier = if (dragEnabled && onDragStart != null && onDrag != null && onDragEnd != null) {
        modifier
            .zIndex(if (isDragging) 3f else 0f)
            .onGloballyPositioned { coordinates ->
                val b = coordinates.boundsInRoot()
                cellBounds = Rect(
                    b.left.roundToInt(),
                    b.top.roundToInt(),
                    b.right.roundToInt(),
                    b.bottom.roundToInt()
                )
                cellScale = if (coordinates.size.width > 0) {
                    b.width / coordinates.size.width
                } else {
                    1f
                }
            }
            .graphicsLayer {
                if (isDragging) {
                    if (sourceHidden) {
                        alpha = 0f
                    } else {
                        val offset = dragOffset()
                        translationX = offset.x
                        translationY = offset.y
                        scaleX = 1.06f
                        scaleY = 1.06f
                        alpha = 0.93f
                    }
                }
            }
            .then(
                if (isDragging && !sourceHidden) {
                    Modifier.border(2.dp, NothingRed, RoundedCornerShape(18.dp))
                } else {
                    Modifier
                }
            )
            // Click must sit outside the drag detector: inside, it consumes the finger-up first and a
            // hold-without-moving becomes a cancelled drag, so the app menu never opens.
            .clickable(
                interactionSource = interaction,
                indication = null
            ) {
                if (SystemClock.uptimeMillis() >= suppressClickUntil) {
                    viewModel.launch(app, iconBounds, fromHome, view)
                }
            }
            .pointerInput(app.key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { start ->
                        suppressClickUntil = Long.MAX_VALUE
                        val cell = cellBounds
                        val icon = iconBounds
                        val ghost = if (cell != null && icon != null) {
                            val touch = Offset(
                                cell.left + start.x * cellScale,
                                cell.top + start.y * cellScale
                            )
                            val width = icon.width().toFloat().coerceAtLeast(1f)
                            val height = icon.height().toFloat().coerceAtLeast(1f)
                            Rect(
                                (touch.x - width / 2f).roundToInt(),
                                (touch.y - height / 2f).roundToInt(),
                                (touch.x + width / 2f).roundToInt(),
                                (touch.y + height / 2f).roundToInt()
                            )
                        } else {
                            icon
                        }
                        currentDragStart?.invoke(ghost)
                    },
                    onDragEnd = {
                        suppressClickUntil = SystemClock.uptimeMillis() + 250
                        currentDragEnd?.invoke()
                    },
                    onDragCancel = {
                        suppressClickUntil = SystemClock.uptimeMillis() + 250
                        currentDragCancel?.invoke()
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        currentDrag?.invoke(amount * cellScale)
                    }
                )
            }
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
                    // Keep the icon bounds for the return-to-home animation.
                    val bounds = coordinates.boundsInRoot()
                    iconBounds = Rect(
                        bounds.left.roundToInt(),
                        bounds.top.roundToInt(),
                        bounds.right.roundToInt(),
                        bounds.bottom.roundToInt()
                    )
                    currentOnPosition?.invoke(app.key, iconBounds)
                }
        ) {
            if (app.folderId != null) {
                FolderIcon(app, config.iconSize, config.monochrome, viewModel, iconPackKey)
            } else {
                AppIcon(icon, config.iconSize, config.monochrome)
            }
            val notified by NotificationDotService.packages.collectAsState()
            val hasDot = if (app.folderId != null) {
                app.folderApps.any { it.packageName in notified }
            } else {
                app.packageName in notified
            }
            if (hasDot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size((config.iconSize * 0.2f).dp.coerceAtLeast(9.dp))
                        .background(NothingRed, CircleShape)
                        .border(1.5.dp, Color.Black.copy(alpha = 0.6f), CircleShape)
                )
            }
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
            AppCell(
                apps[index],
                config,
                viewModel,
                showLabel = config.showLabels,
                iconPackKey = iconPackKey
            )
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
