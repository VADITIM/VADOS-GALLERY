package com.vaditim.gallery.media

import android.graphics.PointF
import android.graphics.RectF

// One line drawn over a photo: its points as fractions of the upright picture, so it lands on the same spot at any size; its width as a fraction of the picture's width; its colour as ARGB.
data class DrawnStroke(val points: List<PointF>, val color: Int, val width: Float)

// The same part of a picture turned a quarter clockwise `quarterTurns` times back to the upright picture it was cut from.
fun RectF.unturned(quarterTurns: Int): RectF {
    var rect = RectF(this)
    repeat(Math.floorMod(quarterTurns, 4)) { rect = RectF(rect.top, 1f - rect.right, rect.bottom, 1f - rect.left) }
    return rect
}
