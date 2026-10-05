package com.vaditim.gallery.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.DisposableEffect
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
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Fewer photos than this scroll by hand quickly enough; the timeline would only cover tiles.
private const val MIN_PHOTOS = 90
// The timeline is always this share of the grid's height, centred on it.
private const val TRACK_SHARE = 0.5f
// The strip a finger takes hold of along the right edge, besides the labels themselves; narrow so tiles under it stay tappable.
private val STRIP_WIDTH = 24.dp
// Room around a label that still counts as taking hold of it.
private val LABEL_GRAB_SLACK = 6.dp
private val LABEL_HEIGHT = 18.dp
private val THUMB_HEIGHT = 22.dp
// Labels are never further apart than this; with few of them the timeline is shorter, centred where it always is.
private val MAX_GAP = 24.dp
private val YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy", Locale.ENGLISH)
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val BUBBLE_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

// Where a month starts in the grid: the index of its header when there is one, so a jump lands with the header in view.
private data class TimelineMark(val index: Int, val month: YearMonth)

private fun marksOf(entries: List<GridEntry>): List<TimelineMark> {
    val marks = ArrayList<TimelineMark>()
    var last: YearMonth? = null
    entries.forEachIndexed { index, entry ->
        if (entry !is GridEntry.Photo) return@forEachIndexed
        val month = YearMonth.from(Instant.ofEpochMilli(entry.item.timestampMillis).atZone(ZoneId.systemDefault()))
        if (month != last) {
            val start = if (index > 0 && entries[index - 1] is GridEntry.Header) index - 1 else index
            marks += TimelineMark(start, month)
            last = month
        }
    }
    return marks
}

// One label on the timeline: a year, or one of the months of the year under the finger.
private data class TimelineLabel(val year: Int, val month: TimelineMark?)

// Every year, and right after the held one its months; all of them the same distance apart.
// At rest the year being shown carries just the month being shown beneath it.
private fun labelsOf(years: List<Int>, monthsByYear: Map<Int, List<TimelineMark>>, heldYear: Int?, restMark: TimelineMark? = null): List<TimelineLabel> =
    years.flatMap { year ->
        val months = when {
            year == heldYear -> monthsByYear[year].orEmpty()
            heldYear == null && year == restMark?.month?.year -> listOf(restMark)
            else -> emptyList()
        }
        listOf(TimelineLabel(year, null)) + months.map { TimelineLabel(year, it) }
    }

// The timeline's hold, worked from the box holding the grid: a sibling over the grid would block every tile under it, but the parent sees each touch first and takes only those on the strip or on a label.
class TimelineGrab {
    var timeline: LayoutCoordinates? = null
    var holder: LayoutCoordinates? = null
    val labels = HashMap<Any, LayoutCoordinates>()
    var begin: () -> Unit = {}
    // Finger height within the timeline's track.
    var follow: (Float) -> Unit = {}
    var release: () -> Unit = {}
    var isActive = false
    var stripWidth = 0f
    var labelSlack = 0f
    var trackTop = 0f
    var trackHeight = 0f

    fun takes(root: Offset): Boolean {
        val timeline = timeline?.takeIf { isActive && it.isAttached } ?: return false
        val local = timeline.rootToLocal(root)
        val isOnStrip = local.x >= timeline.size.width - stripWidth && local.y >= trackTop && local.y <= trackTop + trackHeight
        return isOnStrip || labels.values.any { it.isAttached && timeline.localBoundingBoxOf(it, clipBounds = false).inflate(labelSlack).contains(local) }
    }

    fun trackY(root: Offset): Float = (timeline?.rootToLocal(root)?.y ?: 0f) - trackTop
}

fun Modifier.timelineGrab(grab: TimelineGrab): Modifier =
    onGloballyPositioned { grab.holder = it }.pointerInput(grab) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val coordinates = grab.holder ?: return@awaitEachGesture
            if (!grab.takes(coordinates.localToRoot(down.position))) return@awaitEachGesture
            // Taken before the grid sees it, so the held finger neither scrolls the list, taps a tile nor starts a selection.
            down.consume()
            grab.begin()
            grab.follow(grab.trackY(coordinates.localToRoot(down.position)))
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                grab.follow(grab.trackY(coordinates.localToRoot(change.position)))
                change.consume()
            } while (change.pressed)
            grab.release()
        }
    }

