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
fun LabelReveal(text: String, isShown: Boolean, style: TextStyle, modifier: Modifier = Modifier, presence: () -> Float = { 1f }, isRevealedAtStart: Boolean = false, isCutFromStart: Boolean = false) {
    val accent = LocalAccent.current
    val bar = remember { Animatable(0f) }
    // Text already standing when it first appears (scrolled back into view) shows at once; only a change sweeps.
    val opened = remember { Animatable(if (isRevealedAtStart && isShown) 1f else 0f) }
    var isRetracting by remember { mutableStateOf(false) }
    LaunchedEffect(isShown) {
        if (isShown && opened.value == 1f && !isRetracting) return@LaunchedEffect
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
            val open = opened.value.coerceIn(0f, 1f)
            val present = presence().coerceIn(0f, 1f)
            // The sweep always opens from the left; presence cuts from the right end, or, with isCutFromStart, from the left one only, leaving is then the presence's alone.
            val start = if (isCutFromStart) size.width * (1f - present) else 0f
            val end = size.width * if (isCutFromStart) (if (isShown) open else 1f) else open * present
            // A crossed clip would be read back to front and show a strip, so nothing is drawn once the cuts meet.
            if (end > start) clipRect(left = start, right = end) { this@drawWithContent.drawContent() }
            val width = size.width * bar.value
            // The bar overhangs the line a little, so no ascender or descender shows past it.
            val overhang = size.height * 0.06f
            if (width > 0f) drawRect(accent, Offset(if (isRetracting) size.width - width else 0f, -overhang), Size(width, size.height + overhang * 2f))
        },
    ) {
        BasicText(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
