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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

// What the Albums button shows: the place the user is in, since Locations, the trash and Private are reached from Albums.
enum class PlaceGlyph { ALBUMS, LOCATIONS, TRASH, PRIVATE }

// The chosen section's label grows by this much, landing on the overshoot.
private const val ACTIVE_LABEL_SCALE = 1.06f

// Navigation is the bar and only the bar: a horizontal swipe belongs to the viewer's pager, so sections are never swiped between (dna/06-interaction.md, gesture ownership).
@Composable
fun SectionBar(active: Section, onSelect: (Section) -> Unit, modifier: Modifier = Modifier, accentOf: (Section) -> Color = { it.accent }, albumsGlyph: PlaceGlyph = PlaceGlyph.ALBUMS, isMarked: (Section) -> Boolean = { false }, hasGlass: Boolean = true, itemModifier: Modifier = Modifier, washAlpha: () -> Float = { 1f }) {
    // The new section's label takes its colour on the cut, once the outgoing section has left, as every other accent does.
    var inkActive by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        delay(Motion.SECTION_LEAVE_MS.toLong())
        inkActive = active
    }
    NavBar(Section.entries, active, onSelect, modifier, hasGlass = hasGlass, itemModifier = itemModifier, washAlpha = washAlpha) { section ->
        val ink by animateColorAsState(if (section == inkActive) accentOf(section) else Palette.textMuted, tween(Motion.STATE_MS), label = "section-ink")
        val markAlpha by animateFloatAsState(if (isMarked(section)) 1f else 0f, tween(Motion.STATE_MS), label = "section-mark")
        val mark = accentOf(section)
        // A section that works its own way here is underlined in the accent, just below its icon.
        Box(Modifier.drawBehind {
            if (markAlpha == 0f) return@drawBehind
            val thickness = MARK_THICKNESS.toPx()
            drawRoundRect(mark, Offset(size.width * 0.2f, size.height + MARK_GAP.toPx()), Size(size.width * 0.6f, thickness), CornerRadius(thickness / 2f), alpha = markAlpha)
        }) {
        when (section) {
            Section.RECENT -> ClockIcon(ink, SECTION_ICON)
            // A new place pops the old icon away to nothing, then pops its own in past full size, so the change is seen.
            Section.ALBUMS -> AnimatedContent(
                targetState = albumsGlyph,
                transitionSpec = {
                    scaleIn(tween(Motion.STATE_MS, delayMillis = Motion.STATE_MS, easing = Motion.backOut), initialScale = 0f)
                        .togetherWith(scaleOut(tween(Motion.STATE_MS, easing = Motion.backIn), targetScale = 0f))
                        .using(SizeTransform(clip = false))
                },
                contentAlignment = Alignment.Center,
                label = "albums-glyph",
            ) { glyph ->
                when (glyph) {
                    PlaceGlyph.ALBUMS -> AlbumsIcon(ink, SECTION_ICON)
                    PlaceGlyph.LOCATIONS -> PinIcon(ink, SECTION_ICON)
                    PlaceGlyph.TRASH -> TrashIcon(ink, SECTION_ICON)
                    PlaceGlyph.PRIVATE -> LockIcon(ink, size = SECTION_ICON)
                }
            }
            Section.FAVORITES -> HeartIcon(isFilled = true, color = ink, size = SECTION_ICON)
        }
        }
    }
}

private val MARK_GAP = 4.dp
private val MARK_THICKNESS = 2.dp

// The nav's pill, for any row of choices: glass, a wash that slides to the chosen one, its label grown a little. `scroll` lets a row wider than the screen slide inside the pill.
@Composable
// Inside a glass shared with other bars it leaves the glass out; each option takes itemModifier, and the wash fades with washAlpha, so a bar swap can pop them.
fun <T> NavBar(options: List<T>, active: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, scroll: ScrollState? = null, hasGlass: Boolean = true, itemModifier: Modifier = Modifier, washAlpha: () -> Float = { 1f }, label: @Composable (T) -> Unit) {
    // Where each option's pill sits in the bar, left edge and right edge, so the highlight knows where to slide.
    val spans = remember { mutableStateMapOf<T, Pair<Float, Float>>() }
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
            .then(if (hasGlass) Modifier.glass(Shapes.capsule) else Modifier)
            .then(if (scroll != null) Modifier.horizontalScroll(scroll) else Modifier)
            .padding(5.dp)
            .drawBehind {
                if (left.value.isNaN()) return@drawBehind
                drawRoundRect(wash, Offset(left.value, 0f), Size(right.value - left.value, size.height), CornerRadius(size.height / 2f), alpha = washAlpha())
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val isActive = option == active
            val labelScale by animateFloatAsState(if (isActive) ACTIVE_LABEL_SCALE else 1f, tween(Motion.NAV_SLIDE_MS, easing = Motion.backOut), label = "section-scale")
            Box(
                itemModifier
                    .onPlaced { placed -> spans[option] = placed.positionInParent().x.let { it to it + placed.size.width } }
                    .pressable(onClick = { onSelect(option) })
                    .padding(horizontal = 18.dp, vertical = 13.dp),
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
}

private val SECTION_ICON = 22.dp
