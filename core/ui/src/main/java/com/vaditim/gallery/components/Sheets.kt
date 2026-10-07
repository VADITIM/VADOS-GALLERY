package com.vaditim.gallery.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.settings.AlbumStack
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.glass
import com.vaditim.gallery.vas.pressable
import com.vaditim.gallery.vault.PrivateGroup
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// A menu over content: a pane of glass that arrives from just below on the overshoot and leaves straight down and quicker (dna/05-motion.md §4). Tapping anywhere outside it closes it, so it never traps what is behind it.
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OverlaySheet(visible: Boolean, label: String, onDismiss: () -> Unit, ground: Color = Palette.ground, reveal: () -> Float = { 0f }, trailingLabel: String? = null, isFloating: Boolean = false, isCentered: Boolean = false, isFullHeight: Boolean = false, origin: Rect? = null, onPull: (Float) -> Unit = {}, content: @Composable () -> Unit) {
    // A gesture can raise the sheet before it is open: `reveal` 0 to 1 places it frame by frame, and the gesture opens it once it has carried it all the way.
    val isRevealing by remember { derivedStateOf { reveal() > 0f } }
    val isFollowing = { !visible && reveal() > 0f }
    // A pull down anywhere drags the sheet with the finger; let go far enough or fast enough and it carries on down from there and closes, otherwise it goes back up.
    // Plain state the drag writes straight to: a coroutine per move could land after the release animation had started and cancel it half way, leaving the sheet stranded.
    var pull by remember { mutableFloatStateOf(0f) }
    var sheetHeight by remember { mutableFloatStateOf(1f) }
    val pastEdge = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(visible) { if (visible) pull = 0f }
    // How far down the sheet is pulled, 0 to 1 of its height, for what behind it should follow the finger.
    LaunchedEffect(Unit) { snapshotFlow { (pull / sheetHeight).coerceIn(0f, 1f) }.collect { onPull(it) } }
    // With an origin, the pane opens out of the button that asked for it: as the button's icon leaves, it grows from the button's rectangle to its own, and what it holds then rises in one after another.
    val grow = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    val hasOrigin = origin != null
    LaunchedEffect(visible, hasOrigin) {
        if (!hasOrigin) return@LaunchedEffect
        if (visible) {
            // Out fast and settling, so the pane is under the finger at once rather than easing in from a standstill.
            launch { grow.animateTo(1f, tween(Motion.MORPH_MS, delayMillis = Motion.MORPH_DELAY_MS, easing = Motion.powerTwoOut)) }
            rise.animateTo(RISE_TOTAL_MS, tween(RISE_TOTAL_MS.toInt(), delayMillis = Motion.MORPH_DELAY_MS + Motion.RISE_DELAY_MS, easing = LinearEasing))
        } else {
            // Closing goes the usual way down, so the next opening starts again from the button.
            grow.snapTo(0f)
            rise.snapTo(0f)
        }
    }
    val morphProgress = { if (visible && hasOrigin) grow.value else 1f }
    val riseElapsed = { if (visible && hasOrigin) rise.value else Float.MAX_VALUE }
    var slotPosition by remember { mutableStateOf(Offset.Zero) }
    val pullDrag = rememberDraggableState { delta -> pull = (pull + delta).coerceAtLeast(0f) }
    AnimatedVisibility(
        visible = visible || isRevealing,
        // Already on screen under the finger, so it does not play its own arrival on top.
        enter = if (isRevealing && !visible) EnterTransition.None else fadeIn(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.powerTwoOut)),
        exit = fadeOut(tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn)),
    ) {
        // Registered as the sheet appears, so it is the newest back handler and the back gesture closes the sheet before anything behind it.
        BackHandler(enabled = visible, onBack = onDismiss)
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(SCRIM.copy(alpha = SCRIM.alpha * (if (isFollowing()) reveal() else 1f) * (1f - pull / sheetHeight).coerceIn(0f, 1f))) }
                // The pull works anywhere on screen, not only on the sheet; a tap outside still closes it at once.
                .draggable(
                    pullDrag,
                    Orientation.Vertical,
                    enabled = visible,
                    onDragStopped = { velocity ->
                        if (pull > sheetHeight * PULL_CLOSE || velocity > PULL_FLING) {
                            animate(pull, sheetHeight + pastEdge, velocity, tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoOut)) { value, _ -> pull = value }
                            onDismiss()
                        } else {
                            animate(pull, 0f, velocity, tween(Motion.STATE_MS, easing = Motion.backOut)) { value, _ -> pull = value }
                        }
                    },
                )
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            // A floating sheet hangs from a fixed edge near the top, centred, so a change in its height only ever moves its bottom.
            // A centred sheet sits in the middle of the screen, between the status and navigation bars.
            contentAlignment = if (isFullHeight) Alignment.TopCenter else if (isCentered) Alignment.Center else if (isFloating) Alignment.TopCenter else Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .animateEnterExit(
                        enter = if ((isRevealing && !visible) || hasOrigin) {
                            EnterTransition.None
                        } else {
                            slideInVertically(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.backOut)) { it / 6 } +
                                scaleIn(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.backOut), initialScale = 0.96f)
                        },
                        exit = slideOutVertically(tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn)) { it / 8 } +
                            scaleOut(tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn), targetScale = 0.98f),
                    )
                    .graphicsLayer { translationY = if (isFollowing()) (1f - reveal()) * (size.height + 12.dp.toPx()) else pull }
                    .onSizeChanged { sheetHeight = it.height.toFloat().coerceAtLeast(1f) }
                    .then(if (isCentered || isFullHeight) Modifier.statusBarsPadding().navigationBarsPadding() else if (isFloating) Modifier.statusBarsPadding().padding(top = FLOATING_TOP) else Modifier.navigationBarsPadding())
                    .then(if (isFullHeight) Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = FULL_HEIGHT_TOP) else Modifier.padding(12.dp))
                    // A full-height sheet runs from just under the status bar to just over the navigation bar, covering the top row and the nav.
                    .then(if (isFullHeight) Modifier.fillMaxHeight() else Modifier)
                    .then(if (isFloating || isCentered) Modifier.widthIn(max = FLOATING_WIDTH) else Modifier)
                    .fillMaxWidth()
                    // Where the pane's own corner lies on screen, so the button's rectangle can be placed within it.
                    .onGloballyPositioned { slotPosition = it.positionInRoot() },
            ) {
                Column(
                    Modifier
                        .graphicsLayer { alpha = if (hasOrigin && morphProgress() == 0f) 0f else 1f }
                        .offset {
                            val from = origin?.let { IntOffset((it.left - slotPosition.x).roundToInt(), (it.top - slotPosition.y).roundToInt()) } ?: IntOffset.Zero
                            val progress = morphProgress()
                            IntOffset(lerp(from.x, 0, progress), lerp(from.y, 0, progress))
                        }
                        .glass(Shapes.sheet, ground)
                        // The pane is laid out at its full size throughout and only reports the part of it that has grown, so nothing inside is ever scaled.
                        .then(if (origin != null) Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val progress = morphProgress()
                            layout(lerp(origin.width.roundToInt(), placeable.width, progress), lerp(origin.height.roundToInt(), placeable.height, progress)) { placeable.place(0, 0) }
                        } else Modifier)
                        .then(if (isFullHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                        .padding(top = 18.dp, bottom = 10.dp),
                ) {
                    CompositionLocalProvider(LocalSheetRise provides riseElapsed) {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 6.dp).risesIn(0), horizontalArrangement = Arrangement.SpaceBetween) {
                            MicroLabel(label)
                            // What the sheet is about, at the other end of its label, in the section colour.
                            if (trailingLabel != null) BasicText(trailingLabel.uppercase(), style = Type.microLabel.copy(color = LocalAccent.current))
                        }
                        content()
                    }
                }
            }
        }
    }
}

