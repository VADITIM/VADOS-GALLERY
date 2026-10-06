package com.vaditim.gallery.vas

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.vaditim.gallery.Settings

// The VAS ground, grey ramp and text steps (dna/01-palette.md), plus the screen accents this app casts into its three sections.
object Palette {
    // The ground is the user's: a grey from black up to a dark charcoal, read from the setting so every surface on it follows a change at once.
    val ground: Color get() = (Settings.groundBrightness * Settings.MAX_GROUND_LEVEL / 255f).let { Color(it, it, it) }
    val panel = Color(0xD9121212)
    val surface = Color(0xFF202020)
    val glassTint = Color(0x8C141414)
    val pressedWash = Color(0x1FFFFFFF)
    val panelSolid = Color(0xFF121212)
    val sunkenDeep = Color(0xFF0E0E0E)
    val sunken = Color(0xFF1C1C1C)
    val border = Color(0xFF383838)
    val borderStrong = Color(0xFF454545)
    val borderControl = Color(0xFF5A5A5A)

    val textPrimary = Color(0xDEFFFFFF)
    val textBright = Color(0xFFF0F0F0)
    val textBody = Color(0xFFD8D8D8)
    val textMuted = Color(0xFFBDBDBD)
    val textLabel = Color(0xFFB0B0B0)
    val textIcon = Color(0xFF8E8E8E)
    val textFaint = Color(0xFF707070)

    val danger = Color(0xFFFF6B6B)
    // Every heart is this red, whatever section it is in: a favourite reads the same everywhere.
    val favorite = Color(0xFFFF3B4E)

    val terminalGreen = Color(0xFF2FDE75)
    val amber = Color(0xFFF09B3A)
    val hotPink = Color(0xFFFF2E88)
    // Private's own accent, the same in every section while inside it.
    val privateRed = Color(0xFFFA3438)
    val locationBlue = Color(0xFF148BC7)
    val trashGray = Color(0xFF9A9A9A)
    // Cropping and trimming wear their own accent, photo or video, so the editor reads as a mode of its own.
    val cropViolet = Color(0xFF7E55DD)

    // Photos are the only colour that matters in the viewer, so it stands on black rather than the ground: a grey frame around a picture shifts how its own blacks read.
    val viewerGround = Color(0xFF000000)
}

// The --section-color of this app: set once at the root from the active section, read by every leaf, named by none.
val LocalAccent = staticCompositionLocalOf { Palette.terminalGreen }
