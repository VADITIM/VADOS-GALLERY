package com.vaditim.gallery.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import com.vaditim.gallery.vas.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val JIGGLE_DEGREES = 1.4f
const val HELD_SCALE = 1.05f
// A card held over something it can go into shrinks, as if about to drop in.
const val DROPPING_SCALE = 0.8f

// Dragging a cover past another swaps their places. Every cover grid that can be arranged (albums, private groups, Favorites albums) uses this one, so it feels the same in all of them.
@Stable
class Reorder(private val state: LazyGridState, private val haptic: HapticFeedback, private val scope: CoroutineScope) {
    // Refreshed on every composition by rememberReorder, so a swap always reads the list as it is now.
    var keys: List<Any> = emptyList()
    var onMove: (from: Int, to: Int) -> Unit = { _, _ -> }
    // Albums never go above groups, so a held cover only trades places with its own kind.
    var canSwap: (held: Any, target: Any) -> Boolean = { _, _ -> true }
    // Whether the held cover goes into the one under it (an album into a group) instead of trading places.
    var canDropInto: (held: Any, target: Any) -> Boolean = { _, _ -> false }
    var onDropInto: (held: Any, target: Any) -> Unit = { _, _ -> }
    // How far below its own row an item reaches while it draws over the rows under it (a peeked group), so a cover hanging over that part is still over it.
    var reachBelow: (key: Any) -> Float = { 0f }
    var isRearranging: () -> Boolean = { false }
    // Null where a drag cannot turn rearranging on (while picking covers).
    var onStartRearranging: (() -> Unit)? = null

    var draggedKey by mutableStateOf<Any?>(null)
        private set
    var dropTargetKey by mutableStateOf<Any?>(null)
        private set
    // The open group one of whose albums is being dragged; that album is drawn by its group, which then has to lie above the other rows.
    var groupHoldingKey by mutableStateOf<Any?>(null)
        private set
    // Where the held card's top-left corner belongs in the grid, following the finger; it is drawn there whatever slot the grid has given it, so a swap with a taller row never throws it off.
    private var heldTopLeft by mutableStateOf(Offset.Zero)
    // After a swap the grid has not laid out yet; until it has, no further swap is looked for.
    private var staleLayout: LazyGridLayoutInfo? = null

    internal fun start(key: Any) {
        val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        // A card caught while still gliding in is picked up where it is drawn.
        val gliding = if (key == settlingKey) settleOffset else Offset.Zero
        settling?.cancel()
        settlingKey = null
        heldTopLeft = Offset(info.offset.x.toFloat(), info.offset.y.toFloat()) + gliding
        staleLayout = null
        draggedKey = key
    }

    internal fun end() {
        val held = draggedKey
        val target = dropTargetKey
        // Let go, the card glides the rest of the way into its slot from where the finger left it.
        if (held != null && target == null) settle(held, translation())
        draggedKey = null
        dropTargetKey = null
        if (held != null && target != null) onDropInto(held, target)
    }

    // The card let go last, and how far it still is from its slot while it glides in.
    var settlingKey by mutableStateOf<Any?>(null)
        private set
    private var settleOffset by mutableStateOf(Offset.Zero)
    private var settling: Job? = null

    private fun settle(key: Any, from: Offset) {
        settling?.cancel()
        settlingKey = key
        settleOffset = from
        settling = scope.launch {
            animate(0f, 1f, animationSpec = tween(Motion.RELEASE_MS, easing = Motion.powerTwoOut)) { progress, _ -> settleOffset = from * (1f - progress) }
            settlingKey = null
        }
    }

