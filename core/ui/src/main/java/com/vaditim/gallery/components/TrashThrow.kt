package com.vaditim.gallery.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.media.Thumbnail
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Shapes
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

// Photos thrown into the trash: each flies from where it stood to the trash button on a short arc, shrinking as it goes, and fades into the button, which swells as it swallows them.
// The photo itself stays hidden from the moment it is thrown until the delete has taken it off screen; the delete runs once they have landed.
object TrashThrow {
    // A picture standing somewhere other than a grid tile (the open photo, a duplicate), which a throw starts from before it looks for a tile.
    private class Source(val coordinates: LayoutCoordinates, val picture: () -> MemoryCache.Key?, val isWhole: Boolean)

    private val sources = HashMap<Long, Source>()
    private var target: LayoutCoordinates? = null
    internal val throws = mutableStateListOf<Throw>()
    // Counts throws that have landed, so every trash button on screen swells once for each.
    internal var landings by mutableIntStateOf(0)

    internal fun registerSource(mediaId: Long, placed: LayoutCoordinates, picture: () -> MemoryCache.Key?, isWhole: Boolean) {
        sources[mediaId] = Source(placed, picture, isWhole)
    }

    internal fun forgetSource(mediaId: Long, placed: LayoutCoordinates?) {
        if (placed == null || sources[mediaId]?.coordinates === placed) sources.remove(mediaId)
    }

    internal fun registerTarget(placed: LayoutCoordinates) {
        target = placed
    }

    internal fun forgetTarget(placed: LayoutCoordinates?) {
        if (target === placed) target = null
    }

    // Read where the photo draws, so only its layer changes while it is away.
    fun isThrown(mediaId: Long): Boolean = throws.any { mediaId in it.ids }

    // Throws the photos that are on screen and runs the delete once they are in; with no trash button or none of them on screen, the delete runs at once.
    fun throwAway(items: List<MediaItem>, delete: () -> Unit) {
        val bin = target?.takeIf { it.isAttached }?.boundsInWindow()
        val flights = if (bin == null) emptyList() else items.mapNotNull { item -> flightOf(item) }.take(MAX_FLIGHTS)
        if (bin == null || flights.isEmpty()) {
            delete()
            return
        }
        flights.forEachIndexed { index, flight -> flight.delayMs = min(index * Motion.TRASH_THROW_STAGGER_MS, Motion.TRASH_THROW_STAGGER_MAX_MS) }
        throws.add(Throw(flights, bin, items.map { it.id }.toSet(), delete))
    }

    private fun flightOf(item: MediaItem): Flight? {
        val source = sources[item.id]?.takeIf { it.coordinates.isAttached }
        if (source != null) return Flight(item, Rect(source.coordinates.positionInWindow(), source.coordinates.size.toSize()), source.picture(), source.isWhole)
        // Only picked tiles are thrown, and a picked tile stands smaller than its bounds.
        val tile = TileBounds.of(item.id) ?: return null
        val inset = Offset(tile.width, tile.height) * ((1f - SELECTED_TILE_SCALE) / 2f)
        return Flight(item, Rect(tile.topLeft + inset, tile.bottomRight - inset), TileImages.of(item.id), isWhole = false)
    }

    internal fun land(thrown: Throw) {
        if (thrown.isLanded) return
        thrown.isLanded = true
        landings++
    }

    private const val MAX_FLIGHTS = 30
}

// One photo's way into the trash. A whole photo (the open one) keeps its proportions inside the box; a tile is cropped to it.
internal class Flight(val item: MediaItem, val from: Rect, val picture: MemoryCache.Key?, val isWhole: Boolean) {
    var delayMs = 0
    val progress = Animatable(0f)
}

internal class Throw(val flights: List<Flight>, val bin: Rect, val ids: Set<Long>, val delete: () -> Unit) {
    var isLanded = false
    var isDeleted = false
}

// Marks the trash button the photos fly into; it swells once each time a throw lands in it.
fun Modifier.trashTarget(): Modifier = composed {
    val swell = remember { Animatable(1f) }
    val landingsAtStart = remember { TrashThrow.landings }
    var placed by remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(TrashThrow.landings) {
        if (TrashThrow.landings == landingsAtStart) return@LaunchedEffect
        swell.animateTo(SWALLOW_SCALE, tween(Motion.PRESS_MS, easing = Motion.powerTwoOut))
        swell.animateTo(1f, tween(Motion.RELEASE_MS, easing = Motion.backOut))
    }
    DisposableEffect(Unit) { onDispose { TrashThrow.forgetTarget(placed) } }
    this
        .onGloballyPositioned {
            placed = it
            TrashThrow.registerTarget(it)
        }
        .graphicsLayer {
            scaleX = swell.value
            scaleY = swell.value
        }
}

