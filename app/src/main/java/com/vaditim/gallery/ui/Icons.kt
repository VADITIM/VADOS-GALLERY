package com.vaditim.gallery.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.pressable

// Drawn rather than taken from an icon pack: four glyphs do not justify a library, and drawing keeps them in the app's line weight. Each is laid out on a 24-unit grid and scaled to the box.

@Composable
fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.pressable(onClick = onClick).clip(Shapes.capsule).size(width = 56.dp, height = 48.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { content() }
}

@Composable
fun PlayPauseIcon(isPlaying: Boolean, color: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        if (isPlaying) {
            drawRoundRect(color, Offset(6f * unit, 4f * unit), Size(4f * unit, 16f * unit), CornerRadius(1.5f * unit))
            drawRoundRect(color, Offset(14f * unit, 4f * unit), Size(4f * unit, 16f * unit), CornerRadius(1.5f * unit))
        } else {
            val triangle = Path().apply {
                moveTo(7f * unit, 4f * unit)
                lineTo(19f * unit, 12f * unit)
                lineTo(7f * unit, 20f * unit)
                close()
            }
            drawPath(triangle, color)
            drawPath(triangle, color, style = Stroke(2f * unit, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun HeartIcon(isFilled: Boolean, color: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val heart = Path().apply {
            moveTo(12f * unit, 20.5f * unit)
            cubicTo(4f * unit, 14.5f * unit, 2.5f * unit, 10.5f * unit, 2.5f * unit, 8f * unit)
            cubicTo(2.5f * unit, 5.2f * unit, 4.6f * unit, 3.5f * unit, 7f * unit, 3.5f * unit)
            cubicTo(9f * unit, 3.5f * unit, 11f * unit, 4.6f * unit, 12f * unit, 6.5f * unit)
            cubicTo(13f * unit, 4.6f * unit, 15f * unit, 3.5f * unit, 17f * unit, 3.5f * unit)
            cubicTo(19.4f * unit, 3.5f * unit, 21.5f * unit, 5.2f * unit, 21.5f * unit, 8f * unit)
            cubicTo(21.5f * unit, 10.5f * unit, 20f * unit, 14.5f * unit, 12f * unit, 20.5f * unit)
            close()
        }
        if (isFilled) drawPath(heart, color) else drawPath(heart, color, style = Stroke(2f * unit, join = StrokeJoin.Round))
    }
}

// Two arrows chasing each other round a rounded rectangle: the repeat glyph.
@Composable
fun LoopIcon(color: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val stroke = Stroke(2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val upper = Path().apply {
            moveTo(5f * unit, 11f * unit)
            lineTo(5f * unit, 10f * unit)
            quadraticTo(5f * unit, 7f * unit, 8f * unit, 7f * unit)
            lineTo(19f * unit, 7f * unit)
            moveTo(16f * unit, 4f * unit)
            lineTo(19f * unit, 7f * unit)
            lineTo(16f * unit, 10f * unit)
        }
        val lower = Path().apply {
            moveTo(19f * unit, 13f * unit)
            lineTo(19f * unit, 14f * unit)
            quadraticTo(19f * unit, 17f * unit, 16f * unit, 17f * unit)
            lineTo(5f * unit, 17f * unit)
            moveTo(8f * unit, 14f * unit)
            lineTo(5f * unit, 17f * unit)
            lineTo(8f * unit, 20f * unit)
        }
        drawPath(upper, color, style = stroke)
        drawPath(lower, color, style = stroke)
    }
}
