package com.vaditim.gallery.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.glass
import com.vaditim.gallery.vas.Palette
import androidx.compose.ui.graphics.Color
import com.vaditim.gallery.vas.pressable

// The round glass button of the top row; the crop screen's and the viewer's back are this same button. `ground` is what the screen has behind it.
@Composable
fun TopButton(onClick: () -> Unit, ground: Color = Palette.ground, icon: @Composable () -> Unit) {
    Box(Modifier.pressable(onClick = onClick).glass(Shapes.capsule, ground).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) { icon() }
}
