package com.vaditim.gallery.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vaditim.gallery.components.Selection
import com.vaditim.gallery.media.MediaItem

// What is picked out on screen, and the delete waiting on Confirm above the bar; anything else the user does lets that delete go.
@Stable
class LibrarySelection(private val onTick: () -> Unit) {
    var ids by mutableStateOf(emptySet<Long>())
        private set
    // Albums (by folder path) or private groups (by name) picked in a cover grid; only one of the two grids is ever on screen.
    var covers by mutableStateOf(emptySet<String>())
    var pendingDelete by mutableStateOf<(() -> Unit)?>(null)
    // Only the favourites of the photo grid on screen; changing place lets it go.
    var isFavoritesOnly by mutableStateOf(false)
    var isRearranging by mutableStateOf(false)

    // The grids' view of the picked photos; read fresh, so it always carries the current set.
    val photos: Selection get() = Selection(ids, onToggle = ::toggle)

    fun toggle(item: MediaItem) {
        ids = if (item.id in ids) ids - item.id else ids + item.id
        onTick()
        pendingDelete = null
    }

    fun toggleCovers(keys: List<String>) {
        covers = if (keys.all { it in covers }) covers - keys.toSet() else covers + keys
        onTick()
        pendingDelete = null
    }

    fun clear() {
        ids = emptySet()
        covers = emptySet()
        pendingDelete = null
    }

    // Leaving a place lets go of what was picked in it, the favourites-only filter and rearranging.
    fun reset() {
        clear()
        isFavoritesOnly = false
        isRearranging = false
    }

    fun confirmThen(action: () -> Unit) {
        pendingDelete = action
    }
}
