package com.vaditim.gallery.ui

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// A label that types itself over: it deletes back to what the old and new text share, then (when its width follows the text) resizes from its middle and types the rest in partway into the resize.
// A new colour comes as the new text starts typing, never while the old one is still leaving; with the text unchanged it comes on the cut, with everything else.
@Composable
fun TypedLabel(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    isFilled: Boolean = true,
    fixedWidth: Dp? = null,
    maxWidth: Dp = Dp.Infinity,
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    contentAlignment: Alignment = Alignment.Center,
    isTypedIn: Boolean = false,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val measurer = rememberTextMeasurer()
    val sides = padding.calculateLeftPadding(layoutDirection) + padding.calculateRightPadding(layoutDirection)
    fun widthOf(label: String): Float = with(density) {
        (measurer.measure(label, Type.microLabel, maxLines = 1, softWrap = false).size.width.toDp() + sides).coerceAtMost(maxWidth).toPx()
    }
    var stage by remember { mutableStateOf(if (isTypedIn) "" else text) }
    var shownAccent by remember { mutableStateOf(accent) }
    // When the text being deleted is gone, so a restart in the middle still waits for it.
    var untypedAt by remember { mutableLongStateOf(0L) }
    val freeWidth = remember { Animatable(widthOf(text)) }
    LaunchedEffect(text, accent) {
        val shared = stage.commonPrefixWith(text)
        val removed = stage.length - shared.length
        if (removed > 0) {
            stage = shared
            untypedAt = SystemClock.uptimeMillis() + Motion.UNTYPE_MS * removed
        }
        delay(untypedAt - SystemClock.uptimeMillis())
        val target = widthOf(text)
        if (fixedWidth == null && target != freeWidth.targetValue) {
            launch { freeWidth.animateTo(target, tween(Motion.STATE_MS, easing = Motion.powerThreeInOut)) }
            delay((Motion.STATE_MS * TYPE_IN_AT).toLong())
        } else if (stage == text && accent != shownAccent) {
            delay(Motion.SECTION_LEAVE_MS.toLong())
        }
        shownAccent = accent
        stage = text
    }
    val fill by animateColorAsState(if (isFilled) shownAccent else shownAccent.copy(alpha = 0f), tween(Motion.STATE_MS), label = "label-fill")
    val ink by animateColorAsState(if (isFilled) Palette.sunkenDeep else shownAccent, tween(Motion.STATE_MS), label = "label-ink")
    Box(
        modifier
            .width(fixedWidth ?: with(density) { freeWidth.value.toDp() })
            .background(fill, Shapes.capsule)
            .padding(padding),
        contentAlignment = contentAlignment,
    ) {
        // A caret would widen text whose pill is sized to it, so only a fixed-width label shows one.
        FadingOverflow { TypewriterText(stage, style = Type.microLabel.copy(color = ink, shadow = if (isFilled) null else LABEL_SHADOW), isCaretShown = fixedWidth != null) }
    }
}

// How far into the resize the new text starts typing.
private const val TYPE_IN_AT = 0.3f
// Bare text over photos keeps a dark shadow under it, as the corner heart does.
private val LABEL_SHADOW = Shadow(Color.Black, blurRadius = 8f)
