package com.vaditim.gallery.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

// Content wider than its room fades out at the end instead of being cut off or ellipsed.
@Composable
fun FadingOverflow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var isOverflowing by remember { mutableStateOf(false) }
    Box(
        modifier
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (isOverflowing) {
                    val fade = OVERFLOW_FADE.toPx()
                    drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width), blendMode = BlendMode.DstIn)
                }
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                isOverflowing = placeable.width > constraints.maxWidth
                layout(placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth), placeable.height) { placeable.place(0, 0) }
            },
    ) { content() }
}

private val OVERFLOW_FADE = 20.dp
