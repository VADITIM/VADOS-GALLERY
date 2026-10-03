package com.vaditim.gallery.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow

// Where each visible grid tile is on screen, so the viewer can grow out of a tile and shrink back into it. Tiles register while composed and drop out when they scroll away; the bounds are read on demand, so they are never stale.
object TileBounds {
    private val coordinates = HashMap<Long, LayoutCoordinates>()

    fun register(mediaId: Long, placed: LayoutCoordinates) {
        coordinates[mediaId] = placed
    }

    fun forget(mediaId: Long) {
        coordinates.remove(mediaId)
    }

    fun of(mediaId: Long?): Rect? {
        val placed = coordinates[mediaId ?: return null] ?: return null
        return if (placed.isAttached) placed.boundsInWindow() else null
    }
}
