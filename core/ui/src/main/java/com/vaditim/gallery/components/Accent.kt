package com.vaditim.gallery.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.vaditim.gallery.vas.LocalAccent

// The accent of the place being switched to, at once; LocalAccent itself only takes it on the cut, once the outgoing view has left.
val LocalAccentTarget = staticCompositionLocalOf<Color?> { null }

// The colour of a piece that comes and goes: arriving it already wears the new accent, leaving it keeps the one it had, and staying it changes on the cut with everything else.
@Composable
fun rememberOwnAccent(isShown: Boolean): Color {
    val onCut = LocalAccent.current
    val target = LocalAccentTarget.current ?: onCut
    // Plain fields, not state: they only remember the last composition, so writing them never asks for another.
    val memory = remember { OwnAccent(target, isShown) }
    if (isShown && !memory.wasShown) memory.color = target
    else if (isShown && onCut == target) memory.color = onCut
    memory.wasShown = isShown
    return memory.color
}

private class OwnAccent(var color: Color, var wasShown: Boolean)
