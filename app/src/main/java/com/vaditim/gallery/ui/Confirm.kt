package com.vaditim.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable

// A delete waits in this pill, above everything else over a bar, until it is confirmed; the same in the grids and the viewer.
@Composable
fun ConfirmPill(pending: (() -> Unit)?, onDone: () -> Unit) {
    AnimatedVisibility(pending != null, enter = CONFIRM_ENTER, exit = CONFIRM_EXIT) {
        Box(
            Modifier.padding(bottom = 14.dp)
                .pressable(onClick = {
                    val delete = pending
                    onDone()
                    delete?.invoke()
                })
                .background(Palette.danger, Shapes.capsule)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            // Half again the size of the other labels, so the one irreversible tap is the easiest to find.
            BasicText("CONFIRM", style = Type.microLabel.copy(color = Palette.sunkenDeep, fontSize = Type.microLabel.fontSize * 1.5f, letterSpacing = Type.microLabel.letterSpacing * 1.5f))
        }
    }
}

// The delete button that is waiting on Confirm stays marked, so it is clear what Confirm is for.
fun Modifier.pendingMark(isPending: Boolean): Modifier =
    if (isPending) this.background(Palette.danger.copy(alpha = 0.22f), Shapes.capsule) else this

private val CONFIRM_ENTER = fadeIn(tween(Motion.STATE_MS)) + scaleIn(tween(Motion.STATE_MS, easing = Motion.backOut), initialScale = 0f)
private val CONFIRM_EXIT = fadeOut(tween(Motion.STATE_MS, easing = Motion.powerTwoIn)) + scaleOut(tween(Motion.STATE_MS, easing = Motion.backIn), targetScale = 0f)
