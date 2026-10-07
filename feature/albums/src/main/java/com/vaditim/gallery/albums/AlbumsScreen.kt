package com.vaditim.gallery.albums

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.components.AddCardRow
import com.vaditim.gallery.components.COVER_GAP
import com.vaditim.gallery.components.CollapseButton
import com.vaditim.gallery.components.CoverCard
import com.vaditim.gallery.components.CoverGrid
import com.vaditim.gallery.components.DROPPING_SCALE
import com.vaditim.gallery.components.HELD_SCALE
import com.vaditim.gallery.components.Reorder
import com.vaditim.gallery.components.GridMemory
import com.vaditim.gallery.components.LIST_COVER
import com.vaditim.gallery.components.LocalAccentedCoverNames
import com.vaditim.gallery.components.MediaGrid
import com.vaditim.gallery.components.Selection
import com.vaditim.gallery.components.SpanCell
import com.vaditim.gallery.components.coverColumns
import com.vaditim.gallery.components.coverRowGap
import com.vaditim.gallery.components.currentSettingsView
import com.vaditim.gallery.components.dragToArrange
import com.vaditim.gallery.components.entrance
import com.vaditim.gallery.components.jiggle
import com.vaditim.gallery.components.liftedShadow
import com.vaditim.gallery.components.rememberReorder
import com.vaditim.gallery.components.reorderable
import com.vaditim.gallery.components.stackScale
import com.vaditim.gallery.components.stackShade
import com.vaditim.gallery.components.stackShift
import com.vaditim.gallery.components.stackTilt
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.settings.AlbumArrangement
import com.vaditim.gallery.settings.AlbumStack
import com.vaditim.gallery.settings.Settings
import com.vaditim.gallery.vas.Haptics
import com.vaditim.gallery.vas.LabelReveal
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// What one place in the albums grid holds: an album, or (with Grouped albums on) a group of them lying as a stack.
private sealed interface AlbumEntry {
    val key: Any

    data class Single(val album: Album) : AlbumEntry {
        override val key: Any get() = album.id
    }

    data class Stack(val name: String, val albums: List<Album>) : AlbumEntry {
        override val key: Any get() = "$STACK_KEY_PREFIX$name"
    }
}

// Groups come first and albums after them, never the other way round; among each kind the arranged order decides, a group standing where its first album would.
private fun entriesOf(albums: List<Album>, stacks: List<AlbumStack>): List<AlbumEntry> {
    if (stacks.isEmpty()) return albums.map { AlbumEntry.Single(it) }
    val stackOf = stacks.flatMap { stack -> stack.paths.map { it to stack.name } }.toMap()
    val members = albums.groupBy { stackOf[it.relativePath] }
    val placed = HashSet<String>()
    val groups = albums.mapNotNull { album ->
        val name = stackOf[album.relativePath] ?: return@mapNotNull null
        if (placed.add(name)) AlbumEntry.Stack(name, members.getValue(name)) else null
    }
    return groups + albums.filter { stackOf[it.relativePath] == null }.map { AlbumEntry.Single(it) }
}

private const val STACK_KEY_PREFIX = "stack:"

