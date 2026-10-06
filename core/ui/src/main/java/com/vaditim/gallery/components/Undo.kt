package com.vaditim.gallery.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import kotlinx.coroutines.delay

// The undo button's height and the padding around it, which a pill without one keeps.
private val PILL_HEIGHT = 52.dp

// A change shown for a few seconds: what happened, and how to reverse it when it can be; one that cannot (a new cover) only says so.
class UndoOffer(val message: String, val durationMs: Int = Motion.UNDO_MS, val revert: (() -> Unit)? = null)

// The pill that stands above the bar after a delete, a move or a new cover. It keeps the last offer while sliding out, so its text does not vanish mid-animation.
@Composable
fun UndoPill(offer: UndoOffer?, onUndo: (UndoOffer) -> Unit, onExpired: (UndoOffer) -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(offer) }
    if (offer != null) shown = offer
    LaunchedEffect(offer) {
        if (offer != null) {
            delay(offer.durationMs.toLong())
            onExpired(offer)
        }
    }
    AnimatedVisibility(
        visible = offer != null,
        modifier = modifier,
        enter = fadeIn(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.powerTwoOut)) + slideInVertically(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.backOut)) { it },
        exit = fadeOut(tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn)) + slideOutVertically(tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn)) { it },
    ) {
        val current = shown ?: return@AnimatedVisibility
        // A solid pane rather than glass: the pill also floats over the viewer, where the blur source would be the grid hidden behind the photo.
        val canRevert = current.revert != null
        Row(
            // Without the button the pill keeps the same height and evens its ends.
            Modifier.heightIn(min = PILL_HEIGHT).clip(Shapes.capsule).background(Palette.panelSolid).padding(start = 18.dp, end = if (canRevert) 4.dp else 18.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(current.message, style = Type.value.copy(color = Palette.textBright), maxLines = 1)
            if (canRevert) IconButton(onClick = { onUndo(current) }) { RestoreIcon(LocalAccent.current) }
        }
    }
}
