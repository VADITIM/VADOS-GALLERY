package com.vaditim.gallery.ui

import androidx.activity.compose.BackHandler
import com.vaditim.gallery.AlbumStack
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import com.vaditim.gallery.vas.LocalAccent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.glass
import com.vaditim.gallery.vas.pressable
import com.vaditim.gallery.vault.PrivateGroup

// A menu over content: a pane of glass that arrives from just below on the overshoot and leaves straight down and quicker (dna/05-motion.md §4). Tapping anywhere outside it closes it, so it never traps what is behind it.
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OverlaySheet(visible: Boolean, label: String, onDismiss: () -> Unit, ground: Color = Palette.ground, reveal: () -> Float = { 0f }, trailingLabel: String? = null, isFloating: Boolean = false, onPull: (Float) -> Unit = {}, content: @Composable () -> Unit) {
    // A gesture can raise the sheet before it is open: `reveal` 0 to 1 places it frame by frame, and the gesture opens it once it has carried it all the way.
    val isRevealing by remember { derivedStateOf { reveal() > 0f } }
    val isFollowing = { !visible && reveal() > 0f }
    // A pull down anywhere drags the sheet with the finger; let go far enough or fast enough and it carries on down from there and closes, otherwise it goes back up.
    val pull = remember { Animatable(0f) }
    var sheetHeight by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val pastEdge = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(visible) { if (visible) pull.snapTo(0f) }
    // How far down the sheet is pulled, 0 to 1 of its height, for what behind it should follow the finger.
    LaunchedEffect(Unit) { snapshotFlow { (pull.value / sheetHeight).coerceIn(0f, 1f) }.collect { onPull(it) } }
    val pullDrag = rememberDraggableState { delta -> scope.launch { pull.snapTo((pull.value + delta).coerceAtLeast(0f)) } }
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
                .drawBehind { drawRect(SCRIM.copy(alpha = SCRIM.alpha * (if (isFollowing()) reveal() else 1f) * (1f - pull.value / sheetHeight).coerceIn(0f, 1f))) }
                // The pull works anywhere on screen, not only on the sheet; a tap outside still closes it at once.
                .draggable(
                    pullDrag,
                    Orientation.Vertical,
                    enabled = visible,
                    onDragStopped = { velocity ->
                        if (pull.value > sheetHeight * PULL_CLOSE || velocity > PULL_FLING) {
                            pull.animateTo(sheetHeight + pastEdge, tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoOut), velocity)
                            onDismiss()
                        } else {
                            pull.animateTo(0f, tween(Motion.STATE_MS, easing = Motion.backOut), velocity)
                        }
                    },
                )
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            // A floating sheet hangs from a fixed edge near the top, centred, so a change in its height only ever moves its bottom.
            contentAlignment = if (isFloating) Alignment.TopCenter else Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .animateEnterExit(
                        enter = if (isRevealing && !visible) {
                            EnterTransition.None
                        } else {
                            slideInVertically(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.backOut)) { it / 6 } +
                                scaleIn(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.backOut), initialScale = 0.96f)
                        },
                        exit = slideOutVertically(tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn)) { it / 8 } +
                            scaleOut(tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn), targetScale = 0.98f),
                    )
                    .graphicsLayer { translationY = if (isFollowing()) (1f - reveal()) * (size.height + 12.dp.toPx()) else pull.value }
                    .onSizeChanged { sheetHeight = it.height.toFloat().coerceAtLeast(1f) }
                    .then(if (isFloating) Modifier.statusBarsPadding().padding(top = FLOATING_TOP) else Modifier.navigationBarsPadding())
                    .padding(12.dp)
                    .then(if (isFloating) Modifier.widthIn(max = FLOATING_WIDTH) else Modifier)
                    .fillMaxWidth()
                    .glass(Shapes.sheet, ground)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .padding(top = 18.dp, bottom = 10.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    MicroLabel(label)
                    // What the sheet is about, at the other end of its label, in the section colour.
                    if (trailingLabel != null) BasicText(trailingLabel.uppercase(), style = Type.microLabel.copy(color = LocalAccent.current))
                }
                content()
            }
        }
    }
}

private val SCRIM = Color(0x4D000000)
// A pull past this share of the sheet's height, or a flick faster than this in pixels per second, closes it.
private const val PULL_CLOSE = 0.25f
private const val PULL_FLING = 1200f
// Where a floating sheet hangs: this far under the status bar, no wider than this.
private val FLOATING_TOP = 72.dp
private val FLOATING_WIDTH = 420.dp

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
            item { SheetRow("+ New group", color = Palette.textMuted, onClick = onNewGroup) }
        }
    }
}
