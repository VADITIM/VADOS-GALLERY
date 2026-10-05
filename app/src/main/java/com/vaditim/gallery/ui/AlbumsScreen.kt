package com.vaditim.gallery.ui

import androidx.activity.compose.BackHandler
import kotlin.math.abs
import com.vaditim.gallery.vas.LabelReveal
import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaditim.gallery.AlbumStack
import com.vaditim.gallery.Settings
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

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
private fun entriesOf(albums: List<Album>, stacks: List<AlbumStack>): List<AlbumEntry> {
    if (stacks.isEmpty()) return albums.map { AlbumEntry.Single(it) }
    val stackOf = stacks.flatMap { stack -> stack.paths.map { it to stack.name } }.toMap()
    val members = albums.groupBy { stackOf[it.relativePath] }
    val placed = HashSet<String>()
    return albums.mapNotNull { album ->
        val name = stackOf[album.relativePath] ?: return@mapNotNull AlbumEntry.Single(album)
        if (placed.add(name)) AlbumEntry.Stack(name, members.getValue(name)) else null
    }
}

// Folders, or the albums made inside Favorites. No "Recent" and no "Favorites" album: both are sections already, and an album that repeats a section is the Samsung habit this app exists to drop.
// `stacks` are the groups shown, by album path; the folders pass theirs only while Grouped albums is on.
@Composable
fun AlbumsScreen(
    albums: List<Album>,
    title: String = "Albums",
    stacks: List<AlbumStack> = if (Settings.groupedAlbums) Settings.albumStacks else emptyList(),
    state: LazyGridState,
    onOpen: (Album) -> Unit,
    onLongPress: (Album) -> Unit,
    onStackLongPress: (String) -> Unit,
    onNewAlbum: () -> Unit,
    contentPadding: PaddingValues,
    footer: @Composable () -> Unit = {},
    isRearranging: Boolean = false,
    onArrange: (paths: List<String>) -> Unit = {},
    selectedPaths: Set<String> = emptySet(),
    onToggle: (List<Album>) -> Unit = {},
    openStacks: Set<String> = emptySet(),
    onOpenStacksChange: (Set<String>) -> Unit = {},
) {
    val isPicking = selectedPaths.isNotEmpty()
    val entries = remember(albums, stacks) { entriesOf(albums, stacks) }
    // Several groups can be open at once; opening one leaves the others as they are. The set lives above this screen so it survives opening an album and coming back.
    // While rearranging, back ends rearranging (the app root handles that) rather than closing groups.
    BackHandler(enabled = openStacks.isNotEmpty() && !isRearranging) { onOpenStacksChange(emptySet()) }

    fun arrange(moved: List<AlbumEntry>, group: String? = null, groupAlbums: List<Album> = emptyList()) = onArrange(
        moved.flatMap { entry ->
            when (entry) {
                is AlbumEntry.Single -> listOf(entry.album.relativePath)
                is AlbumEntry.Stack -> (if (entry.name == group) groupAlbums else entry.albums).map { it.relativePath }
            }
        },
    )
    // Albums and closed groups move as wholes; an open group's albums move inside it.
    val reorder = rememberReorder(state, entries.map { it.key }) { from, to -> arrange(entries.toMutableList().apply { add(to, removeAt(from)) }) }
    val glide = tween<IntOffset>(Motion.STACK_MS, easing = Motion.powerThreeInOut)

    // Every group is a row of its own, so the albums before it end their row early.
    val spans = remember(entries, Settings.coverColumns) { spansOf(entries, Settings.coverColumns) }

    CoverGrid(state, contentPadding) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
            BasicText(title, style = Type.title, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        }
        entries.forEachIndexed { index, entry ->
            val span = spans[index]
            when (entry) {
                is AlbumEntry.Single -> item(key = entry.key, span = { GridItemSpan(span) }, contentType = "album") {
                    val album = entry.album
                    SpanCell(span, (if (isRearranging) reorderable(reorder, entry.key, true) else Modifier.animateItem(placementSpec = glide)).entrance()) {
                        CoverCard(
                            album.name,
                            album.cover,
                            album.items.size,
                            onClick = { if (isPicking) onToggle(listOf(album)) else if (!isRearranging) onOpen(album) },
                            onLongClick = { if (isPicking) onToggle(listOf(album)) else if (!isRearranging) onLongPress(album) },
                            modifier = Modifier.jiggle(reorder, entry.key, isRearranging),
                            isSelected = album.relativePath in selectedPaths,
                        )
                    }
                }
                is AlbumEntry.Stack -> item(key = entry.key, span = { GridItemSpan(maxLineSpan) }, contentType = "stack") {
                    val isOpen = entry.name in openStacks
                    val isMovable = isRearranging && !isOpen
                    GroupRow(
                        stack = entry,
                        isOpen = isOpen,
                        onOpenChange = { open -> onOpenStacksChange(if (open) openStacks + entry.name else openStacks - entry.name) },
                        isPicking = isPicking,
                        selectedPaths = selectedPaths,
                        onToggle = onToggle,
                        onOpenAlbum = onOpen,
                        onLongPress = onLongPress,
                        onStackLongPress = { onStackLongPress(entry.name) },
                        isRearranging = isRearranging,
                        isMovable = isMovable,
                        onArrangeGroup = { reordered -> arrange(entries, entry.name, reordered) },
                        modifier = (if (isMovable) reorderable(reorder, entry.key, true) else Modifier.animateItem(placementSpec = glide))
                            .entrance()
                            .padding(vertical = GROUP_GAP),
                    )
                }
            }
        }
        item(key = "new-album", contentType = "new-album") { Box(Modifier.animateItem(placementSpec = glide).entrance()) { AddCard("New album", onClick = onNewAlbum) } }
        item(key = "footer", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") { Box(Modifier.animateItem(placementSpec = glide).entrance()) { footer() } }
    }
}

