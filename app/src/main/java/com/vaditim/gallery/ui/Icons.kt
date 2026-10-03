package com.vaditim.gallery.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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

// Chevrons pointing the way the picture is moving: one for gentle speeds, up to three for fast ones.
@Composable
fun SpeedArrows(isReverse: Boolean, count: Int, color: Color, size: Dp = 14.dp) {
    Canvas(Modifier.size(width = size * count, height = size)) {
        val unit = this.size.height / 14f
        val stroke = Stroke(2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        for (index in 0 until count) {
            val left = index * 14f * unit
            val chevron = Path().apply {
                if (isReverse) {
                    moveTo(left + 9f * unit, 2f * unit)
                    lineTo(left + 4f * unit, 7f * unit)
                    lineTo(left + 9f * unit, 12f * unit)
                } else {
                    moveTo(left + 5f * unit, 2f * unit)
                    lineTo(left + 10f * unit, 7f * unit)
                    lineTo(left + 5f * unit, 12f * unit)
                }
            }
            drawPath(chevron, color, style = stroke)
        }
    }
}

// Two sliders: the settings glyph.
@Composable
fun SettingsIcon(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val stroke = Stroke(2f * unit, cap = StrokeCap.Round)
        drawLine(color, Offset(3f * unit, 8f * unit), Offset(21f * unit, 8f * unit), 2f * unit, StrokeCap.Round)
        drawLine(color, Offset(3f * unit, 16f * unit), Offset(21f * unit, 16f * unit), 2f * unit, StrokeCap.Round)
        drawCircle(Color.Black, 3.6f * unit, Offset(8f * unit, 8f * unit))
        drawCircle(color, 3.6f * unit, Offset(8f * unit, 8f * unit), style = stroke)
        drawCircle(Color.Black, 3.6f * unit, Offset(16f * unit, 16f * unit))
        drawCircle(color, 3.6f * unit, Offset(16f * unit, 16f * unit), style = stroke)
    }
}

// Line glyphs: one stroke weight for all, so a bar of them reads as one set.
@Composable
private fun LineGlyph(color: Color, size: Dp, draw: DrawScope.(unit: Float, stroke: Stroke) -> Unit) {
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        draw(unit, Stroke(1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun path(unit: Float, build: Path.(Float) -> Unit): Path = Path().apply { build(unit) }

@Composable
fun ShareIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u ->
        moveTo(12f * u, 3.5f * u); lineTo(12f * u, 14.5f * u)
        moveTo(8f * u, 7.5f * u); lineTo(12f * u, 3.5f * u); lineTo(16f * u, 7.5f * u)
        moveTo(8.5f * u, 10.5f * u); lineTo(5.5f * u, 10.5f * u); lineTo(5.5f * u, 20.5f * u); lineTo(18.5f * u, 20.5f * u); lineTo(18.5f * u, 10.5f * u); lineTo(15.5f * u, 10.5f * u)
    }, color, style = stroke)
}

@Composable
fun TrashIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u ->
        moveTo(4f * u, 6.5f * u); lineTo(20f * u, 6.5f * u)
        moveTo(9f * u, 6.5f * u); lineTo(9f * u, 4f * u); lineTo(15f * u, 4f * u); lineTo(15f * u, 6.5f * u)
        moveTo(6.2f * u, 6.5f * u); lineTo(7.2f * u, 20.5f * u); lineTo(16.8f * u, 20.5f * u); lineTo(17.8f * u, 6.5f * u)
        moveTo(10f * u, 10.5f * u); lineTo(10f * u, 16.5f * u)
        moveTo(14f * u, 10.5f * u); lineTo(14f * u, 16.5f * u)
    }, color, style = stroke)
}

@Composable
fun LockIcon(color: Color, isOpen: Boolean = false, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawRoundRect(color, Offset(5f * unit, 10.5f * unit), Size(14f * unit, 10f * unit), CornerRadius(2.5f * unit), style = stroke)
    drawPath(path(unit) { u ->
        moveTo(8f * u, 10.5f * u); lineTo(8f * u, 7.5f * u)
        arcTo(Rect(8f * u, 3.5f * u, 16f * u, 11.5f * u), 180f, if (isOpen) 150f else 180f, false)
        if (!isOpen) lineTo(16f * u, 10.5f * u)
    }, color, style = stroke)
    drawCircle(color, 1.3f * unit, Offset(12f * unit, 15.5f * unit))
}

