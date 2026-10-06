package com.vaditim.gallery.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize
import coil3.memory.MemoryCache

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
        // The whole tile even where the grid hides part of it, so a tile at the edge flies with the same shape and rounding as any other.
        return if (placed.isAttached) Rect(placed.positionInWindow(), placed.size.toSize()) else null
    }
}

// The upright picture each tile last drew, so the viewer opens on it at once instead of on black while the full photo decodes.
object TileImages {
    private val keys = HashMap<Long, MemoryCache.Key>()

    fun register(mediaId: Long, key: MemoryCache.Key?) {
        if (key != null) keys[mediaId] = key
    }

    fun of(mediaId: Long): MemoryCache.Key? = keys[mediaId]
}

// The box a photo occupies when shown "fit" inside a screen, which is where the viewer draws it.
fun fitInside(ratio: Float, width: Float, height: Float): Rect {
    var fittedWidth = width
    var fittedHeight = width / ratio
    if (fittedHeight > height) {
        fittedHeight = height
        fittedWidth = height * ratio
    }
    return Rect((width - fittedWidth) / 2f, (height - fittedHeight) / 2f, (width + fittedWidth) / 2f, (height + fittedHeight) / 2f)
}
