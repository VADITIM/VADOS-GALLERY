package com.vaditim.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.media.LocationGroup
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable

// A tile at the foot of Albums (Private, Locations, Trash), as big as an album cover: its icon in the middle, its name in its place's colour below.
@Composable
fun FolderEntry(title: String, color: Color, icon: @Composable (Color) -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier, count: Int? = null) {
    Column(modifier.pressable(onClick = onClick, pressedScale = 0.96f)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(Shapes.cover).background(Palette.surface), contentAlignment = Alignment.Center) {
            icon(color)
        }
        Row(Modifier.padding(start = 4.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, style = Type.cardTitle.copy(color = color), maxLines = 1)
            if (count != null && count > 0) BasicText(count.toString(), style = Type.value)
        }
    }
}

// The three tiles at the foot of Albums, a row of three whatever the albums' own columns are.
@Composable
fun FolderEntries(onPrivate: () -> Unit, onLocations: () -> Unit, onTrash: () -> Unit, trashCount: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(COVER_GAP)) {
        FolderEntry("Private", Palette.privateRed, icon = { LockIcon(it, size = FOLDER_ICON) }, onClick = onPrivate, modifier = Modifier.weight(1f))
        FolderEntry("Locations", Palette.locationBlue, icon = { PinIcon(it, size = FOLDER_ICON) }, onClick = onLocations, modifier = Modifier.weight(1f))
        FolderEntry("Trash", Palette.trashGray, icon = { TrashIcon(it, size = FOLDER_ICON) }, onClick = onTrash, modifier = Modifier.weight(1f), count = trashCount)
    }
}

// A little larger than the icons on buttons, so it holds the middle of a cover.
private val FOLDER_ICON = 34.dp

// A hairline with room around it between the albums and the rows after them, so the rows read as their own places.
@Composable
fun FolderDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).height(1.dp).background(Palette.border))
}

// Trashed photos, kept by Android for 30 days. A tap opens one like anywhere else; a long press selects.
@Composable
fun TrashScreen(items: List<MediaItem>, memory: GridMemory, onOpen: (Int) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues, selection: Selection) {
    BackHandler(enabled = selection.selectedIds.isEmpty(), onBack = onBack)
    MediaGrid(
        items = items,
        memory = memory,
        onOpen = onOpen,
        contentPadding = contentPadding,
        selection = selection,
        emptyCaption = "Trash is empty.",
        // Days until Android removes it for good.
        badge = { item -> if (item.expiresMillis > 0) "${((item.expiresMillis - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0) + 1}d" else null },
    )
}

// One card per city, the cities with the most photos first.
@Composable
fun LocationsScreen(groups: List<LocationGroup>, onOpen: (LocationGroup) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues, state: LazyGridState) {
    BackHandler(onBack = onBack)
    // Each place's name takes Locations' colour, as the albums inside Private take Private's.
    CompositionLocalProvider(LocalAccentedCoverNames provides true) { CoverGrid(state, contentPadding) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
            BasicText("Locations", style = Type.title, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        }
        items(groups, key = { it.key }, contentType = { "location" }) { group ->
            CoverCard(group.city, group.cover, group.items.size, onClick = { onOpen(group) }, modifier = Modifier.entrance())
        }
    } }
}

@Composable
fun LocationScreen(items: List<MediaItem>, memory: GridMemory, onOpen: (Int) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues, selection: Selection) {
    // While selecting, back clears the selection first (the app root handles that), so this one stands aside.
    BackHandler(enabled = !selection.isActive, onBack = onBack)
    MediaGrid(items = items, memory = memory, onOpen = onOpen, contentPadding = contentPadding, selection = selection)
}
