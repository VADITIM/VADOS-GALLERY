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
    val reorder = rememberReorder(state, albums.map { it.id }, onMove)
    CoverGrid(state, contentPadding) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
            BasicText("Albums", style = Type.title, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        }
        items(albums, key = { it.id }, contentType = { "album" }) { album ->
            CoverCard(
                album.name,
                album.cover,
                album.items.size,
                onClick = { if (!isRearranging) onOpen(album) },
                onLongClick = { if (!isRearranging) onLongPress(album) },
                modifier = reorderable(reorder, album.id, isRearranging),
            )
        }
        item(contentType = "new-album") { AddCard("New album", onClick = onNewAlbum) }
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "footer") { footer() }
    }
}

// The header (back and the album's name) floats over the grid in the app's top layer, so it can blur what scrolls under it.
@Composable
fun AlbumScreen(album: Album, memory: GridMemory, onOpen: (Int) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues, selection: Selection) {
    // While selecting, back clears the selection first (the app root handles that), so this one stands aside.
    BackHandler(enabled = !selection.isActive, onBack = onBack)
    MediaGrid(items = album.items, memory = memory, onOpen = onOpen, contentPadding = contentPadding, selection = selection)
}
