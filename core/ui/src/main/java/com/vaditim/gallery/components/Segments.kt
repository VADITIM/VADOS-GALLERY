package com.vaditim.gallery.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

val SEGMENT_HEIGHT = 38.dp
val SEGMENT_INSET = 3.dp

// One-of-many as a sunken track with every option as a stop; the accent pill under the chosen one follows the finger across them and settles on the nearest when let go, which is when the choice is made.
@Composable
fun Segments(options: List<String>, chosen: Int, isEnabled: Boolean = true, height: Dp = SEGMENT_HEIGHT, style: TextStyle = Type.navigation, onChoose: (Int) -> Unit) {
    val count = options.size
    val accent = LocalAccent.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentChosen by rememberUpdatedState(chosen)
    val currentOnChoose by rememberUpdatedState(onChoose)
    val position = remember { Animatable(chosen.toFloat()) }
    var isHeld by remember { mutableStateOf(false) }
    // A choice made elsewhere (a pinch on the grid) moves the pill too.
    LaunchedEffect(chosen) {
        if (!isHeld) position.animateTo(chosen.toFloat(), tween(Motion.STATE_MS, easing = Motion.backOut))
    }
    val nearest = position.value.roundToInt().coerceIn(0, count - 1)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(Shapes.capsule)
            .background(Palette.sunkenDeep)
            .border(1.dp, Palette.borderStrong, Shapes.capsule)
            .then(
                if (!isEnabled) Modifier else Modifier.pointerInput(count) {
                    val inset = SEGMENT_INSET.toPx()
                    val stopWidth = (size.width - inset * 2f) / count
                    fun stopAt(x: Float) = ((x - inset) / stopWidth - 0.5f).coerceIn(0f, (count - 1).toFloat())
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isHeld = true
                        var lastStop = position.value.roundToInt()
                        scope.launch { position.animateTo(stopAt(down.position.x), tween(Motion.PRESS_MS, easing = Motion.powerTwoOut)) }
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull() ?: break
                            if (change.position != change.previousPosition) scope.launch { position.snapTo(stopAt(change.position.x)) }
                            val stop = stopAt(change.position.x).roundToInt()
                            if (stop != lastStop) {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                lastStop = stop
                            }
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        val picked = lastStop
                        scope.launch {
                            position.animateTo(picked.toFloat(), tween(Motion.STATE_MS, easing = Motion.backOut))
                            isHeld = false
                        }
                        if (picked != currentChosen) currentOnChoose(picked)
                    }
                },
            )
            .drawBehind {
                val inset = SEGMENT_INSET.toPx()
                val width = (size.width - inset * 2f) / count
                drawRoundRect(accent, Offset(inset + width * position.value, inset), Size(width, size.height - inset * 2f), CornerRadius((size.height - inset * 2f) / 2f))
            }
            .padding(horizontal = SEGMENT_INSET),
    ) {
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, option ->
                val ink by animateColorAsState(if (index == nearest) Palette.sunkenDeep else Palette.textMuted, tween(Motion.PRESS_MS), label = "segment-ink")
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    BasicText(option, style = style.copy(color = ink), maxLines = 1)
                }
            }
        }
    }
}