// How many milliseconds into its rising each element of an opening sheet is: they start one after another and each takes the same time.
val LocalSheetRise = staticCompositionLocalOf<() -> Float> { { Float.MAX_VALUE } }

// An element that fades in from below as its turn comes in an opening sheet; `order` is its place in the line. A sheet that does not open out of a button never delays it.
@Composable
fun Modifier.risesIn(order: Int): Modifier {
    val elapsed = LocalSheetRise.current
    return graphicsLayer {
        val progress = Motion.powerTwoOut.transform(((elapsed() - order * Motion.RISE_STAGGER_MS) / Motion.RISE_MS).coerceIn(0f, 1f))
        alpha = progress
        translationY = (1f - progress) * RISE_DISTANCE.toPx()
    }
}

private val RISE_DISTANCE = 28.dp
private const val RISE_ORDERS = 8
private const val RISE_TOTAL_MS = (Motion.RISE_MS + RISE_ORDERS * Motion.RISE_STAGGER_MS).toFloat()

private val SCRIM = Color(0x4D000000)
// A pull past this share of the sheet's height, or a flick faster than this in pixels per second, closes it.
private const val PULL_CLOSE = 0.25f
private const val PULL_FLING = 1200f
// Where a floating sheet hangs: this far under the status bar, no wider than this.
private val FLOATING_TOP = 72.dp
private val FLOATING_WIDTH = 420.dp
// A full-height sheet rises closer to the status bar than the 12dp margin its sides and foot keep.
private val FULL_HEIGHT_TOP = 4.dp

