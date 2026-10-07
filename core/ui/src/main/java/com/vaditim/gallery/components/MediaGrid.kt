package com.vaditim.gallery.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyLayoutScrollScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.media.MotionPhoto
import com.vaditim.gallery.media.SimilarShots
import com.vaditim.gallery.media.Thumbnail
import com.vaditim.gallery.settings.DateGroup
import com.vaditim.gallery.settings.Settings
import com.vaditim.gallery.settings.SettingsView
import com.vaditim.gallery.vas.Haptics
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val MIN_COLUMNS = 1
private const val MAX_COLUMNS = 6

// How far a pinch has to travel before the grid steps one column: spreading past this shows fewer, larger photos.
private const val PINCH_STEP = 1.28f
private const val AUTO_SCROLL_STEP = 22f
private val GAP = 3.dp
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yy", Locale.ENGLISH)
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.ENGLISH)

// The scroll position and whether the grid has been put at its newest end yet. Held above the grid so leaving a section and coming back finds it where it was; the column count rides along so a folder keeps the zoom it was left at.
// Whether the photo grids of the main view show only favourites, set by the toggle in the corner above the bar.
val LocalFavoritesOnly = compositionLocalOf { false }
// How far the viewer has grown over the grid, 0 to 1: the timeline slides off the right edge with it and back in as the photo shrinks, as the top row and the nav leave by theirs.
val LocalViewerGrowth = compositionLocalOf<() -> Float> { { 0f } }

// The grid lies under the viewer's photo, so its timeline is drawn a second time on a layer above the photo. Both are always composed and only swap which one shows while the viewer is up, read at draw time, so the swap never misses a frame.
class TimelineAbove {
    val grids = mutableStateListOf<TimelineAboveGrid>()
    var isViewerShown by mutableStateOf(false)

    fun isShownAbove(grid: TimelineAboveGrid): Boolean = isViewerShown && grids.lastOrNull() === grid
}

class TimelineAboveGrid {
    var content by mutableStateOf<@Composable () -> Unit>({})
    var bounds by mutableStateOf(Rect.Zero)
}

val LocalTimelineAbove = compositionLocalOf<TimelineAbove?> { null }

// The layer above the photo: the newest grid's timeline, exactly over the grid's own one.
@Composable
fun BoxScope.TimelineAboveHost(above: TimelineAbove, modifier: Modifier = Modifier) {
    val grid = above.grids.lastOrNull() ?: return
    val bounds = grid.bounds
    val density = LocalDensity.current
    key(grid) {
        Box(
            modifier
                .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                .graphicsLayer { alpha = if (above.isShownAbove(grid)) 1f else 0f },
        ) { grid.content() }
    }
}

// True while a sheet covers the screen: a grid still gliding stops, so the blur behind the sheet is not redrawn every frame of its arrival.
val LocalScreenCovered = compositionLocalOf { false }

// Stops a fling the moment a sheet covers the grid.
@Composable
fun HoldUnderSheet(state: LazyGridState) {
    val isCovered = LocalScreenCovered.current
    LaunchedEffect(isCovered) { if (isCovered) state.stopScroll() }
}

// `folder` is the album's stable key, which may keep headers of its own.
class GridMemory(val view: SettingsView = Settings.view, private val isStacking: Boolean = true, val folder: String? = null) {
    val state = LazyGridState()
    var isPositioned = false
    var knownCount = 0
    var columns by mutableIntStateOf(Settings.columnsIn(view))
    // Stacks of similar shots opened out in this grid, by the id of their cover.
    var openStacks by mutableStateOf<Set<Long>>(emptySet())

    // Every reader of this grid's entries builds them the same way, so an entry index means the same tile everywhere.
    fun entriesOf(items: List<MediaItem>): List<GridEntry> = buildEntries(items, openStacks, isStacking && Settings.stackSimilarIn(view), view, folder)
}

