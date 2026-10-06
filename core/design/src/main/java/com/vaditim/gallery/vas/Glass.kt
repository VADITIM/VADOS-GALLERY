package com.vaditim.gallery.vas

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.settings.Settings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

// The surface everything floating stands on: the content behind it, blurred and darkened, and no border. This is the gallery's one deliberate departure from VAS's "the border is the design" — over photographs a hairline reads as a frame around a hole, a blur reads as a pane of glass.
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

object Glass {
    // A black veil over the blur, its strength and the blur's both set in Settings. `ground` is what the screen really has behind its content, so empty areas blur to that rather than to black.
    fun style(ground: Color = Palette.ground): HazeStyle =
        HazeStyle(backgroundColor = ground, tint = HazeTint(Color.Black.copy(alpha = Settings.glassOpacity)), blurRadius = Settings.blurDp.dp, noiseFactor = 0.03f)
}

@Composable
fun Modifier.glass(shape: Shape, ground: Color = Palette.ground): Modifier {
    val state = LocalHazeState.current
    val clipped = this.clip(shape)
    return if (state == null) clipped.background(ground) else clipped.hazeEffect(state, Glass.style(ground))
}
