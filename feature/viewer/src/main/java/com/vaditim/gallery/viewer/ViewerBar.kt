package com.vaditim.gallery.viewer

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

    fun reset() {
        isShown = true
        pull = 0f
    }
}

// The share of the full pull by which the buttons have left completely, and by which the nav has changed back.
const val CHROME_PULL_SHARE = 0.35f