// A grid is photos with a header in front of the first photo of each year, month, week and day it is cut into. `index` is the photo's place in the original list, which is what the viewer opens at.
sealed interface GridEntry {
    data class Header(val label: String, val key: String, val group: DateGroup) : GridEntry
    // `stack` holds every shot of a similar-shot stack the photo belongs to (the newest is its cover); `isStackOpen` says the stack is laid out tile by tile rather than folded into the cover.
    // `stampDay` is set on the first photo of each day when there are no day or week headers, which carries the day on its corner.
    data class Photo(val item: MediaItem, val index: Int, val stack: List<MediaItem> = emptyList(), val isStackOpen: Boolean = false, val stampDay: LocalDate? = null) : GridEntry {
        val isFoldedStack: Boolean get() = stack.isNotEmpty() && !isStackOpen
    }
}

fun buildEntries(items: List<MediaItem>, openStacks: Set<Long> = emptySet(), isStacking: Boolean = false, view: SettingsView = Settings.view, folder: String? = null): List<GridEntry> {
    val entries = ArrayList<GridEntry>(items.size + 24)
    val groups = Settings.activeDateGroupsFor(view, folder)
    val isWeeks = DateGroup.WEEKS in groups
    val isDays = DateGroup.DAYS in groups
    var currentYear: Int? = null
    var currentMonth: YearMonth? = null
    var currentDay: LocalDate? = null
    // The open week's header is put in when its first photo comes and labelled when the week closes.
    var weekHeaderAt = -1
    var weekFirst: LocalDate? = null
    fun closeWeek() {
        val first = weekFirst ?: return
        entries[weekHeaderAt] = GridEntry.Header(calendarWeekLabel(first), "week-${YearMonth.from(first)}-${calendarWeekOf(first)}", DateGroup.WEEKS)
        weekFirst = null
    }
    val stackAt = arrayOfNulls<List<MediaItem>>(items.size)
    if (isStacking) SimilarShots.runsOf(items).forEach { run -> val members = items.subList(run.first, run.last + 1); run.forEach { stackAt[it] = members } }
    items.forEachIndexed { index, item ->
        val stack = stackAt[index]
        val isOpen = stack != null && stack.last().id in openStacks
        // A folded stack shows only its cover.
        if (stack != null && !isOpen && item !== stack.last()) return@forEachIndexed
        val day = dayOf(item.timestampMillis)
        val month = YearMonth.from(day)
        // A week is cut where a larger group starts, so a header never stands inside a week.
        if (day.year != currentYear) {
            if (DateGroup.YEARS in groups) {
                closeWeek()
                entries += GridEntry.Header(day.year.toString(), "year-${day.year}", DateGroup.YEARS)
            }
            currentYear = day.year
        }
        if (month != currentMonth) {
            if (DateGroup.MONTHS in groups) {
                closeWeek()
                entries += GridEntry.Header(MONTH_FORMAT.format(month), "month-$month", DateGroup.MONTHS)
            }
            currentMonth = month
        }
        if (isWeeks) {
            if (weekFirst == null || calendarWeekOf(day) != calendarWeekOf(weekFirst!!)) {
                closeWeek()
                weekHeaderAt = entries.size
                entries += GridEntry.Header("", "", DateGroup.WEEKS)
                weekFirst = day
            }
        }
        if (isDays && day != currentDay) entries += GridEntry.Header(DAY_FORMAT.format(day).uppercase(Locale.ENGLISH), "day-$day", DateGroup.DAYS)
        entries += GridEntry.Photo(item, index, stack.orEmpty(), isOpen, stampDay = if (!isWeeks && !isDays && day != currentDay) day else null)
        currentDay = day
    }
    closeWeek()
    return entries
}

