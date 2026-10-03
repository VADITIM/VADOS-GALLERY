package com.vaditim.gallery.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val COLUMNS = 4
private val GAP = 3.dp
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

// The scroll position and whether the grid has been put at its newest end yet. Held above the grid so leaving a section and coming back finds it where it was.
class GridMemory {
    val state = LazyGridState()
    var isPositioned = false
    var knownCount = 0
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

    LaunchedEffect(items.size) {
        if (items.isEmpty()) return@LaunchedEffect
        val lastVisible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val wasAtNewest = lastVisible >= memory.knownCount - COLUMNS
        if (!memory.isPositioned || (items.size > memory.knownCount && wasAtNewest)) {
            state.scrollToItem(items.lastIndex)
            memory.isPositioned = true
        }
        memory.knownCount = items.size
    }

    // Tapping the section the bar already shows takes you home, which here is the newest end.
    LaunchedEffect(scrollToNewestRequest) {
        if (scrollToNewestRequest > 0 && items.isNotEmpty()) state.animateScrollToItem(items.lastIndex)
    }

    if (items.isEmpty()) {
        Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            BasicText(emptyCaption, style = Type.caption.copy(color = Palette.textFaint))
        }
        return
    }

    val tileSize = thumbnailPixels(COLUMNS, GAP)
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalArrangement = Arrangement.spacedBy(GAP),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }, contentType = { _, _ -> "tile" }) { index, item ->
            val isSelected = selection != null && item.id in selection.selectedIds
            Tile(
                item = item,
                sizePixels = tileSize,
                isSelected = isSelected,
                onClick = {
                    if (selection != null && selection.isActive) selection.onToggle(item) else onOpen(index)
                },
                onLongClick = selection?.let { { it.onToggle(item) } },
            )
        }
    }
}

// The month of the top visible row, for the chip that floats over the grid.
@Composable
fun rememberVisibleMonth(items: List<MediaItem>, memory: GridMemory): State<String> =
    remember(items, memory) {
        derivedStateOf {
            items.getOrNull(memory.state.firstVisibleItemIndex)?.let {
                MONTH_FORMAT.format(Instant.ofEpochMilli(it.timestampMillis).atZone(ZoneId.systemDefault()))
            } ?: ""
        }
    }

// What a grid needs to know about multi-select: which tiles are in it, and how to toggle one. A long press toggles (and so starts a selection); once one is active, a tap toggles too.
class Selection(val selectedIds: Set<Long>, private val isAlwaysActive: Boolean = false, val onToggle: (MediaItem) -> Unit) {
    val isActive: Boolean get() = isAlwaysActive || selectedIds.isNotEmpty()
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
