package com.vaditim.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable

private const val COVER_PIXELS = 512

// Folders only. No "Recent" and no "Favorites" album: both are sections already, and an album that repeats a section is the Samsung habit this app exists to drop.
@Composable
fun AlbumsScreen(albums: List<Album>, onOpen: (Album) -> Unit, contentPadding: PaddingValues) {
    val state = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = state,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            MicroLabel("${albums.size} ALBUMS", Modifier.padding(bottom = 2.dp))
        }
        items(albums, key = { it.id }) { album -> AlbumCard(album, onClick = { onOpen(album) }) }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    val context = LocalContext.current
    val request = remember(album.cover.uri) { ImageRequest.Builder(context).data(album.cover.uri).size(COVER_PIXELS).build() }
    Column(Modifier.pressable(onClick = onClick, pressedScale = 0.96f)) {
        AsyncImage(
            model = request,
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(Shapes.panel)
                .background(Palette.sunken)
                .border(1.dp, Palette.border, Shapes.panel),
        )
        BasicText(album.name, style = Type.cardTitle, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
        BasicText(album.items.size.toString(), style = Type.value, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun AlbumScreen(album: Album, memory: GridMemory, onOpen: (Int) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize()) {
        MediaGrid(
            items = album.items,
            memory = memory,
            onOpen = onOpen,
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 52.dp,
                bottom = contentPadding.calculateBottomPadding(),
            ),
        )
        Box(
            Modifier
                .padding(top = contentPadding.calculateTopPadding() + 6.dp, start = 16.dp)
                .pressable(onClick = onBack),
        ) {
            BasicText("‹ ${album.name.uppercase()}", style = Type.navigation.copy(color = LocalAccent.current), maxLines = 1)
        }
    }
}