// Oldest at the top, newest at the bottom right, opened at the bottom — the Apple order. Items arrive already sorted ascending.
@Composable
fun MediaGrid(
    items: List<MediaItem>,
    memory: GridMemory,
    onOpen: (Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    selection: Selection? = null,
    scrollToNewestRequest: Int = 0,
    emptyCaption: String = "Nothing here yet.",
    badge: ((MediaItem) -> String?)? = null,
    // A photo that is already where it is being added: darker, with a check over it.
    isMarked: (MediaItem) -> Boolean = { false },
) {
    val state = memory.state
    HoldUnderSheet(state)
    // Narrowed to favourites by the corner toggle; a tap still opens the photo by its place among all of them.
    val isFavoritesOnly = LocalFavoritesOnly.current
    val shownItems = remember(items, isFavoritesOnly) { if (isFavoritesOnly) items.filter { it.isFavorite } else items }
    val entries = remember(shownItems, Settings.activeDateGroupsFor(memory.view, memory.folder), Settings.stackSimilarIn(memory.view), SimilarShots.hashes, memory.openStacks) { memory.entriesOf(shownItems) }
    val columns = memory.columns

    LaunchedEffect(entries.size) {
        if (entries.isEmpty()) return@LaunchedEffect
        val lastVisible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val wasAtNewest = lastVisible >= memory.knownCount - MAX_COLUMNS
        if (!memory.isPositioned || (entries.size > memory.knownCount && wasAtNewest)) {
            state.scrollToItem(entries.lastIndex)
            memory.isPositioned = true
        }
        memory.knownCount = entries.size
    }

    // Tapping the section the bar already shows takes you home, which here is the newest end.
    // Only a tap made while this grid is shown scrolls it; coming back to it keeps where it was left.
    val requestOnArrival = remember { scrollToNewestRequest }
    LaunchedEffect(scrollToNewestRequest) {
        if (scrollToNewestRequest != requestOnArrival && entries.isNotEmpty()) state.glideToEnd()
    }

    if (shownItems.isEmpty()) {
        Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            BasicText(emptyCaption, style = Type.caption.copy(color = Palette.textFaint))
        }
        return
    }

    val tileSize = thumbnailPixels(columns, GAP)
    // Sharp pictures are only fetched while the grid stands still, so a fling only ever decodes the small cached thumbnails.
    val isSettled by remember(state) { derivedStateOf { !state.isScrollInProgress } }
    // What a tile stands for when selected: a folded stack is all its shots at once.
    val photosById = remember(entries) { entries.filterIsInstance<GridEntry.Photo>().associate { it.item.id to if (it.isFoldedStack) it.stack else listOf(it.item) } }
    val context = LocalContext.current
    val currentSelection by rememberUpdatedState(selection)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // How fast the grid is moving, in pixels per second: a hold on a fast fling only stops it, but one on a grid that merely drifts still selects.
    val scrollSpeed = remember(state) { floatArrayOf(0f) }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collectLatest { isMoving ->
            if (!isMoving) {
                scrollSpeed[0] = 0f
                return@collectLatest
            }
            var lastKey: Any? = null
            var lastOffset = 0
            var lastNanos = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val first = state.layoutInfo.visibleItemsInfo.firstOrNull()
                    // Measured on the same row from one frame to the next; when the first row changes, the last speed stands.
                    if (first != null && first.key == lastKey && nanos > lastNanos) {
                        scrollSpeed[0] = abs(first.offset.y - lastOffset) * 1_000_000_000f / (nanos - lastNanos)
                    }
                    lastKey = first?.key
                    lastOffset = first?.offset?.y ?: 0
                    lastNanos = nanos
                }
            }
        }
    }
    val timelineGrab = remember { TimelineGrab() }
    val aboveGrid = remember { TimelineAboveGrid() }
    Box(modifier.fillMaxSize().onGloballyPositioned { aboveGrid.bounds = it.boundsInWindow() }.timelineGrab(timelineGrab)) {
    ProvideEntrance {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalArrangement = Arrangement.spacedBy(GAP),
        modifier = Modifier.fillMaxSize().pinchColumns(memory, haptic).dragSelect(state, photosById, { currentSelection }, scope, { scrollSpeed[0] }),
    ) {
        items(
            entries,
            key = { entry -> if (entry is GridEntry.Photo) entry.item.id else (entry as GridEntry.Header).key },
            span = { entry -> if (entry is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            contentType = { entry -> if (entry is GridEntry.Header) "header" else "tile" },
        ) { entry ->
            when (entry) {
                is GridEntry.Header -> when {
                    entry.group == DateGroup.WEEKS -> WeekHeader(entry.label)
                    entry.group == DateGroup.DAYS -> DayHeader(entry.label)
                    entry.group == DateGroup.YEARS -> YearHeader(entry.label)
                    else -> MonthHeader(entry.label, isRoomy = DateGroup.WEEKS in Settings.dateGroupsFor(memory.view, memory.folder))
                }
                is GridEntry.Photo -> {
                    val item = entry.item
                    Tile(
                        modifier = Modifier.entrance(),
                        item = item,
                        sizePixels = tileSize,
                        isSettled = isSettled,
                        isSelected = selection != null && item.id in selection.selectedIds,
                        badge = badge?.invoke(item),
                        isMarked = isMarked(item),
                        stackSize = if (entry.isFoldedStack) entry.stack.size else 0,
                        // From four columns a tile is too small to carry a date over the picture.
                        stampDay = entry.stampDay.takeIf { Settings.dayStamps && Settings.headersFor(memory.view, memory.folder) && columns <= MAX_STAMP_COLUMNS },
                        stackPlace = if (entry.isStackOpen) "${entry.stack.indexOf(item) + 1}/${entry.stack.size}" else null,
                        onCloseStack = { memory.openStacks = memory.openStacks - entry.stack.last().id },
                        onClick = {
                            when {
                                selection != null && selection.isActive -> selection.toggleAll(photosById[item.id] ?: listOf(item))
                                entry.isFoldedStack -> {
                                    memory.openStacks = memory.openStacks + item.id
                                    Haptics.tick(context)
                                }
                                else -> onOpen(if (shownItems === items) entry.index else items.indexOf(shownItems[entry.index]))
                            }
                        },
                    )
                }
            }
        }
    }
    }
    val viewerGrowth = LocalViewerGrowth.current
    val above = LocalTimelineAbove.current
    if (above != null) {
        DisposableEffect(above) {
            above.grids.add(aboveGrid)
            onDispose { above.grids.remove(aboveGrid) }
        }
        // The copy above only shows; the grid's own one keeps the finger.
        val aboveGrab = remember { TimelineGrab() }
        SideEffect {
            aboveGrid.content = { GridTimeline(entries, state, contentPadding, aboveGrab, Modifier.fillMaxSize().graphicsLayer { translationX = viewerGrowth() * TIMELINE_LEAVE.toPx() }) }
        }
    }
    GridTimeline(
        entries, state, contentPadding, timelineGrab,
        Modifier.align(Alignment.TopEnd).graphicsLayer {
            translationX = viewerGrowth() * TIMELINE_LEAVE.toPx()
            alpha = if (above != null && above.isShownAbove(aboveGrid)) 0f else 1f
        },
    )
    }
}

