package com.vaditim.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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

// A row at the foot of Albums (Locations, Trash), shaped like the Private row below them.
@Composable
fun FolderEntry(title: String, icon: @Composable (Color) -> Unit, onClick: () -> Unit, count: Int? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, pressedScale = 0.97f)
            .clip(Shapes.panel)
            .background(Palette.surface)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(Palette.textMuted)
        BasicText(title, style = Type.cardTitle, modifier = Modifier.weight(1f))
        if (count != null && count > 0) BasicText(count.toString(), style = Type.value)
    }
}

// Trashed photos, kept by Android for 30 days. Everything here is picked rather than opened, so a tap selects.
@Composable
fun TrashScreen(items: List<MediaItem>, memory: GridMemory, onBack: () -> Unit, contentPadding: PaddingValues, selection: Selection) {
    BackHandler(enabled = selection.selectedIds.isEmpty(), onBack = onBack)
    MediaGrid(items = items, memory = memory, onOpen = {}, contentPadding = contentPadding, selection = selection, emptyCaption = "Trash is empty.")
}

// One card per city, the cities with the most photos first.
@Composable
fun LocationsScreen(groups: List<LocationGroup>, onOpen: (LocationGroup) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues, state: LazyGridState) {
    BackHandler(onBack = onBack)
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
            BasicText("Locations", style = Type.title, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        }
        items(groups, key = { it.key }, contentType = { "location" }) { group ->
            CoverCard(group.city, group.cover, group.items.size, onClick = { onOpen(group) })
        }
    }
}

@Composable
fun LocationScreen(items: List<MediaItem>, memory: GridMemory, onOpen: (Int) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues, selection: Selection) {
    // While selecting, back clears the selection first (the app root handles that), so this one stands aside.
    BackHandler(enabled = !selection.isActive, onBack = onBack)
    MediaGrid(items = items, memory = memory, onOpen = onOpen, contentPadding = contentPadding, selection = selection)
}
