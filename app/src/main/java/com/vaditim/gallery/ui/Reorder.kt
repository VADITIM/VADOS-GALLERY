package com.vaditim.gallery.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import com.vaditim.gallery.vas.Motion
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex

private const val JIGGLE_DEGREES = 1.4f

// Dragging a cover onto another swaps their places. Every cover grid that can be arranged (albums, private groups) uses this one, so it feels the same in both.
@Stable
class Reorder(private val state: LazyGridState, private val haptic: HapticFeedback) {
    // Refreshed on every composition by rememberReorder, so a swap always reads the list as it is now.
    var keys: List<Any> = emptyList()
    var onMove: (from: Int, to: Int) -> Unit = { _, _ -> }

    var draggedKey by mutableStateOf<Any?>(null)
        private set
    private var dragOffset by mutableStateOf(Offset.Zero)
    // After a swap the grid has not laid out yet; until the dragged card shows up at its new slot, no further swap is looked for.
    private var awaitedOffset: IntOffset? = null

    internal fun start(key: Any) {
        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        draggedKey = key
        dragOffset = Offset.Zero
        awaitedOffset = null
    }

    internal fun end() {
        draggedKey = null
        dragOffset = Offset.Zero
    }

    internal fun translation(): Offset = dragOffset

    internal fun drag(amount: Offset) {
        dragOffset += amount
        findSwap()
    }

    private fun findSwap() {
        val visible = state.layoutInfo.visibleItemsInfo
        val dragged = visible.firstOrNull { it.key == draggedKey } ?: return
        if (awaitedOffset != null && dragged.offset != awaitedOffset) return
        awaitedOffset = null
        val centre = Offset(dragged.offset.x + dragged.size.width / 2f, dragged.offset.y + dragged.size.height / 2f) + dragOffset
        val target = visible.firstOrNull { info ->
            info.key in keys && info.key != draggedKey &&
                centre.x >= info.offset.x && centre.x < info.offset.x + info.size.width &&
                centre.y >= info.offset.y && centre.y < info.offset.y + info.size.height
        } ?: return
        val from = keys.indexOf(draggedKey)
        val to = keys.indexOf(target.key)
        if (from < 0 || to < 0) return
        onMove(from, to)
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        // The card takes the target's slot, so the offset is rebased onto that slot to stay under the finger.
        dragOffset += Offset((dragged.offset.x - target.offset.x).toFloat(), (dragged.offset.y - target.offset.y).toFloat())
        awaitedOffset = target.offset
    }
}

@androidx.compose.runtime.Composable
fun rememberReorder(state: LazyGridState, keys: List<Any>, onMove: (from: Int, to: Int) -> Unit): Reorder {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val reorder = androidx.compose.runtime.remember(state) { Reorder(state, haptic) }
    reorder.keys = keys
    reorder.onMove = onMove
    return reorder
}

// The modifier chain keeps one shape whether or not this cover is the one held: swapping an element out recreates the pointer input below it and cancels the drag that just started. It goes on the grid item; the card inside it takes `jiggle`.
fun LazyGridItemScope.reorderable(reorder: Reorder, key: Any, isEnabled: Boolean): Modifier {
    if (!isEnabled) return Modifier
    val isDragged = key == reorder.draggedKey
    return Modifier
        .animateItem(placementSpec = if (isDragged) null else spring<IntOffset>())
        .zIndex(if (isDragged) 1f else 0f)
        .graphicsLayer {
            if (isDragged) {
                translationX = reorder.translation().x
                translationY = reorder.translation().y
            }
        }
        // On the card itself, so it claims the drag before the grid can scroll with it.
        .pointerInput(key) {
            detectDragGestures(
                onDragStart = { reorder.start(key) },
                onDragEnd = { reorder.end() },
                onDragCancel = { reorder.end() },
                onDrag = { change, amount ->
                    change.consume()
                    reorder.drag(amount)
                },
            )
        }
}

// On the card, not the grid item, so a card in a wider cell still swings about its own centre (or `pivot`, measured from its top-left corner, for a card drawn inside a wider box). Each cover waiting to be moved jiggles a little, out of step with its neighbours so the grid shivers rather than sways; the one held is lifted instead.
@Composable
fun Modifier.jiggle(key: Any, isEnabled: Boolean, pivot: Dp? = null, isHeld: () -> Boolean = { false }): Modifier {
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
            scaleX = 1.05f
            scaleY = 1.05f
        } else {
            rotationZ = JIGGLE_DEGREES * swing
        }
    }
}

@Composable
fun Modifier.jiggle(reorder: Reorder, key: Any, isEnabled: Boolean): Modifier = jiggle(key, isEnabled) { key == reorder.draggedKey }