// Folders, or the albums made inside Favorites. No "Recent" and no "Favorites" album: both are sections already, and an album that repeats a section is the Samsung habit this app exists to drop.
// `stacks` are the groups shown, by album path; the folders pass theirs only while Grouped albums is on.
@Composable
fun AlbumsScreen(
    albums: List<Album>,
    title: String = "Albums",
    stacks: List<AlbumStack> = if (Settings.groupedAlbumsIn(currentSettingsView())) AlbumArrangement.albumStacks.all else emptyList(),
    state: LazyGridState,
    onOpen: (Album) -> Unit,
    onLongPress: (Album) -> Unit,
    onStackLongPress: (String) -> Unit,
    onNewAlbum: () -> Unit,
    contentPadding: PaddingValues,
    // Shown above New album while the albums are grouped.
    onNewGroup: (() -> Unit)? = null,
    footer: @Composable () -> Unit = {},
    isRearranging: Boolean = false,
    onArrange: (paths: List<String>) -> Unit = {},
    selectedPaths: Set<String> = emptySet(),
    onToggle: (List<Album>) -> Unit = {},
    openStacks: Set<String> = emptySet(),
    onOpenStacksChange: (Set<String>) -> Unit = {},
    isAccented: Boolean = false,
    // A cover held a moment and then dragged turns rearranging on with it.
    onStartRearranging: () -> Unit = {},
    // An album dropped onto a group while rearranging, or dragged out of its own group (group null).
    onRegroup: (album: Album, group: String?) -> Unit = { _, _ -> },
) = CompositionLocalProvider(LocalAccentedCoverNames provides isAccented) {
    val isPicking = selectedPaths.isNotEmpty()
    val entries = remember(albums, stacks) { entriesOf(albums, stacks) }
    val picturesWidth = with(LocalDensity.current) { (LIST_COVER + stackShift(STACK_DEPTH) + GROUP_LABEL_GAP / 2).toPx() }
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
    reorder.isRearranging = { isRearranging }
    reorder.onStartRearranging = if (isPicking) null else onStartRearranging
    // Groups trade places with groups and albums with albums, so an album never lands above a group.
    reorder.canSwap = { held, target -> (held is String) == (target is String) }
    reorder.canDropInto = { held, target -> held !is String && target is String }
    // The shut group an album hangs over, peeked open; it folds back once the album moves off it or is let go.
    var peeked by remember { mutableStateOf<String?>(null) }
    // How far each group's opened cards reach below its shut row, for the album hanging over a peek to still count as over the group.
    val peekReach = remember { HashMap<String, Float>() }
    reorder.reachBelow = { key -> if (key is String && key.removePrefix(STACK_KEY_PREFIX) == peeked) peekReach[key.removePrefix(STACK_KEY_PREFIX)] ?: 0f else 0f }
    val regroup = onRegroup
    reorder.onDropInto = { held, target ->
        val album = entries.firstNotNullOfOrNull { (it as? AlbumEntry.Single)?.album?.takeIf { album -> album.id == held } }
        if (album != null && target is String) regroup(album, target.removePrefix(STACK_KEY_PREFIX))
    }
    // While a group opens or closes, the rows under it follow its height frame by frame instead of gliding after it, so nothing overlaps.
    var movingGroups by remember { mutableStateOf(emptySet<String>()) }
    val glide = if (movingGroups.isEmpty()) tween<IntOffset>(Motion.STACK_MS, easing = Motion.powerThreeInOut) else null
    val currentOpenStacks by rememberUpdatedState(openStacks)
    val currentOnOpenStacksChange by rememberUpdatedState(onOpenStacksChange)
    val setOpen = { name: String, open: Boolean ->
        movingGroups = movingGroups + name
        currentOnOpenStacksChange(if (open) currentOpenStacks + name else currentOpenStacks - name)
    }
    val hovered = (reorder.dropTargetKey as? String)?.removePrefix(STACK_KEY_PREFIX)
    LaunchedEffect(hovered) {
        if (peeked != null && peeked != hovered) peeked = null
        if (hovered != null && hovered !in currentOpenStacks && hovered != peeked) {
            delay(Motion.HOVER_OPEN_MS)
            peeked = hovered
        }
    }

    // Every group is a row of its own, so the albums before it end their row early.
    val spans = coverColumns().let { columns -> remember(entries, columns) { spansOf(entries, columns) } }

    CoverGrid(state, contentPadding, onBackgroundLongPress = if (isPicking || isRearranging) null else onStartRearranging) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
            BasicText(title, style = Type.title, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        }
        entries.forEachIndexed { index, entry ->
            val span = spans[index]
            when (entry) {
                is AlbumEntry.Single -> item(key = entry.key, span = { GridItemSpan(span) }, contentType = "album") {
                    val album = entry.album
                    SpanCell(span, reorderable(reorder, entry.key, isEnabled = true, placement = glide).entrance()) {
                        CoverCard(
                            album.name,
                            album.cover,
                            album.items.size,
                            onClick = { if (isPicking) onToggle(listOf(album)) else if (!isRearranging) onOpen(album) },
                            // While rearranging a held card has no long press: one that fired would keep the finger to itself and the drag would never start.
                            onLongClick = if (isRearranging) null else { { if (isPicking) onToggle(listOf(album)) else onLongPress(album) } },
                            modifier = Modifier.jiggle(reorder, entry.key, isRearranging),
                            isSelected = album.relativePath in selectedPaths,
                            isLifted = reorder.draggedKey == entry.key,
                        )
                    }
                }
                is AlbumEntry.Stack -> item(key = entry.key, span = { GridItemSpan(maxLineSpan) }, contentType = "stack") {
                    val hasAlbumBefore = entries.getOrNull(index - 1) is AlbumEntry.Single
                    val hasAlbumAfter = entries.getOrNull(index + 1) is AlbumEntry.Single
                    val isOpen = entry.name in openStacks
                    val isMovable = isRearranging && !isOpen
                    val isDropTarget = reorder.dropTargetKey == entry.key
                    val dropScale by animateFloatAsState(if (isDropTarget) DROP_TARGET_SCALE else 1f, tween(Motion.STATE_MS, easing = Motion.backOut), label = "drop-target")
                    GroupRow(
                        stack = entry,
                        isOpen = isOpen,
                        onOpenChange = { open -> setOpen(entry.name, open) },
                        onMotion = { isMoving -> movingGroups = if (isMoving) movingGroups + entry.name else movingGroups - entry.name },
                        isPicking = isPicking,
                        selectedPaths = selectedPaths,
                        onToggle = onToggle,
                        onOpenAlbum = onOpen,
                        onLongPress = onLongPress,
                        onStackLongPress = { onStackLongPress(entry.name) },
                        isRearranging = isRearranging,
                        isMovable = isMovable,
                        isHeld = { reorder.draggedKey == entry.key },
                        onArrangeGroup = { reordered -> arrange(entries, entry.name, reordered) },
                        onStartRearranging = if (isPicking) null else onStartRearranging,
                        reorder = reorder,
                        onRegroup = regroup,
                        isPeeked = peeked == entry.name,
                        onPeekReach = { reach -> peekReach[entry.name] = reach },
                        // Before rearranging, only a drag that starts on the stack's pictures picks the group up; anywhere else the swipe still opens it.
                        modifier = reorderable(
                            reorder,
                            entry.key,
                            isEnabled = !isOpen,
                            placement = glide,
                            startArea = { at, _ -> at.x < picturesWidth },
                            // An album dragged out of this group is drawn by it, so the group lies above the rows it is dragged over; a peek lies over the rows below it, under any held album.
                            lift = if (reorder.groupHoldingKey == entry.key) 2f else if (peeked == entry.name) 1f else 0f,
                        )
                            .graphicsLayer {
                                scaleX = dropScale
                                scaleY = dropScale
                            }
                            .entrance()
                            .albumDividers(hasAlbumBefore, hasAlbumAfter, coverRowGap())
                            .padding(vertical = GROUP_GAP),
                    )
                }
            }
        }
        // One item with what follows it, so no row gap pushes the divider under the buttons further away than the one above them.
        item(key = "new-album", span = { GridItemSpan(maxLineSpan) }, contentType = "new-album") {
            Column(Modifier.animateItem(placementSpec = glide).entrance()) {
                AddCardRow(onNewAlbum, onNewGroup)
                footer()
            }
        }
    }
}

