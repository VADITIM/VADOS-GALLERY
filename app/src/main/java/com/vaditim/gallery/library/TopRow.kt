package com.vaditim.gallery.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.components.AlbumsIcon
import com.vaditim.gallery.components.BackIcon
import com.vaditim.gallery.components.CloseIcon
import com.vaditim.gallery.components.GridIcon
import com.vaditim.gallery.components.LocalAccentTarget
import com.vaditim.gallery.components.PlusIcon
import com.vaditim.gallery.components.SettingsIcon
import com.vaditim.gallery.components.TopButton
import com.vaditim.gallery.components.TypedLabel
import com.vaditim.gallery.components.VisibleMonth
import com.vaditim.gallery.components.rememberOwnAccent
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.glass
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MONTH_CHIP_WIDTH = 148.dp

// The top layer: the way back out of a folder and the month you are looking at, or — while selecting — the count and the way out of the selection.
// The month chip has a fixed width, so a month with a longer name never shifts or resizes the buttons.
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun TopRow(isHidden: Boolean, month: VisibleMonth, title: String?, isMonthFilled: Boolean, selectedCount: Int, onBack: (() -> Unit)?, onAdd: (() -> Unit)?, onCancelSelection: () -> Unit, onSettings: () -> Unit, isSettingsOpen: Boolean, onSettingsBounds: (Rect) -> Unit, onToggleView: (() -> Unit)? = null, isAlbumsView: Boolean = false) {
    // Selecting swaps the whole row: what stands there pops away, each on its own, then the new buttons pop in; otherwise each button comes and goes on its own as the place changes, the others sliding to make room.
    AnimatedContent(
        targetState = when {
            isHidden -> TopMode.HIDDEN
            selectedCount > 0 -> TopMode.SELECTING
            else -> TopMode.NORMAL
        },
        transitionSpec = { EnterTransition.None.togetherWith(ExitTransition.None).using(SizeTransform(clip = false)) },
        label = "topRow",
    ) { mode ->
        val pop = Modifier.animateEnterExit(enter = TOP_POP_IN, exit = TOP_POP_OUT)
        // As tall as a button, whether or not one is shown, so the month pill never moves up or down as back and add come and go.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).height(TOP_ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            if (mode == TopMode.HIDDEN) {
                // Nothing while a photo is open; the buttons pop away and come back.
            } else if (mode == TopMode.SELECTING) {
                Box(pop) { TopButton(onCancelSelection) { CloseIcon(LocalAccent.current) } }
                Box(Modifier.weight(1f))
                Chip("$selectedCount selected", pop)
            } else {
                // Back sits at the far left, then the month, or the folder's name in its place, which types itself over as it changes; the buttons gather at the right.
                val isMonthShown = month.label.isNotEmpty()
                var lastLabel by remember { mutableStateOf(title ?: month.label.uppercase()) }
                if (isMonthShown) lastLabel = title ?: month.label.uppercase()
                val monthVisibility = remember { MutableTransitionState(isMonthShown) }
                monthVisibility.targetState = isMonthShown
                // Back arriving pushes the pill aside only when it was already showing; arriving together, it pops in where it ends up.
                Box(pop) { MakeRoomButton(onBack, isNeighbourShown = monthVisibility.currentState && isMonthShown) { BackIcon(LocalAccent.current) } }
                AnimatedVisibility(monthVisibility, modifier = pop, enter = TOP_ENTER, exit = TOP_EXIT) {
                    // A section-coloured pill while it names the folder; with the folder named above the nav, the glass pill with the month in the section colour.
                    TypedLabel(
                        lastLabel,
                        LocalAccentTarget.current ?: LocalAccent.current,
                        isFilled = isMonthFilled,
                        fixedWidth = MONTH_CHIP_WIDTH,
                        padding = PaddingValues(start = 17.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                        contentAlignment = Alignment.Center,
                        isTypedIn = true,
                    )
                }
                Box(Modifier.weight(1f))
                // The toggle's icon shows where a tap goes: the grid of every favourite, or the albums.
                val action = when {
                    onAdd != null -> TopAction.ADD
                    onToggleView != null -> if (isAlbumsView) TopAction.SHOW_GRID else TopAction.SHOW_ALBUMS
                    else -> null
                }
                Box(pop) { TopActionButton(action, onAdd ?: onToggleView) }
                // The settings sheet opens out of this button, so it reports where it is.
                Box(pop.padding(start = 8.dp).onGloballyPositioned { onSettingsBounds(it.boundsInRoot()) }) { TopButton(onSettings) { SpinningGear(isSettingsOpen) } }
            }
        }
    }
}