@Composable
fun MoveIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u ->
        moveTo(3.5f * u, 6.5f * u); lineTo(9.5f * u, 6.5f * u); lineTo(11.5f * u, 8.5f * u); lineTo(20.5f * u, 8.5f * u); lineTo(20.5f * u, 19f * u); lineTo(3.5f * u, 19f * u); close()
        moveTo(8f * u, 13.75f * u); lineTo(15.5f * u, 13.75f * u)
        moveTo(12.75f * u, 11f * u); lineTo(15.5f * u, 13.75f * u); lineTo(12.75f * u, 16.5f * u)
    }, color, style = stroke)
}

@Composable
fun ImageIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawRoundRect(color, Offset(3.5f * unit, 5f * unit), Size(17f * unit, 14f * unit), CornerRadius(2.5f * unit), style = stroke)
    drawCircle(color, 1.6f * unit, Offset(9f * unit, 9.8f * unit), style = stroke)
    drawPath(path(unit) { u ->
        moveTo(4f * u, 17f * u); lineTo(9f * u, 12.8f * u); lineTo(12.8f * u, 16f * u); lineTo(15.6f * u, 13.6f * u); lineTo(20f * u, 17.2f * u)
    }, color, style = stroke)
}

@Composable
fun PenIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u ->
        moveTo(5f * u, 19f * u); lineTo(6f * u, 15f * u); lineTo(15.5f * u, 5.5f * u); lineTo(18.5f * u, 8.5f * u); lineTo(9f * u, 18f * u); close()
        moveTo(13.5f * u, 7.5f * u); lineTo(16.5f * u, 10.5f * u)
        moveTo(12.5f * u, 20f * u); lineTo(19.5f * u, 20f * u)
    }, color, style = stroke)
}

@Composable
fun RestoreIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u ->
        arcTo(Rect(5f * u, 5f * u, 19f * u, 19f * u), 205f, 300f, true)
        moveTo(4.6f * u, 4.8f * u); lineTo(5.6f * u, 9.1f * u); lineTo(9.8f * u, 8f * u)
    }, color, style = stroke)
}

@Composable
fun PlusIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u -> moveTo(12f * u, 5f * u); lineTo(12f * u, 19f * u); moveTo(5f * u, 12f * u); lineTo(19f * u, 12f * u) }, color, style = stroke)
}

@Composable
fun CloseIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u -> moveTo(6.5f * u, 6.5f * u); lineTo(17.5f * u, 17.5f * u); moveTo(17.5f * u, 6.5f * u); lineTo(6.5f * u, 17.5f * u) }, color, style = stroke)
}

@Composable
fun InfoIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawCircle(color, 9f * unit, Offset(12f * unit, 12f * unit), style = stroke)
    drawPath(path(unit) { u -> moveTo(12f * u, 11f * u); lineTo(12f * u, 16.5f * u) }, color, style = stroke)
    drawCircle(color, 1.1f * unit, Offset(12f * unit, 7.8f * unit))
}

@Composable
fun MoreIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, _ ->
    listOf(5.5f, 12f, 18.5f).forEach { x -> drawCircle(color, 1.7f * unit, Offset(x * unit, 12f * unit)) }
}

@Composable
fun SlidersIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u ->
        moveTo(4f * u, 8f * u); lineTo(20f * u, 8f * u)
        moveTo(4f * u, 16f * u); lineTo(20f * u, 16f * u)
    }, color, style = stroke)
    drawCircle(Color.Black, 2.6f * unit, Offset(9f * unit, 8f * unit))
    drawCircle(color, 2.6f * unit, Offset(9f * unit, 8f * unit), style = stroke)
    drawCircle(Color.Black, 2.6f * unit, Offset(15f * unit, 16f * unit))
    drawCircle(color, 2.6f * unit, Offset(15f * unit, 16f * unit), style = stroke)
}

@Composable
fun PinIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u ->
        moveTo(12f * u, 21f * u)
        cubicTo(7f * u, 15.5f * u, 5f * u, 12.5f * u, 5f * u, 9.5f * u)
        cubicTo(5f * u, 5.6f * u, 8.1f * u, 3f * u, 12f * u, 3f * u)
        cubicTo(15.9f * u, 3f * u, 19f * u, 5.6f * u, 19f * u, 9.5f * u)
        cubicTo(19f * u, 12.5f * u, 17f * u, 15.5f * u, 12f * u, 21f * u)
        close()
    }, color, style = stroke)
    drawCircle(color, 2.4f * unit, Offset(12f * unit, 9.5f * unit), style = stroke)
}

@Composable
fun CheckIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u -> moveTo(5f * u, 12.5f * u); lineTo(10f * u, 17.5f * u); lineTo(19f * u, 7f * u) }, color, style = stroke)
}

@Composable
fun GripIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, _ ->
    listOf(9f, 15f).forEach { x -> listOf(6f, 12f, 18f).forEach { y -> drawCircle(color, 1.6f * unit, Offset(x * unit, y * unit)) } }
}
