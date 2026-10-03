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
import com.vaditim.gallery.vault.PrivateGroup

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
fun PrivateGroupsScreen(groups: List<PrivateGroup>, onOpen: (PrivateGroup) -> Unit, onNewGroup: () -> Unit, onBack: () -> Unit, contentPadding: PaddingValues) {
    BackHandler(onBack = onBack)
    val state = rememberLazyGridState()
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
        items(groups, key = { it.directory.absolutePath }, contentType = { "group" }) { group -> GroupCard(group, onClick = { onOpen(group) }) }
        item(contentType = "new-group") { NewGroupCard(onClick = onNewGroup) }
    }
}

@Composable
private fun GroupCard(group: PrivateGroup, onClick: () -> Unit) {
    val context = LocalContext.current
    val cover = group.cover
    val request = remember(cover?.uri) { cover?.let { ImageRequest.Builder(context).data(it.uri).size(COVER_PIXELS).build() } }
    Column(Modifier.pressable(onClick = onClick, pressedScale = 0.96f)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(Shapes.cover).background(Palette.sunken)) {
            if (request != null) {
                AsyncImage(model = request, contentDescription = group.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        BasicText(group.name, style = Type.cardTitle, maxLines = 1, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
        BasicText(group.items.size.toString(), style = Type.value, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
    }
}

@Composable
private fun NewGroupCard(onClick: () -> Unit) {
    Column(Modifier.pressable(onClick = onClick, pressedScale = 0.96f)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(Shapes.cover).background(Palette.surface),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("+", style = Type.title.copy(color = LocalAccent.current))
        }
        BasicText("New group", style = Type.cardTitle.copy(color = Palette.textMuted), modifier = Modifier.padding(start = 4.dp, top = 10.dp))
    }
}

@Composable
fun PrivateGroupScreen(group: PrivateGroup, memory: GridMemory, onOpen: (Int) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues) {
    BackHandler(onBack = onBack)
    MediaGrid(items = group.items, memory = memory, onOpen = onOpen, contentPadding = contentPadding, emptyCaption = "Move photos here from the ••• menu.")
}
