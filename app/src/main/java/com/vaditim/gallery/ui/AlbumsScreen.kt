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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

// Folders only. No "Recent" and no "Favorites" album: both are sections already, and an album that repeats a section is the Samsung habit this app exists to drop.
@Composable
fun AlbumsScreen(albums: List<Album>, state: LazyGridState, onOpen: (Album) -> Unit, contentPadding: PaddingValues) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = state,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
            Column(Modifier.padding(start = 4.dp, bottom = 2.dp)) {
                BasicText("Albums", style = Type.title)
                MicroLabel("${albums.size} folders", Modifier.padding(top = 6.dp))
            }
        }
        items(albums, key = { it.id }, contentType = { "album" }) { album -> AlbumCard(album, onClick = { onOpen(album) }) }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    val context = LocalContext.current
    val request = remember(album.cover.uri) {
        ImageRequest.Builder(context).data(Thumbnail(album.cover.uri, COVER_PIXELS)).size(COVER_PIXELS).build()
    }
    Column(Modifier.pressable(onClick = onClick, pressedScale = 0.96f)) {
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
        BasicText(album.name, style = Type.cardTitle, maxLines = 1, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
        BasicText(album.items.size.toString(), style = Type.value, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
    }
}

// The header (back and the album's name) floats over the grid in the app's top layer, so it can blur what scrolls under it.
@Composable
fun AlbumScreen(album: Album, memory: GridMemory, onOpen: (Int) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues) {
    BackHandler(onBack = onBack)
    MediaGrid(items = album.items, memory = memory, onOpen = onOpen, contentPadding = contentPadding)
}
