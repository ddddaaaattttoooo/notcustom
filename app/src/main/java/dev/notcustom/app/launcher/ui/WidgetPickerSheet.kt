package dev.notcustom.app.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import dev.notcustom.app.launcher.HOME_ROWS
import dev.notcustom.app.launcher.LauncherState
import dev.notcustom.app.launcher.LauncherViewModel
import dev.notcustom.app.launcher.WidgetChoice
import dev.notcustom.app.ui.theme.Line
import dev.notcustom.app.ui.theme.Muted
import dev.notcustom.app.ui.theme.NothingRed
import dev.notcustom.app.ui.theme.Paper
import dev.notcustom.app.ui.theme.Surface0
import dev.notcustom.app.ui.theme.Surface1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun WidgetPickerSheet(
    state: LauncherState,
    viewModel: LauncherViewModel,
    onInstall: (AppWidgetProviderInfo) -> Unit,
    slotBounds: Map<Int, Rect>,
    page: Int,
    columns: Int
) {
    val choices = remember(state.widgetPickerPackage) {
        viewModel.widgetChoices(state.widgetPickerPackage)
    }
    var query by remember { mutableStateOf("") }
    var drag by remember { mutableStateOf<WidgetPickerDrag?>(null) }
    val filtered = remember(choices, query) {
        if (query.isBlank()) {
            choices
        } else {
            choices.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.appLabel.contains(query, ignoreCase = true)
            }
        }
    }
    val recommendations = remember(choices, query, state.widgetPickerPackage) {
        if (query.isNotBlank() || state.widgetPickerPackage != null) emptyList() else choices
            .map { it to recommendationScore(it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .distinctBy { it.second }
            .take(4)
            .map { it.first }
    }
    val appGroups = remember(filtered) {
        filtered.groupBy { it.appLabel }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    BackHandler { if (drag == null) viewModel.closeWidgetPicker() }

    val finishDrag: () -> Unit = {
        val finished = drag
        drag = null
        if (finished != null) {
            val pageSize = columns * HOME_ROWS
            val x = finished.origin.exactCenterX() + finished.offset.x
            val y = finished.origin.exactCenterY() + finished.offset.y
            val target = slotBounds.entries.firstOrNull {
                it.key / pageSize == page &&
                    it.value.contains(x.roundToInt(), y.roundToInt())
            }?.key
            if (target != null) {
                onInstall(finished.choice.info)
            } else {
                viewModel.showMessage("Drop the widget onto a Home slot to add it.")
            }
        }
    }
    val updateHover: (WidgetPickerDrag) -> Unit = { d ->
        val pageSize = columns * HOME_ROWS
        val x = d.origin.exactCenterX() + d.offset.x
        val y = d.origin.exactCenterY() + d.offset.y
        d.hoverSlot = slotBounds.entries.firstOrNull {
            it.key / pageSize == page && it.value.contains(x.roundToInt(), y.roundToInt())
        }?.key ?: -1
    }
    val dragging = drag != null
    Box(Modifier.fillMaxSize()) {
        if (!dragging) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .clickable(interactionSource = interaction, indication = null) {
                        viewModel.closeWidgetPicker()
                    }
            )
        }
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                color = Surface0,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = if (dragging) 0f else 1f }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 14.dp, bottom = 20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ADD WIDGET", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.width(24.dp).height(2.dp).background(NothingRed))
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${filtered.size}/${choices.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (state.widgetPickerPackage == null) {
                            "Browse by app, or long-press a widget to drag it onto Home."
                        } else {
                            "Widgets provided by this app. Long-press to drag onto Home."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                    Spacer(Modifier.height(14.dp))
                    WidgetSearch(query) { query = it }
                    Spacer(Modifier.height(14.dp))
                    if (filtered.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(170.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (choices.isEmpty()) {
                                    if (state.widgetPickerPackage != null) {
                                        "This app does not provide widgets."
                                    } else {
                                        "No widgets are available on this device."
                                    }
                                } else {
                                    "No widgets match \"$query\"."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 4.dp)
                        ) {
                            if (recommendations.isNotEmpty()) {
                                item(key = "recommendations-title", span = { GridItemSpan(maxLineSpan) }) {
                                    SectionTitle("FOR YOU", "Useful widgets from apps you have installed")
                                }
                                items(recommendations, key = { "pick-${widgetKey(it)}" }) { choice ->
                                    DraggableWidgetCard(
                                        choice = choice,
                                        featured = true,
                                        drag = drag,
                                        onDragChange = { drag = it },
                                        onDragMove = updateHover,
                                        onDrop = finishDrag,
                                        onClick = { onInstall(choice.info) }
                                    )
                                }
                            }
                            appGroups.forEach { (appLabel, appChoices) ->
                                item(key = "app-title-$appLabel", span = { GridItemSpan(maxLineSpan) }) {
                                    SectionTitle(
                                        appLabel.uppercase(),
                                        "${appChoices.size} ${if (appChoices.size == 1) "widget" else "widgets"}"
                                    )
                                }
                                items(appChoices, key = { "app-${widgetKey(it)}" }) { choice ->
                                    DraggableWidgetCard(
                                        choice = choice,
                                        drag = drag,
                                        onDragChange = { drag = it },
                                        onDragMove = updateHover,
                                        onDrop = finishDrag,
                                        onClick = { onInstall(choice.info) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        drag?.let { current ->
            val pageSize = columns * HOME_ROWS
            slotBounds.forEach { (slot, rect) ->
                if (slot / pageSize == page) {
                    val active = current.hoverSlot == slot
                    Box(
                        Modifier
                            .offset { IntOffset(rect.left, rect.top) }
                            .size(
                                with(LocalDensity.current) { rect.width().toDp() },
                                with(LocalDensity.current) { rect.height().toDp() }
                            )
                            .background(
                                if (active) NothingRed.copy(alpha = 0.10f) else Color.Transparent,
                                RoundedCornerShape(18.dp)
                            )
                            .border(
                                if (active) 2.dp else 1.dp,
                                if (active) NothingRed else NothingRed.copy(alpha = 0.35f),
                                RoundedCornerShape(18.dp)
                            )
                    )
                }
            }
            WidgetPickerDragGhost(current)
        }
    }
}

private class WidgetPickerDrag(val choice: WidgetChoice, val origin: Rect) {
    var offset by mutableStateOf(Offset.Zero)
    var hoverSlot by mutableIntStateOf(-1)
}

@Composable
private fun DraggableWidgetCard(
    choice: WidgetChoice,
    featured: Boolean = false,
    drag: WidgetPickerDrag?,
    onDragChange: (WidgetPickerDrag?) -> Unit,
    onDragMove: (WidgetPickerDrag) -> Unit,
    onDrop: () -> Unit,
    onClick: () -> Unit
) {
    var bounds by remember(choice.info.provider) { mutableStateOf(Rect()) }
    var suppressClickUntil by remember { mutableStateOf(0L) }
    val currentDrag by rememberUpdatedState(drag)
    val currentDrop by rememberUpdatedState(onDrop)
    val currentDragChange by rememberUpdatedState(onDragChange)
    val currentDragMove by rememberUpdatedState(onDragMove)
    val isDragging = drag?.choice?.info?.provider == choice.info.provider
    Surface(
        onClick = {
            if (android.os.SystemClock.uptimeMillis() >= suppressClickUntil) onClick()
        },
        color = Surface1,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (isDragging) NothingRed else Line),
        modifier = Modifier
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
            .pointerInput(choice.info.provider) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        suppressClickUntil = android.os.SystemClock.uptimeMillis() + 800
                        currentDragChange(WidgetPickerDrag(choice, bounds))
                    },
                    onDragEnd = {
                        suppressClickUntil = android.os.SystemClock.uptimeMillis() + 800
                        currentDrop()
                    },
                    onDragCancel = {
                        suppressClickUntil = android.os.SystemClock.uptimeMillis() + 800
                        currentDragChange(null)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        currentDrag?.let {
                            it.offset += amount
                            currentDragMove(it)
                        }
                    }
                )
            }
    ) {
        Column(Modifier.padding(10.dp)) {
            WidgetPreview(choice)
            Spacer(Modifier.height(8.dp))
            Text(
                choice.appLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                choice.label,
                style = MaterialTheme.typography.bodySmall,
                color = Paper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    choice.sizeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingRed
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (featured) "DRAG · TAP" else "DRAG · TAP",
                    style = MaterialTheme.typography.labelSmall,
                    color = Paper
                )
            }
        }
    }
}

@Composable
private fun WidgetPickerDragGhost(drag: WidgetPickerDrag) {
    val density = LocalDensity.current
    val width = with(density) { drag.origin.width().toDp() }
    val height = with(density) { drag.origin.height().toDp() }
    Surface(
        color = Surface0.copy(alpha = 0.94f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, NothingRed),
        modifier = Modifier
            .offset {
                IntOffset(
                    (drag.origin.left + drag.offset.x).roundToInt(),
                    (drag.origin.top + drag.offset.y).roundToInt()
                )
            }
            .size(width, height)
            .zIndex(50f)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                drag.choice.appLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                drag.choice.label,
                style = MaterialTheme.typography.bodySmall,
                color = Paper,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "DROP ON HOME",
                style = MaterialTheme.typography.labelSmall,
                color = NothingRed
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DotGlyph(
            Modifier.size(17.dp),
            tint = Muted.copy(alpha = 0.75f),
            center = NothingRed
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = Paper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

private fun widgetKey(choice: WidgetChoice): String =
    choice.info.provider.flattenToString() + "/" + choice.label

private fun recommendationScore(choice: WidgetChoice): Int {
    val text = "${choice.label} ${choice.appLabel}".lowercase()
    val usefulTypes = listOf(
        listOf("clock", "time", "deskclock") to 100,
        listOf("calendar", "agenda") to 90,
        listOf("weather", "forecast") to 80,
        listOf("battery") to 70,
        listOf("task", "todo", "reminder", "to-do") to 60,
        listOf("note", "keep") to 50,
        listOf("music", "player", "podcast") to 40,
        listOf("photo", "gallery") to 30
    )
    return usefulTypes.firstOrNull { (terms, _) -> terms.any(text::contains) }?.second ?: 0
}

@Composable
private fun WidgetPreview(choice: WidgetChoice) {
    val context = LocalContext.current
    val preview by produceState<ImageBitmap?>(initialValue = null, choice.info.provider) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                choice.info
                    .loadPreviewImage(context, context.resources.displayMetrics.densityDpi)
                    ?.toBitmap(220, 220)
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface0),
        contentAlignment = Alignment.Center
    ) {
        if (preview != null) {
            Image(
                bitmap = preview!!,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            )
        } else {
            DotGlyph(
                Modifier.size(30.dp),
                tint = Muted.copy(alpha = 0.45f),
                center = NothingRed.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun WidgetSearch(value: String, onChange: (String) -> Unit) {
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
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
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
                            "Search widgets",
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