// Brings a photo's tile on screen, so the viewer has somewhere to shrink back to when it was paged away from where it opened.
suspend fun GridMemory.revealItem(items: List<MediaItem>, mediaId: Long) {
    val index = entriesOf(items).indexOfFirst { it is GridEntry.Photo && (it.item.id == mediaId || (it.isFoldedStack && it.stack.any { shot -> shot.id == mediaId })) }
    if (index < 0 || state.layoutInfo.visibleItemsInfo.any { it.index == index }) return
    state.scrollToItem((index - columns * 2).coerceAtLeast(0))
}

// In the week layout months stand further apart, so the weeks inside one read as belonging together.
@Composable
private fun MonthHeader(label: String, isRoomy: Boolean) {
    // Dates sit at the left, across the grid from the timeline, in the section colour so they read as the grid's markers.
    BasicText(label, style = Type.cardTitle.copy(fontSize = 16.sp, color = LocalAccent.current), modifier = Modifier.padding(start = 4.dp, top = if (isRoomy) 34.dp else 18.dp, bottom = 8.dp))
}

@Composable
private fun DayHeader(label: String) {
    BasicText(label, style = Type.microLabel.copy(fontSize = 10.sp, color = Palette.textMuted), maxLines = 1, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 3.dp))
}

@Composable
private fun YearHeader(label: String) {
    BasicText(label, style = Type.cardTitle.copy(fontSize = 24.sp, color = LocalAccent.current), modifier = Modifier.padding(start = 4.dp, top = 30.dp, bottom = 6.dp))
}

