package com.vaditim.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.vaditim.gallery.vas.LocalAccent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.vaditim.gallery.Settings
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.media.Thumbnail
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable

private const val COVER_PIXELS = 512
private const val PINCH_STEP = 1.28f

// Folders only. No "Recent" and no "Favorites" album: both are sections already, and an album that repeats a section is the Samsung habit this app exists to drop.
@Composable
fun AlbumsScreen(
    albums: List<Album>,
    state: LazyGridState,
    onOpen: (Album) -> Unit,
    onLongPress: (Album) -> Unit,
    onNewAlbum: () -> Unit,
    contentPadding: PaddingValues,
    footer: @Composable () -> Unit,
    isRearranging: Boolean = false,
    onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
) {
    val haptic = LocalHapticFeedback.current
    val currentAlbums by rememberUpdatedState(albums)
    val currentOnMove by rememberUpdatedState(onMove)
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    // After a swap the grid has not laid out yet; until the dragged card shows up at its new slot, no further swap is looked for.
    var awaitedOffset by remember { mutableStateOf<IntOffset?>(null) }

    fun findSwap() {
        val visible = state.layoutInfo.visibleItemsInfo
        val dragged = visible.firstOrNull { it.key == draggedId } ?: return
        if (awaitedOffset != null && dragged.offset != awaitedOffset) return
        awaitedOffset = null
        val centre = Offset(dragged.offset.x + dragged.size.width / 2f, dragged.offset.y + dragged.size.height / 2f) + dragOffset
        val target = visible.firstOrNull { info ->
            info.key is Long && info.key != draggedId &&
                centre.x >= info.offset.x && centre.x < info.offset.x + info.size.width &&
                centre.y >= info.offset.y && centre.y < info.offset.y + info.size.height
        } ?: return
        val from = currentAlbums.indexOfFirst { it.id == draggedId }
        val to = currentAlbums.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0) return
        currentOnMove(from, to)
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        // The card takes the target's slot, so the offset is rebased onto that slot to stay under the finger.
        dragOffset += Offset((dragged.offset.x - target.offset.x).toFloat(), (dragged.offset.y - target.offset.y).toFloat())
        awaitedOffset = target.offset
    }

    val isList = Settings.albumColumns == 1
    LazyVerticalGrid(
        columns = GridCells.Fixed(Settings.albumColumns),
        state = state,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(if (isList) 12.dp else 20.dp),
        modifier = Modifier.fillMaxSize().pinchAlbumColumns(haptic),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
            BasicText("Albums", style = Type.title, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        }
        items(albums, key = { it.id }, contentType = { "album" }) { album ->
            val isDragged = album.id == draggedId
            val modifier = if (!isRearranging) {
                Modifier
            } else {
                // The chain keeps the same shape whether dragged or not: swapping an element out recreates the pointer input below it and cancels the drag that just started.
                Modifier
                    .animateItem(placementSpec = if (isDragged) null else spring<IntOffset>())
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        if (isDragged) {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            scaleX = 1.05f
                            scaleY = 1.05f
                        }
                    }
                    // On the card itself, so it claims the drag before the grid can scroll with it.
                    .pointerInput(album.id) {
                        detectDragGestures(
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                draggedId = album.id
                                dragOffset = Offset.Zero
                                awaitedOffset = null
                            },
                            onDragEnd = { draggedId = null; dragOffset = Offset.Zero },
                            onDragCancel = { draggedId = null; dragOffset = Offset.Zero },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount
                                findSwap()
                            },
                        )
                    }
            }
            if (isList) {
                AlbumRow(
                    album,
                    onClick = { if (!isRearranging) onOpen(album) },
                    onLongClick = { if (!isRearranging) onLongPress(album) },
                    modifier = modifier,
                )
            } else {
                AlbumCard(
                    album,
                    onClick = { if (!isRearranging) onOpen(album) },
                    onLongClick = { if (!isRearranging) onLongPress(album) },
                    modifier = modifier,
                )
            }
        }
        item(contentType = "new-album") { if (isList) AddRow("New album", onClick = onNewAlbum) else AddCard("New album", onClick = onNewAlbum) }
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "footer") { footer() }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(album.cover.uri) {
        ImageRequest.Builder(context).data(Thumbnail(album.cover.uri, COVER_PIXELS)).size(COVER_PIXELS).build()
    }
    Column(modifier.pressable(onClick = onClick, pressedScale = 0.96f, onLongClick = onLongClick)) {
        AsyncImage(
            model = request,
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(Shapes.cover)
                .background(Palette.sunken),
        )
        // Narrow cards shrink the name until it fits, down to a size that still reads; only past that is it cut.
        BasicText(
            album.name,
            style = Type.cardTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = Type.cardTitle.fontSize, stepSize = 0.5.sp),
            modifier = Modifier.padding(start = 4.dp, top = 10.dp),
        )
        BasicText(
            album.items.size.toString(),
            style = Type.value,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = Type.value.fontSize, stepSize = 0.5.sp),
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )
    }
}

// One album per row reads as a list: the cover small at the start, the name large beside it.
@Composable
private fun AlbumRow(album: Album, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(album.cover.uri) {
        ImageRequest.Builder(context).data(Thumbnail(album.cover.uri, COVER_PIXELS)).size(COVER_PIXELS).build()
    }
    Row(modifier.fillMaxWidth().pressable(onClick = onClick, pressedScale = 0.98f, onLongClick = onLongClick), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = request,
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(LIST_COVER).clip(Shapes.cover).background(Palette.sunken),
        )
        Column(Modifier.padding(start = 18.dp).weight(1f)) {
            BasicText(album.name, style = Type.cardTitle.copy(fontSize = 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(album.items.size.toString(), style = Type.value.copy(fontSize = 15.sp), modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(onClick = onClick, pressedScale = 0.98f), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(LIST_COVER).clip(Shapes.cover).background(Palette.surface), contentAlignment = Alignment.Center) {
            BasicText("+", style = Type.title.copy(color = LocalAccent.current))
        }
        BasicText(label, style = Type.cardTitle.copy(fontSize = 20.sp, color = Palette.textMuted), modifier = Modifier.padding(start = 18.dp))
    }
}

private val LIST_COVER = 84.dp

// Two fingers change how many albums sit in a row; one finger still scrolls.
private fun Modifier.pinchAlbumColumns(haptic: HapticFeedback): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zoom = 1f
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                zoom *= event.calculateZoom()
                val before = Settings.albumColumns
                if (zoom > PINCH_STEP) {
                    Settings.updateAlbumColumns(Settings.albumColumns - 1)
                    zoom = 1f
                } else if (zoom < 1f / PINCH_STEP) {
                    Settings.updateAlbumColumns(Settings.albumColumns + 1)
                    zoom = 1f
                }
                if (Settings.albumColumns != before) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

// The header (back and the album's name) floats over the grid in the app's top layer, so it can blur what scrolls under it.
@Composable
fun AlbumScreen(album: Album, memory: GridMemory, onOpen: (Int) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues, selection: Selection) {
    // While selecting, back clears the selection first (the app root handles that), so this one stands aside.
    BackHandler(enabled = !selection.isActive, onBack = onBack)
    MediaGrid(items = album.items, memory = memory, onOpen = onOpen, contentPadding = contentPadding, selection = selection)
}