// A short hairline, centred, in the middle of the room between a group and the albums beside it (the grid's row gap and the group's own gap), so the two kinds read apart.
private fun Modifier.albumDividers(isAbove: Boolean, isBelow: Boolean, rowGap: Dp): Modifier = if (!isAbove && !isBelow) this else drawBehind {
    val width = size.width * ALBUM_DIVIDER_SHARE
    val left = (size.width - width) / 2f
    val inset = (GROUP_GAP.toPx() - rowGap.toPx()) / 2f
    val thickness = 1.dp.toPx()
    if (isAbove) drawRect(Palette.border, Offset(left, inset - thickness / 2f), Size(width, thickness))
    if (isBelow) drawRect(Palette.border, Offset(left, size.height - inset - thickness / 2f), Size(width, thickness))
}

private const val ALBUM_DIVIDER_SHARE = 0.35f
// A group swells a little while an album hangs over it, ready to go in.
private const val DROP_TARGET_SCALE = 1.03f

// Extra room above and below a group, so groups read as separate rows.
private val GROUP_GAP = 12.4.dp
// How much of the row's width a swipe left takes to pull an opened group all the way shut, and how far through it letting go closes it.
private const val PULL_REACH = 0.8f
private const val PULL_CLOSE = 0.3f
private const val PUSH_OPEN = 0.3f
// The share of the arrow's way, centred on its middle, over which it turns from pointing right to pointing left.
private const val ARROW_TURN = 0.5f
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
    var headingHeight = 0f
    // From the top of the group to the top of its first row of cards.
    var headingSpace = 0f

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
    isHeld: () -> Boolean,
    onArrangeGroup: (List<Album>) -> Unit,
    onStartRearranging: (() -> Unit)?,
    reorder: Reorder,
    onRegroup: (Album, String?) -> Unit,
    onMotion: (Boolean) -> Unit,
    // Opened only to show where an album hanging over it would go: the cards lay themselves out over the rows below, and the row keeps its shut height, so nothing under the finger moves.
    isPeeked: Boolean = false,
    // How far the opened cards reach below the shut row, measured each layout, so the album hanging over them still counts as over the group.
    onPeekReach: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val albums = stack.albums
    val count = albums.size
    val isShown = isOpen || isPeeked
    // Time through opening or closing, 0 to 1, run evenly; each card turns it into its own eased, staggered progress.
    val time = remember(stack.name) { Animatable(if (isOpen) 1f else 0f) }
    // Held while a peek is open and until it has folded back, so the row keeps its shut height the whole way.
    var isPeekLayout by remember(stack.name) { mutableStateOf(false) }
    LaunchedEffect(isPeeked, isOpen) {
        if (isOpen) isPeekLayout = false else if (isPeeked) isPeekLayout = true
    }
    // A swipe left pulls the opened group shut by the finger: it drives the same time, on the closing curve, so letting go carries on from where the cards are.
    var isPulled by remember(stack.name) { mutableStateOf(false) }
    // Where the arrow is, 0 left of the shut group's name, pointing right, to 1 at the right end of the heading, pointing left; it turns half way. Closing, the heading is cut the moment it is back.
    val arrow = remember(stack.name) { Animatable(if (isOpen) 1f else 0f) }
    var isHeadingShown by remember(stack.name) { mutableStateOf(isOpen) }
    // Before the effect below, which clears isPulled, so it can still tell a pull from a tap.
    LaunchedEffect(isShown) {
        if (isShown) {
            isHeadingShown = true
            if (arrow.value < 1f) arrow.animateTo(1f, tween((Motion.STACK_MS * (1f - arrow.value)).roundToInt(), easing = Motion.backOut))
        } else {
            // From wherever the finger left it, so a pull carries straight on.
            arrow.animateTo(0f, tween((Motion.STACK_MS * arrow.value).roundToInt(), easing = if (isPulled) Motion.powerTwoOut else Motion.powerThreeInOut))
            isHeadingShown = false
        }
    }
    LaunchedEffect(isShown) {
        if (!isShown) isPulled = false
        val target = if (isShown) 1f else 0f
        // Started part way, by a pull, it takes only the share of the time that is left.
        time.animateTo(target, tween((Motion.STACK_MS * abs(target - time.value)).roundToInt(), easing = LinearEasing))
        // The cards land a little after the time ends when they overshoot, so the rows below are freed only once they have.
        delay(Motion.STATE_MS.toLong())
        if (!isShown) isPeekLayout = false
        onMotion(false)
    }
    // A group scrolled away mid-motion must not keep the rows below from gliding.
    DisposableEffect(stack.name) { onDispose { onMotion(false) } }
    val isShut = !isShown && time.value == 0f
    val isArranging = isRearranging && isOpen
    val geometry = remember { GroupGeometry() }
    val currentAlbums by rememberUpdatedState(albums)
    val currentIsRearranging by rememberUpdatedState(isRearranging)
    val currentIsOpen by rememberUpdatedState(isOpen)
    val currentIsPicking by rememberUpdatedState(isPicking)
    val currentOnStartRearranging by rememberUpdatedState(onStartRearranging)
    val currentOnArrangeGroup by rememberUpdatedState(onArrangeGroup)
    var heldId by remember { mutableStateOf<Long?>(null) }
    var heldOffset by remember { mutableStateOf(Offset.Zero) }
    // Off the group's own row, the held album no longer trades places inside it: it is on its way out, or into another group.
    var isHeldOutside by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<Job?>(null) }
    // Where this group lay in the grid when one of its albums was picked up. Another group opening above pushes this one down, but the held album is the finger's, so it is drawn back by however far the group has moved.
    var groupStart by remember { mutableStateOf<Offset?>(null) }
    val drift = { groupStart?.let { start -> (reorder.topLeftOf(stack.key) ?: start) - start } ?: Offset.Zero }
    val groupGap = with(LocalDensity.current) { GROUP_GAP.toPx() }
    val heldScale by animateFloatAsState(if (reorder.dropTargetKey != null && heldId != null) DROPPING_SCALE else HELD_SCALE, tween(Motion.STATE_MS, easing = Motion.backOut), label = "held")
    val haptic = LocalHapticFeedback.current
    // The whole group carried, its name and count lift with it.
    val headerShadow = liftedShadow(isHeld())

    // Card `index` of the albums: the deeper ones leave a little later and come back a little sooner, and opening overshoots slightly.
    fun progressOf(index: Int): Float {
        val start = index.toFloat() / count * STAGGER_SPAN
        val local = ((time.value - start) / (1f - STAGGER_SPAN)).coerceIn(0f, 1f)
        return if (isShown && !isPulled) Motion.backOut.transform(local) else Motion.powerThreeInOut.transform(local)
    }

    val openGroup = { if (isPicking) onToggle(albums) else onOpenChange(true) }
    val slotStates = albums.mapIndexed { index, album ->
        key(album.id) { animateFloatAsState(index.toFloat(), tween(Motion.STATE_MS, easing = Motion.powerTwoOut), label = "slot") }
    }

    val context = LocalContext.current
    val picturesWidth = with(LocalDensity.current) { (LIST_COVER + stackShift(STACK_DEPTH) + GROUP_LABEL_GAP / 2).toPx() }
    val currentOnOpenChange by rememberUpdatedState(onOpenChange)
    val scope = rememberCoroutineScope()
    Layout(
        modifier = modifier
            .jiggle(stack.key, isMovable, pivot = LIST_COVER / 2, isHeld = isHeld)
            // The group's own gestures stay attached whatever state it is in and each asks, as a finger lands, whether it applies: taking a gesture handler off while a finger is down cancels every gesture under it, so turning rearranging on would drop the album just being picked up.
            // Each card follows the swipe back toward the stack, the last ones pulled hardest, so they land under one another; let go far enough and the group lays itself down, otherwise the cards go back.
            .pointerInput(stack.name) {
                var pulled = 0f
                val home = {
                    scope.launch {
                        launch { arrow.animateTo(1f, tween((Motion.STATE_MS * (1f - arrow.value)).roundToInt(), easing = Motion.powerTwoOut)) }
                        time.animateTo(1f, tween((Motion.STATE_MS * (1f - time.value)).roundToInt(), easing = Motion.powerTwoOut))
                        isPulled = false
                        onMotion(false)
                    }
                    Unit
                }
                val pull = { amount: Float ->
                    pulled = (pulled + amount).coerceAtMost(0f)
                    val reach = size.width * PULL_REACH
                    val shown = (1f + pulled / reach).coerceIn(0f, 1f)
                    scope.launch {
                        time.snapTo(shown)
                        arrow.snapTo(shown)
                    }
                    Unit
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!currentIsOpen || currentIsRearranging) return@awaitEachGesture
                    var overSlop = 0f
                    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                        change.consume()
                        overSlop = over
                    } ?: return@awaitEachGesture
                    pulled = 0f
                    isPulled = true
                    onMotion(true)
                    pull(overSlop)
                    val isReleased = horizontalDrag(slop.id) { change ->
                        change.consume()
                        pull(change.positionChange().x)
                    }
                    if (isReleased && 1f - time.value > PULL_CLOSE) {
                        Haptics.tick(context)
                        currentOnOpenChange(false)
                    } else {
                        home()
                    }
                }
            }
            // Closed, a swipe right opens it by the finger the same way, the arrow going with it; letting go far enough carries it on, otherwise the cards go back.
            .pointerInput(stack.name) {
                var pushed = 0f
                val settle = {
                    scope.launch {
                        launch { arrow.animateTo(0f, tween((Motion.STATE_MS * arrow.value).roundToInt(), easing = Motion.powerTwoOut)) }
                        time.animateTo(0f, tween((Motion.STATE_MS * time.value).roundToInt(), easing = Motion.powerTwoOut))
                        onMotion(false)
                    }
                    Unit
                }
                val push = { amount: Float ->
                    pushed = (pushed + amount).coerceAtLeast(0f)
                    val shown = (pushed / (size.width * PULL_REACH)).coerceIn(0f, 1f)
                    scope.launch {
                        time.snapTo(shown)
                        arrow.snapTo(shown)
                    }
                    Unit
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (currentIsOpen || currentIsRearranging || currentIsPicking) return@awaitEachGesture
                    // On the stack's pictures a drag picks the group up instead, so the swipe stands aside there.
                    if (currentOnStartRearranging != null && down.position.x < picturesWidth) return@awaitEachGesture
                    var overSlop = 0f
                    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                        change.consume()
                        overSlop = over
                    } ?: return@awaitEachGesture
                    pushed = 0f
                    onMotion(true)
                    push(overSlop)
                    val isReleased = horizontalDrag(slop.id) { change ->
                        change.consume()
                        push(change.positionChange().x)
                    }
                    if (isReleased && time.value > PUSH_OPEN) {
                        Haptics.tick(context)
                        currentOnOpenChange(true)
                    } else {
                        settle()
                    }
                }
            }
            // A tap on the heading's line closes the group, not only on its arrow.
            .pointerInput(stack.name) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!currentIsOpen || currentIsRearranging || down.position.y >= geometry.headingHeight) return@awaitEachGesture
                    waitForUpOrCancellation()?.let { up ->
                        up.consume()
                        currentOnOpenChange(false)
                    }
                }
            },
        content = {
            // The name and count take the whole rest of the row, so a tap anywhere beside the stack opens it; opened, the area is gone from under the cards.
            Column(
                if (isOpen) Modifier else Modifier.pressable(onClick = openGroup, pressedScale = 0.98f, onLongClick = if (isRearranging) null else { { if (isPicking) onToggle(albums) else onStackLongPress() } }),
                verticalArrangement = Arrangement.Center,
            ) {
                // Never swept: opening, or pushed open, it is sliced away from its left end in step with the cards, and closing gives it back the same way.
                LabelReveal(
                    stack.name,
                    isShown = true,
                    style = Type.cardTitle.copy(fontSize = 20.sp, color = LocalAccent.current, shadow = headerShadow),
                    presence = { 1f - time.value },
                    isRevealedAtStart = true,
                    isCutFromStart = true,
                )
                BasicText(
                    albums.sumOf { it.items.size }.toString(),
                    style = Type.value.copy(fontSize = 15.sp, shadow = headerShadow),
                    modifier = Modifier.padding(top = 6.dp).graphicsLayer { alpha = (1f - Motion.powerThreeInOut.transform(time.value) * 2f).coerceIn(0f, 1f) },
                )
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
                        onLongClick = if (isRearranging) null else { {
                            when {
                                isShut -> if (isPicking) onToggle(albums) else onStackLongPress()
                                isPicking -> onToggle(listOf(album))
                                else -> onLongPress(album)
                            }
                        } },
                        isSelected = isSelected,
                        isList = false,
                        isLifted = heldId == album.id,
                        // The name waits until the card is well clear of the stack, so names never print over each other.
                        labelAlpha = { ((progressOf(index) - 0.5f) * 2f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            // Open, the group's albums are arranged inside it the same way covers are in the grid: at once while rearranging, or held a moment and dragged to start rearranging.
                            .then(
                                if (!isOpen) {
                                    Modifier
                                } else {
                                    Modifier.dragToArrange(
                                        key = album.id,
                                        isRearranging = { currentIsRearranging },
                                        canStart = { _, _ -> currentOnStartRearranging != null },
                                        onStart = {
                                            if (!currentIsRearranging) currentOnStartRearranging?.invoke()
                                            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                            settling?.cancel()
                                            // A card caught while still gliding home is picked up where it is drawn.
                                            if (heldId != album.id) heldOffset = Offset.Zero
                                            heldId = album.id
                                            isHeldOutside = false
                                            reorder.startFromGroup(stack.key)
                                            groupStart = reorder.topLeftOf(stack.key)
                                        },
                                        onDrag = drag@{ amount ->
                                            heldOffset += amount
                                            val list = currentAlbums
                                            val from = list.indexOfFirst { it.id == album.id }
                                            if (from < 0) return@drag
                                            // Where the card is drawn, in the group's terms, once the group's own movement is taken off.
                                            val shown = heldOffset - drift()
                                            // Where its centre is in the grid, to tell whether it has left this group or hangs over another.
                                            val topLeft = reorder.topLeftOf(stack.key)
                                            val inGroup = Offset(0f, geometry.headingSpace) + geometry.slot(from) + shown + Offset(geometry.cell / 2f, geometry.cell / 2f)
                                            isHeldOutside = topLeft != null && reorder.hoverFromGroup(stack.key, album.id, topLeft + Offset(0f, groupGap) + inGroup)
                                            if (isHeldOutside) return@drag
                                            val centre = geometry.slot(from) + shown + Offset(geometry.cell / 2f, geometry.cell / 2f)
                                            val to = geometry.slotAt(centre, list.size)
                                            if (to != from) {
                                                currentOnArrangeGroup(list.toMutableList().apply { add(to, removeAt(from)) })
                                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                // The card takes the new slot, so the offset is rebased onto it to stay under the finger.
                                                heldOffset += geometry.slot(from) - geometry.slot(to)
                                            }
                                        },
                                        onEnd = {
                                            val target = reorder.endFromGroup()
                                            val isLeaving = target != null || isHeldOutside
                                            isHeldOutside = false
                                            // From here on the card belongs to the group again, so the drift is folded into its offset.
                                            heldOffset -= drift()
                                            groupStart = null
                                            if (isLeaving) {
                                                heldId = null
                                                onRegroup(album, (target as? String)?.removePrefix(STACK_KEY_PREFIX))
                                            } else {
                                                // Let go inside the group, the card glides the rest of the way into its slot from where the finger left it.
                                                val from = heldOffset
                                                settling = scope.launch {
                                                    animate(0f, 1f, animationSpec = tween(Motion.RELEASE_MS, easing = Motion.powerTwoOut)) { progress, _ -> heldOffset = from * (1f - progress) }
                                                    if (heldId == album.id) heldId = null
                                                }
                                            }
                                        },
                                    )
                                },
                            )
                            .jiggle(album.id, isArranging, isHeld = { heldId == album.id }, heldScale = { heldScale }),
                    )
                }
            }
            // Pulled back by the finger, the name is cut once the arrow reaches it, and comes back if the finger lets it go again.
            LabelReveal(
                stack.name,
                isShown = isHeadingShown && !(isPulled && arrow.value <= 0f),
                style = Type.title.copy(fontSize = 22.sp, color = LocalAccent.current),
                // Closing, or pulled shut, the name is sliced away from its right end in step with the cards; opening, its own sweep brings it in.
                presence = { if (isShown && !isPulled) 1f else time.value },
                isRevealedAtStart = true,
            )
            // Shut, it stands left of the name pointing right and opens the group; opened, it folds the group back from the right end of its heading.
            CollapseButton(onClick = { if (isOpen) onOpenChange(false) else openGroup() })
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = COVER_GAP.roundToPx()
        val cell = (width - gap * (GROUP_COLUMNS - 1)) / GROUP_COLUMNS
        val small = LIST_COVER.roundToPx()
        val labelStart = small + GROUP_LABEL_GAP.roundToPx() + stackShift(GROUP_COLUMNS).roundToPx()
        val back = measurables.last().measure(Constraints())
        val header = measurables.first().measure(Constraints.fixed((width - labelStart).coerceAtLeast(0), small))
        val heading = measurables[measurables.size - 2].measure(Constraints(maxWidth = (width - back.width).coerceAtLeast(0)))
        val cards = measurables.subList(1, measurables.size - 2).map { it.measure(Constraints.fixedWidth(cell)) }
        val rowHeight = cards.maxOfOrNull { it.height } ?: cell
        val rowGap = GROUP_ROW_GAP.roundToPx()
        geometry.cell = cell.toFloat()
        geometry.gap = gap.toFloat()
        geometry.rowHeight = rowHeight.toFloat()
        geometry.rowGap = rowGap.toFloat()
        val rows = (cards.size + GROUP_COLUMNS - 1) / GROUP_COLUMNS
        // Opened, the group's name stands over its cards as a heading.
        val headingHeight = maxOf(heading.height, back.height)
        geometry.headingHeight = headingHeight.toFloat()
        val headingSpace = headingHeight + GROUP_HEADING_GAP.roundToPx()
        geometry.headingSpace = headingSpace.toFloat()
        val openHeight = headingSpace + rows * rowHeight + (rows - 1).coerceAtLeast(0) * rowGap
        val openness = Motion.powerThreeInOut.transform(time.value)
        val placements = cards.mapIndexed { index, card ->
            val p = progressOf(index)
            val depth = min(index, STACK_DEPTH)
            val isHeld = albums[index].id == heldId
            val open = Offset(0f, headingSpace.toFloat()) + when {
                isHeld -> geometry.slot(index) + heldOffset
                else -> geometry.slot(slotStates[index].value)
            }
            // Lying in the stack, the card's picture is centred on the stack's own, shifted by its depth.
            val shut = Offset(small / 2f + stackShift(depth).toPx() - cell / 2f, small / 2f - cell / 2f)
            val shutScale = small.toFloat() / cell * stackScale(depth)
            CardPlacement(shut.x + (open.x - shut.x) * p, shut.y + (open.y - shut.y) * p, shutScale + (1f - shutScale) * p, p, depth, isHeld)
        }
        // At rest the row keeps its own height; moving, it grows to where the pictures reach, so the rows below are pushed, never crossed.
        val reach = cards.indices.maxOfOrNull { index ->
            val placement = placements[index]
            if (placement.isHeld) 0f else placement.y + cell / 2f + cell / 2f * placement.scale
        } ?: 0f
        onPeekReach((openHeight - small).toFloat())
        // A peek never moves the rows below; the opened cards lie over them.
        val height = if (isPeekLayout) small else maxOf(small + (openHeight - small) * openness, reach).roundToInt()
        layout(width, height) {
            header.place(labelStart, 0)
            heading.place(0, (headingHeight - heading.height) / 2)
            // From left of the shut name to the right end of the heading, always at the heading's height, turning from right to left over the middle of its way.
            // Shut, its glyph stands in the gap between the stack and the name, nearer the stack, so the name keeps its place.
            val shutX = labelStart - GROUP_LABEL_GAP.toPx() * 0.5f - back.width / 2f
            val openX = (width - back.width).toFloat()
            val openY = (headingHeight - back.height) / 2f
            back.placeWithLayer(0, 0) {
                translationX = shutX + (openX - shutX) * arrow.value
                translationY = openY
                rotationZ = 180f * (1f - ((arrow.value - (1f - ARROW_TURN) / 2f) / ARROW_TURN).coerceIn(0f, 1f))
            }
            cards.forEachIndexed { index, card ->
                val (x, y, scale, p, depth, isHeld) = placements[index]
                card.placeWithLayer(x.roundToInt(), y.roundToInt(), zIndex = if (isHeld) 100f else (count - index).toFloat()) {
                    transformOrigin = TransformOrigin(0.5f, cell / 2f / card.height)
                    scaleX = scale
                    scaleY = scale
                    rotationZ = stackTilt(depth) * (1f - p)
                    // The held card stays under the finger however far the group itself has been pushed.
                    if (isHeld) {
                        val moved = drift()
                        translationX = -moved.x
                        translationY = -moved.y
                    }
                    // Deeper cards lie darker, as in the stack; the ground is near black, so fading darkens. Past the visible layers they are hidden until they leave.
                    val shade = if (index > STACK_DEPTH) 0f else stackShade(depth)
                    alpha = shade + (1f - shade) * p.coerceIn(0f, 1f)
                }
            }
        }
    }
}

// Where a group's card stands at this frame, worked out before the layout so the row's height can follow the cards.
private data class CardPlacement(val x: Float, val y: Float, val scale: Float, val progress: Float, val depth: Int, val isHeld: Boolean)

// How many cards show under the top one while a group lies shut.
private const val STACK_DEPTH = 2

// The header (back and the album's name) floats over the grid in the app's top layer, so it can blur what scrolls under it.
@Composable
fun AlbumScreen(album: Album, memory: GridMemory, onOpen: (Int) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues, selection: Selection) {
    // While selecting, back clears the selection first (the app root handles that), so this one stands aside.
    BackHandler(enabled = !selection.isActive, onBack = onBack)
    MediaGrid(items = album.items, memory = memory, onOpen = onOpen, contentPadding = contentPadding, selection = selection)
}