@Composable
private fun WeekHeader(label: String) {
    BasicText(label, style = Type.microLabel.copy(fontSize = 11.sp, color = LocalAccent.current), maxLines = 1, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp))
}

// Two fingers change the column count and nothing else; one finger is left alone so the grid still scrolls. Watching on the initial pass lets the pinch claim its events before the list can start scrolling.
private fun Modifier.pinchColumns(memory: GridMemory, haptic: HapticFeedback): Modifier = pointerInput(memory) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zoom = 1f
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                zoom *= event.calculateZoom()
                val before = memory.columns
                if (zoom > PINCH_STEP) {
                    memory.columns = (memory.columns - 1).coerceAtLeast(MIN_COLUMNS)
                    zoom = 1f
                } else if (zoom < 1f / PINCH_STEP) {
                    memory.columns = (memory.columns + 1).coerceAtMost(MAX_COLUMNS)
                    zoom = 1f
                }
                if (memory.columns != before) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

// Hold a tile to start selecting, then slide across the others to select (or, if the first was already selected, deselect) them. It watches on the initial pass so the held finger never scrolls the list or taps a tile; near the top or bottom edge the grid scrolls on its own.
private fun Modifier.dragSelect(
    state: LazyGridState,
    photosById: Map<Long, List<MediaItem>>,
    selection: () -> Selection?,
    scope: CoroutineScope,
    scrollSpeed: () -> Float,
): Modifier = pointerInput(state, photosById) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // Taken by the timeline, or landing on a grid that is really moving: that finger is stopping a scroll, not selecting. A slight drift does not count.
        if (down.isConsumed || scrollSpeed() > Motion.SELECT_FAST_SCROLL_PX_PER_S) return@awaitEachGesture
        val holdMs = if (selection()?.isActive == true) Motion.SELECT_HOLD_ACTIVE_MS else viewConfiguration.longPressTimeoutMillis + Motion.SELECT_HOLD_EXTRA_MS
        val isHeld = withTimeoutOrNull(holdMs) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break
                val isSingleFinger = event.changes.count { it.pressed } == 1
                if (!isSingleFinger || !change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
            }
            true
        } == null
        val chosen = selection()
        if (!isHeld || chosen == null) return@awaitEachGesture

        // Item offsets are measured from the end of the top content padding, so the finger is moved into that frame first.
        fun itemAt(position: Offset): List<MediaItem>? {
            val y = position.y + state.layoutInfo.viewportStartOffset
            return state.layoutInfo.visibleItemsInfo
                .firstOrNull { info ->
                    position.x >= info.offset.x && position.x < info.offset.x + info.size.width &&
                        y >= info.offset.y && y < info.offset.y + info.size.height
                }
                ?.let { info -> (info.key as? Long)?.let { photosById[it] } }
        }

        val first = itemAt(down.position) ?: return@awaitEachGesture
        val isSelecting = first.first().id !in chosen.selectedIds
        val visited = HashSet<Long>()
        fun reach(position: Offset) {
            val group = itemAt(position) ?: return
            if (!visited.add(group.last().id)) return
            val current = selection() ?: return
            // The toggle itself ticks, once per photo the finger reaches; a folded stack brings all its shots along.
            group.filter { (it.id in current.selectedIds) != isSelecting }.forEach { current.onToggle(it) }
        }
        reach(down.position)
        down.consume()

        var lastPosition = down.position
        val edge = 90.dp.toPx()
        val autoScroll = scope.launch {
            while (true) {
                val direction = when {
                    lastPosition.y < edge -> -1f
                    lastPosition.y > size.height - edge -> 1f
                    else -> 0f
                }
                if (direction != 0f) {
                    state.scrollBy(direction * AUTO_SCROLL_STEP)
                    reach(lastPosition)
                }
                delay(16)
            }
        }
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull()?.let { change ->
                    lastPosition = change.position
                    reach(change.position)
                }
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        } finally {
            autoScroll.cancel()
        }
    }
}

