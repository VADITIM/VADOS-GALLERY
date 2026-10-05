package com.vaditim.gallery.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.vaditim.gallery.vas.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.vaditim.gallery.Settings
import com.vaditim.gallery.PhotoLayout
import com.vaditim.gallery.SettingsView
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable

// The settings, as a sheet in three tabs: the place you are in, what the app does everywhere, and how it looks. The look tab keeps the glass settings, so their effect can be watched on the sheet itself while they are dragged.
private enum class SettingsTab { PLACE, GENERAL, INTERFACE }

// The first tab is named after the view it changes, since each view keeps its own grid and album settings.
private fun SettingsTab.label(): String = when (this) {
    SettingsTab.PLACE -> Settings.view.label.uppercase()
    SettingsTab.GENERAL -> "GENERAL"
    SettingsTab.INTERFACE -> "INTERFACE"
}

// `isCovers`: the place shows albums or groups rather than photos, so only the album settings apply. `onReview` sorts through the photos of the place, when it has any.
@Composable
fun SettingsSheet(visible: Boolean, isCovers: Boolean, onReview: (() -> Unit)?, onDismiss: () -> Unit, onColumnsChanged: (Int) -> Unit) {
    var tab by remember { mutableStateOf(SettingsTab.PLACE) }
    OverlaySheet(visible = visible, label = "SETTINGS", onDismiss = onDismiss, isFloating = true) {
        SettingsTabs(tab, onSelect = { tab = it })
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                fadeIn(tween(Motion.SECTION_ENTER_MS, Motion.SECTION_ENTER_DELAY_MS, Motion.powerTwoOut))
                    .togetherWith(fadeOut(tween(Motion.SECTION_LEAVE_MS, easing = Motion.powerTwoIn)))
                    .using(SizeTransform(clip = false) { _, _ -> tween(Motion.STATE_MS, easing = Motion.powerTwoOut) })
            },
            label = "settings-tab",
        ) { shown ->
            Column {
                when (shown) {
                    SettingsTab.PLACE -> if (isCovers) {
                        SettingsHeader("Albums")
                        // Grouped albums lie as rows, so the column count only counts with grouping off.
                        val canGroup = Settings.view.canGroup
                        if (canGroup) SettingsToggle("Grouped albums", Settings.groupedAlbums) { Settings.updateGroupedAlbums(it) }
                        SheetRow("Album columns", trailing = Settings.albumColumns.toString(), isEnabled = !(canGroup && Settings.groupedAlbums)) {
                            Settings.updateAlbumColumns(if (Settings.albumColumns >= Settings.MAX_ALBUM_COLUMNS) Settings.MIN_COLUMNS else Settings.albumColumns + 1)
                        }
                    } else {
                        SettingsHeader("Photos")
                        SheetRow("Image columns", trailing = Settings.defaultColumns.toString()) {
                            val next = if (Settings.defaultColumns >= Settings.MAX_COLUMNS) Settings.MIN_COLUMNS else Settings.defaultColumns + 1
                            Settings.updateDefaultColumns(next)
                            onColumnsChanged(next)
                        }
                        SheetRow("Layout", trailing = Settings.photoLayout.label) {
                            Settings.updatePhotoLayout(PhotoLayout.entries[(Settings.photoLayout.ordinal + 1) % PhotoLayout.entries.size])
                        }
                        SettingsToggle("Month headers", Settings.showMonthHeaders) { Settings.updateShowMonthHeaders(it) }
                        SettingsToggle("Stack similar shots", Settings.stackSimilar) { Settings.updateStackSimilar(it) }
                    }
                    SettingsTab.GENERAL -> {
                        SettingsHeader("Videos")
                        SettingsToggle("Autoplay videos", Settings.autoplayVideos) { Settings.updateAutoplayVideos(it) }
                        if (onReview != null) {
                            SettingsHeader("Review")
                            SheetRow("Review photos", trailing = Settings.view.label, icon = { ReviewIcon(it) }) {
                                onDismiss()
                                onReview()
                            }
                        }
                    }
                    SettingsTab.INTERFACE -> {
                        SettingsHeader("Overlays")
                        SettingsSlider("Blur", Settings.blurDp / Settings.MAX_BLUR_DP, "${Settings.blurDp.toInt()}") { Settings.updateBlur(it * Settings.MAX_BLUR_DP) }
                        SettingsSlider("Opacity", Settings.glassOpacity, "${(Settings.glassOpacity * 100).toInt()}%") { Settings.updateGlassOpacity(it) }
                        SettingsHeader("Background")
                        SettingsSlider("Brightness", Settings.groundBrightness, "${(Settings.groundBrightness * 100).toInt()}%") { Settings.updateGroundBrightness(it) }
                    }
                }
            }
        }
    }
}

// The tabs as one capsule; the accent fill slides under the chosen one.
@Composable
private fun SettingsTabs(active: SettingsTab, onSelect: (SettingsTab) -> Unit) {
    val accent = LocalAccent.current
    val position by animateFloatAsState(active.ordinal.toFloat(), tween(Motion.STATE_MS, easing = Motion.powerTwoOut), label = "settings-tabs")
    Box(
        Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(Shapes.capsule)
            .background(Palette.sunkenDeep)
            .drawBehind {
                val width = size.width / SettingsTab.entries.size
                drawRoundRect(accent, Offset(width * position, 0f), Size(width, size.height), CornerRadius(size.height / 2f))
            },
    ) {
        Row(Modifier.fillMaxWidth()) {
            SettingsTab.entries.forEach { tab ->
                Box(
                    Modifier.weight(1f).pressable(onClick = { onSelect(tab) }, pressedScale = 0.96f).padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(tab.label(), style = Type.navigation.copy(color = if (tab == active) Palette.sunkenDeep else Palette.textMuted))
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(text: String) = SheetHeader(text)

@Composable
private fun SettingsSlider(label: String, fraction: Float, value: String, onChange: (Float) -> Unit) {
    val haptic = LocalHapticFeedback.current
    // A tick every twentieth of the track, so dragging it feels stepped.
    val tick: (Float) -> Unit = { next ->
        if ((next * 20).toInt() != (fraction * 20).toInt()) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        onChange(next)
    }
    Column(Modifier.fillMaxWidth().rowDivider().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(label, style = Type.cardTitle)
            BasicText(value, style = Type.value.copy(color = LocalAccent.current))
        }
        Box(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        tick((down.position.x / size.width).coerceIn(0f, 1f))
                        down.consume()
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull() ?: break
                            tick((change.position.x / size.width).coerceIn(0f, 1f))
                            change.consume()
                        } while (event.changes.any { it.pressed })
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(Shapes.capsule).background(Palette.borderControl))
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).clip(Shapes.capsule).background(LocalAccent.current))
        }
    }
}

@Composable
private fun SettingsToggle(label: String, isOn: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().rowDivider().pressable(onClick = { onChange(!isOn) }, pressedScale = 0.98f).padding(horizontal = 20.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = Type.cardTitle)
        Box(Modifier.size(width = 44.dp, height = 26.dp).clip(Shapes.capsule).background(if (isOn) LocalAccent.current else Palette.borderControl)) {
            Box(
                Modifier
                    .padding(3.dp)
                    .size(20.dp)
                    .align(if (isOn) Alignment.CenterEnd else Alignment.CenterStart)
                    .clip(Shapes.capsule)
                    .background(if (isOn) Palette.ground else Palette.textBright),
            )
        }
    }
}
