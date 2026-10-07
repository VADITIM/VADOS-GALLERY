package com.vaditim.gallery.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.media.Thumbnail
import com.vaditim.gallery.settings.AlbumArrangement
import com.vaditim.gallery.settings.Settings
import com.vaditim.gallery.settings.SettingsView
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable
import kotlin.math.min
import kotlinx.coroutines.delay

private const val COVER_PIXELS = 512
private const val SHARP_COVER_DELAY_MS = 120L
private const val PINCH_STEP = 1.28f
val LIST_COVER = 84.dp
val COVER_GAP = 14.dp

// The room between rows of covers; rows of one column lie closer.
@Composable
fun coverRowGap(): Dp = if (coverColumns() == 1) 12.dp else 20.dp

// Albums, private groups and locations are one kind of screen: a grid of covers whose columns, list layout, shrinking names and pinch are the same everywhere. A new cover screen is built from these, not beside them.
@Composable
fun CoverGrid(state: LazyGridState, contentPadding: PaddingValues, content: LazyGridScope.() -> Unit) {
    val haptic = LocalHapticFeedback.current
    HoldUnderSheet(state)
    ProvideEntrance {
    LazyVerticalGrid(
        columns = GridCells.Fixed(coverColumns()),
        state = state,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(COVER_GAP),
        verticalArrangement = Arrangement.spacedBy(coverRowGap()),
        modifier = Modifier.fillMaxSize().pinchAlbumColumns(haptic),
        content = content,
    )
    }
}

// The view a screen belongs to, held by each section while it leaves, so a section change never re-lays the outgoing covers with the incoming view's settings.
val LocalSettingsView = compositionLocalOf<SettingsView?> { null }

@Composable
fun currentSettingsView(): SettingsView = LocalSettingsView.current ?: Settings.view

@Composable
fun coverColumns(): Int = Settings.coverColumnsIn(currentSettingsView())

// Whether cover names take the section colour, as the albums made inside Favorites do; real folders keep plain names.
val LocalAccentedCoverNames = staticCompositionLocalOf { false }

// A cover with its name and count: a card in a grid, a row when there is one column.
@Composable
fun CoverCard(name: String, cover: MediaItem?, count: Int, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, modifier: Modifier = Modifier, isSelected: Boolean = false, labelAlpha: () -> Float = { 1f }, isList: Boolean = coverColumns() == 1) {
    val request = rememberCoverRequest(cover)
    val sharpRequest = rememberSharpCoverRequest(cover)
    val nameColor = if (LocalAccentedCoverNames.current) LocalAccent.current else Palette.textBright
    if (isList) {
        Row(modifier.fillMaxWidth().pressable(onClick = onClick, pressedScale = 0.98f, onLongClick = onLongClick), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(request, sharpRequest, name, Modifier.size(LIST_COVER), isSelected)
            Column(Modifier.padding(start = 18.dp).weight(1f).graphicsLayer { alpha = labelAlpha() }) {
                BasicText(name, style = Type.cardTitle.copy(fontSize = 20.sp, color = nameColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(count.toString(), style = Type.value.copy(fontSize = 15.sp), modifier = Modifier.padding(top = 6.dp))
            }
        }
    } else {
        Column(modifier.pressable(onClick = onClick, pressedScale = 0.96f, onLongClick = onLongClick)) {
            CoverImage(request, sharpRequest, name, Modifier.fillMaxWidth().aspectRatio(1f), isSelected)
            // Narrow cards shrink the name until it fits, down to a size that still reads; only past that is it cut.
            BasicText(
                name,
                style = Type.cardTitle.copy(color = nameColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = Type.cardTitle.fontSize, stepSize = 0.5.sp),
                modifier = Modifier.padding(start = 4.dp, top = 10.dp).graphicsLayer { alpha = labelAlpha() },
            )
            BasicText(
                count.toString(),
                style = Type.value,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = Type.value.fontSize, stepSize = 0.5.sp),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp).graphicsLayer { alpha = labelAlpha() },
            )
        }
    }
}

@Composable
private fun rememberCoverRequest(cover: MediaItem?): ImageRequest? {
    val context = LocalContext.current
    return remember(cover?.uri) {
        cover?.let {
            // Private covers live outside MediaStore and have no cached thumbnail, so they are decoded from the file.
            val data: Any = if (it.uri.scheme == "content") Thumbnail(it.uri, COVER_PIXELS) else it.uri
            ImageRequest.Builder(context).data(data).size(COVER_PIXELS).build()
        }
    }
}

// The system's cached thumbnail is small, and for some photos stale (pixelated, or turned the wrong way), so the cover gets the photo itself on top once it has stood on screen a moment, as a tile in a grid does.
@Composable
private fun rememberSharpCoverRequest(cover: MediaItem?): ImageRequest? {
    val context = LocalContext.current
    var isStanding by remember(cover?.uri) { mutableStateOf(false) }
    LaunchedEffect(cover?.uri) {
        if (cover != null && cover.uri.scheme == "content") {
            delay(SHARP_COVER_DELAY_MS)
            isStanding = true
        }
    }
    if (!isStanding) return null
    return remember(cover?.uri) {
        cover?.let { ImageRequest.Builder(context).data(it.uri).size(COVER_PIXELS).apply { if (it.isVideo) decoderFactory(VideoFrameDecoder.Factory()) }.build() }
    }
}