    internal fun translation(): Offset {
        val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedKey } ?: return Offset.Zero
        return heldTopLeft - Offset(info.offset.x.toFloat(), info.offset.y.toFloat())
    }

    internal fun offsetOf(key: Any): Offset = when (key) {
        draggedKey -> translation()
        settlingKey -> settleOffset
        else -> Offset.Zero
    }

    internal fun drag(amount: Offset) {
        if (draggedKey == null) return
        heldTopLeft += amount
        findTarget()
    }

    private fun findTarget() {
        val layout = state.layoutInfo
        if (staleLayout === layout) return
        staleLayout = null
        val heldKey = draggedKey ?: return
        val visible = layout.visibleItemsInfo
        val held = visible.firstOrNull { it.key == heldKey } ?: return
        val centre = heldTopLeft + Offset(held.size.width / 2f, held.size.height / 2f)
        val into = visible.firstOrNull { it.key != heldKey && canDropInto(heldKey, it.key) && it.holds(centre, reachBelow(it.key)) }?.key
        aimAt(into)
        if (into != null) return
        val from = keys.indexOf(heldKey)
        if (from < 0) return
        val target = visible.firstOrNull { info ->
            if (info.key == heldKey || info.key !in keys || !canSwap(heldKey, info.key)) return@firstOrNull false
            if (centre.x < info.offset.x || centre.x >= info.offset.x + info.size.width) return@firstOrNull false
            val top = info.offset.y.toFloat()
            val bottom = top + info.size.height
            when {
                // In the same row the cards are alike, so crossing into one is enough.
                info.offset.y == held.offset.y -> centre.y >= top && centre.y < bottom
                // Into another row the held card has to come as far as where it will sit once swapped, so a swap with a taller row never swaps straight back.
                keys.indexOf(info.key) > from -> centre.y >= maxOf(top, bottom - held.size.height / 2f) && centre.y < bottom
                else -> centre.y >= top && centre.y < minOf(bottom, top + held.size.height / 2f)
            }
        } ?: return
        val to = keys.indexOf(target.key)
        if (to < 0) return
        onMove(from, to)
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        staleLayout = layout
    }

    private fun aimAt(target: Any?) {
        if (target == dropTargetKey) return
        dropTargetKey = target
        if (target != null) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    // Where an item's top-left corner lies in the grid, for a group to place what it draws in the grid's terms.
    fun topLeftOf(key: Any): Offset? = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { Offset(it.offset.x.toFloat(), it.offset.y.toFloat()) }

    fun startFromGroup(group: Any) {
        groupHoldingKey = group
    }

    // An album dragged inside an open group is drawn by the group, not the grid, so the group reports where its centre is in the grid. Off its own group it is on its way out; over another group it can go in. Returns whether it is off its own group.
    fun hoverFromGroup(group: Any, album: Any, centre: Offset): Boolean {
        val visible = state.layoutInfo.visibleItemsInfo
        val isOutside = visible.firstOrNull { it.key == group }?.holds(centre) != true
        aimAt(if (isOutside) visible.firstOrNull { it.key != group && canDropInto(album, it.key) && it.holds(centre, reachBelow(it.key)) }?.key else null)
        return isOutside
    }

    // The group it was let go over, if any; the hold is over either way.
    fun endFromGroup(): Any? {
        val target = dropTargetKey
        dropTargetKey = null
        groupHoldingKey = null
        return target
    }
}

private fun LazyGridItemInfo.holds(point: Offset, below: Float = 0f): Boolean =
    point.x >= offset.x && point.x < offset.x + size.width && point.y >= offset.y && point.y < offset.y + size.height + below

@Composable
fun rememberReorder(state: LazyGridState, keys: List<Any>, onMove: (from: Int, to: Int) -> Unit): Reorder {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val reorder = remember(state) { Reorder(state, haptic, scope) }
    reorder.keys = keys
    reorder.onMove = onMove
    return reorder
}

