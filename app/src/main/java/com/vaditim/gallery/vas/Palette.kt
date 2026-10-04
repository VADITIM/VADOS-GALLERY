package com.vaditim.gallery.vas

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// The VAS ground, grey ramp and text steps (dna/01-palette.md), plus the screen accents this app casts into its three sections.
object Palette {
    val ground = Color(0xFF181818)
    val panel = Color(0xD9121212)
    val surface = Color(0xFF202020)
    val glassTint = Color(0x8C141414)
    val pressedWash = Color(0x1FFFFFFF)
    val panelSolid = Color(0xFF121212)
    val sunkenDeep = Color(0xFF0E0E0E)
    val sunken = Color(0xFF1C1C1C)
    val border = Color(0xFF262626)
    val borderStrong = Color(0xFF2C2C2C)
    val borderControl = Color(0xFF3A3A3A)

    val textPrimary = Color(0xDEFFFFFF)
    val textBright = Color(0xFFF0F0F0)
    val textBody = Color(0xFFD8D8D8)
    val textMuted = Color(0xFF9A9A9A)
    val textLabel = Color(0xFF8A8A8A)
    val textIcon = Color(0xFF6A6A6A)
    val textFaint = Color(0xFF4A4A4A)

    val danger = Color(0xFFFF6B6B)
    // Every heart is this red, whatever section it is in: a favourite reads the same everywhere.
    val favorite = Color(0xFFFF3B4E)

    val terminalGreen = Color(0xFF5BFD5B)
    val amber = Color(0xFFF09B3A)
    val hotPink = Color(0xFFFF2E88)

    // Photos are the only colour that matters in the viewer, so it stands on black rather than the ground: a grey frame around a picture shifts how its own blacks read.
    val viewerGround = Color(0xFF000000)
}

// The --section-color of this app: set once at the root from the active section, read by every leaf, named by none.
val LocalAccent = staticCompositionLocalOf { Palette.terminalGreen }
