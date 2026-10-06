package com.vaditim.gallery.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.glass
import com.vaditim.gallery.vas.pressable

// The round glass button of the top row; the crop screen's back is this same button.
@Composable
fun TopButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(Modifier.pressable(onClick = onClick).glass(Shapes.capsule).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) { icon() }
}
