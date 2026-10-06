package com.vaditim.gallery.library

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.vaditim.gallery.vas.Motion

// Top buttons pop: they grow in past full size and settle, and shrink away to nothing, in place rather than sliding.
internal val TOP_ENTER = fadeIn(tween(Motion.STATE_MS)) + scaleIn(tween(Motion.STATE_MS, easing = Motion.backOut), initialScale = 0f)
internal val TOP_EXIT = fadeOut(tween(Motion.STATE_MS, easing = Motion.powerTwoIn)) + scaleOut(tween(Motion.STATE_MS, easing = Motion.backIn), targetScale = 0f)

// The old look pops away to nothing, then the new one pops in past full size.
internal val TOP_POP_IN = scaleIn(tween(Motion.STATE_MS, delayMillis = Motion.STATE_MS, easing = Motion.backOut), initialScale = 0f)
internal val TOP_POP_OUT = scaleOut(tween(Motion.STATE_MS, easing = Motion.backIn), targetScale = 0f)
