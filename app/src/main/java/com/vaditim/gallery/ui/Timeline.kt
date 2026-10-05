package com.vaditim.gallery.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.glass
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt

// Fewer photos than this scroll by hand quickly enough; the timeline would only cover tiles.
private const val MIN_PHOTOS = 90
// The window the timeline shows through is this share of the grid's height, centred on it.
private const val TRACK_SHARE = 0.36f
// The strip a finger takes hold of along the right edge, besides the labels themselves; narrow so tiles under it stay tappable.
private val STRIP_WIDTH = 24.dp
// Room around a label that still counts as taking hold of it.
private val LABEL_GRAB_SLACK = 6.dp
private val LABEL_HEIGHT = 18.dp
private val THUMB_HEIGHT = 22.dp
// The room a full-size label takes on the strip; smaller labels take a share of it, so the strip draws together away from the marker.
private val LABEL_STEP = 22.dp
// How far past the window's edge a label is still on its way in: it fades, shrinks and slides out to the side over this.
private val EDGE_FADE = 36.dp
private val EDGE_SLIDE = 18.dp
// Labels shrink, and draw closer together, by this rate for each step away from the marker and keep shrinking; the near ones read clearly and the far months fade to nothing.
private const val FALLOFF = 0.34f
// Years never shrink below this, so far from the marker the strip reads as the years alone.
private const val YEAR_SMALLEST = 0.8f
// A month this small is gone; it fades out over the share above it.
private const val MONTH_GONE = 0.14f
private const val MONTH_FADE = 0.2f
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val BUBBLE_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

// Where a month starts in the grid: the index of its first header when there is one, so a jump lands with the header in view.
private data class TimelineMark(val index: Int, val month: YearMonth)

private fun marksOf(entries: List<GridEntry>): List<TimelineMark> {
    val marks = ArrayList<TimelineMark>()
    var last: YearMonth? = null
    entries.forEachIndexed { index, entry ->
        if (entry !is GridEntry.Photo) return@forEachIndexed
        val month = YearMonth.from(Instant.ofEpochMilli(entry.item.timestampMillis).atZone(ZoneId.systemDefault()))
        if (month != last) {
            // A month header and a week header can both stand in front of the first photo.
            var start = index
            while (start > 0 && entries[start - 1] is GridEntry.Header) start--
            marks += TimelineMark(start, month)
            last = month
        }
    }
    return marks
}

// One step of the strip: a year, or a month under the year before it.
private data class TimelineLabel(val year: Int, val month: TimelineMark?) {
    fun sizeAt(distance: Float): Float = exp(-FALLOFF * abs(distance)).let { if (month == null) maxOf(it, YEAR_SMALLEST) else it }
}

private fun labelsOf(marks: List<TimelineMark>): List<TimelineLabel> =
    marks.groupBy { it.month.year }.toSortedMap().flatMap { (year, months) -> listOf(TimelineLabel(year, null)) + months.map { TimelineLabel(year, it) } }

// The timeline's hold, worked from the box holding the grid: a sibling over the grid would block every tile under it, but the parent sees each touch first and takes only those on the strip or on a label.
class TimelineGrab {
    var timeline: LayoutCoordinates? = null
    var holder: LayoutCoordinates? = null
    val labels = HashMap<Any, LayoutCoordinates>()
    // Finger height within the timeline's window.
    var follow: (Float) -> Unit = {}
    var release: () -> Unit = {}
    var isActive = false
    var stripWidth = 0f
    var labelSlack = 0f
    var trackTop = 0f
    var trackHeight = 0f

    fun takes(root: Offset): Boolean {
        val timeline = timeline?.takeIf { isActive && it.isAttached } ?: return false
        val local = timeline.windowToLocal(root)
        // Labels fading in beyond the window are on their way, not there to take.
        if (local.y < trackTop - labelSlack || local.y > trackTop + trackHeight + labelSlack) return false
        val isOnStrip = local.x >= timeline.size.width - stripWidth
        return isOnStrip || labels.values.any { it.isAttached && timeline.localBoundingBoxOf(it, clipBounds = false).inflate(labelSlack).contains(local) }
    }

    fun trackY(root: Offset): Float = (timeline?.windowToLocal(root)?.y ?: 0f) - trackTop
}

fun Modifier.timelineGrab(grab: TimelineGrab): Modifier =
    onGloballyPositioned { grab.holder = it }.pointerInput(grab) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val coordinates = grab.holder ?: return@awaitEachGesture
            if (!grab.takes(coordinates.localToWindow(down.position))) return@awaitEachGesture
            // Taken before the grid sees it, so the held finger neither scrolls the list, taps a tile nor starts a selection.
            down.consume()
            grab.follow(grab.trackY(coordinates.localToWindow(down.position)))
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                grab.follow(grab.trackY(coordinates.localToWindow(change.position)))
                change.consume()
            } while (change.pressed)
            grab.release()
        }
    }