// Extra room above and below a group, so groups read as separate rows.
private val GROUP_GAP = 8.dp
// How much of the row's width a swipe left takes to pull an opened group all the way shut, and how far through it letting go closes it.
private const val PULL_REACH = 0.8f
private const val PULL_CLOSE = 0.3f
// Opened, a group lays its albums out this many to a row, whatever the album columns are.
private const val GROUP_COLUMNS = 3
// How much of the opening the albums' departures are spread over; each album then takes the rest to arrive.
private const val STAGGER_SPAN = 0.35f
private val GROUP_LABEL_GAP = 18.dp
private val GROUP_ROW_GAP = 20.dp
private val GROUP_HEADING_GAP = 14.dp

// The albums before a group stretch to the end of their row, so the group starts a row of its own.
private fun spansOf(entries: List<AlbumEntry>, columns: Int): List<Int> {
    if (columns <= 1) return List(entries.size) { 1 }
    var column = 0
    return entries.mapIndexed { index, entry ->
        val endsRow = entry is AlbumEntry.Stack || entries.getOrNull(index + 1) is AlbumEntry.Stack
        val span = if (endsRow) columns - column else 1
        column = (column + span) % columns
        span
    }
}

// Geometry of the last layout, for the drag inside an open group to find the slot under the finger.
private class GroupGeometry {
    var cell = 1f
    var gap = 0f
    var rowHeight = 1f
    var rowGap = 0f

    fun slot(index: Int): Offset = Offset((index % GROUP_COLUMNS) * (cell + gap), (index / GROUP_COLUMNS) * (rowHeight + rowGap))

    // A fractional slot lies between its two neighbours, so a card gliding to a new place passes through the ones between.
    fun slot(position: Float): Offset {
        val lower = floor(position).toInt().coerceAtLeast(0)
        val fraction = position - lower
        return if (fraction == 0f) slot(lower) else slot(lower) + (slot(lower + 1) - slot(lower)) * fraction
    }

    fun slotAt(point: Offset, count: Int): Int {
        val column = (point.x / (cell + gap)).toInt().coerceIn(0, GROUP_COLUMNS - 1)
        val row = (point.y / (rowHeight + rowGap)).toInt().coerceAtLeast(0)
        return (row * GROUP_COLUMNS + column).coerceIn(0, count - 1)
    }
}

