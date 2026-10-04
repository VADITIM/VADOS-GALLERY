package com.vaditim.gallery.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import android.view.TextureView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.vault.PrivateGroup
import kotlin.random.Random

private const val COVER_PIXELS = 512

// The entry to Private, at the foot of the albums list where Apple keeps Hidden: present, never in the way.
@Composable
fun PrivateEntry(onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, pressedScale = 0.97f)
            .clip(Shapes.panel)
            .background(Palette.surface)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        BasicText("Private", style = Type.cardTitle)
    }
}

@Composable
fun PrivateGroupsScreen(
    groups: List<PrivateGroup>,
    favorites: List<MediaItem>,
    onOpen: (PrivateGroup) -> Unit,
    onLongPress: (PrivateGroup) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSelection: (Int) -> Unit,
    onNewGroup: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    state: LazyGridState,
    isViewerOpen: Boolean = false,
    isRearranging: Boolean = false,
    onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
    selectedNames: Set<String> = emptySet(),
    onToggle: (PrivateGroup) -> Unit = {},
) {
    val isPicking = selectedNames.isNotEmpty()
    // While picking, back clears the pick first (the app root handles that), so this one stands aside.
    BackHandler(enabled = !isPicking, onBack = onBack)
    val reorder = rememberReorder(state, groups.map { it.name }, onMove)
    // Drawn once per entry into Private and kept while scrolling, so the pick does not reshuffle when the card scrolls out of view.
    val selectionSeed = remember { Random.nextInt(Int.MAX_VALUE) }
    CoverGrid(state, contentPadding) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
            BasicText("Private", style = Type.title, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        }
        if (favorites.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "selection") {
                val todaysIndex = selectionSeed % favorites.size
                TodaysSelection(favorites[todaysIndex], isCovered = isViewerOpen, onClick = { onOpenSelection(todaysIndex) })
            }
        }
        items(groups, key = { it.name }, contentType = { "group" }) { group ->
            CoverCard(
                group.name,
                group.cover,
                group.items.size,
                onClick = { if (isPicking) onToggle(group) else if (!isRearranging) onOpen(group) },
                onLongClick = { if (isPicking) onToggle(group) else if (!isRearranging) onLongPress(group) },
                modifier = reorderable(reorder, group.name, isRearranging).jiggle(reorder, group.name, isRearranging),
                isSelected = group.name in selectedNames,
            )
        }
        item(contentType = "new-group") { AddCard("New group", onClick = onNewGroup) }
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "favorites") {
            FavoritesFolder(favorites.lastOrNull(), favorites.size, onClick = onOpenFavorites)
        }
    }
}

@Composable
private fun TodaysSelection(item: MediaItem, isCovered: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val request = remember(item.uri) { ImageRequest.Builder(context).data(item.uri).size(1080).build() }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 5f)
            .pressable(onClick = onClick, pressedScale = 0.97f)
            .clip(Shapes.sheet)
            .background(Palette.sunken),
    ) {
        AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (item.isVideo) LoopingPreview(item, isCovered)
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                // A solid pane, not glass: this card scrolls inside the blurred content itself, and an effect inside its own source would blur itself.
                .clip(Shapes.panel)
                .background(Palette.panel)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            MicroLabel("Today's selection")
            BasicText("for you 😏", style = Type.cardTitle, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// A picked video plays on its own, silent and looping, cropped to fill the card like the still behind it (which shows until the first frame arrives). The surface is attached from the start: without one the player never reports the video's size. It pauses while the viewer is over it.
@Composable
private fun LoopingPreview(item: MediaItem, isCovered: Boolean) {
    val context = LocalContext.current
    var ratio by remember(item.id) { mutableFloatStateOf(0f) }
    val player = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, isCovered) { player.playWhenReady = !isCovered }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val shown = if (ratio > 0f) ratio else maxWidth / maxHeight
        val isWider = shown > maxWidth / maxHeight
        val width = if (isWider) maxHeight * shown else maxWidth
        val height = if (isWider) maxHeight else maxWidth / shown
        AndroidView(
            factory = { TextureView(it) },
            update = { player.setVideoTextureView(it) },
            onRelease = { player.clearVideoTextureView(it) },
            // Invisible until the size is known, so the first frames are never shown stretched.
            modifier = Modifier.requiredSize(width, height).graphicsLayer { alpha = if (ratio > 0f) 1f else 0f },
        )
    }
}

// The private favourites, always the first thing in Private: a wide row, so it reads as a folder apart from the groups below it.
@Composable
private fun FavoritesFolder(cover: MediaItem?, count: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val request = remember(cover?.uri) { cover?.let { ImageRequest.Builder(context).data(it.uri).size(COVER_PIXELS).build() } }
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, pressedScale = 0.97f)
            .clip(Shapes.panel)
            .background(Palette.panel)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(Shapes.tile).background(Palette.sunken)) {
            if (request != null) {
                AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.padding(start = 14.dp)) {
            BasicText("Favorites", style = Type.cardTitle.copy(color = LocalAccent.current))
            BasicText(count.toString(), style = Type.value, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// A private group, or the private favourites: the same grid either way.
@Composable
fun PrivateItemsScreen(items: List<MediaItem>, memory: GridMemory, onOpen: (Int) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues, selection: Selection) {
    // While selecting, back clears the selection first (the app root handles that), so this one stands aside.
    BackHandler(enabled = !selection.isActive, onBack = onBack)
    MediaGrid(items = items, memory = memory, onOpen = onOpen, contentPadding = contentPadding, selection = selection)
}
