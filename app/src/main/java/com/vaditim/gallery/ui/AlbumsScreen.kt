package com.vaditim.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.vaditim.gallery.Settings
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Type
import kotlinx.coroutines.launch

// What one place in the albums grid holds: an album, or (with Grouped albums on) a group of them lying as a stack.
private sealed interface AlbumEntry {
    val key: Any

    data class Single(val album: Album) : AlbumEntry {
        override val key: Any get() = album.id
    }

    data class Stack(val name: String, val albums: List<Album>) : AlbumEntry {
        override val key: Any get() = "stack:$name"
    }
}

// A group sits where its first album would, so the arranged order still decides where everything is.
private fun entriesOf(albums: List<Album>): List<AlbumEntry> {
    if (!Settings.groupedAlbums) return albums.map { AlbumEntry.Single(it) }
    val stackOf = Settings.albumStacks.flatMap { stack -> stack.paths.map { it to stack.name } }.toMap()
    val members = albums.groupBy { stackOf[it.relativePath] }
    val placed = HashSet<String>()
    return albums.mapNotNull { album ->
        val name = stackOf[album.relativePath] ?: return@mapNotNull AlbumEntry.Single(album)
        if (placed.add(name)) AlbumEntry.Stack(name, members.getValue(name)) else null
    }
}

// Folders only. No "Recent" and no "Favorites" album: both are sections already, and an album that repeats a section is the Samsung habit this app exists to drop.
@Composable
fun AlbumsScreen(
    albums: List<Album>,
    state: LazyGridState,
    onOpen: (Album) -> Unit,
    onLongPress: (Album) -> Unit,
    onStackLongPress: (String) -> Unit,
    onNewAlbum: () -> Unit,
    contentPadding: PaddingValues,
    footer: @Composable () -> Unit,
    isRearranging: Boolean = false,
    onArrange: (paths: List<String>) -> Unit = {},
    selectedPaths: Set<String> = emptySet(),
    onToggle: (List<Album>) -> Unit = {},
) {
    val isPicking = selectedPaths.isNotEmpty()
    val entries = remember(albums, Settings.groupedAlbums, Settings.albumStacks) { entriesOf(albums) }
    val scope = rememberCoroutineScope()
    var openStack by remember { mutableStateOf<String?>(null) }
    // 0 is the group lying as a stack, 1 is its albums laid out in the grid.
    val spread = remember { Animatable(1f) }
    // Where the group's top card is on screen: the opened albums leave from it and return to it.
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shownStack = openStack.takeIf { name -> !isRearranging && entries.any { it is AlbumEntry.Stack && it.name == name } }
    val expand: (String) -> Unit = { name ->
        scope.launch {
            openStack = name
            spread.snapTo(0f)
            spread.animateTo(1f, tween(Motion.STACK_MS, easing = Motion.powerThreeInOut))
        }
    }
    val collapse: () -> Unit = {
        scope.launch {
            spread.animateTo(0f, tween(Motion.STACK_MS, easing = Motion.powerThreeInOut))
            openStack = null
        }
    }
    BackHandler(enabled = shownStack != null, onBack = collapse)

    val reorder = rememberReorder(state, entries.map { it.key }) { from, to ->
        val moved = entries.toMutableList().apply { add(to, removeAt(from)) }
        onArrange(
            moved.flatMap { entry ->
                when (entry) {
                    is AlbumEntry.Single -> listOf(entry.album.relativePath)
                    is AlbumEntry.Stack -> entry.albums.map { it.relativePath }
                }
            },
        )
    }
    // Outside rearranging, every card still glides to its new place when an opened group pushes the rest along.
    fun LazyGridItemScope.placement(key: Any): Modifier = if (isRearranging) reorderable(reorder, key, true) else Modifier.animateItem()

    // Every group starts a row of its own and ends it, so a stack never shares a row and opening it fans out to the right of it.
    val cells = remember(entries, shownStack) { cellsOf(entries, shownStack) }
    val spans = remember(cells, Settings.albumColumns) { spansOf(cells, Settings.albumColumns) }

    CoverGrid(state, contentPadding) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
            BasicText("Albums", style = Type.title, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        }
        cells.forEachIndexed { index, cell ->
            val span = spans[index]
            when (cell) {
                is AlbumCell.Single -> item(key = cell.key, span = { GridItemSpan(span) }, contentType = "album") {
                    val album = cell.album
                    SpanCell(span, placement(cell.key)) {
                        CoverCard(
                            album.name,
                            album.cover,
                            album.items.size,
                            onClick = { if (isPicking) onToggle(listOf(album)) else if (!isRearranging) onOpen(album) },
                            onLongClick = { if (isPicking) onToggle(listOf(album)) else if (!isRearranging) onLongPress(album) },
                            isSelected = album.relativePath in selectedPaths,
                        )
                    }
                }
                is AlbumCell.Stack -> item(key = cell.key, span = { GridItemSpan(span) }, contentType = "stack") {
                    val stack = cell.stack
                    SpanCell(span, placement(cell.key)) {
                        StackCard(
                            stack.name,
                            stack.albums.map { it.cover },
                            stack.albums.sumOf { it.items.size },
                            onClick = { if (isPicking) onToggle(stack.albums) else if (!isRearranging) expand(stack.name) },
                            onLongClick = { if (isPicking) onToggle(stack.albums) else if (!isRearranging) onStackLongPress(stack.name) },
                            isSelected = stack.albums.all { it.relativePath in selectedPaths },
                        )
                    }
                }
                // The top card keeps the group's place and stays above the albums sliding out from under it.
                is AlbumCell.Top -> item(key = cell.key, span = { GridItemSpan(span) }, contentType = "album") {
                    val album = cell.album
                    SpanCell(span, Modifier.animateItem().zIndex(1f)) {
                        CoverCard(
                            album.name,
                            album.cover,
                            album.items.size,
                            onClick = { if (isPicking) onToggle(listOf(album)) else onOpen(album) },
                            onLongClick = { if (isPicking) onToggle(listOf(album)) else onLongPress(album) },
                            modifier = Modifier.onGloballyPositioned { origin = it.positionInRoot() },
                            isSelected = album.relativePath in selectedPaths,
                        )
                    }
                }
                is AlbumCell.Member -> item(key = cell.key, span = { GridItemSpan(span) }, contentType = "album") {
                    val album = cell.album
                    val depth = cell.depth
                    var own by remember { mutableStateOf<Offset?>(null) }
                    SpanCell(span, Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null).zIndex(-depth.toFloat())) {
                        CoverCard(
                            album.name,
                            album.cover,
                            album.items.size,
                            onClick = { if (isPicking) onToggle(listOf(album)) else onOpen(album) },
                            onLongClick = { if (isPicking) onToggle(listOf(album)) else onLongPress(album) },
                            isSelected = album.relativePath in selectedPaths,
                            modifier = Modifier
                                .onGloballyPositioned { own = it.positionInRoot() }
                                .graphicsLayer {
                                    val at = own
                                    val p = spread.value
                                    if (at == null) {
                                        alpha = 0f
                                    } else {
                                        // Leaves from exactly where its card lay in the stack: shifted, leaning, smaller.
                                        translationX = (origin.x - at.x + stackShift(depth).toPx()) * (1f - p)
                                        translationY = (origin.y - at.y) * (1f - p)
                                        rotationZ = stackTilt(depth) * (1f - p)
                                        val scale = stackScale(depth) + (1f - stackScale(depth)) * p
                                        scaleX = scale
                                        scaleY = scale
                                        // Hidden while it still lies under the top card, so two names never print over each other.
                                        alpha = (p * 4f).coerceAtMost(1f)
                                    }
                                },
                        )
                    }
                }
                is AlbumCell.Collapse -> item(key = cell.key, span = { GridItemSpan(span) }, contentType = "collapse") {
                    SpanCell(span, Modifier.animateItem()) {
                        CollapseCard(cell.name, onClick = collapse, modifier = Modifier.graphicsLayer { alpha = spread.value })
                    }
                }
            }
        }
        item(key = "new-album", contentType = "new-album") { Box(Modifier.animateItem()) { AddCard("New album", onClick = onNewAlbum) } }
        item(key = "footer", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") { Box(Modifier.animateItem()) { footer() } }
    }
}

