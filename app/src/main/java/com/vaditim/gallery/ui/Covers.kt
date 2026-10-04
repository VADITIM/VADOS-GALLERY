package com.vaditim.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.vaditim.gallery.Settings
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.media.Thumbnail
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable

private const val COVER_PIXELS = 512
private const val PINCH_STEP = 1.28f
private val LIST_COVER = 84.dp

// Albums, private groups and locations are one kind of screen: a grid of covers whose columns, list layout, shrinking names and pinch are the same everywhere. A new cover screen is built from these, not beside them.
@Composable
fun CoverGrid(state: LazyGridState, contentPadding: PaddingValues, content: LazyGridScope.() -> Unit) {
    val haptic = LocalHapticFeedback.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(Settings.albumColumns),
        state = state,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(if (Settings.albumColumns == 1) 12.dp else 20.dp),
        modifier = Modifier.fillMaxSize().pinchAlbumColumns(haptic),
        content = content,
    )
}

// A cover with its name and count: a card in a grid, a row when there is one column.
@Composable
fun CoverCard(name: String, cover: MediaItem?, count: Int, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val request = rememberCoverRequest(cover)
    if (Settings.albumColumns == 1) {
        Row(modifier.fillMaxWidth().pressable(onClick = onClick, pressedScale = 0.98f, onLongClick = onLongClick), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(request, name, Modifier.size(LIST_COVER))
            Column(Modifier.padding(start = 18.dp).weight(1f)) {
                BasicText(name, style = Type.cardTitle.copy(fontSize = 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(count.toString(), style = Type.value.copy(fontSize = 15.sp), modifier = Modifier.padding(top = 6.dp))
            }
        }
    } else {
        Column(modifier.pressable(onClick = onClick, pressedScale = 0.96f, onLongClick = onLongClick)) {
            CoverImage(request, name, Modifier.fillMaxWidth().aspectRatio(1f))
            // Narrow cards shrink the name until it fits, down to a size that still reads; only past that is it cut.
            BasicText(
                name,
                style = Type.cardTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = Type.cardTitle.fontSize, stepSize = 0.5.sp),
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
            BasicText(
                count.toString(),
                style = Type.value,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = Type.value.fontSize, stepSize = 0.5.sp),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
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

// How far each card of a stack leans: the top one straight, the ones under it alternating sides and leaning less the deeper they lie.
fun stackTilt(depth: Int): Float = when (depth) {
    0 -> 0f
    else -> (if (depth % 2 == 1) -1f else 1f) * (STACK_TILT_DEGREES - depth).coerceAtLeast(2f)
}

// The scale of a card lying under the top one, so its leaning corners stay mostly hidden behind it.
const val STACK_UNDER_SCALE = 0.94f
private const val STACK_TILT_DEGREES = 8f
private const val STACK_VISIBLE_LAYERS = 3

// A group of covers lying on each other, each leaning a little, the first on top.
@Composable
fun StackCard(name: String, covers: List<MediaItem?>, count: Int, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val requests = covers.take(STACK_VISIBLE_LAYERS).map { rememberCoverRequest(it) }
    val stack: @Composable (Modifier) -> Unit = { size ->
        Box(size) {
            for (depth in requests.indices.reversed()) {
                CoverImage(
                    requests[depth],
                    name,
                    Modifier.matchParentSize().graphicsLayer {
                        rotationZ = stackTilt(depth)
                        val scale = if (depth == 0) 1f else STACK_UNDER_SCALE
                        scaleX = scale
                        scaleY = scale
                    },
                )
            }
        }
    }
    if (Settings.albumColumns == 1) {
        Row(modifier.fillMaxWidth().pressable(onClick = onClick, pressedScale = 0.98f, onLongClick = onLongClick), verticalAlignment = Alignment.CenterVertically) {
            stack(Modifier.size(LIST_COVER))
            Column(Modifier.padding(start = 18.dp).weight(1f)) {
                BasicText(name, style = Type.cardTitle.copy(fontSize = 20.sp, color = LocalAccent.current), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(count.toString(), style = Type.value.copy(fontSize = 15.sp), modifier = Modifier.padding(top = 6.dp))
            }
        }
    } else {
        Column(modifier.pressable(onClick = onClick, pressedScale = 0.96f, onLongClick = onLongClick)) {
            stack(Modifier.fillMaxWidth().aspectRatio(1f))
            BasicText(
                name,
                style = Type.cardTitle.copy(color = LocalAccent.current),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = Type.cardTitle.fontSize, stepSize = 0.5.sp),
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
            BasicText(
                count.toString(),
                style = Type.value,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = Type.value.fontSize, stepSize = 0.5.sp),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}

// The card at the end of an opened stack that lays it back down.
@Composable
fun CollapseCard(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val glyph = @Composable { BasicText("‹", style = Type.title.copy(color = LocalAccent.current)) }
    if (Settings.albumColumns == 1) {
        Row(modifier.fillMaxWidth().pressable(onClick = onClick, pressedScale = 0.98f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(LIST_COVER).clip(Shapes.cover).background(Palette.surface), contentAlignment = Alignment.Center) { glyph() }
            BasicText(label, style = Type.cardTitle.copy(fontSize = 20.sp, color = Palette.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 18.dp))
        }
    } else {
        Column(modifier.pressable(onClick = onClick, pressedScale = 0.96f)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(Shapes.cover).background(Palette.surface), contentAlignment = Alignment.Center) { glyph() }
            BasicText(
                label,
                style = Type.cardTitle.copy(color = Palette.textMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = Type.cardTitle.fontSize, stepSize = 0.5.sp),
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
        }
    }
}

@Composable
private fun CoverImage(request: ImageRequest?, name: String, modifier: Modifier) {
    Box(modifier.clip(Shapes.cover).background(Palette.sunken)) {
        if (request != null) {
            AsyncImage(model = request, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

// The "make a new one" card at the end of a grid of covers: albums and private groups both end with one.
@Composable
fun AddCard(label: String, onClick: () -> Unit) {
    if (Settings.albumColumns == 1) {
        Row(Modifier.fillMaxWidth().pressable(onClick = onClick, pressedScale = 0.98f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(LIST_COVER).clip(Shapes.cover).background(Palette.surface), contentAlignment = Alignment.Center) {
                BasicText("+", style = Type.title.copy(color = LocalAccent.current))
            }
            BasicText(label, style = Type.cardTitle.copy(fontSize = 20.sp, color = Palette.textMuted), modifier = Modifier.padding(start = 18.dp))
        }
    } else {
        Column(Modifier.pressable(onClick = onClick, pressedScale = 0.96f)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(Shapes.cover).background(Palette.surface), contentAlignment = Alignment.Center) {
                BasicText("+", style = Type.title.copy(color = LocalAccent.current))
            }
            BasicText(label, style = Type.cardTitle.copy(color = Palette.textMuted), maxLines = 1, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
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
            if (event.changes.count { it.pressed } >= 2) {
                zoom *= event.calculateZoom()
                val before = Settings.albumColumns
                if (zoom > PINCH_STEP) {
                    Settings.updateAlbumColumns(Settings.albumColumns - 1)
                    zoom = 1f
                } else if (zoom < 1f / PINCH_STEP) {
                    Settings.updateAlbumColumns(Settings.albumColumns + 1)
                    zoom = 1f
                }
                if (Settings.albumColumns != before) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