// The month of the top visible row, for the chip that floats over the grid.
@Composable
fun rememberVisibleMonth(items: List<MediaItem>, memory: GridMemory): State<VisibleMonth> {
    val entries = remember(items, Settings.activeDateGroupsFor(memory.view, memory.folder), Settings.stackSimilarIn(memory.view), SimilarShots.hashes, memory.openStacks) { memory.entriesOf(items) }
    val countByMonth = remember(items) { items.groupingBy { YearMonth.from(Instant.ofEpochMilli(it.timestampMillis).atZone(ZoneId.systemDefault())) }.eachCount() }
    return remember(entries, memory, countByMonth) {
        derivedStateOf {
            // A header at the top belongs to the photos under it.
            val photo = (memory.state.firstVisibleItemIndex until entries.size).firstNotNullOfOrNull { entries[it] as? GridEntry.Photo }
            photo?.let {
                val date = Instant.ofEpochMilli(it.item.timestampMillis).atZone(ZoneId.systemDefault())
                VisibleMonth(MONTH_FORMAT.format(date), countByMonth[YearMonth.from(date)] ?: 0)
            } ?: VisibleMonth("", 0)
        }
    }
}

// The month at the top of a grid and how many of the grid's photos were taken in it.
data class VisibleMonth(val label: String, val count: Int)

// What a grid needs to know about multi-select: which tiles are in it, and how to toggle one. A long press (see `dragSelect`) starts a selection; once one is active, a tap toggles too.
class Selection(
    val selectedIds: Set<Long>,
    private val isAlwaysActive: Boolean = false,
    val onToggle: (MediaItem) -> Unit,
) {
    val isActive: Boolean get() = isAlwaysActive || selectedIds.isNotEmpty()

    // A group goes in or out as one, following its first shot.
    fun toggleAll(group: List<MediaItem>) {
        val isAdding = group.firstOrNull()?.id !in selectedIds
        group.filter { (it.id in selectedIds) != isAdding }.forEach(onToggle)
    }
}

