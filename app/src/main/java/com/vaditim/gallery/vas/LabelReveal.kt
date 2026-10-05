package com.vaditim.gallery.vas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

// The VAS bar-sweep (components/08-text-reveal.md): an accent bar grows across the text, the text opens under it, and the bar retracts off the far edge. Leaving is a cut, never the sweep reversed. `presence` lets a gesture take the text away with the finger.
@Composable
fun LabelReveal(text: String, isShown: Boolean, style: TextStyle, modifier: Modifier = Modifier, presence: () -> Float = { 1f }) {
    val accent = LocalAccent.current
    val bar = remember { Animatable(0f) }
    val opened = remember { Animatable(0f) }
    var isRetracting by remember { mutableStateOf(false) }
    LaunchedEffect(isShown) {
        if (isShown) {
            isRetracting = false
            opened.snapTo(0f)
            bar.snapTo(0f)
            bar.animateTo(1f, tween(Motion.SWEEP_GROW_MS, easing = Motion.powerThreeInOut))
            // The text opens and the bar turns round on the frame it is full, so the reveal is never caught half way.
            opened.snapTo(1f)
            isRetracting = true
            bar.animateTo(0f, tween(Motion.SWEEP_RETRACT_MS, easing = Motion.powerThreeInOut))
        } else {
            bar.snapTo(0f)
            opened.animateTo(0f, tween(Motion.SWEEP_LEAVE_MS, easing = Motion.powerTwoIn))
        }
    }
    Box(
        modifier.drawWithContent {
            val shown = (opened.value * presence()).coerceIn(0f, 1f)
            clipRect(right = size.width * shown) { this@drawWithContent.drawContent() }
            val width = size.width * bar.value
            // The bar overhangs the line a little, so no ascender or descender shows past it.
            val overhang = size.height * 0.06f
            if (width > 0f) drawRect(accent, Offset(if (isRetracting) size.width - width else 0f, -overhang), Size(width, size.height + overhang * 2f))
        },
    ) {
        BasicText(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
