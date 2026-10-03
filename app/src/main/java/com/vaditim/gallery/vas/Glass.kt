package com.vaditim.gallery.vas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

// The surface everything floating stands on: the content behind it, blurred and darkened, and no border. This is the gallery's one deliberate departure from VAS's "the border is the design" — over photographs a hairline reads as a frame around a hole, a blur reads as a pane of glass.
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

object Glass {
    fun style(ground: Color = Palette.ground): HazeStyle =
        HazeStyle(backgroundColor = ground, tint = HazeTint(Palette.glassTint), blurRadius = 26.dp, noiseFactor = 0.03f)
}

@Composable
fun Modifier.glass(shape: Shape, ground: Color = Palette.ground): Modifier {
    val state = LocalHazeState.current
    val clipped = this.clip(shape)
    return if (state == null) clipped.background(Palette.panel) else clipped.hazeEffect(state, Glass.style(ground))
}

// The status-bar edge: full blur at the top of the screen, none where it meets the content, so the grid dissolves under the clock instead of being cut by a bar.
@Composable
fun Modifier.fadingGlass(): Modifier {
    val state = LocalHazeState.current ?: return this
    return this.hazeEffect(state, Glass.style()) {
        progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
    }
}
