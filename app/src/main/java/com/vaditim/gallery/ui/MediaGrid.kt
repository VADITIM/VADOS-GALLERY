package com.vaditim.gallery.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.media.Thumbnail
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DEFAULT_COLUMNS = 4
private const val MIN_COLUMNS = 1
private const val MAX_COLUMNS = 5

// How far a pinch has to travel before the grid steps one column: spreading past this shows fewer, larger photos.
private const val PINCH_STEP = 1.28f
private val GAP = 3.dp
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

// The scroll position and whether the grid has been put at its newest end yet. Held above the grid so leaving a section and coming back finds it where it was; the column count rides along so a folder keeps the zoom it was left at.
class GridMemory {
    val state = LazyGridState()
    var isPositioned = false
    var knownCount = 0
    var columns by mutableIntStateOf(DEFAULT_COLUMNS)
}

// A grid is photos with a month header in front of each month's first photo. `index` is the photo's place in the original list, which is what the viewer opens at.
sealed interface GridEntry {
    data class Header(val label: String, val key: String) : GridEntry
    data class Photo(val item: MediaItem, val index: Int) : GridEntry
}

fun buildEntries(items: List<MediaItem>): List<GridEntry> {
    val entries = ArrayList<GridEntry>(items.size + 24)
    var currentMonth: YearMonth? = null
    items.forEachIndexed { index, item ->
        val month = YearMonth.from(Instant.ofEpochMilli(item.timestampMillis).atZone(ZoneId.systemDefault()))
        if (month != currentMonth) {
            entries += GridEntry.Header(MONTH_FORMAT.format(month), "month-$month")
            currentMonth = month
        }
        entries += GridEntry.Photo(item, index)
    }
    return entries
}

// Oldest at the top, newest at the bottom right, opened at the bottom — the Apple order. Items arrive already sorted ascending.
@Composable
fun MediaGrid(
    items: List<MediaItem>,
    memory: GridMemory,
    onOpen: (Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    selection: Selection? = null,
    scrollToNewestRequest: Int = 0,
    emptyCaption: String = "Nothing here yet.",
) {
    val state = memory.state
    val entries = remember(items) { buildEntries(items) }
    val columns = memory.columns

    LaunchedEffect(entries.size) {
        if (entries.isEmpty()) return@LaunchedEffect
        val lastVisible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val wasAtNewest = lastVisible >= memory.knownCount - MAX_COLUMNS
        if (!memory.isPositioned || (entries.size > memory.knownCount && wasAtNewest)) {
            state.scrollToItem(entries.lastIndex)
            memory.isPositioned = true
        }
        memory.knownCount = entries.size
    }

    // Tapping the section the bar already shows takes you home, which here is the newest end.
    LaunchedEffect(scrollToNewestRequest) {
        if (scrollToNewestRequest > 0 && entries.isNotEmpty()) state.animateScrollToItem(entries.lastIndex)
    }

    if (items.isEmpty()) {
        Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            BasicText(emptyCaption, style = Type.caption.copy(color = Palette.textFaint))
        }
        return
    }

    val tileSize = thumbnailPixels(columns, GAP)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalArrangement = Arrangement.spacedBy(GAP),
        modifier = modifier.fillMaxSize().pinchColumns(memory),
    ) {
        items(
            entries,
            key = { entry -> if (entry is GridEntry.Photo) entry.item.id else (entry as GridEntry.Header).key },
            span = { entry -> if (entry is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            contentType = { entry -> if (entry is GridEntry.Header) "header" else "tile" },
        ) { entry ->
            when (entry) {
                is GridEntry.Header -> MonthHeader(entry.label)
                is GridEntry.Photo -> {
                    val item = entry.item
                    Tile(
                        item = item,
                        sizePixels = tileSize,
                        isSelected = selection != null && item.id in selection.selectedIds,
                        onClick = {
                            if (selection != null && selection.isActive) selection.onToggle(item) else onOpen(entry.index)
                        },
                        onLongClick = selection?.let { chosen -> { chosen.onLongPress(item) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(label: String) {
    BasicText(label, style = Type.cardTitle, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))
}

// Two fingers change the column count and nothing else; one finger is left alone so the grid still scrolls. Watching on the initial pass lets the pinch claim its events before the list can start scrolling.
private fun Modifier.pinchColumns(memory: GridMemory): Modifier = pointerInput(memory) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zoom = 1f
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                zoom *= event.calculateZoom()
                if (zoom > PINCH_STEP) {
                    memory.columns = (memory.columns - 1).coerceAtLeast(MIN_COLUMNS)
                    zoom = 1f
                } else if (zoom < 1f / PINCH_STEP) {
                    memory.columns = (memory.columns + 1).coerceAtMost(MAX_COLUMNS)
                    zoom = 1f
                }
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

// The month of the top visible row, for the chip that floats over the grid.
@Composable
fun rememberVisibleMonth(items: List<MediaItem>, memory: GridMemory): State<String> {
    val entries = remember(items) { buildEntries(items) }
    return remember(entries, memory) {
        derivedStateOf {
            when (val entry = entries.getOrNull(memory.state.firstVisibleItemIndex)) {
                is GridEntry.Header -> entry.label
                is GridEntry.Photo -> MONTH_FORMAT.format(Instant.ofEpochMilli(entry.item.timestampMillis).atZone(ZoneId.systemDefault()))
                null -> ""
            }
        }
    }
}

// What a grid needs to know about multi-select: which tiles are in it, and how to toggle one. A long press toggles (and so starts a selection); once one is active, a tap toggles too.
// `onStart`, when given, handles the long press that would begin a selection (an album opens a menu instead); once a selection is active, long press toggles like a tap.
class Selection(
    val selectedIds: Set<Long>,
    private val isAlwaysActive: Boolean = false,
    private val onStart: ((MediaItem) -> Unit)? = null,
    val onToggle: (MediaItem) -> Unit,
) {
    val isActive: Boolean get() = isAlwaysActive || selectedIds.isNotEmpty()

    fun onLongPress(item: MediaItem) {
        if (!isActive && onStart != null) onStart(item) else onToggle(item)
    }
}

@Composable
private fun Tile(item: MediaItem, sizePixels: Int, isSelected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val context = LocalContext.current
    val request = remember(item.uri, sizePixels) {
        // Private photos live outside MediaStore and have no cached thumbnail, so they are decoded from the file, sampled down.
        val data: Any = if (item.uri.scheme == "content") Thumbnail(item.uri, sizePixels) else item.uri
        ImageRequest.Builder(context).data(data).size(sizePixels).build()
    }
    val selectedScale by animateFloatAsState(if (isSelected) 0.86f else 1f, tween(Motion.STATE_MS, easing = Motion.backOut), label = "selected")
    Box(
        Modifier
            .aspectRatio(1f)
            .pressable(onClick = onClick, pressedScale = 0.94f, onLongClick = onLongClick)
            .graphicsLayer { scaleX = selectedScale; scaleY = selectedScale }
            .clip(Shapes.tile)
            .background(Palette.sunken),
    ) {
        AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (item.isVideo) {
            BasicText(
                if (item.durationMillis > 0) formatDuration(item.durationMillis) else "VIDEO",
                style = Type.value.copy(color = Palette.textBright),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .clip(Shapes.capsule)
                    .background(Palette.panel)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        if (isSelected) Box(Modifier.fillMaxSize().border(3.dp, LocalAccent.current, Shapes.tile))
    }
}

@Composable
private fun thumbnailPixels(columns: Int, gap: Dp): Int {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    return with(density) { ((configuration.screenWidthDp.dp - gap * (columns - 1)) / columns).roundToPx() }
}

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
