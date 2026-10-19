package com.vaditim.gallery.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private const val DEGREES = 360f
private val THUMB_RADIUS = 11.dp
private val THUMB_RING = 3.dp

// A full circle of hues, all 360 degrees around, from white at the centre to the pure colour at the rim. A finger anywhere on it, or dragged across it, picks the colour under it; `color` places the marker.
@Composable
fun ColorWheel(color: Color?, onPick: (Color) -> Unit, modifier: Modifier = Modifier, diameter: Dp = 208.dp) {
    val currentOnPick by rememberUpdatedState(onPick)
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV((color ?: Color.White).toArgb(), it) }
    Box(
        modifier
            .size(diameter)
            .pointerInput(Unit) {
                val radius = min(size.width, size.height) / 2f
                val centre = Offset(size.width / 2f, size.height / 2f)
                fun pick(at: Offset) {
                    val dx = at.x - centre.x
                    val dy = at.y - centre.y
                    val hue = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + DEGREES) % DEGREES
                    currentOnPick(Color.hsv(hue, (hypot(dx, dy) / radius).coerceIn(0f, 1f), 1f))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pick(down.position)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.filter { it.pressed }.forEach {
                            pick(it.position)
                            it.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        Canvas(Modifier.size(diameter)) {
            val radius = size.minDimension / 2f
            val centre = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Brush.sweepGradient(List(HUE_STOPS + 1) { Color.hsv(it * DEGREES / HUE_STOPS, 1f, 1f) }, centre), radius, centre)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), centre, radius), radius, centre)
            if (color != null) {
                val angle = Math.toRadians(hsv[0].toDouble())
                val at = Offset(centre.x + cos(angle).toFloat() * hsv[1] * radius, centre.y + sin(angle).toFloat() * hsv[1] * radius)
                drawCircle(color, THUMB_RADIUS.toPx(), at)
                drawCircle(Color.White, THUMB_RADIUS.toPx(), at, style = Stroke(THUMB_RING.toPx()))
            }
        }
    }
}

private const val HUE_STOPS = 12
