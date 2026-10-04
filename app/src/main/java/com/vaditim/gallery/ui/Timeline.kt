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
// The strip a finger takes hold of, along the right edge; narrow so tiles under it stay tappable.
private val STRIP_WIDTH = 24.dp
private val LABEL_HEIGHT = 18.dp
// Labels closer than this would overlap, so the later one is left out.
private val YEAR_GAP = 22.dp
private val MONTH_GAP = 18.dp
private val THUMB_HEIGHT = 30.dp
private val YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy", Locale.ENGLISH)
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val BUBBLE_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

// Where a month starts in the grid: the index of its header when there is one, so a jump lands with the header in view.
private data class TimelineMark(val index: Int, val month: YearMonth, val isYear: Boolean)

private fun marksOf(entries: List<GridEntry>): List<TimelineMark> {
    val marks = ArrayList<TimelineMark>()
    var last: YearMonth? = null
    entries.forEachIndexed { index, entry ->
        if (entry !is GridEntry.Photo) return@forEachIndexed
        val month = YearMonth.from(Instant.ofEpochMilli(entry.item.timestampMillis).atZone(ZoneId.systemDefault()))
        if (month != last) {
            val start = if (index > 0 && entries[index - 1] is GridEntry.Header) index - 1 else index
            marks += TimelineMark(start, month, last == null || last.year != month.year)
            last = month
        }
    }
    return marks
}

// Years first, each kept only if it clears the one above; then, while held, every month that clears all of those.
private fun visibleMarks(marks: List<TimelineMark>, lastIndex: Int, track: Float, yearGap: Float, monthGap: Float?): List<TimelineMark> {
    val kept = ArrayList<Float>()
    val shown = ArrayList<TimelineMark>()
    var lastYear = -Float.MAX_VALUE
    marks.filter { it.isYear }.forEach { mark ->
        val y = mark.index.toFloat() / lastIndex * track
        if (y - lastYear >= yearGap) {
            shown += mark
            kept += y
            lastYear = y
        }
    }
    if (monthGap != null) {
        marks.filter { !it.isYear }.forEach { mark ->
            val y = mark.index.toFloat() / lastIndex * track
            if (kept.none { abs(it - y) < monthGap }) {
                shown += mark
                kept += y
            }
        }
    }
    return shown
}

private fun monthAt(marks: List<TimelineMark>, index: Int): YearMonth? = marks.lastOrNull { it.index <= index }?.month ?: marks.firstOrNull()?.month

// Top to bottom along the right edge, oldest to newest like the grid: the years at rest, and the months too while a finger slides on it, the grid following the finger.
@Composable
fun GridTimeline(entries: List<GridEntry>, state: LazyGridState, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val marks = remember(entries) { marksOf(entries) }
    val photoCount = remember(entries) { entries.count { it is GridEntry.Photo } }
    if (photoCount < MIN_PHOTOS || marks.isEmpty()) return
    val lastIndex = entries.lastIndex.coerceAtLeast(1)
    val haptic = LocalHapticFeedback.current
    val accent = LocalAccent.current
    var heldFraction by remember { mutableStateOf<Float?>(null) }
    var heldMonth by remember { mutableStateOf<YearMonth?>(null) }
    val isHeld = heldFraction != null
    val reveal by animateFloatAsState(if (isHeld) 1f else 0f, tween(Motion.TIMELINE_REVEAL_MS, easing = Motion.powerTwoOut), label = "timeline")
    // The newest index asked for; scrolls are conflated, so a fast slide never queues up jumps.
    val target = remember { mutableIntStateOf(-1) }
    LaunchedEffect(state) { snapshotFlow { target.intValue }.collect { if (it >= 0) state.scrollToItem(it) } }
    val viewFraction by remember(state, lastIndex) {
        derivedStateOf {
            val visible = state.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) 0f else ((visible.first().index + visible.last().index) / 2f / lastIndex).coerceIn(0f, 1f)
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxHeight()
            .padding(top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 8.dp),
    ) {
        val track = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val labelHalf = with(density) { LABEL_HEIGHT.toPx() } / 2f
        val thumbHalf = with(density) { THUMB_HEIGHT.toPx() } / 2f
        val yearGap = with(density) { YEAR_GAP.toPx() }
        val monthGap = with(density) { MONTH_GAP.toPx() }
        val shownYears = remember(marks, track) { visibleMarks(marks, lastIndex, track, yearGap, null) }
        val shownAll = remember(marks, track) { visibleMarks(marks, lastIndex, track, yearGap, monthGap) }
        val position = (heldFraction ?: viewFraction) * track

        (if (reveal > 0f) shownAll else shownYears).forEach { mark ->
            val y = mark.index.toFloat() / lastIndex * track
            val isCurrent = isHeld && heldMonth?.let { if (mark.isYear) it.year == mark.month.year else it == mark.month } == true
            BasicText(
                if (mark.isYear) YEAR_FORMAT.format(mark.month) else MONTH_FORMAT.format(mark.month).uppercase(Locale.ENGLISH),
                style = Type.value.copy(
                    fontSize = if (mark.isYear) 11.sp else 10.sp,
                    color = if (isCurrent) accent else if (mark.isYear) Palette.textBright else Palette.textMuted,
                ),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, (y - labelHalf).roundToInt()) }
                    .padding(end = STRIP_WIDTH - 4.dp)
                    .graphicsLayer { alpha = if (mark.isYear) 1f else reveal }
                    .clip(Shapes.capsule)
                    .background(Palette.panel)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        // Where the grid is now, or where the finger is.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (position - thumbHalf).roundToInt().coerceIn(0, (track - 2 * thumbHalf).roundToInt().coerceAtLeast(0))) }
                .padding(end = 6.dp)
                .size(width = 4.dp, height = THUMB_HEIGHT)
                .clip(Shapes.capsule)
                .background(if (isHeld) accent else Palette.textMuted),
        )

        heldMonth?.let { month ->
            BasicText(
                BUBBLE_FORMAT.format(month).uppercase(Locale.ENGLISH),
                style = Type.microLabel.copy(color = Palette.textBright),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, (position - labelHalf * 1.6f).roundToInt()) }
                    .padding(end = 76.dp)
                    .graphicsLayer { alpha = reveal }
                    .glass(Shapes.capsule)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(STRIP_WIDTH)
                .pointerInput(marks, lastIndex, track) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        fun follow(y: Float) {
                            val fraction = (y / track).coerceIn(0f, 1f)
                            val index = (fraction * lastIndex).roundToInt()
                            val month = monthAt(marks, index)
                            if (month != heldMonth) {
                                if (heldMonth != null) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                heldMonth = month
                            }
                            heldFraction = fraction
                            target.intValue = index
                        }
                        follow(down.position.y)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            follow(change.position.y)
                            change.consume()
                        } while (change.pressed)
                        heldFraction = null
                        heldMonth = null
                        target.intValue = -1
                    }
                },
        )
    }
}
