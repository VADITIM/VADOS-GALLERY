package com.vaditim.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.glass
import com.vaditim.gallery.vas.pressable
import com.vaditim.gallery.vault.PrivateGroup

// A menu over content: a pane of glass that arrives from just below on the overshoot and leaves straight down and quicker (dna/05-motion.md §4). Tapping anywhere outside it closes it, so it never traps what is behind it.
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OverlaySheet(visible: Boolean, label: String, onDismiss: () -> Unit, ground: Color = Palette.ground, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.powerTwoOut)),
        exit = fadeOut(tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x4D000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .animateEnterExit(
                        enter = slideInVertically(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.backOut)) { it / 6 } +
                            scaleIn(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.backOut), initialScale = 0.96f),
                        exit = slideOutVertically(tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn)) { it / 8 } +
                            scaleOut(tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn), targetScale = 0.98f),
                    )
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .fillMaxWidth()
                    .glass(Shapes.sheet, ground)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .padding(top = 18.dp, bottom = 10.dp),
            ) {
                MicroLabel(label, Modifier.padding(start = 20.dp, bottom = 6.dp))
                content()
            }
        }
    }
}

@Composable
fun SheetRow(text: String, trailing: String? = null, color: Color = Palette.textBright, icon: (@Composable (Color) -> Unit)? = null, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().pressable(onClick = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick); onClick() }, pressedScale = 0.98f).padding(horizontal = 20.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.invoke(color)
            BasicText(text, style = Type.cardTitle.copy(color = color), maxLines = 1)
        }
        if (trailing != null) BasicText(trailing, style = Type.value)
    }
}

@Composable
fun AlbumPickerSheet(
    visible: Boolean,
    label: String,
    albums: List<Album>,
    excludedAlbumId: Long?,
    onPick: (Album) -> Unit,
    onNewAlbum: () -> Unit,
    onDismiss: () -> Unit,
    ground: Color = Palette.ground,
) {
    OverlaySheet(visible = visible, label = label, onDismiss = onDismiss, ground = ground) {
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            items(albums.filter { it.id != excludedAlbumId }, key = { it.id }) { album ->
                SheetRow(album.name, trailing = album.items.size.toString()) { onPick(album) }
            }
            item { SheetRow("+ New album", color = Palette.textMuted, onClick = onNewAlbum) }
        }
    }
}

@Composable
fun GroupPickerSheet(
    visible: Boolean,
    label: String,
    groups: List<PrivateGroup>,
    excludedGroupName: String?,
    onPick: (String) -> Unit,
    onNewGroup: () -> Unit,
    onDismiss: () -> Unit,
    ground: Color = Palette.ground,
) {
    OverlaySheet(visible = visible, label = label, onDismiss = onDismiss, ground = ground) {
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            items(groups.filter { it.name != excludedGroupName }, key = { it.directory.absolutePath }) { group ->
                SheetRow(group.name, trailing = group.items.size.toString()) { onPick(group.name) }
            }
            item { SheetRow("+ New group", color = Palette.textMuted, onClick = onNewGroup) }
        }
    }
}