// A hairline under a row of a sheet, inset to the row's text, so rows read as separate lines.
fun Modifier.rowDivider(): Modifier = drawBehind {
    val inset = 20.dp.toPx()
    drawLine(Palette.border, Offset(inset, size.height - 0.5f), Offset(size.width - inset, size.height - 0.5f), strokeWidth = 1.dp.toPx())
}

// A small label over a run of rows, as Settings and the long-press menus divide theirs.
@Composable
fun SheetHeader(text: String) {
    MicroLabel(text, Modifier.padding(start = 20.dp, top = 18.dp, bottom = 2.dp))
}

private const val DISABLED_ALPHA = 0.38f

@Composable
fun SheetRow(text: String, trailing: String? = null, color: Color = Palette.textBright, icon: (@Composable (Color) -> Unit)? = null, isEnabled: Boolean = true, onClick: () -> Unit) {
    Row(
        // A row that does not apply right now stays in its place, greyed, so the list does not jump as it comes and goes.
        Modifier.fillMaxWidth().rowDivider().then(if (isEnabled) Modifier.pressable(onClick = onClick, pressedScale = 0.98f) else Modifier.alpha(DISABLED_ALPHA)).padding(horizontal = 20.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.invoke(color)
            BasicText(text, style = Type.cardTitle.copy(color = color), maxLines = 1)
        }
        if (trailing != null) BasicText(trailing, style = Type.value.copy(color = LocalAccent.current))
    }
}