// Half the screen tall and centred, oldest at the top like the grid: the years at rest, evenly apart. Held, the year under the finger opens into its months and everything respaces evenly; the grid follows the finger month by month.
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
    val monthsByYear = remember(marks) { marks.groupBy { it.month.year } }
    val years = remember(marks) { monthsByYear.keys.sorted() }
    val haptic = LocalHapticFeedback.current
    val accent = LocalAccent.current
    var heldYear by remember { mutableStateOf<Int?>(null) }
    var heldMark by remember { mutableStateOf<TimelineMark?>(null) }
    var fingerY by remember { mutableStateOf<Float?>(null) }
    val isHeld = heldMark != null
    val reveal by animateFloatAsState(if (isHeld) 1f else 0f, tween(Motion.TIMELINE_REVEAL_MS, easing = Motion.powerTwoOut), label = "timeline")
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
    // The month the grid is showing, for the marker at rest.
    val viewMark by remember(state, marks) {
        derivedStateOf {
            val visible = state.layoutInfo.visibleItemsInfo
            val middle = if (visible.isEmpty()) 0 else (visible.first().index + visible.last().index) / 2
            marks.lastOrNull { it.index <= middle } ?: marks.first()
        }
    }
    val labels = labelsOf(years, monthsByYear, heldYear, viewMark)

    BoxWithConstraints(modifier.fillMaxHeight().onGloballyPositioned { grab.timeline = it }) {
        val height = constraints.maxHeight.toFloat()
        val track = height * TRACK_SHARE
        val top = (height - track) / 2f
        val density = LocalDensity.current
        val labelHalf = with(density) { LABEL_HEIGHT.toPx() } / 2f
        val thumbHalf = with(density) { THUMB_HEIGHT.toPx() } / 2f
        val maxGap = with(density) { MAX_GAP.toPx() }
        fun gapFor(count: Int): Float = if (count <= 1) 0f else minOf(maxGap, track / (count - 1))
        // Evenly apart and centred on the track, so a few years sit close together in the middle rather than spread over half the screen.
        fun slotY(slot: Int, count: Int): Float = track / 2f + (slot - (count - 1) / 2f) * gapFor(count)
        fun yOf(label: TimelineLabel, within: List<TimelineLabel>): Float = top + slotY(within.indexOf(label), within.size)

        labels.forEach { label ->
            key(label) {
                DisposableEffect(label) { onDispose { grab.labels.remove(label) } }
                val y by animateFloatAsState(yOf(label, labels), tween(Motion.TIMELINE_REVEAL_MS, easing = Motion.powerTwoOut), label = "timeline-label")
                val isMonth = label.month != null
                val isCurrent = if (isHeld) (if (isMonth) label.month == heldMark else heldYear == label.year) else !isMonth && label.year == viewMark.month.year
                BasicText(
                    if (isMonth) MONTH_FORMAT.format(label.month!!.month).uppercase(Locale.ENGLISH) else label.year.toString(),
                    style = Type.value.copy(
                        fontSize = if (isMonth) 10.sp else 11.sp,
                        color = if (isCurrent) accent else if (isMonth && isHeld) Palette.textMuted else Palette.textBright,
                    ),
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, (y - labelHalf).roundToInt()) }
                        .padding(end = STRIP_WIDTH - 4.dp)
                        // The label itself can be taken hold of, not only the strip beside it; a month hidden at rest is not there to take.
                        .onGloballyPositioned { if (!isMonth || isHeld || label.month == viewMark) grab.labels[label] = it else grab.labels.remove(label) }
                        .graphicsLayer { alpha = if (isMonth && isHeld) reveal else 1f }
                        .clip(Shapes.capsule)
                        .background(Palette.panel)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        // Where the grid is now: on its year at rest, on the finger while held.
        val restY = labels.indexOfFirst { it.month == viewMark }.let { top + slotY(it.coerceAtLeast(0), labels.size) }
        val markerY by animateFloatAsState(fingerY ?: restY, tween(Motion.PRESS_MS), label = "timeline-marker")
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (markerY - thumbHalf).roundToInt()) }
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
                    .offset { IntOffset(0, (markerY - labelHalf * 1.6f).roundToInt()) }
                    .padding(end = 76.dp)
                    .graphicsLayer { alpha = reveal }
                    .glass(Shapes.capsule)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        // Only the timeline's own half of the edge and its labels take a finger; above and below it the grid scrolls as usual.
        // A year opening or closing respaces the labels under a still finger, so after a switch the finger has to travel a little before another one counts.
        val gesture = remember { object { var switchedAt = Float.NaN } }
        grab.stripWidth = with(density) { STRIP_WIDTH.toPx() }
        grab.labelSlack = with(density) { LABEL_GRAB_SLACK.toPx() }
        grab.trackTop = top
        grab.trackHeight = track
        grab.begin = { gesture.switchedAt = Float.NaN }
        grab.follow = follow@{ y ->
            val shown = labelsOf(years, monthsByYear, heldYear)
            val gap = gapFor(shown.size)
            // The finger can be anywhere on the half-screen strip; past the first or last label it stays on that label.
            val clamped = y.coerceIn(slotY(0, shown.size), slotY(shown.lastIndex, shown.size))
            fingerY = top + clamped
            val slot = if (shown.size <= 1) 0 else ((clamped - slotY(0, shown.size)) / gap).roundToInt().coerceIn(0, shown.lastIndex)
            val label = shown[slot]
            val canSwitch = gesture.switchedAt.isNaN() || abs(clamped - gesture.switchedAt) > gap
            val mark = when {
                label.month != null -> label.month
                label.year == heldYear -> heldMark ?: monthsByYear[label.year]?.first()
                heldYear == null || canSwitch -> {
                    val isGoingDown = heldYear?.let { label.year > it } ?: false
                    heldYear = label.year
                    gesture.switchedAt = clamped
                    monthsByYear[label.year]?.let { if (isGoingDown) it.first() else it.last() }
                }
                else -> heldMark
            } ?: return@follow
            if (mark != heldMark) {
                if (heldMark != null) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                heldMark = mark
                target.intValue = mark.index
            }
        }
        grab.release = {
            heldMark = null
            heldYear = null
            fingerY = null
            target.intValue = -1
        }
    }
}
