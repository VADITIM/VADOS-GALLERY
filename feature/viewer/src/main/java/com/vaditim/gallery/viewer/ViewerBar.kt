package com.vaditim.gallery.viewer

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import dev.chrisbanes.haze.HazeState

// Ties the library's nav to the open viewer, so the one nav changes into the viewer's buttons instead of a second bar standing in.
@Stable
class ViewerBar {
    // A delete from the bar waiting on Confirm, so its button shows it.
    var isPendingDelete by mutableStateOf(false)
    // What the bar's buttons ask of the viewer, set by the viewer while it is open.
    var onCrop: () -> Unit = {}
    var onDelete: () -> Unit = {}
    var onMore: () -> Unit = {}
    // Off once a tap on the photo has sent the buttons away.
    var isShown by mutableStateOf(true)
    // How far a swipe on the photo has gone, read only where the bar draws.
    var pull by mutableFloatStateOf(0f)
    // The photo's own blur, so the nav over it frosts the photo rather than the grid hidden under it.
    var hazeState by mutableStateOf<HazeState?>(null)

    fun reset() {
        isShown = true
        pull = 0f
    }
}

// Shrinks the bar with a swipe on the photo frame by frame, as the viewer's other buttons do.
fun Modifier.followingPull(bar: ViewerBar): Modifier = graphicsLayer {
    val out = (bar.pull / CHROME_PULL_SHARE).coerceIn(0f, 1f)
    scaleX = 1f - out
    scaleY = 1f - out
}