// A group: its albums lie as a leaning stack with the name and count beside it, like a one-column row. Opened, the same cards lift off the stack one after another into rows of three, ending with the card that lays them back down; nothing is swapped, so opening and closing are one continuous motion.
@Composable
private fun GroupRow(
    stack: AlbumEntry.Stack,
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    isPicking: Boolean,
    selectedPaths: Set<String>,
    onToggle: (List<Album>) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onLongPress: (Album) -> Unit,
    onStackLongPress: () -> Unit,
    isRearranging: Boolean,
    isMovable: Boolean,
    onArrangeGroup: (List<Album>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val albums = stack.albums
    val count = albums.size
    // Time through opening or closing, 0 to 1, run evenly; each card turns it into its own eased, staggered progress.
    val time = remember(stack.name) { Animatable(if (isOpen) 1f else 0f) }
    // A swipe left pulls the opened group shut by the finger: it drives the same time, on the closing curve, so letting go carries on from where the cards are.
    var isPulled by remember(stack.name) { mutableStateOf(false) }
    LaunchedEffect(isOpen) {
        if (!isOpen) isPulled = false
        val target = if (isOpen) 1f else 0f
        // Started part way, by a pull, it takes only the share of the time that is left.
        time.animateTo(target, tween((Motion.STACK_MS * abs(target - time.value)).roundToInt(), easing = LinearEasing))
    }
    val isShut = !isOpen && time.value == 0f
    val isArranging = isRearranging && isOpen
    val geometry = remember { GroupGeometry() }
    val currentAlbums by rememberUpdatedState(albums)
    var heldId by remember { mutableStateOf<Long?>(null) }
    var heldOffset by remember { mutableStateOf(Offset.Zero) }
    val haptic = LocalHapticFeedback.current

    // Card `index` of `slots` (the albums, then the closing card): the deeper ones leave a little later and come back a little sooner, and opening overshoots slightly.
    fun progressOf(index: Int): Float {
        val start = index.toFloat() / count * STAGGER_SPAN
        val local = ((time.value - start) / (1f - STAGGER_SPAN)).coerceIn(0f, 1f)
        return if (isOpen && !isPulled) Motion.backOut.transform(local) else Motion.powerThreeInOut.transform(local)
    }

    val openGroup = { if (isPicking) onToggle(albums) else onOpenChange(true) }
    val slotStates = albums.mapIndexed { index, album ->
        key(album.id) { animateFloatAsState(index.toFloat(), tween(Motion.STATE_MS, easing = Motion.powerTwoOut), label = "slot") }
    }

    val context = LocalContext.current
    val currentOnOpenChange by rememberUpdatedState(onOpenChange)
    val scope = rememberCoroutineScope()
    Layout(
        modifier = modifier
            .jiggle(stack.key, isMovable, pivot = LIST_COVER / 2)
            // Each card follows the swipe back toward the stack, the last ones pulled hardest, so they land under one another; let go far enough and the group lays itself down, otherwise the cards go back.
            .then(
                if (!isOpen || isRearranging) Modifier else Modifier.pointerInput(stack.name) {
                    var pulled = 0f
                    val home = {
                        scope.launch {
                            time.animateTo(1f, tween((Motion.STATE_MS * (1f - time.value)).roundToInt(), easing = Motion.powerTwoOut))
                            isPulled = false
                        }
                        Unit
                    }
                    detectHorizontalDragGestures(
                        onDragStart = {
                            pulled = 0f
                            isPulled = true
                        },
                        onDragEnd = {
                            if (1f - time.value > PULL_CLOSE) {
                                Haptics.tick(context)
                                currentOnOpenChange(false)
                            } else {
                                home()
                            }
                        },
                        onDragCancel = { home() },
                    ) { change, amount ->
                        change.consume()
                        pulled = (pulled + amount).coerceAtMost(0f)
                        val reach = size.width * PULL_REACH
                        scope.launch { time.snapTo((1f + pulled / reach).coerceIn(0f, 1f)) }
                    }
                },
            ),
        content = {
            // The name and count take the whole rest of the row, so a tap anywhere beside the stack opens it; opened, the area is gone from under the cards.
            Column(
                if (isOpen) Modifier else Modifier.pressable(onClick = openGroup, pressedScale = 0.98f, onLongClick = { if (isPicking) onToggle(albums) else if (!isRearranging) onStackLongPress() }),
                verticalArrangement = Arrangement.Center,
            ) {
                BasicText(stack.name, style = Type.cardTitle.copy(fontSize = 20.sp, color = LocalAccent.current), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(albums.sumOf { it.items.size }.toString(), style = Type.value.copy(fontSize = 15.sp), modifier = Modifier.padding(top = 6.dp))
            }
            albums.forEachIndexed { index, album ->
                key(album.id) {
                    val isSelected = if (isShut) index == 0 && albums.all { it.relativePath in selectedPaths } else album.relativePath in selectedPaths
                    CoverCard(
                        album.name,
                        album.cover,
                        album.items.size,
                        onClick = {
                            when {
                                isShut -> openGroup()
                                isPicking -> onToggle(listOf(album))
                                !isRearranging -> onOpenAlbum(album)
                            }
                        },
                        onLongClick = {
                            when {
                                isShut -> if (isPicking) onToggle(albums) else if (!isRearranging) onStackLongPress()
                                isPicking -> onToggle(listOf(album))
                                !isRearranging -> onLongPress(album)
                            }
                        },
                        isSelected = isSelected,
                        isList = false,
                        // The name waits until the card is well clear of the stack, so names never print over each other.
                        labelAlpha = { ((progressOf(index) - 0.5f) * 2f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .then(
                                if (!isArranging) {
                                    Modifier
                                } else {
                                    Modifier.pointerInput(album.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                                heldId = album.id
                                                heldOffset = Offset.Zero
                                            },
                                            onDragEnd = { heldId = null },
                                            onDragCancel = { heldId = null },
                                        ) { change, amount ->
                                            change.consume()
                                            heldOffset += amount
                                            val list = currentAlbums
                                            val from = list.indexOfFirst { it.id == album.id }
                                            if (from < 0) return@detectDragGestures
                                            val centre = geometry.slot(from) + heldOffset + Offset(geometry.cell / 2f, geometry.cell / 2f)
                                            val to = geometry.slotAt(centre, list.size)
                                            if (to != from) {
                                                onArrangeGroup(list.toMutableList().apply { add(to, removeAt(from)) })
                                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                // The card takes the new slot, so the offset is rebased onto it to stay under the finger.
                                                heldOffset += geometry.slot(from) - geometry.slot(to)
                                            }
                                        }
                                    }
                                },
                            )
                            .jiggle(album.id, isArranging) { heldId == album.id },
                    )
                }
            }
            CollapseCard(onClick = { onOpenChange(false) }, isList = false)
            LabelReveal(stack.name, isShown = isOpen, style = Type.title.copy(fontSize = 22.sp, color = LocalAccent.current), presence = { time.value })
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = COVER_GAP.roundToPx()
        val cell = (width - gap * (GROUP_COLUMNS - 1)) / GROUP_COLUMNS
        val small = LIST_COVER.roundToPx()
        val labelStart = small + GROUP_LABEL_GAP.roundToPx() + stackShift(GROUP_COLUMNS).roundToPx()
        val header = measurables.first().measure(Constraints.fixed((width - labelStart).coerceAtLeast(0), small))
        val heading = measurables.last().measure(Constraints(maxWidth = width))
        val cards = measurables.subList(1, measurables.size - 1).map { it.measure(Constraints.fixedWidth(cell)) }
        val rowHeight = cards.maxOf { it.height }
        val rowGap = GROUP_ROW_GAP.roundToPx()
        geometry.cell = cell.toFloat()
        geometry.gap = gap.toFloat()
        geometry.rowHeight = rowHeight.toFloat()
        geometry.rowGap = rowGap.toFloat()
        val rows = (cards.size + GROUP_COLUMNS - 1) / GROUP_COLUMNS
        // Opened, the group's name stands over its cards as a heading.
        val headingSpace = heading.height + GROUP_HEADING_GAP.roundToPx()
        val openHeight = headingSpace + rows * rowHeight + (rows - 1) * rowGap
        val openness = Motion.powerThreeInOut.transform(time.value)
        val height = (small + (openHeight - small) * openness).roundToInt()
        layout(width, height) {
            header.placeWithLayer(labelStart, 0) { alpha = (1f - openness * 2f).coerceIn(0f, 1f) }
            heading.place(0, 0)
            cards.forEachIndexed { index, card ->
                val isCloser = index == count
                val p = progressOf(index)
                val depth = min(index, STACK_DEPTH)
                val isHeld = !isCloser && albums[index].id == heldId
                val open = Offset(0f, headingSpace.toFloat()) + when {
                    isCloser -> geometry.slot(count)
                    isHeld -> geometry.slot(index) + heldOffset
                    else -> geometry.slot(slotStates[index].value)
                }
                // Lying in the stack, the card's picture is centred on the stack's own, shifted by its depth.
                val shut = Offset(small / 2f + stackShift(depth).toPx() - cell / 2f, small / 2f - cell / 2f)
                val x = shut.x + (open.x - shut.x) * p
                val y = shut.y + (open.y - shut.y) * p
                card.placeWithLayer(x.roundToInt(), y.roundToInt(), zIndex = if (isHeld) 100f else (count - index).toFloat()) {
                    transformOrigin = TransformOrigin(0.5f, cell / 2f / card.height)
                    val shutScale = small.toFloat() / cell * stackScale(depth)
                    val scale = shutScale + (1f - shutScale) * p
                    scaleX = scale
                    scaleY = scale
                    rotationZ = stackTilt(depth) * (1f - p)
                    // Deeper cards lie darker, as in the stack; the ground is near black, so fading darkens. Past the visible layers, and the closing card, they are hidden until they leave.
                    val shade = if (isCloser || index > STACK_DEPTH) 0f else stackShade(depth)
                    alpha = shade + (1f - shade) * p.coerceIn(0f, 1f)
                }
            }
        }
    }
}

// How many cards show under the top one while a group lies shut.
private const val STACK_DEPTH = 3

// The header (back and the album's name) floats over the grid in the app's top layer, so it can blur what scrolls under it.
@Composable
fun AlbumScreen(album: Album, memory: GridMemory, onOpen: (Int) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues, selection: Selection) {
    // While selecting, back clears the selection first (the app root handles that), so this one stands aside.
    BackHandler(enabled = !selection.isActive, onBack = onBack)
    MediaGrid(items = album.items, memory = memory, onOpen = onOpen, contentPadding = contentPadding, selection = selection)
}
