package com.vaditim.gallery.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.glass
import com.vaditim.gallery.vas.pressable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

// What the Albums button shows: the place the user is in, since Locations, the trash and Private are reached from Albums.
enum class PlaceGlyph { ALBUMS, LOCATIONS, TRASH, PRIVATE }

// The chosen section's label grows by this much, landing on the overshoot.
private const val ACTIVE_LABEL_SCALE = 1.06f

// Prime component (VAS components/19-pop-bar.md): change the entry there first, then this.
// The nav's pill, for any row of choices: glass, a wash that slides to the chosen one, its label grown a little. `scroll` lets a row wider than the screen slide inside the pill. `isVertical` stacks the choices in a column, the first at the top, and the wash slides up and down.
@Composable
// Inside a glass shared with other bars it leaves the glass out; each option takes itemModifier, and the wash fades with washAlpha, so a bar swap can pop them.
fun <T> NavBar(options: List<T>, active: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, scroll: ScrollState? = null, hasGlass: Boolean = true, itemModifier: Modifier = Modifier, washAlpha: () -> Float = { 1f }, isVertical: Boolean = false, label: @Composable (T) -> Unit) {
    // Where each option's pill sits along the bar, its near edge and its far edge, so the highlight knows where to slide.
    val spans = remember { mutableStateMapOf<T, Pair<Float, Float>>() }
    val start = remember { Animatable(Float.NaN) }
    val end = remember { Animatable(Float.NaN) }
    val target = spans[active]
    // The option the wash has already started towards; the bar settling into its place afterwards moves the wash at once, where it is still hidden, instead of sliding it.
    val slidTo = remember { arrayOfNulls<Any>(1) }
    LaunchedEffect(active, target) {
        val (toStart, toEnd) = target ?: return@LaunchedEffect
        if (start.value.isNaN() || slidTo[0] == active) {
            slidTo[0] = active
            start.snapTo(toStart)
            end.snapTo(toEnd)
            return@LaunchedEffect
        }
        slidTo[0] = active
        // The edge on the side it is heading leaves first and the other follows, so the highlight stretches across and then gathers itself.
        val isMovingOn = toEnd > end.value
        val lead = tween<Float>(Motion.NAV_SLIDE_MS, easing = Motion.powerTwoOut)
        val trail = tween<Float>(Motion.NAV_SLIDE_MS, Motion.NAV_TRAIL_MS, Motion.powerTwoOut)
        coroutineScope {
            launch { start.animateTo(toStart, if (isMovingOn) trail else lead) }
            launch { end.animateTo(toEnd, if (isMovingOn) lead else trail) }
        }
    }
    val wash = Palette.pressedWash
    val barModifier = modifier
        .then(if (hasGlass) Modifier.glass(Shapes.capsule) else Modifier)
        .then(if (scroll == null) Modifier else if (isVertical) Modifier.verticalScroll(scroll) else Modifier.horizontalScroll(scroll))
        .padding(5.dp)
        .drawBehind {
            if (start.value.isNaN()) return@drawBehind
            val length = end.value - start.value
            if (isVertical) {
                drawRoundRect(wash, Offset(0f, start.value), Size(size.width, length), CornerRadius(min(size.width, length) / 2f), alpha = washAlpha())
            } else {
                drawRoundRect(wash, Offset(start.value, 0f), Size(length, size.height), CornerRadius(size.height / 2f), alpha = washAlpha())
            }
        }
    val choices: @Composable () -> Unit = {
        options.forEach { option ->
            val isActive = option == active
            val labelScale by animateFloatAsState(if (isActive) ACTIVE_LABEL_SCALE else 1f, tween(Motion.NAV_SLIDE_MS, easing = Motion.backOut), label = "section-scale")
            Box(
                itemModifier
                    .then(if (isVertical) Modifier.fillMaxWidth() else Modifier)
                    .onPlaced { placed ->
                        val at = placed.positionInParent()
                        spans[option] = if (isVertical) at.y to at.y + placed.size.height else at.x to at.x + placed.size.width
                    }
                    .pressable(onClick = { onSelect(option) })
                    .padding(horizontal = 18.dp, vertical = if (isVertical) VERTICAL_OPTION_PADDING else 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.graphicsLayer {
                        scaleX = labelScale
                        scaleY = labelScale
                    },
                ) { label(option) }
            }
        }
    }
    // Standing, every option is as wide as the widest, so the wash is one width all the way up.
    if (isVertical) {
        Column(barModifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(2.dp)) { choices() }
    } else {
        Row(barModifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) { choices() }
    }
}

// A standing bar's options sit closer together, so a long list still fits beside the picture.
private val VERTICAL_OPTION_PADDING = 9.dp

private val SECTION_ICON = 22.dp
