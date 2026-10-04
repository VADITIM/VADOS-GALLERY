package com.vaditim.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.TransformOrigin
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
    // Time through opening or closing, 0 to 1, run evenly; each album turns it into its own eased, staggered progress.
    val spread = remember { Animatable(1f) }
    var isOpening by remember { mutableStateOf(true) }
    // Where the group's top card is on screen: the opened albums leave from it and return to it.
    var origin by remember { mutableStateOf(Offset.Zero) }
    val openGroup = entries.firstOrNull { it is AlbumEntry.Stack && it.name == openStack } as AlbumEntry.Stack?
    val shownStack = openGroup?.name
    val expand: (String) -> Unit = { name ->
        scope.launch {
            openStack = name
            isOpening = true
            spread.snapTo(0f)
            spread.animateTo(1f, tween(Motion.STACK_MS, easing = LinearEasing))
        }
    }
    val collapse: () -> Unit = {
        scope.launch {
            isOpening = false
            spread.animateTo(0f, tween(Motion.STACK_MS, easing = LinearEasing))
            openStack = null
        }
    }
    // While rearranging, back ends rearranging (the app root handles that) rather than closing the group.
    BackHandler(enabled = shownStack != null && !isRearranging, onBack = collapse)

    // An open group rearranges its own albums; with every group closed, the albums and groups are what move.
    val isArrangingGroup = isRearranging && openGroup != null
    val reorderKeys = if (openGroup != null && isArrangingGroup) openGroup.albums.map { it.id } else entries.map { it.key }
    val reorder = rememberReorder(state, reorderKeys) { from, to ->
        val inGroup = openGroup?.takeIf { isArrangingGroup }?.albums?.toMutableList()?.apply { add(to, removeAt(from)) }
        val moved = if (inGroup != null) entries else entries.toMutableList().apply { add(to, removeAt(from)) }
        onArrange(
            moved.flatMap { entry ->
                when (entry) {
                    is AlbumEntry.Single -> listOf(entry.album.relativePath)
                    is AlbumEntry.Stack -> (if (inGroup != null && entry.name == openGroup?.name) inGroup else entry.albums).map { it.relativePath }
                }
            },
        )
    }
    // Cards that cannot move right now still glide, at the group's pace, when an opening group pushes the rest along.
    @Composable
    fun LazyGridItemScope.placement(key: Any, isMovable: Boolean, still: Modifier = Modifier): Modifier =
        if (isMovable) reorderable(reorder, key, true) else Modifier.animateItem(placementSpec = tween(Motion.STACK_MS, easing = Motion.powerThreeInOut)).then(still)

    // One album's progress out of the stack: the deeper cards leave a little later and come back a little sooner, and opening overshoots its place slightly.
    fun progressOf(depth: Int, count: Int): Float {
        val start = if (count <= 1) 0f else (depth - 1).toFloat() / (count - 1) * STAGGER_SPAN
        val local = ((spread.value - start) / (1f - STAGGER_SPAN)).coerceIn(0f, 1f)
        return if (isOpening) Motion.backOut.transform(local) else Motion.powerThreeInOut.transform(local)
    }

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
                    val isMovable = isRearranging && !isArrangingGroup
                    SpanCell(span, placement(cell.key, isMovable)) {
                        CoverCard(
                            album.name,
                            album.cover,
                            album.items.size,
                            onClick = { if (isPicking) onToggle(listOf(album)) else if (!isRearranging) onOpen(album) },
                            onLongClick = { if (isPicking) onToggle(listOf(album)) else if (!isRearranging) onLongPress(album) },
                            modifier = Modifier.jiggle(reorder, cell.key, isMovable),
                            isSelected = album.relativePath in selectedPaths,
                        )
                    }
                }
                is AlbumCell.Stack -> item(key = cell.key, span = { GridItemSpan(span) }, contentType = "stack") {
                    val stack = cell.stack
                    val isMovable = isRearranging && !isArrangingGroup
                    SpanCell(span, placement(cell.key, isMovable)) {
                        StackCard(
                            stack.name,
                            stack.albums.map { it.cover },
                            stack.albums.sumOf { it.items.size },
                            onClick = { if (isPicking) onToggle(stack.albums) else expand(stack.name) },
                            onLongClick = { if (isPicking) onToggle(stack.albums) else if (!isRearranging) onStackLongPress(stack.name) },
                            modifier = Modifier.jiggle(reorder, cell.key, isMovable),
                            isSelected = stack.albums.all { it.relativePath in selectedPaths },
                        )
                    }
                }
                // The top card keeps the group's place and stays above the albums sliding out from under it.
                is AlbumCell.Top -> item(key = cell.key, span = { GridItemSpan(span) }, contentType = "album") {
                    val album = cell.album
                    SpanCell(span, placement(cell.key, isArrangingGroup, Modifier.zIndex(1f))) {
                        CoverCard(
                            album.name,
                            album.cover,
                            album.items.size,
                            onClick = { if (isPicking) onToggle(listOf(album)) else if (!isRearranging) onOpen(album) },
                            onLongClick = { if (isPicking) onToggle(listOf(album)) else if (!isRearranging) onLongPress(album) },
                            modifier = Modifier.onGloballyPositioned { origin = it.positionInRoot() }.jiggle(reorder, cell.key, isArrangingGroup),
                            isSelected = album.relativePath in selectedPaths,
                        )
                    }
                }
                is AlbumCell.Member -> item(key = cell.key, span = { GridItemSpan(span) }, contentType = "album") {
                    val album = cell.album
                    val depth = cell.depth
                    var own by remember { mutableStateOf<Offset?>(null) }
                    SpanCell(span, placement(cell.key, isArrangingGroup, Modifier.zIndex(-depth.toFloat()))) {
                        CoverCard(
                            album.name,
                            album.cover,
                            album.items.size,
                            onClick = { if (isPicking) onToggle(listOf(album)) else if (!isRearranging) onOpen(album) },
                            onLongClick = { if (isPicking) onToggle(listOf(album)) else if (!isRearranging) onLongPress(album) },
                            isSelected = album.relativePath in selectedPaths,
                            // The name waits until the card is well clear of the stack, so two names never print over each other.
                            labelAlpha = { ((progressOf(depth, cell.count) - 0.5f) * 2f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .onGloballyPositioned { own = it.positionInRoot() }
                                .graphicsLayer {
                                    val at = own
                                    if (at == null) {
                                        alpha = 0f
                                    } else {
                                        val p = progressOf(depth, cell.count)
                                        val rest = 1f - p
                                        // Turned and scaled about the middle of its picture, the way the card lay in the stack.
                                        transformOrigin = if (Settings.albumColumns == 1) {
                                            TransformOrigin(LIST_COVER.toPx() / 2f / size.width, 0.5f)
                                        } else {
                                            TransformOrigin(0.5f, size.width / 2f / size.height)
                                        }
                                        translationX = (origin.x - at.x + stackShift(depth).toPx()) * rest
                                        translationY = (origin.y - at.y) * rest
                                        rotationZ = stackTilt(depth) * rest
                                        val scale = stackScale(depth) + (1f - stackScale(depth)) * p
                                        scaleX = scale
                                        scaleY = scale
                                        // Lying deeper it is darker, as in the stack; the ground behind is near black, so fading it darkens it.
                                        alpha = stackShade(depth) + (1f - stackShade(depth)) * p.coerceIn(0f, 1f)
                                    }
                                }
                                .jiggle(reorder, cell.key, isArrangingGroup),
                        )
                    }
                }
                is AlbumCell.Collapse -> item(key = cell.key, span = { GridItemSpan(span) }, contentType = "collapse") {
                    SpanCell(span, Modifier.animateItem(placementSpec = tween(Motion.STACK_MS, easing = Motion.powerThreeInOut))) {
                        CollapseCard(
                            cell.name,
                            onClick = collapse,
                            modifier = Modifier.graphicsLayer {
                                // Arrives last and leaves first.
                                val shown = ((spread.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
                                alpha = shown
                                scaleX = 0.85f + 0.15f * shown
                                scaleY = scaleX
                            },
                        )
                    }
                }
            }
        }
        item(key = "new-album", contentType = "new-album") { Box(Modifier.animateItem(placementSpec = tween(Motion.STACK_MS, easing = Motion.powerThreeInOut))) { AddCard("New album", onClick = onNewAlbum) } }
        item(key = "footer", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") { Box(Modifier.animateItem(placementSpec = tween(Motion.STACK_MS, easing = Motion.powerThreeInOut))) { footer() } }
    }
}

// How much of the opening the albums' departures are spread over; each album then takes the rest to arrive.
private const val STAGGER_SPAN = 0.35f

// One grid item of the albums screen; an opened group is its top card, the albums under it and the card that folds them back.
private sealed interface AlbumCell {
    val key: Any

    data class Single(val album: Album) : AlbumCell {
        override val key: Any get() = album.id
    }

    data class Stack(val stack: AlbumEntry.Stack) : AlbumCell {
        override val key: Any get() = stack.key
    }

    // Keyed by the album, not the group, so the open group's albums can be rearranged among themselves.
    data class Top(val stack: AlbumEntry.Stack, val album: Album) : AlbumCell {
        override val key: Any get() = album.id
    }

    data class Member(val album: Album, val depth: Int, val count: Int) : AlbumCell {
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
                entry.albums.drop(1).mapIndexed { index, album -> AlbumCell.Member(album, index + 1, entry.albums.size - 1) } +
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