// Two moves that are alternatives share one row: the main one in words on the left, the other as only its icon over the right 30%, split by a slash that reads as "or".
@Composable
fun SplitSheetRow(text: String, icon: @Composable (Color) -> Unit, onClick: () -> Unit, sideIcon: @Composable (Color) -> Unit, onSide: () -> Unit, color: Color = Palette.textBright, sideColor: Color = color) {
    Row(Modifier.fillMaxWidth().rowDivider(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(0.7f).pressable(onClick = onClick, pressedScale = 0.98f).padding(horizontal = 20.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon(color)
            BasicText(text, style = Type.cardTitle.copy(color = color), maxLines = 1)
        }
        BasicText("/", style = Type.cardTitle.copy(color = Palette.textFaint))
        Box(Modifier.weight(0.3f).pressable(onClick = onSide, pressedScale = 0.98f).padding(vertical = 15.dp), contentAlignment = Alignment.Center) {
            sideIcon(sideColor)
        }
    }
}

@Composable
fun AlbumPickerSheet(
    visible: Boolean,
    label: String,
    albums: List<Album>,
    excludedAlbumId: Long?,
    onPick: (Album) -> Unit,
    onNewAlbum: () -> Unit,
    onDismiss: () -> Unit,
    ground: Color = Palette.ground,
) {
    OverlaySheet(visible = visible, label = label, onDismiss = onDismiss, ground = ground) {
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            items(albums.filter { it.id != excludedAlbumId }, key = { it.id }) { album ->
                SheetRow(album.name, trailing = album.items.size.toString()) { onPick(album) }
            }
            item { SheetRow("+ New album", color = Palette.textMuted, onClick = onNewAlbum) }
        }
    }
}

@Composable
fun StackPickerSheet(visible: Boolean, label: String, stacks: List<AlbumStack>, onPick: (String) -> Unit, onNewStack: () -> Unit, onDismiss: () -> Unit) =
    NamePickerSheet(visible, label, stacks.map { it.name to it.paths.size }, "+ New group", onPick, onNewStack, onDismiss)

// A list of named things with their counts, ending with the way to make a new one.
@Composable
fun NamePickerSheet(visible: Boolean, label: String, choices: List<Pair<String, Int>>, newLabel: String, onPick: (String) -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    OverlaySheet(visible = visible, label = label, onDismiss = onDismiss) {
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            items(choices, key = { it.first }) { (name, count) ->
                SheetRow(name, trailing = count.toString()) { onPick(name) }
            }
            item { SheetRow(newLabel, color = Palette.textMuted, onClick = onNew) }
        }
    }
}

@Composable
fun GroupPickerSheet(
    visible: Boolean,
    label: String,
    groups: List<PrivateGroup>,
    excludedGroupName: String?,
    onPick: (String) -> Unit,
    onNewGroup: () -> Unit,
    onDismiss: () -> Unit,
    ground: Color = Palette.ground,
) {
    OverlaySheet(visible = visible, label = label, onDismiss = onDismiss, ground = ground) {
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            items(groups.filter { it.name != excludedGroupName }, key = { it.directory.absolutePath }) { group ->
                SheetRow(group.name, trailing = group.items.size.toString()) { onPick(group.name) }
            }
            item { SheetRow("+ New album", color = Palette.textMuted, onClick = onNewGroup) }
        }
    }
}

// Ticking albums for a new group: every album with its count, ticked ones in the accent, and the row that makes the group once any are ticked.
@Composable
fun AlbumChoiceSheet(visible: Boolean, label: String, choices: List<Pair<String, Pair<String, Int>>>, onCreate: (Set<String>) -> Unit, onDismiss: () -> Unit, initiallyTicked: Set<String> = emptySet()) {
    var ticked by remember(visible) { mutableStateOf(initiallyTicked) }
    OverlaySheet(visible = visible, label = label, onDismiss = onDismiss) {
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            items(choices, key = { it.first }) { (key, nameAndCount) ->
                val isTicked = key in ticked
                SheetRow(
                    nameAndCount.first,
                    trailing = nameAndCount.second.toString(),
                    color = if (isTicked) LocalAccent.current else Palette.textBright,
                    icon = if (isTicked) { color -> CheckIcon(color) } else null,
                ) { ticked = if (isTicked) ticked - key else ticked + key }
            }
        }
        SheetRow(if (ticked.isEmpty()) "Create group" else "Create group with ${ticked.size}", color = if (ticked.isEmpty()) Palette.textFaint else LocalAccent.current, isEnabled = ticked.isNotEmpty()) { onCreate(ticked) }
    }
}