// Every year with all its months, oldest at the top like the grid, laid on one strip seen through a window half the screen tall. The marker runs down the window as the grid scrolls, the strip slides the other way so the month shown sits on it, and labels come in over the window's edges. Held, the marker is the finger and the label under it is where the grid goes.
@Composable
fun GridTimeline(entries: List<GridEntry>, state: LazyGridState, contentPadding: PaddingValues, grab: TimelineGrab, modifier: Modifier = Modifier) {
    val marks = remember(entries) { marksOf(entries) }
    val photoCount = remember(entries) { entries.count { it is GridEntry.Photo } }
    val isShown = photoCount >= MIN_PHOTOS && marks.isNotEmpty()
    DisposableEffect(grab, isShown) {
        grab.isActive = isShown
        onDispose { grab.isActive = false }
    }
    if (!isShown) return
    val labels = remember(marks) { labelsOf(marks) }
    val slotOfMark = remember(labels) { labels.withIndex().filter { it.value.month != null }.associate { it.value.month!! to it.index } }
    val haptic = LocalHapticFeedback.current
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    var heldMark by remember { mutableStateOf<TimelineMark?>(null) }
    // Where the finger holds the strip, in steps; read only while held.
    var heldPosition by remember { mutableFloatStateOf(0f) }
    val isHeld = heldMark != null
    val reveal by animateFloatAsState(if (isHeld) 1f else 0f, tween(Motion.TIMELINE_REVEAL_MS, easing = Motion.powerTwoOut), label = "timeline")
    // Taking hold or letting go hands the strip between the finger and the grid; this carries the difference away so neither jumps.
    val handover = remember { Animatable(0f) }
    // The newest index asked for; scrolls are conflated, so a fast slide never queues up jumps.
    val target = remember { mutableIntStateOf(-1) }
    LaunchedEffect(state) {
        snapshotFlow { target.intValue }.collect { index ->
            if (index < 0) return@collect
            state.scrollToItem(index)
            // The month's first photo (or its header) belongs at the top, just under the header room, not merely somewhere on screen.
            state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.let { if (it.offset.y != 0) state.scrollBy(it.offset.y.toFloat()) }
        }
    }
    // Where the grid stands on the strip, in steps, between the month at its top and the next: a scrollbar's reading, so the very end of the grid reaches the newest month.
    val gridPosition by remember(state, entries, marks, slotOfMark) {
        derivedStateOf {
            val visible = state.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) return@derivedStateOf 0f
            val firstIndex = state.firstVisibleItemIndex
            val first = visible.firstOrNull { it.index == firstIndex } ?: visible.first()
            val rowCount = visible.count { it.offset.y == first.offset.y }.coerceAtLeast(1)
            val top = firstIndex + state.firstVisibleItemScrollOffset.toFloat() / first.size.height.coerceAtLeast(1) * rowCount
            val bottom = (visible.last().index + 1).toFloat()
            val screenful = (bottom - top).coerceAtLeast(0f)
            val reach = (top / (entries.size - screenful).coerceAtLeast(1f)).coerceIn(0f, 1f)
            val reading = top + screenful * reach
            val at = marks.indexOfLast { it.index <= reading }.coerceAtLeast(0)
            val mark = marks[at]
            val next = marks.getOrNull(at + 1)
            val start = slotOfMark[mark] ?: 0
            if (next == null) {
                start.toFloat()
            } else {
                val fraction = ((reading - mark.index) / (next.index - mark.index).coerceAtLeast(1)).coerceIn(0f, 1f)
                start + fraction * ((slotOfMark[next] ?: start) - start)
            }
        }
    }
    val position = { (if (heldMark != null) heldPosition else gridPosition) + handover.value }
    // The month shown: the one the finger chose while held, otherwise the one at the grid's top.
    val shownMark by remember(labels) {
        // Past a month the grid reads between it and the next; the year label on the way still belongs to the month before.
        derivedStateOf { heldMark ?: (floor(gridPosition + 0.001f).toInt() downTo 0).firstNotNullOfOrNull { labels.getOrNull(it)?.month } }
    }

    BoxWithConstraints(modifier.fillMaxHeight().onGloballyPositioned { grab.timeline = it }) {
        val height = constraints.maxHeight.toFloat()
        val track = height * TRACK_SHARE
        val top = (height - track) / 2f
        val density = LocalDensity.current
        val labelHalf = with(density) { LABEL_HEIGHT.toPx() } / 2f
        val thumbHalf = with(density) { THUMB_HEIGHT.toPx() } / 2f
        val step = with(density) { LABEL_STEP.toPx() }
        val fade = with(density) { EDGE_FADE.toPx() }
        val slide = with(density) { EDGE_SLIDE.toPx() }
        val last = (labels.size - 1).coerceAtLeast(1)
        // A short strip sits in the middle of the window and mostly the marker moves; a long one runs the marker down the window as the strip slides.
        val span = minOf(step * (labels.size - 1), track)
        val spanTop = top + (track - span) / 2f
        fun markerY(at: Float): Float = spanTop + span * (at / last)
        // Each label's height on screen: neighbours stand apart by the mean of their sizes, laid outward from the marker, which sits between the two labels it falls between.
        fun placesAt(at: Float): FloatArray {
            val places = FloatArray(labels.size)
            val anchor = floor(at).toInt().coerceIn(0, labels.lastIndex)
            fun gap(slot: Int): Float = step * (labels[slot].sizeAt(slot - at) + labels[slot + 1].sizeAt(slot + 1 - at)) / 2f
            places[anchor] = markerY(at) - if (anchor < labels.lastIndex) (at - anchor) * gap(anchor) else 0f
            for (slot in anchor + 1..labels.lastIndex) places[slot] = places[slot - 1] + gap(slot - 1)
            for (slot in anchor - 1 downTo 0) places[slot] = places[slot + 1] - gap(slot)
            return places
        }
        val places by remember(labels, top, track) { derivedStateOf { placesAt(position()) } }
        fun labelY(slot: Int): Float = places.getOrElse(slot) { 0f }

        // Only the labels in or near the window are composed; the range moves a step at a time, never per frame.
        val window by remember(labels, top, track) {
            derivedStateOf {
                val at = places
                var from = position().roundToInt().coerceIn(0, labels.lastIndex)
                var to = from
                while (from > 0 && at[from - 1] > top - fade) from--
                while (to < labels.lastIndex && at[to + 1] < top + track + fade) to++
                from..to
            }
        }
        window.forEach { slot ->
            val label = labels[slot]
            key(label) {
                DisposableEffect(label) { onDispose { grab.labels.remove(label) } }
                val isMonth = label.month != null
                val isCurrent = if (isMonth) label.month == shownMark else label.year == shownMark?.month?.year
                BasicText(
                    if (isMonth) MONTH_FORMAT.format(label.month!!.month).uppercase(Locale.ENGLISH) else label.year.toString(),
                    style = Type.value.copy(
                        fontSize = if (isMonth) 10.sp else 13.sp,
                        color = if (isCurrent) accent else if (isMonth) Palette.textMuted else Palette.textBright,
                    ),
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, (labelY(slot) - labelHalf).roundToInt()) }
                        .padding(end = STRIP_WIDTH - 4.dp)
                        // The label itself can be taken hold of, not only the strip beside it.
                        .onGloballyPositioned { grab.labels[label] = it }
                        .graphicsLayer {
                            val y = labelY(slot)
                            val size = label.sizeAt(slot - position())
                            // Inside the window a label is whole; past its edge it is pulled away to the side as it fades.
                            val inside = ((minOf(y - top, top + track - y) + fade) / fade).coerceIn(0f, 1f)
                            val presence = if (isMonth) ((size - MONTH_GONE) / MONTH_FADE).coerceIn(0f, 1f) else 1f
                            alpha = inside * presence
                            translationX = (1f - inside) * slide
                            // Shrinks toward the edge of the screen, so every label keeps its right side on the line.
                            transformOrigin = TransformOrigin(1f, 0.5f)
                            scaleX = size * (0.8f + 0.2f * inside)
                            scaleY = scaleX
                        }
                        .clip(Shapes.capsule)
                        .background(Palette.panel)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        // Where the grid is, running down the window with it; under the finger while held.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (markerY(position()) - thumbHalf).roundToInt()) }
                .padding(end = 6.dp)
                .size(width = 4.dp, height = THUMB_HEIGHT)
                .clip(Shapes.capsule)
                .background(if (isHeld) accent else Palette.textMuted),
        )

        heldMark?.let { mark ->
            BasicText(
                BUBBLE_FORMAT.format(mark.month).uppercase(Locale.ENGLISH),
                style = Type.microLabel.copy(color = Palette.textBright),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, (markerY(position()) - labelHalf * 1.6f).roundToInt()) }
                    .padding(end = 76.dp)
                    .graphicsLayer { alpha = reveal }
                    .glass(Shapes.capsule)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        // Hands the strip from one reading to the other, starting from where it was drawn.
        fun handOver(from: Float, to: Float) {
            // Started at once, so the frame that swaps the reading already carries the difference.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                handover.snapTo(from - to)
                handover.animateTo(0f, tween(Motion.TIMELINE_REVEAL_MS, easing = Motion.powerTwoOut))
            }
        }

        grab.stripWidth = with(density) { STRIP_WIDTH.toPx() }
        grab.labelSlack = with(density) { LABEL_GRAB_SLACK.toPx() }
        grab.trackTop = top
        grab.trackHeight = track
        grab.follow = follow@{ y ->
            val at = if (span <= 0f) 0f else ((top + y - spanTop) / span * last).coerceIn(0f, labels.lastIndex.toFloat())
            if (heldMark == null) handOver(position(), at)
            heldPosition = at
            val label = labels[at.roundToInt().coerceIn(0, labels.lastIndex)]
            // A year stands for its first month.
            val mark = label.month ?: labels.getOrNull(at.roundToInt() + 1)?.month ?: return@follow
            if (mark != heldMark) {
                if (heldMark != null) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                heldMark = mark
                target.intValue = mark.index
            }
        }
        grab.release = {
            if (heldMark != null) {
                val from = position()
                heldMark = null
                target.intValue = -1
                handOver(from, gridPosition)
            }
        }
    }
}