// The modifier chain keeps one shape whether or not rearranging is on or this cover is the one held: swapping an element out recreates the pointer input below it and cancels the drag that just started (a drag can turn rearranging on mid-gesture). It goes on the grid item; the card inside it takes `jiggle`.
// `startArea` limits where a drag may begin before rearranging is on, in the item's own pixels. `lift` raises the item above the rows around it while it draws over them.
fun LazyGridItemScope.reorderable(reorder: Reorder, key: Any, isEnabled: Boolean, placement: FiniteAnimationSpec<IntOffset>? = spring(), startArea: (Offset, IntSize) -> Boolean = { _, _ -> true }, lift: Float = 0f): Modifier {
    if (!isEnabled) return Modifier.animateItem(placementSpec = placement).zIndex(lift)
    val isDragged = key == reorder.draggedKey || key == reorder.settlingKey
    return Modifier
        .animateItem(placementSpec = if (isDragged) null else if (reorder.isRearranging()) spring() else placement)
        .zIndex(if (isDragged) maxOf(1f, lift) else lift)
        .graphicsLayer {
            val moved = reorder.offsetOf(key)
            translationX = moved.x
            translationY = moved.y
        }
        // On the card itself, so it claims the drag before the grid can scroll with it.
        .dragToArrange(
            key = key,
            isRearranging = { reorder.isRearranging() },
            canStart = { at, size -> reorder.onStartRearranging != null && startArea(at, size) },
            onStart = {
                if (!reorder.isRearranging()) reorder.onStartRearranging?.invoke()
                reorder.start(key)
            },
            onDrag = reorder::drag,
            onEnd = reorder::end,
        )
}

// One gesture for every cover that can be arranged. While rearranging, a drag moves the cover at once. Otherwise a finger that rests a moment and then moves turns rearranging on and carries the cover from there, before or after the long press (whose menu then closes); one that moves straight away is a scroll or a swipe and is left alone.
fun Modifier.dragToArrange(
    key: Any,
    isRearranging: () -> Boolean,
    canStart: (at: Offset, size: IntSize) -> Boolean,
    onStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: () -> Unit,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val wasRearranging = isRearranging()
        if (!wasRearranging) {
            if (!canStart(down.position, size)) return@awaitEachGesture
            if (withTimeoutOrNull(Motion.DRAG_REST_MS) { awaitLeave(down) } != null) return@awaitEachGesture
        }
        var overSlop = Offset.Zero
        val slop = awaitTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            overSlop = over
        } ?: return@awaitEachGesture
        onStart()
        onDrag(overSlop)
        drag(slop.id) { change ->
            onDrag(change.positionChange())
            change.consume()
        }
        onEnd()
    }
}

// Returns once the finger lifts or wanders off where it went down; a rest is waited out by the caller's timeout.
private suspend fun AwaitPointerEventScope.awaitLeave(down: PointerInputChange): Boolean {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return true
        if (!change.pressed || change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) return true
    }
}

// On the card, not the grid item, so a card in a wider cell still swings about its own centre (or `pivot`, measured from its top-left corner, for a card drawn inside a wider box). Each cover waiting to be moved jiggles a little, out of step with its neighbours so the grid shivers rather than sways; the one held is lifted instead, or shrinks while it hangs over something it can go into.
@Composable
fun Modifier.jiggle(key: Any, isEnabled: Boolean, pivot: Dp? = null, isHeld: () -> Boolean = { false }, heldScale: () -> Float = { HELD_SCALE }): Modifier {
    if (!isEnabled) return this
    val seed = key.hashCode()
    val swing by rememberInfiniteTransition(label = "jiggle").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(Motion.JIGGLE_MS + Math.floorMod(seed, 3) * 10, easing = Motion.powerThreeInOut),
            RepeatMode.Reverse,
            StartOffset(Math.floorMod(seed, Motion.JIGGLE_MS)),
        ),
        label = "jiggle",
    )
    return graphicsLayer {
        if (pivot != null && size.width > 0f && size.height > 0f) transformOrigin = TransformOrigin(pivot.toPx() / size.width, pivot.toPx() / size.height)
        if (isHeld()) {
            val scale = heldScale()
            scaleX = scale
            scaleY = scale
        } else {
            rotationZ = JIGGLE_DEGREES * swing
        }
    }
}

@Composable
fun Modifier.jiggle(reorder: Reorder, key: Any, isEnabled: Boolean): Modifier {
    val heldScale by animateFloatAsState(if (reorder.dropTargetKey != null) DROPPING_SCALE else HELD_SCALE, tween(Motion.STATE_MS, easing = Motion.backOut), label = "held")
    return jiggle(key, isEnabled, isHeld = { key == reorder.draggedKey }, heldScale = { heldScale })
}
