package com.vaditim.gallery.vas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// The panel, softened: a raised fill and a large squircle, with the VAS micro-label kept in its corner. A panel that floats over content uses Modifier.glass instead.
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    label: String? = null,
    shape: Shape = Shapes.panel,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.clip(shape).background(Palette.surface)) {
        Box(Modifier.padding(top = if (label != null) 30.dp else 0.dp)) { content() }
        if (label != null) MicroLabel(label, Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 14.dp))
    }
}

@Composable
fun MicroLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(text.uppercase(), modifier, style = Type.microLabel)
}

// The platform ripple is removed everywhere, so every pressable owns its press (dna/05-motion.md §8): in fast, out on the enter curve. A long press, where there is one, gets the system's long-press haptic from combinedClickable.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(onClick: () -> Unit, pressedScale: Float = 0.95f, onLongClick: (() -> Unit)? = null): Modifier {
    // Every press answers with a tick from the vibrator itself, so no button is ever silent under One UI's haptic settings.
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = if (isPressed) tween(Motion.PRESS_MS, easing = Motion.powerTwoOut) else tween(Motion.RELEASE_MS, easing = Motion.backOut),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onLongClick = onLongClick?.let { longClick -> { Haptics.tick(context); longClick() } },
            onClick = { Haptics.tick(context); onClick() },
        )
}
