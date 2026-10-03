package com.vaditim.gallery.ui

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
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable

// The settings, as a sheet. The glass settings sit first so their effect can be watched on the sheet itself while they are dragged.
@Composable
fun SettingsSheet(visible: Boolean, onDismiss: () -> Unit, onColumnsChanged: (Int) -> Unit) {
    OverlaySheet(visible = visible, label = "SETTINGS", onDismiss = onDismiss) {
        SettingsSlider("Blur", Settings.blurDp / Settings.MAX_BLUR_DP, "${Settings.blurDp.toInt()}") { Settings.updateBlur(it * Settings.MAX_BLUR_DP) }
        SettingsSlider("Background", Settings.glassOpacity, "${(Settings.glassOpacity * 100).toInt()}%") { Settings.updateGlassOpacity(it) }
        SheetRow("Columns", trailing = Settings.defaultColumns.toString()) {
            val next = if (Settings.defaultColumns >= Settings.MAX_COLUMNS) Settings.MIN_COLUMNS else Settings.defaultColumns + 1
            Settings.updateDefaultColumns(next)
            onColumnsChanged(next)
        }
        SheetRow("Album columns", trailing = Settings.albumColumns.toString()) {
            Settings.updateAlbumColumns(if (Settings.albumColumns >= Settings.MAX_ALBUM_COLUMNS) Settings.MIN_COLUMNS else Settings.albumColumns + 1)
        }
        SettingsToggle("Month headers", Settings.showMonthHeaders) { Settings.updateShowMonthHeaders(it) }
        SettingsToggle("Autoplay videos", Settings.autoplayVideos) { Settings.updateAutoplayVideos(it) }
    }
}

@Composable
private fun SettingsSlider(label: String, fraction: Float, value: String, onChange: (Float) -> Unit) {
    val haptic = LocalHapticFeedback.current
    // A tick every twentieth of the track, so dragging it feels stepped.
    val tick: (Float) -> Unit = { next ->
        if ((next * 20).toInt() != (fraction * 20).toInt()) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        onChange(next)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(label, style = Type.cardTitle)
            BasicText(value, style = Type.value)
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
            Box(Modifier.fillMaxWidth().height(4.dp).clip(Shapes.capsule).background(Palette.borderStrong))
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).clip(Shapes.capsule).background(LocalAccent.current))
        }
    }
}

@Composable
private fun SettingsToggle(label: String, isOn: Boolean, onChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().pressable(onClick = {
            haptic.performHapticFeedback(if (isOn) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
            onChange(!isOn)
        }, pressedScale = 0.98f).padding(horizontal = 20.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = Type.cardTitle)
        Box(Modifier.size(width = 44.dp, height = 26.dp).clip(Shapes.capsule).background(if (isOn) LocalAccent.current else Palette.borderStrong)) {
            Box(
                Modifier
                    .padding(3.dp)
                    .size(20.dp)
                    .align(if (isOn) Alignment.CenterEnd else Alignment.CenterStart)
                    .clip(Shapes.capsule)
                    .background(Palette.textBright),
            )
        }
    }
}
