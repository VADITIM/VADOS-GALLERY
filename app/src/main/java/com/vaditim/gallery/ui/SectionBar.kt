package com.vaditim.gallery.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.glass
import com.vaditim.gallery.vas.pressable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// The chosen section's label grows by this much, landing on the overshoot.
private const val ACTIVE_LABEL_SCALE = 1.06f

// Navigation is the bar and only the bar: a horizontal swipe belongs to the viewer's pager, so sections are never swiped between (dna/06-interaction.md, gesture ownership).
@Composable
fun SectionBar(active: Section, onSelect: (Section) -> Unit, modifier: Modifier = Modifier) {
    // Where each section's pill sits in the bar, left edge and right edge, so the highlight knows where to slide.
    val spans = remember { mutableStateMapOf<Section, Pair<Float, Float>>() }
    val left = remember { Animatable(Float.NaN) }
    val right = remember { Animatable(Float.NaN) }
    val target = spans[active]
    LaunchedEffect(active, target) {
        val (toLeft, toRight) = target ?: return@LaunchedEffect
        if (left.value.isNaN()) {
            left.snapTo(toLeft)
            right.snapTo(toRight)
            return@LaunchedEffect
        }
        // The edge on the side it is heading leaves first and the other follows, so the highlight stretches across and then gathers itself.
        val isMovingRight = toRight > right.value
        val lead = tween<Float>(Motion.NAV_SLIDE_MS, easing = Motion.powerTwoOut)
        val trail = tween<Float>(Motion.NAV_SLIDE_MS, Motion.NAV_TRAIL_MS, Motion.powerTwoOut)
        coroutineScope {
            launch { left.animateTo(toLeft, if (isMovingRight) trail else lead) }
            launch { right.animateTo(toRight, if (isMovingRight) lead else trail) }
        }
    }
    val wash = Palette.pressedWash
    Row(
        modifier
            .glass(Shapes.capsule)
            .padding(5.dp)
            .drawBehind {
                if (left.value.isNaN()) return@drawBehind
                drawRoundRect(wash, Offset(left.value, 0f), Size(right.value - left.value, size.height), CornerRadius(size.height / 2f))
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Section.entries.forEach { section ->
            val isActive = section == active
            val ink by animateColorAsState(if (isActive) section.accent else Palette.textMuted, tween(Motion.STATE_MS), label = "section-ink")
            val labelScale by animateFloatAsState(if (isActive) ACTIVE_LABEL_SCALE else 1f, tween(Motion.NAV_SLIDE_MS, easing = Motion.backOut), label = "section-scale")
            Box(
                Modifier
                    .onPlaced { placed -> spans[section] = placed.positionInParent().x.let { it to it + placed.size.width } }
                    .pressable(onClick = { onSelect(section) })
                    .padding(horizontal = 18.dp, vertical = 13.dp),
            ) {
                BasicText(
                    section.label,
                    style = Type.navigation.copy(color = ink),
                    modifier = Modifier.graphicsLayer {
                        scaleX = labelScale
                        scaleY = labelScale
                    },
                )
            }
        }
    }
}
