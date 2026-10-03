package com.vaditim.gallery.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Panel
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable

// Navigation is the bar and only the bar: a horizontal swipe belongs to the viewer's pager, so sections are never swiped between (dna/06-interaction.md, gesture ownership).
@Composable
fun SectionBar(active: Section, onSelect: (Section) -> Unit, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Section.entries.forEach { section ->
                val color by animateColorAsState(
                    targetValue = if (section == active) section.accent else Palette.textLabel,
                    animationSpec = tween(Motion.STATE_MS),
                    label = "section-color",
                )
                Box(Modifier.pressable(onClick = { onSelect(section) }).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    BasicText(section.label, style = Type.navigation.copy(color = color))
                }
            }
        }
    }
}
