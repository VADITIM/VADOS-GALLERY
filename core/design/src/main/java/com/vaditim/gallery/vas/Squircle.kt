package com.vaditim.gallery.vas

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

private const val EXPONENT = 5.0
private const val STEPS_PER_CORNER = 12

// A superellipse corner reaches further along the edge than a circular one of the same radius before it bends, so the corner is drawn over this multiple of the radius to sit at roughly the same visual depth.
private const val EXTENT_PER_RADIUS = 1.8f

// VAS law 4: corners are squircles. One shape for every box; text is never clipped by it.
class SquircleShape(private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val extent = min(with(density) { radius.toPx() } * EXTENT_PER_RADIUS, min(size.width, size.height) / 2f)
        if (extent <= 0f) return Outline.Rectangle(Rect(Offset.Zero, size))
        val path = Path()
        addCorner(path, centerX = extent, centerY = extent, extent = extent, startAngle = PI, isFirst = true)
        addCorner(path, centerX = size.width - extent, centerY = extent, extent = extent, startAngle = 1.5 * PI, isFirst = false)
        addCorner(path, centerX = size.width - extent, centerY = size.height - extent, extent = extent, startAngle = 0.0, isFirst = false)
        addCorner(path, centerX = extent, centerY = size.height - extent, extent = extent, startAngle = 0.5 * PI, isFirst = false)
        path.close()
        return Outline.Generic(path)
    }

    private fun addCorner(path: Path, centerX: Float, centerY: Float, extent: Float, startAngle: Double, isFirst: Boolean) {
        for (step in 0..STEPS_PER_CORNER) {
            val angle = startAngle + (PI / 2.0) * step / STEPS_PER_CORNER
            val x = centerX + extent * superellipse(cos(angle))
            val y = centerY + extent * superellipse(sin(angle))
            if (isFirst && step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }

    private fun superellipse(value: Double): Float = (sign(value) * abs(value).pow(2.0 / EXPONENT)).toFloat()
}

// Squircles for the few large shapes on screen; plain rounded rects where there are hundreds, because a rounded rect clips on the GPU outline path for free while a generic path is clipped per cell, per frame.
object Shapes {
    val sheet = SquircleShape(30.dp)
    val panel = SquircleShape(22.dp)
    val cover = SquircleShape(20.dp)
    // A card holding a group of settings (components/03-panel-and-field.md §2).
    val field = SquircleShape(18.dp)
    val tile = RoundedCornerShape(8.dp)
    // The open photo's rounding, which the crop screen runs down to square corners as the photo travels into it.
    val viewerPhotoCorner = 24.dp
    val viewerPhoto = RoundedCornerShape(viewerPhotoCorner)
    val capsule = RoundedCornerShape(percent = 50)
}