// One grid item of the albums screen; an opened group is its top card, the albums under it and the card that folds them back.
private sealed interface AlbumCell {
    val key: Any

    data class Single(val album: Album) : AlbumCell {
        override val key: Any get() = album.id
    }

    data class Stack(val stack: AlbumEntry.Stack) : AlbumCell {
        override val key: Any get() = stack.key
    }

    data class Top(val stack: AlbumEntry.Stack, val album: Album) : AlbumCell {
        override val key: Any get() = stack.key
    }

    data class Member(val album: Album, val depth: Int) : AlbumCell {
        override val key: Any get() = album.id
    }

    data class Collapse(val name: String) : AlbumCell {
        override val key: Any get() = "collapse:$name"
    }
}

private fun cellsOf(entries: List<AlbumEntry>, openStack: String?): List<AlbumCell> = entries.flatMap { entry ->
    when (entry) {
        is AlbumEntry.Single -> listOf(AlbumCell.Single(entry.album))
        is AlbumEntry.Stack -> if (entry.name != openStack) {
            listOf(AlbumCell.Stack(entry))
        } else {
            listOf<AlbumCell>(AlbumCell.Top(entry, entry.albums.first())) +
                entry.albums.drop(1).mapIndexed { index, album -> AlbumCell.Member(album, index + 1) } +
                AlbumCell.Collapse(entry.name)
        }
    }
}

// How many cells each item takes: a stack fills its row, and whatever comes right before a group, or closes one, stretches to the row's end.
private fun spansOf(cells: List<AlbumCell>, columns: Int): List<Int> {
    if (columns <= 1) return List(cells.size) { 1 }
    var column = 0
    return cells.mapIndexed { index, cell ->
        val next = cells.getOrNull(index + 1)
        val endsRow = cell is AlbumCell.Stack || cell is AlbumCell.Collapse || next is AlbumCell.Stack || next is AlbumCell.Top
        val span = if (endsRow) columns - column else 1
        column = (column + span) % columns
        span
    }
}

// The header (back and the album's name) floats over the grid in the app's top layer, so it can blur what scrolls under it.
@Composable
fun AlbumScreen(album: Album, memory: GridMemory, onOpen: (Int) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues, selection: Selection) {
    // While selecting, back clears the selection first (the app root handles that), so this one stands aside.
    BackHandler(enabled = !selection.isActive, onBack = onBack)
    MediaGrid(items = album.items, memory = memory, onOpen = onOpen, contentPadding = contentPadding, selection = selection)
}