// The cards under the top one fan out to the right, each leaning further, shifted further and a little smaller and darker, so the stack reads as several cards at a glance.
fun stackTilt(depth: Int): Float = STACK_TILT_DEGREES * min(depth, STACK_VISIBLE_LAYERS - 1)
fun stackShift(depth: Int): Dp = STACK_SHIFT * min(depth, STACK_VISIBLE_LAYERS - 1)
fun stackScale(depth: Int): Float = 1f - STACK_SHRINK * min(depth, STACK_VISIBLE_LAYERS - 1)
fun stackShade(depth: Int): Float = 1f - STACK_DARKEN * min(depth, STACK_VISIBLE_LAYERS - 1)

private const val STACK_TILT_DEGREES = 7f
private val STACK_SHIFT = 9.dp
private const val STACK_SHRINK = 0.05f
private const val STACK_DARKEN = 0.2f
private const val STACK_VISIBLE_LAYERS = 3

// The arrow at the right end of an opened group's heading that lays the group back down.
@Composable
fun CollapseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.pressable(onClick = onClick, pressedScale = 0.9f).padding(horizontal = 14.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        BasicText("‹", style = Type.title.copy(color = LocalAccent.current))
    }
}

// `shade` below 1 darkens the picture, for the cards lying deeper in a stack.
@Composable
private fun CoverImage(request: ImageRequest?, sharpRequest: ImageRequest?, name: String, modifier: Modifier, isSelected: Boolean = false, shade: Float = 1f) {
    val selectedScale by animateFloatAsState(if (isSelected) 0.9f else 1f, tween(Motion.STATE_MS, easing = Motion.backOut), label = "selected")
    Box(modifier.graphicsLayer { scaleX = selectedScale; scaleY = selectedScale }.clip(Shapes.cover).background(Palette.sunken)) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                colorFilter = if (shade < 1f) ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(shade, shade, shade, 1f) }) else null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (sharpRequest != null) {
            AsyncImage(
                model = sharpRequest,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                colorFilter = if (shade < 1f) ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(shade, shade, shade, 1f) }) else null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isSelected) Box(Modifier.fillMaxSize().border(3.dp, LocalAccent.current, Shapes.cover))
    }
}

// A cover that takes `span` cells of a row but draws in the first, so the row ends after it; cells keep their width and gap.
@Composable
fun SpanCell(span: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (span <= 1) {
        Box(modifier) { content() }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(COVER_GAP)) {
            Box(Modifier.weight(1f)) { content() }
            repeat(span - 1) { Spacer(Modifier.weight(1f)) }
        }
    }
}

// The "make a new one" card at the end of a grid of covers: albums and private groups both end with one.
@Composable
fun AddCard(label: String, onClick: () -> Unit, hasDivider: Boolean = true, modifier: Modifier = Modifier) {
    // The whole row is the button: a divider above (a card right under another leaves it out), the plus with its label under it, centred.
    Column(modifier.fillMaxWidth().pressable(onClick = onClick, pressedScale = 0.98f), horizontalAlignment = Alignment.CenterHorizontally) {
        if (hasDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.border))
        Column(Modifier.padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlusIcon(LocalAccent.current, size = 28.dp)
            BasicText(label, style = Type.cardTitle.copy(color = Palette.textMuted), maxLines = 1)
        }
    }
}

// New group and New album side by side, group on the left; without groups, New album alone and centred.
@Composable
fun AddCardRow(onNewAlbum: () -> Unit, onNewGroup: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.border))
        Row(Modifier.fillMaxWidth()) {
            if (onNewGroup != null) AddCard("New group", onClick = onNewGroup, hasDivider = false, modifier = Modifier.weight(1f))
            AddCard("New album", onClick = onNewAlbum, hasDivider = false, modifier = Modifier.weight(1f))
        }
    }
}

// Two fingers change how many covers sit in a row; one finger still scrolls.
private fun Modifier.pinchAlbumColumns(haptic: HapticFeedback): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zoom = 1f
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            // Grouped albums lie as rows, so there is no column count to pinch.
            if (event.changes.count { it.pressed } >= 2 && !(Settings.groupedAlbumsInView && Settings.view.canGroup)) {
                zoom *= event.calculateZoom()
                val before = Settings.albumColumnsInView
                if (zoom > PINCH_STEP) {
                    Settings.updateAlbumColumns(Settings.albumColumnsInView - 1)
                    zoom = 1f
                } else if (zoom < 1f / PINCH_STEP) {
                    Settings.updateAlbumColumns(Settings.albumColumnsInView + 1)
                    zoom = 1f
                }
                if (Settings.albumColumnsInView != before) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
