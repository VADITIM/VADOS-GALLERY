package com.vaditim.gallery.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
// The strip a finger takes hold of, along the right edge; narrow so tiles under it stay tappable.
private val STRIP_WIDTH = 24.dp
private val LABEL_HEIGHT = 18.dp
private val THUMB_HEIGHT = 22.dp
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
private fun labelsOf(years: List<Int>, monthsByYear: Map<Int, List<TimelineMark>>, heldYear: Int?): List<TimelineLabel> =
    years.flatMap { year ->
        listOf(TimelineLabel(year, null)) + if (year == heldYear) monthsByYear[year].orEmpty().map { TimelineLabel(year, it) } else emptyList()
    }

// Half the screen tall and centred, oldest at the top like the grid: the years at rest, evenly apart. Held, the year under the finger opens into its months and everything respaces evenly; the grid follows the finger month by month.
@Composable
fun GridTimeline(entries: List<GridEntry>, state: LazyGridState, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val marks = remember(entries) { marksOf(entries) }
    val photoCount = remember(entries) { entries.count { it is GridEntry.Photo } }
    if (photoCount < MIN_PHOTOS || marks.isEmpty()) return
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
    LaunchedEffect(state) { snapshotFlow { target.intValue }.collect { if (it >= 0) state.scrollToItem(it) } }
    // The month the grid is showing, for the marker at rest.
    val viewMark by remember(state, marks) {
        derivedStateOf {
            val visible = state.layoutInfo.visibleItemsInfo
            val middle = if (visible.isEmpty()) 0 else (visible.first().index + visible.last().index) / 2
            marks.lastOrNull { it.index <= middle } ?: marks.first()
        }
    }
    val labels = labelsOf(years, monthsByYear, heldYear)

    BoxWithConstraints(modifier.fillMaxHeight()) {
        val height = constraints.maxHeight.toFloat()
        val track = height * TRACK_SHARE
        val top = (height - track) / 2f
        val density = LocalDensity.current
        val labelHalf = with(density) { LABEL_HEIGHT.toPx() } / 2f
        val thumbHalf = with(density) { THUMB_HEIGHT.toPx() } / 2f
        fun slotY(slot: Int, count: Int): Float = if (count <= 1) track / 2f else track * slot / (count - 1)
        fun yOf(label: TimelineLabel, within: List<TimelineLabel>): Float = top + slotY(within.indexOf(label), within.size)

        labels.forEach { label ->
            key(label) {
                val y by animateFloatAsState(yOf(label, labels), tween(Motion.TIMELINE_REVEAL_MS, easing = Motion.powerTwoOut), label = "timeline-label")
                val isMonth = label.month != null
                val isCurrent = isHeld && if (isMonth) label.month == heldMark else heldYear == label.year
                BasicText(
                    if (isMonth) MONTH_FORMAT.format(label.month!!.month).uppercase(Locale.ENGLISH) else label.year.toString(),
                    style = Type.value.copy(
                        fontSize = if (isMonth) 10.sp else 11.sp,
                        color = if (isCurrent) accent else if (isMonth) Palette.textMuted else Palette.textBright,
                    ),
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, (y - labelHalf).roundToInt()) }
                        .padding(end = STRIP_WIDTH - 4.dp)
                        .graphicsLayer { alpha = if (isMonth) reveal else 1f }
                        .clip(Shapes.capsule)
                        .background(Palette.panel)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        // Where the grid is now: on its year at rest, on the finger while held.
        val restY = labels.indexOfFirst { it.year == viewMark.month.year && it.month == null }.let { top + slotY(it.coerceAtLeast(0), labels.size) }
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

        // Only the timeline's own half of the edge takes a finger; above and below it the grid scrolls as usual.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, top.roundToInt()) }
                .size(width = STRIP_WIDTH, height = with(density) { track.toDp() })
                .pointerInput(marks, track) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        // A year opening or closing respaces the labels under a still finger, so after a switch the finger has to travel a little before another one counts.
                        var switchedAt = Float.NaN
                        fun follow(y: Float) {
                            val clamped = y.coerceIn(0f, track)
                            fingerY = top + clamped
                            val shown = labelsOf(years, monthsByYear, heldYear)
                            val slot = if (shown.size <= 1) 0 else (clamped / track * (shown.size - 1)).roundToInt()
                            val label = shown[slot]
                            val canSwitch = switchedAt.isNaN() || abs(clamped - switchedAt) > track / (shown.size.coerceAtLeast(2) - 1)
                            val mark = when {
                                label.month != null -> label.month
                                label.year == heldYear -> heldMark ?: monthsByYear[label.year]?.first()
                                heldYear == null || canSwitch -> {
                                    val isGoingDown = heldYear?.let { label.year > it } ?: false
                                    heldYear = label.year
                                    switchedAt = clamped
                                    monthsByYear[label.year]?.let { if (isGoingDown) it.first() else it.last() }
                                }
                                else -> heldMark
                            } ?: return
                            if (mark != heldMark) {
                                if (heldMark != null) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                heldMark = mark
                                target.intValue = mark.index
                            }
                        }
                        follow(down.position.y)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            follow(change.position.y)
                            change.consume()
                        } while (change.pressed)
                        heldMark = null
                        heldYear = null
                        fingerY = null
                        target.intValue = -1
                    }
                },
        )
    }
}
