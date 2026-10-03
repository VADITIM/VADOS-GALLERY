package com.vaditim.gallery.vas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

// The panel primitive (dna/03-surface.md): translucent near-black fill, one hairline, one squircle, a micro-label in the corner. Depth is the border, never a shadow.
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    label: String? = null,
    shape: Shape = Shapes.panel,
    isSolid: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(if (isSolid) Palette.panelSolid else Palette.panel)
            .border(1.dp, Palette.border, shape),
    ) {
        Box(Modifier.padding(top = if (label != null) 26.dp else 0.dp)) { content() }
        if (label != null) MicroLabel(label, Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 10.dp))
    }
}

@Composable
fun MicroLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(text.uppercase(), modifier, style = Type.microLabel)
}

// The platform ripple is removed everywhere, so every pressable owns its press (dna/05-motion.md §8): in fast, out on the enter curve.
@Composable
fun Modifier.pressable(onClick: () -> Unit, pressedScale: Float = 0.95f): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = if (isPressed) tween(Motion.PRESS_MS, easing = Motion.powerTwoOut) else tween(Motion.RELEASE_MS, easing = Motion.backOut),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}