@Composable
private fun Tile(
    item: MediaItem,
    sizePixels: Int,
    isSettled: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
    stampDay: LocalDate? = null,
    isMarked: Boolean = false,
    stackSize: Int = 0,
    stackPlace: String? = null,
    onCloseStack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = remember(item.uri, sizePixels) {
        // Private photos live outside MediaStore and have no cached thumbnail, so they are decoded from the file, sampled down.
        val data: Any = if (item.uri.scheme == "content") Thumbnail(item.uri, sizePixels) else item.uri
        // A file without an extension gives the loader no type, so a video says so itself.
        ImageRequest.Builder(context).data(data).size(sizePixels).apply { if (item.isVideo && item.uri.scheme != "content") decoderFactory(VideoFrameDecoder.Factory()) }.build()
    }
    // The system's cached thumbnail is small, and for some photos stale: pixelated, or turned the wrong way. So every tile gets the photo itself on top (decoded sampled and upright) once it has stood on screen a moment; scrolling shows the thumbnails. Tiles off screen are not composed at all, so nothing far away is ever decoded.
    // A video's own decode picks another frame than its system thumbnail, so the tile would change picture once it settles; it keeps the thumbnail.
    val needsSharp = item.uri.scheme == "content" && !item.isVideo
    var isSharpWanted by remember(item.id, sizePixels) { mutableStateOf(needsSharp && isSettled) }
    LaunchedEffect(needsSharp, isSettled) {
        if (needsSharp && isSettled && !isSharpWanted) {
            delay(SHARP_DELAY_MS)
            isSharpWanted = true
        }
    }
    val sharpRequest = if (isSharpWanted) {
        remember(item.uri, sizePixels) {
            ImageRequest.Builder(context).data(item.uri).size(sizePixels).apply { if (item.isVideo) decoderFactory(VideoFrameDecoder.Factory()) }.build()
        }
    } else {
        null
    }
    // Whether the photo carries a clip is read off the file once it has stood on screen, then remembered for the session.
    var isMotion by remember(item.id) { mutableStateOf(MotionPhoto.knownFor(item) == true) }
    LaunchedEffect(item.id, isSettled) {
        if (isSettled && !item.isVideo && MotionPhoto.knownFor(item) == null) isMotion = MotionPhoto.isMotion(context, item)
    }
    val selectedScale by animateFloatAsState(if (isSelected) 0.86f else 1f, tween(Motion.STATE_MS, easing = Motion.backOut), label = "selected")
    DisposableEffect(item.id) { onDispose { TileBounds.forget(item.id) } }
    Box(
        modifier
            .aspectRatio(1f)
            .onGloballyPositioned { TileBounds.register(item.id, it) }
            .pressable(onClick = onClick, pressedScale = 0.94f)
            .graphicsLayer { scaleX = selectedScale; scaleY = selectedScale }
            .clip(Shapes.tile)
            .background(Palette.sunken),
    ) {
        AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (sharpRequest != null) AsyncImage(model = sharpRequest, contentDescription = null, contentScale = ContentScale.Crop, onSuccess = { TileImages.register(item.id, it.result.memoryCacheKey) }, modifier = Modifier.fillMaxSize())
        // Top left, away from the timeline: the day this photo opens, on a backdrop so it reads over any picture.
        if (stampDay != null) {
            BasicText(
                dayStamp(stampDay),
                style = Type.value.copy(color = Palette.textBright, fontSize = 9.sp),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(Shapes.capsule)
                    .background(Palette.panel)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (badge != null) {
            BasicText(
                badge,
                style = Type.value.copy(color = Palette.textBright),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .clip(Shapes.capsule)
                    .background(Palette.panel)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        // Bottom right: the mark of a motion photo, the heart of a favourite, then a video's length beside it.
        if (item.isFavorite || item.isVideo || isMotion) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isMotion) {
                    Box(Modifier.clip(Shapes.capsule).background(Palette.panel).padding(horizontal = 3.dp, vertical = 2.dp)) {
                        MotionIcon(Palette.textBright, size = TILE_HEART + 2.dp)
                    }
                }
                if (item.isFavorite) {
                    Box(Modifier.clip(Shapes.capsule).background(Palette.panel).padding(horizontal = 4.dp, vertical = 3.dp)) {
                        HeartIcon(isFilled = true, color = Palette.favorite, size = TILE_HEART)
                    }
                }
                if (item.isVideo) {
                    BasicText(
                        if (item.durationMillis > 0) formatDuration(item.durationMillis) else "VIDEO",
                        style = Type.value.copy(color = Palette.textBright),
                        modifier = Modifier
                            .clip(Shapes.capsule)
                            .background(Palette.panel)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
        // Top right: a folded stack's count, or an opened stack's place in it, which folds it back when tapped.
        if (stackSize > 0) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(5.dp).clip(Shapes.capsule).background(Palette.panel).padding(horizontal = 5.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReviewIcon(Palette.textBright, size = TILE_HEART)
                BasicText("$stackSize", style = Type.value.copy(color = Palette.textBright))
            }
        } else if (stackPlace != null) {
            BasicText(
                stackPlace,
                style = Type.value.copy(color = LocalAccent.current),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .pressable(onClick = onCloseStack)
                    .padding(5.dp)
                    .clip(Shapes.capsule)
                    .background(Palette.panel)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        if (isMarked) {
            Box(Modifier.fillMaxSize().background(MARKED_SHADE), contentAlignment = Alignment.Center) {
                Box(Modifier.clip(Shapes.capsule).background(Palette.panel).padding(5.dp)) { CheckIcon(Palette.textBright, size = 16.dp) }
            }
        }
        if (isSelected) Box(Modifier.fillMaxSize().border(3.dp, LocalAccent.current, Shapes.tile))
    }
}

private val TILE_HEART = 11.dp
private const val SHARP_DELAY_MS = 120L
private const val MAX_STAMP_COLUMNS = 3
private val MARKED_SHADE = androidx.compose.ui.graphics.Color(0x99000000)

@Composable
private fun thumbnailPixels(columns: Int, gap: Dp): Int {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    return with(density) { ((configuration.screenWidthDp.dp - gap * (columns - 1)) / columns).roundToPx() }
}

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

// Far enough right that the strip and its labels are off the screen.
private val TIMELINE_LEAVE = 96.dp

// One continuous glide from where the grid stands to its end. The distance left is only known once the end is on screen, so it is guessed from the rows shown and guessed again every frame; the eased progress is applied to the latest guess, so the motion stays smooth and lands exactly.
// Scrolled row by row, a frame that moves far would compose and load every tile it passes; so a frame moving more than half a screen skips straight to where it lands, which at that speed looks the same.
private suspend fun LazyGridState.glideToEnd() {
    val viewport = layoutInfo.viewportSize.height.coerceAtLeast(1)
    val first = remainingToEnd()
    if (first <= 0f) return
    val screens = first / viewport
    val duration = (Motion.SCROLL_TO_END_MS + Motion.SCROLL_TO_END_PER_SCREEN_MS * screens).coerceAtMost(Motion.SCROLL_TO_END_MAX_MS.toFloat())
    scroll {
        val items = LazyLayoutScrollScope(this@glideToEnd, this)
        var scrolled = 0f
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val progress = ((now - start) / 1_000_000f / duration).coerceIn(0f, 1f)
            val total = scrolled + remainingToEnd()
            // Never back up when a guess comes in shorter; the glide only ever moves toward the end.
            val step = (Motion.powerThreeInOut.transform(progress) * total - scrolled).coerceAtLeast(0f)
            val skipped = if (step > viewport * SKIP_FROM_SCREENS) (step / itemHeight()).toInt() else 0
            if (skipped > 0) {
                with(items) { snapToItem(firstVisibleItemIndex + skipped, firstVisibleItemScrollOffset) }
                scrolled += skipped * itemHeight()
            } else {
                scrolled += scrollBy(step)
            }
            if (progress >= 1f) {
                // Whatever the last guess missed, now that the end is on screen.
                while (scrollBy(viewport.toFloat()) > 0f) Unit
                break
            }
        }
    }
}

// How far the grid still is from its end: exact once the last item shows, otherwise the rows left at the height the shown ones average.
private fun LazyGridState.remainingToEnd(): Float {
    val info = layoutInfo
    val shown = info.visibleItemsInfo
    if (shown.isEmpty()) return 0f
    val lastShown = shown.maxBy { it.offset.y + it.size.height }
    val lastIndex = shown.maxOf { it.index }
    val bottom = (lastShown.offset.y + lastShown.size.height).toFloat()
    val itemsLeft = info.totalItemsCount - 1 - lastIndex
    return (bottom + info.afterContentPadding - info.viewportEndOffset + itemsLeft * itemHeight()).coerceAtLeast(0f)
}

// The height each item adds on average, from the ones on screen: a row's height shared by the tiles in it.
private fun LazyGridState.itemHeight(): Float {
    val shown = layoutInfo.visibleItemsInfo
    if (shown.isEmpty()) return 1f
    val firstShown = shown.first()
    val lastShown = shown.maxBy { it.offset.y + it.size.height }
    val bottom = (lastShown.offset.y + lastShown.size.height).toFloat()
    return ((bottom - firstShown.offset.y) / (shown.maxOf { it.index } - firstShown.index + 1).coerceAtLeast(1)).coerceAtLeast(1f)
}

private const val SKIP_FROM_SCREENS = 0.5f