// The gear turns and pops down as settings open, so the sheet can grow from the empty button, and turns back in once the sheet has gone.
@Composable
private fun SpinningGear(isSettingsOpen: Boolean) {
    val turn = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(isSettingsOpen) {
        if (isSettingsOpen) {
            launch { turn.animateTo(GEAR_TURN, tween(Motion.STATE_MS, easing = Motion.backIn)) }
            pop.animateTo(0f, tween(Motion.STATE_MS, easing = Motion.backIn))
        } else {
            delay(Motion.OVERLAY_LEAVE_MS.toLong())
            launch { turn.animateTo(0f, tween(Motion.STATE_MS, easing = Motion.backOut)) }
            pop.animateTo(1f, tween(Motion.STATE_MS, easing = Motion.backOut))
        }
    }
    Box(Modifier.graphicsLayer {
        rotationZ = turn.value
        scaleX = pop.value
        scaleY = pop.value
    }) { SettingsIcon(Palette.textBright) }
}

private const val GEAR_TURN = 180f

// The back button takes its room and gives it back while it pops, in one step as long as the add button's: the month slides over as it pops in, and slides into its place as it pops away.
@Composable
private fun MakeRoomButton(onClick: (() -> Unit)?, isNeighbourShown: Boolean, icon: @Composable () -> Unit) {
    var lastClick by remember { mutableStateOf(onClick) }
    if (onClick != null) lastClick = onClick
    val isShown = onClick != null
    val ownAccent = rememberOwnAccent(isShown)
    val room = remember { Animatable(if (isShown) 1f else 0f) }
    val pop = remember { Animatable(if (isShown) 1f else 0f) }
    LaunchedEffect(isShown) {
        if (isShown) {
            if (isNeighbourShown) launch { room.animateTo(1f, tween(Motion.STATE_MS, easing = Motion.powerThreeInOut)) } else room.snapTo(1f)
            pop.animateTo(1f, tween(Motion.STATE_MS, easing = Motion.backOut))
        } else {
            launch { room.animateTo(0f, tween(Motion.STATE_MS, easing = Motion.powerThreeInOut)) }
            pop.animateTo(0f, tween(Motion.STATE_MS, easing = Motion.backIn))
        }
    }
    if (room.value == 0f && pop.value == 0f && !isShown) return
    Layout(
        content = {
            Box(Modifier.padding(end = 8.dp).graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                alpha = pop.value.coerceIn(0f, 1f)
            }) { CompositionLocalProvider(LocalAccent provides ownAccent) { TopButton({ lastClick?.invoke() }, icon = icon) } }
        },
    ) { measurables, constraints ->
        val button = measurables.first().measure(constraints.copy(minWidth = 0))
        val width = (button.width * room.value).roundToInt()
        layout(width, button.height) { button.place(0, 0) }
    }
}

// A top button's height: its 22dp icon and 10dp above and below.
private val TOP_ROW_HEIGHT = 42.dp

// What the button beside settings does.
private enum class TopAction { ADD, SHOW_GRID, SHOW_ALBUMS }

// One button beside settings for adding and for switching Favorites' view, so one never leaves while another arrives; a new action or colour pops it away and back in, as the nav's albums icon does.
@Composable
private fun TopActionButton(action: TopAction?, onClick: (() -> Unit)?) {
    var lastClick by remember { mutableStateOf(onClick) }
    if (onClick != null) lastClick = onClick
    val target = LocalAccentTarget.current ?: LocalAccent.current
    // Held while it leaves, so the button goes as it was instead of popping to a new look on its way out.
    var shown by remember { mutableStateOf(action?.let { it to target }) }
    if (action != null) shown = action to target
    AnimatedVisibility(action != null, enter = TOP_ENTER, exit = TOP_EXIT) {
        Box(Modifier.padding(start = 8.dp)) {
            AnimatedContent(
                targetState = shown,
                transitionSpec = { TOP_POP_IN.togetherWith(TOP_POP_OUT).using(SizeTransform(clip = false)) },
                contentAlignment = Alignment.Center,
                label = "top-action",
            ) { look ->
                val (kind, accent) = look ?: return@AnimatedContent
                CompositionLocalProvider(LocalAccent provides accent) {
                    TopButton({ lastClick?.invoke() }) {
                        when (kind) {
                            TopAction.ADD -> PlusIcon(accent)
                            TopAction.SHOW_GRID -> GridIcon(accent)
                            TopAction.SHOW_ALBUMS -> AlbumsIcon(accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, modifier: Modifier = Modifier) {
    Box(modifier.glass(Shapes.capsule).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        // One line always: a long month must not grow the pill into two.
        BasicText(text.uppercase(), style = Type.microLabel, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

private enum class TopMode { NORMAL, SELECTING, HIDDEN }
