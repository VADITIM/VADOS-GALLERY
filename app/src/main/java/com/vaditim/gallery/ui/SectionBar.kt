package com.vaditim.gallery.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.vaditim.gallery.vas.glass
import com.vaditim.gallery.vas.pressable

// Navigation is the bar and only the bar: a horizontal swipe belongs to the viewer's pager, so sections are never swiped between (dna/06-interaction.md, gesture ownership).
@Composable
fun SectionBar(active: Section, onSelect: (Section) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier.glass(Shapes.capsule).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Section.entries.forEach { section ->
            val isActive = section == active
            val ink by animateColorAsState(if (isActive) section.accent else Palette.textMuted, tween(Motion.STATE_MS), label = "section-ink")
            val wash by animateColorAsState(if (isActive) Palette.pressedWash else Color.Transparent, tween(Motion.STATE_MS), label = "section-wash")
            Box(
                Modifier
                    .pressable(onClick = { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(section) })
                    .clip(Shapes.capsule)
                    .background(wash)
                    .padding(horizontal = 18.dp, vertical = 13.dp),
            ) {
                BasicText(section.label, style = Type.navigation.copy(color = ink))
            }
        }
    }
}
