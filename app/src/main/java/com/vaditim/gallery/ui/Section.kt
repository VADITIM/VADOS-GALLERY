package com.vaditim.gallery.ui

import androidx.compose.ui.graphics.Color
import com.vaditim.gallery.vas.Palette

// The registry (dna/07-architecture.md): one ordered list generates the bar, the accents and the order. Adding a section here is the whole change.
enum class Section(val label: String, val accent: Color) {
    RECENT("RECENT", Palette.terminalGreen),
    ALBUMS("ALBUMS", Palette.amber),
    FAVORITES("FAVORITES", Palette.hotPink),
}