// Marks where a photo stands outside a grid tile (the open photo, a duplicate), so a throw starts from there; `picture` is what it shows, read when it is thrown.
// A whole photo is drawn fitted, keeping its proportions; anything else is cropped like a tile.
fun Modifier.trashSource(mediaId: Long, picture: () -> MemoryCache.Key? = { null }, isWhole: Boolean = false): Modifier = composed {
    var placed by remember(mediaId) { mutableStateOf<LayoutCoordinates?>(null) }
    DisposableEffect(mediaId) { onDispose { TrashThrow.forgetSource(mediaId, placed) } }
    this.onGloballyPositioned {
        placed = it
        TrashThrow.registerSource(mediaId, it, picture, isWhole)
    }
}

// Over everything: the photos in flight. Laid over the whole window, so a flight is drawn where its photo stood.
@Composable
fun TrashThrowLayer(modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        TrashThrow.throws.forEach { thrown -> key(thrown) { Flights(thrown, origin) } }
    }
}

@Composable
private fun Flights(thrown: Throw, origin: Offset) {
    LaunchedEffect(thrown) {
        try {
            coroutineScope {
                thrown.flights.forEach { flight ->
                    launch {
                        delay(flight.delayMs.toLong())
                        flight.progress.animateTo(1f, tween(Motion.TRASH_THROW_MS, easing = LinearEasing))
                    }
                }
            }
            TrashThrow.land(thrown)
            // The button swells first, so the change it makes (a bar turning back into the nav) follows the swallow rather than cutting it.
            delay(Motion.PRESS_MS.toLong())
            thrown.isDeleted = true
            thrown.delete()
            // Hidden until the delete has taken the photos off screen; one the delete did not take comes back.
            delay(Motion.TRASH_HIDE_MS)
        } finally {
            TrashThrow.land(thrown)
            if (!thrown.isDeleted) {
                thrown.isDeleted = true
                thrown.delete()
            }
            TrashThrow.throws.remove(thrown)
        }
    }
    thrown.flights.forEach { flight -> FlyingPhoto(flight, thrown.bin.center - origin, origin) }
}

@Composable
private fun FlyingPhoto(flight: Flight, bin: Offset, origin: Offset) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val from = flight.from.translate(-origin)
    val request = remember(flight) {
        val item = flight.item
        val pixels = from.width.roundToInt().coerceAtLeast(1)
        // A tile's own thumbnail, or the open photo's picture as the viewer drew it; either is already in memory, so the flight never waits on a decode.
        val data: Any = if (!flight.isWhole && item.uri.scheme == "content") Thumbnail.of(item, pixels) else item.uri
        ImageRequest.Builder(context).data(data).size(pixels).placeholderMemoryCacheKey(flight.picture).build()
    }
    val start = from.center
    // Up first by a little, then down into the bin, as a thing tossed rather than slid.
    val lift = min(with(density) { TOSS_LIFT.toPx() }, (bin - start).getDistance() * 0.25f)
    val control = Offset(start.x + (bin.x - start.x) * 0.25f, min(start.y, bin.y) - lift)
    val endScale = with(density) { LANDED_SIZE.toPx() } / maxOf(from.width, from.height)
    Box(
        Modifier
            .offset { IntOffset(from.left.roundToInt(), from.top.roundToInt()) }
            .size(with(density) { from.width.toDp() }, with(density) { from.height.toDp() })
            .graphicsLayer {
                val t = flight.progress.value
                val travel = Motion.powerTwoIn.transform(t)
                val rest = 1f - travel
                val position = start * (rest * rest) + control * (2f * rest * travel) + bin * (travel * travel)
                translationX = position.x - start.x
                translationY = position.y - start.y
                val scale = 1f + (endScale - 1f) * Motion.powerTwoOut.transform(t)
                scaleX = scale
                scaleY = scale
                alpha = if (t >= 1f) 0f else (1f - (t - FADE_FROM) / (1f - FADE_FROM)).coerceIn(0f, 1f)
            }
            .clip(if (flight.isWhole) Shapes.viewerPhoto else Shapes.tile),
    ) {
        AsyncImage(model = request, contentDescription = null, contentScale = if (flight.isWhole) ContentScale.Fit else ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

private const val SWALLOW_SCALE = 1.3f
// The share of the flight after which the photo fades into the button.
private const val FADE_FROM = 0.7f
private val TOSS_LIFT = 72.dp
private val LANDED_SIZE = 18.dp
