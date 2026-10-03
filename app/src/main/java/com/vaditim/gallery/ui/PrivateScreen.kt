package com.vaditim.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
fun PrivateEntry(isUnlocked: Boolean, groupCount: Int, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, pressedScale = 0.97f)
            .clip(Shapes.panel)
            .background(Palette.surface)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        BasicText("Private", style = Type.cardTitle)
        MicroLabel(if (isUnlocked) "$groupCount groups · unlocked" else "Locked · fingerprint", Modifier.padding(top = 6.dp))
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
) {
    BackHandler(onBack = onBack)
    val state = rememberLazyGridState()
    // Drawn once per entry into Private and kept while scrolling, so the pick does not reshuffle when the card scrolls out of view.
    val selectionSeed = remember { Random.nextInt(Int.MAX_VALUE) }
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
                BasicText("Private", style = Type.title)
                MicroLabel("${groups.size} groups", Modifier.padding(top = 6.dp))
            }
        }
        if (favorites.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "selection") {
                val todaysIndex = selectionSeed % favorites.size
                TodaysSelection(favorites[todaysIndex], onClick = { onOpenSelection(todaysIndex) })
            }
            item(contentType = "group") { CoverCard("Private Favorites", favorites.lastOrNull(), favorites.size, onClick = onOpenFavorites) }
        }
        items(groups, key = { it.directory.absolutePath }, contentType = { "group" }) { group ->
            CoverCard(group.name, group.cover, group.items.size, onClick = { onOpen(group) }, onLongClick = { onLongPress(group) })
        }
        item(contentType = "new-group") { AddCard("New group", onClick = onNewGroup) }
    }
}

@Composable
private fun TodaysSelection(item: MediaItem, onClick: () -> Unit) {
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
        Column(
            Modifier
                .align(Alignment.BottomStart)
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

@Composable
private fun CoverCard(name: String, cover: MediaItem?, count: Int, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val request = remember(cover?.uri) { cover?.let { ImageRequest.Builder(context).data(it.uri).size(COVER_PIXELS).build() } }
    Column(Modifier.pressable(onClick = onClick, pressedScale = 0.96f, onLongClick = onLongClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(Shapes.cover).background(Palette.sunken)) {
            if (request != null) {
                AsyncImage(model = request, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        BasicText(name, style = Type.cardTitle, maxLines = 1, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
        BasicText(count.toString(), style = Type.value, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
    }
}

// The "make a new one" card at the end of a grid of covers: albums and private groups both end with one.
@Composable
fun AddCard(label: String, onClick: () -> Unit) {
    Column(Modifier.pressable(onClick = onClick, pressedScale = 0.96f)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(Shapes.cover).background(Palette.surface),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("+", style = Type.title.copy(color = LocalAccent.current))
        }
        BasicText(label, style = Type.cardTitle.copy(color = Palette.textMuted), modifier = Modifier.padding(start = 4.dp, top = 10.dp))
    }
}

// A private group, or the private favourites: the same grid either way.
@Composable
fun PrivateItemsScreen(items: List<MediaItem>, memory: GridMemory, onOpen: (Int) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues, selection: Selection) {
    // While selecting, back clears the selection first (the app root handles that), so this one stands aside.
    BackHandler(enabled = !selection.isActive, onBack = onBack)
    MediaGrid(items = items, memory = memory, onOpen = onOpen, contentPadding = contentPadding, selection = selection, emptyCaption = "Move photos here from the ••• menu.")
}
