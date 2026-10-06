package com.vaditim.gallery.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import com.vaditim.gallery.components.TileBounds
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.vas.Motion

// How far the viewer has grown into place before its buttons start arriving.
private const val VIEWER_CHROME_AT = 0.85f

// The viewer growing out of the tile it was opened from and shrinking back into the tile of the photo it ends on.
@Stable
class ViewerTransition {
    // `request` is what is wanted open; `shown` stays composed through the closing animation.
    var request by mutableStateOf<ViewerRequest?>(null)
    var shown by mutableStateOf<ViewerRequest?>(null)
        private set
    var currentId by mutableStateOf<Long?>(null)
    var tile by mutableStateOf<Rect?>(null)
        private set
    var ratio by mutableFloatStateOf(1f)
        private set
    // How far a swipe down has already pulled the viewer towards its tile (0 to 1); the draw lambdas fold it into the animation's progress.
    var pull by mutableFloatStateOf(0f)
        private set
    // The photo whose tile a pull has already scrolled into view, so it is asked for once.
    private var revealingId: Long? = null
    private val photoRatios = HashMap<Long, Float>()
    val progress = Animatable(0f)

    // The viewer's buttons come in once the photo has nearly grown into place, and leave as soon as it starts closing.
    val isSettled by derivedStateOf { request != null && progress.value > VIEWER_CHROME_AT }
    // Anything short of full size is on its way to or from its tile, where the navigation lies in front of it.
    val isShrunk by derivedStateOf { progress.value * (1f - pull) < 1f }
    val isOpen: Boolean get() = request != null

    // Read only where it draws, so the animation never recomposes the library.
    fun growth(): Float = if (shown == null) 0f else progress.value * (1f - pull)

    fun open(source: ViewerSource, index: Int) {
        request = ViewerRequest(source, index)
    }

    fun close() {
        request = null
    }

    fun rememberRatio(id: Long, value: Float) {
        photoRatios[id] = value
    }

    // A photo's proportions as the viewer last decoded them (the stored width and height ignore rotation), else as stored, else square.
    private fun ratioOf(item: MediaItem?): Float =
        item?.let { photoRatios[it.id] ?: if (it.width > 0 && it.height > 0) it.width.toFloat() / it.height else null } ?: 1f

    // Runs whenever the request changes: grows the viewer in, or shrinks it back onto the tile of the photo it ends on, bringing that tile on screen first.
    suspend fun animate(itemsFor: (ViewerSource) -> List<MediaItem>, reveal: suspend (Long) -> Unit) {
        val opening = request
        if (opening != null) {
            val opened = itemsFor(opening.source).getOrNull(opening.startIndex)
            currentId = opened?.id
            tile = TileBounds.of(opened?.id)
            ratio = ratioOf(opened)
            pull = 0f
            revealingId = null
            shown = opening
            progress.snapTo(0f)
            progress.animateTo(1f, tween(Motion.VIEWER_ENTER_MS, easing = Motion.powerTwoOut))
        } else if (shown != null) {
            val closingId = currentId
            if (closingId != null && TileBounds.of(closingId) == null) {
                reveal(closingId)
                withFrameNanos { }
                withFrameNanos { }
            }
            tile = TileBounds.of(closingId)
            ratio = ratioOf(shown?.let { closing -> itemsFor(closing.source).firstOrNull { it.id == closingId } })
            if (pull > 0f) {
                // Closed by a pull: the shrink carries on from where the finger let go, already moving, instead of restarting from full size.
                progress.snapTo(progress.value * (1f - pull))
                pull = 0f
                progress.animateTo(0f, tween(Motion.VIEWER_CLOSE_MS, easing = Motion.powerTwoOut))
            } else {
                progress.animateTo(0f, tween(Motion.VIEWER_CLOSE_MS, easing = Motion.powerThreeInOut))
            }
            shown = null
            pull = 0f
        }
    }

    // The tile is brought on screen as soon as the pull starts, so the shrink aims at it from the first frame instead of switching target on release.
    fun onPull(fraction: Float, reveal: (Long) -> Unit) {
        val pulledId = currentId
        tile = TileBounds.of(pulledId)
        ratio = photoRatios[pulledId] ?: ratio
        pull = fraction
        if (fraction > 0f && tile == null && pulledId != null && revealingId != pulledId) {
            revealingId = pulledId
            reveal(pulledId)
        }
    }
}
