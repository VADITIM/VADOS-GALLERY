package com.vaditim.gallery.settings.sheet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.backup.Backup
import com.vaditim.gallery.components.OverlaySheet
import com.vaditim.gallery.components.ReviewIcon
import com.vaditim.gallery.components.SheetHeader
import com.vaditim.gallery.components.SheetRow
import com.vaditim.gallery.components.rowDivider
import com.vaditim.gallery.settings.AlbumArrangement
import com.vaditim.gallery.settings.DateGroup
import com.vaditim.gallery.settings.Settings
import com.vaditim.gallery.settings.SettingsView
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The settings, as a sheet in three tabs: the place you are in, what the app does everywhere, and how it looks. The look tab keeps the glass settings, so their effect can be watched on the sheet itself while they are dragged.
private enum class SettingsTab { PLACE, GENERAL, INTERFACE }

// The first tab is named after the view it changes, since each view keeps its own grid and album settings.
private fun SettingsTab.label(): String = when (this) {
    SettingsTab.PLACE -> Settings.view.label.uppercase()
    SettingsTab.GENERAL -> "GENERAL"
    SettingsTab.INTERFACE -> "INTERFACE"
}

private const val DISABLED_ALPHA = 0.38f

// `isCovers`: the place shows albums or groups rather than photos, so only the album settings apply. `onReview` sorts through the photos of the place, when it has any.
@Composable
fun SettingsSheet(visible: Boolean, isCovers: Boolean, onReview: (() -> Unit)?, onDismiss: () -> Unit, onColumnsChanged: (Int) -> Unit, onAnnounce: (String) -> Unit, onPull: (Float) -> Unit = {}) {
    var tab by remember { mutableStateOf(SettingsTab.PLACE) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The launchers live here, not in the tab, which is also composed once more just to be measured.
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val isSaved = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri)!!.use { Backup.write(context, it) } }.isSuccess }
            onAnnounce(if (isSaved) "Backup saved" else "Backup failed")
        }
    }
    val loadBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val isRestored = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)!!.use { Backup.restore(context, it) } }.getOrDefault(false) }
            if (isRestored) Backup.restart(context) else onAnnounce("Not a backup")
        }
    }
    val onBackup = { saveBackup.launch("vados-gallery-backup.json") }
    val onRestore = { loadBackup.launch(arrayOf("*/*")) }
    OverlaySheet(visible = visible, label = "SETTINGS", onDismiss = onDismiss, isCentered = true, onPull = onPull) {
        // Every tab is measured and the sheet takes the tallest, so switching tabs never changes its height; a shorter tab sits in the middle of it.
        SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
            val loose = constraints.copy(minHeight = 0)
            val height = SettingsTab.entries.maxOf { measured ->
                subcompose("measure-$measured") { SettingsTabContent(measured, isCovers, onReview, onDismiss, onColumnsChanged, onBackup, onRestore, isFilling = false) }.maxOf { it.measure(loose).height }
            }
            val fixed = constraints.copy(minHeight = height, maxHeight = height)
            val shown = subcompose("shown") {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        fadeIn(tween(Motion.SECTION_ENTER_MS, Motion.SECTION_ENTER_DELAY_MS, Motion.powerTwoOut))
                            .togetherWith(fadeOut(tween(Motion.SECTION_LEAVE_MS, easing = Motion.powerTwoIn)))
                    },
                    contentAlignment = Alignment.Center,
                    label = "settings-tab",
                ) { shown -> SettingsTabContent(shown, isCovers, onReview, onDismiss, onColumnsChanged, onBackup, onRestore) }
            }.map { it.measure(fixed) }
            layout(constraints.maxWidth, height) { shown.forEach { it.place(0, 0) } }
        }
        SettingsTabs(tab, onSelect = { tab = it })
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            BasicText("V/AS", style = Type.microLabel.copy(color = Palette.textFaint.copy(alpha = 0.35f)))
        }
    }
}

@Composable
// `isFilling`: the shown tab fills the sheet's fixed height and centres in it; measured, it takes only its own.
private fun SettingsTabContent(shown: SettingsTab, isCovers: Boolean, onReview: (() -> Unit)?, onDismiss: () -> Unit, onColumnsChanged: (Int) -> Unit, onBackup: () -> Unit, onRestore: () -> Unit, isFilling: Boolean = true) {
    Column(Modifier.fillMaxWidth().then(if (isFilling) Modifier.fillMaxHeight() else Modifier), verticalArrangement = Arrangement.Center) {
        when (shown) {
            SettingsTab.PLACE -> {
                if (onReview != null) {
                    SheetRow("Review photos", icon = { ReviewIcon(it) }) {
                        onDismiss()
                        onReview()
                    }
                }
                if (isCovers) {
                    SettingsHeader("Albums")
                    // Grouped albums lie as rows, so the column count only counts with grouping off.
                    val canGroup = Settings.view.canGroup
                    if (canGroup) SettingsToggle("Grouped albums", Settings.groupedAlbumsInView) { Settings.updateGroupedAlbums(it) }
                    SettingsSteps("Album columns", Settings.MIN_COLUMNS..Settings.MAX_ALBUM_COLUMNS, Settings.albumColumnsInView, isEnabled = !(canGroup && Settings.groupedAlbumsInView)) {
                        Settings.updateAlbumColumns(it)
                    }
                } else {
                    SettingsHeader("Photos")
                    SettingsSteps("Image columns", Settings.MIN_COLUMNS..Settings.MAX_COLUMNS, Settings.defaultColumns) {
                        Settings.updateDefaultColumns(it)
                        onColumnsChanged(it)
                    }
                    // An open album shows Recent's headers until it is given its own.
                    val isAlbum = Settings.folder != null
                    if (isAlbum) SettingsToggle("Own headers", Settings.hasOwnHeaders(Settings.folder)) { Settings.updateOwnHeaders(it) }
                    HeadersLayout(Settings.headersInView, Settings.dateGroupsInView, isEnabled = !isAlbum || Settings.hasOwnHeaders(Settings.folder))
                }
                if (Settings.view == SettingsView.PRIVATE) {
                    SettingsHeader("Private")
                    SettingsToggle("Today's selection", Settings.todaysSelection) { Settings.updateTodaysSelection(it) }
                }
            }
            SettingsTab.GENERAL -> {
                SettingsHeader("Videos")
                SettingsToggle("Autoplay videos", Settings.autoplayVideos) { Settings.updateAutoplayVideos(it) }
                SettingsHeader("Photos")
                SettingsToggle("Stack similar shots", Settings.stackSimilarInView) { Settings.updateStackSimilar(it) }
                SettingsToggle("Day stamps", Settings.dayStamps) { Settings.updateDayStamps(it) }
                SettingsChoice("Folder label", listOf("Top", "Bottom"), if (Settings.folderLabel) 1 else 0) { Settings.updateFolderLabel(it == 1) }
                SettingsHeader("Backup")
                SheetRow("Back up", onClick = onBackup)
                SheetRow("Restore", onClick = onRestore)
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

// The tabs as one capsule at the foot of the sheet; the accent fill slides under the chosen one.
@Composable
private fun SettingsTabs(active: SettingsTab, onSelect: (SettingsTab) -> Unit) {
    val accent = LocalAccent.current
    val position by animateFloatAsState(active.ordinal.toFloat(), tween(Motion.STATE_MS, easing = Motion.powerTwoOut), label = "settings-tabs")
    Box(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 14.dp)
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
    val accent = LocalAccent.current
    var isHeld by remember { mutableStateOf(false) }
    val thumb by animateDpAsState(if (isHeld) 22.dp else 16.dp, tween(Motion.STATE_MS, easing = Motion.backOut), label = "slider-thumb")
    // A tick every twentieth of the track, so dragging it feels stepped.
    val tick: (Float) -> Unit = { next ->
        if ((next * 20).toInt() != (fraction * 20).toInt()) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        onChange(next)
    }
    Column(Modifier.fillMaxWidth().rowDivider().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(label, style = Type.cardTitle)
            BasicText(value, style = Type.value.copy(color = accent))
        }
        Box(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isHeld = true
                        tick((down.position.x / size.width).coerceIn(0f, 1f))
                        down.consume()
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull() ?: break
                            tick((change.position.x / size.width).coerceIn(0f, 1f))
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        isHeld = false
                    }
                }
                .drawBehind {
                    val trackHeight = 6.dp.toPx()
                    val top = (size.height - trackHeight) / 2f
                    val end = size.width * fraction.coerceIn(0f, 1f)
                    drawRoundRect(Palette.borderControl, Offset(0f, top), Size(size.width, trackHeight), CornerRadius(trackHeight / 2f))
                    drawRoundRect(accent, Offset(0f, top), Size(end, trackHeight), CornerRadius(trackHeight / 2f))
                    val radius = thumb.toPx() / 2f
                    drawCircle(Palette.textBright, radius, Offset(end.coerceIn(radius, size.width - radius), size.height / 2f))
                },
        )
    }
}

// A switch whose knob slides across on the overshoot and stretches while pressed, the track taking the accent as it goes.
@Composable
private fun SettingsToggle(label: String, isOn: Boolean, isEnabled: Boolean = true, isDivided: Boolean = true, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    val travel by animateFloatAsState(if (isOn) 1f else 0f, tween(Motion.STATE_MS, easing = Motion.backOut), label = "toggle-travel")
    val track by animateColorAsState(if (isOn) accent else Palette.borderControl, tween(Motion.STATE_MS), label = "toggle-track")
    val knob by animateColorAsState(if (isOn) Palette.sunkenDeep else Palette.textBright, tween(Motion.STATE_MS), label = "toggle-knob")
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (isDivided) Modifier.rowDivider() else Modifier)
            .then(if (isEnabled) Modifier.pressable(onClick = { onChange(!isOn) }, pressedScale = 0.98f) else Modifier.alpha(DISABLED_ALPHA))
            .padding(horizontal = 20.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = Type.cardTitle)
        Box(Modifier.size(width = TOGGLE_WIDTH, height = TOGGLE_HEIGHT).clip(Shapes.capsule).background(track), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    .offset(x = TOGGLE_INSET + (TOGGLE_WIDTH - TOGGLE_KNOB - TOGGLE_INSET * 2) * travel)
                    .size(TOGGLE_KNOB)
                    .clip(Shapes.capsule)
                    .background(knob),
            )
        }
    }
}

private val TOGGLE_WIDTH = 52.dp
private val TOGGLE_HEIGHT = 30.dp
private val TOGGLE_KNOB = 24.dp
private val TOGGLE_INSET = 3.dp

// Every possible value laid out as a stop; the accent pill under the chosen one follows the finger across them and settles on the nearest when let go, which is when the value changes.
@Composable
private fun SettingsSteps(label: String, range: IntRange, value: Int, isEnabled: Boolean = true, onChange: (Int) -> Unit) {
    val count = range.last - range.first + 1
    val accent = LocalAccent.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val position = remember { Animatable((value - range.first).toFloat()) }
    var isHeld by remember { mutableStateOf(false) }
    // A value changed elsewhere (a pinch on the grid) moves the pill too.
    LaunchedEffect(value) {
        if (!isHeld) position.animateTo((value - range.first).toFloat(), tween(Motion.STATE_MS, easing = Motion.backOut))
    }
    val nearest = position.value.roundToInt().coerceIn(0, count - 1)
    Column(
        Modifier
            .fillMaxWidth()
            .rowDivider()
            .then(if (isEnabled) Modifier else Modifier.alpha(DISABLED_ALPHA))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        BasicText(label, style = Type.cardTitle)
        Box(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(STEPS_HEIGHT)
                .clip(Shapes.capsule)
                .background(Palette.sunkenDeep)
                .then(
                    if (!isEnabled) Modifier else Modifier.pointerInput(range) {
                        val stopWidth = size.width.toFloat() / count
                        fun stopAt(x: Float) = (x / stopWidth - 0.5f).coerceIn(0f, (count - 1).toFloat())
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            isHeld = true
                            var lastStop = position.value.roundToInt()
                            scope.launch { position.animateTo(stopAt(down.position.x), tween(Motion.PRESS_MS, easing = Motion.powerTwoOut)) }
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull() ?: break
                                if (change.position != change.previousPosition) scope.launch { position.snapTo(stopAt(change.position.x)) }
                                val stop = stopAt(change.position.x).roundToInt()
                                if (stop != lastStop) {
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                    lastStop = stop
                                }
                                change.consume()
                            } while (event.changes.any { it.pressed })
                            val chosen = lastStop
                            scope.launch {
                                position.animateTo(chosen.toFloat(), tween(Motion.STATE_MS, easing = Motion.backOut))
                                isHeld = false
                            }
                            if (range.first + chosen != value) onChange(range.first + chosen)
                        }
                    },
                )
                .drawBehind {
                    val width = size.width / count
                    drawRoundRect(accent, Offset(width * position.value, 0f), Size(width, size.height), CornerRadius(size.height / 2f))
                },
        ) {
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                range.forEachIndexed { index, stop ->
                    val ink by animateColorAsState(if (index == nearest) Palette.sunkenDeep else Palette.textMuted, tween(Motion.PRESS_MS), label = "step-ink")
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        BasicText(stop.toString(), style = Type.value.copy(color = ink))
                    }
                }
            }
        }
    }
}

private val STEPS_HEIGHT = 36.dp

// Days, weeks, months and years can be on together, at least one; the switch under them turns every cut off and greys them, keeping the pick for when it comes back.
// An empty pick stored before it could not be emptied reads as off, and turning on from it starts at months.
@Composable
private fun HeadersLayout(isOn: Boolean, groups: Set<DateGroup>, isEnabled: Boolean = true) {
    val isActive = isOn && groups.isNotEmpty()
    Column(Modifier.fillMaxWidth().rowDivider()) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
            BasicText("Headers - Layout", style = Type.cardTitle)
            Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DateGroup.entries.forEach { group ->
                    LayoutChip(group.label, isOn = group in groups, isGreyed = !isActive || !isEnabled, modifier = Modifier.weight(1f)) {
                        if (!isEnabled) return@LayoutChip
                        val next = if (group in groups) groups - group else groups + group
                        if (next.isNotEmpty()) Settings.updateDateGroups(next)
                    }
                }
            }
        }
        SettingsToggle("Headers", isActive, isEnabled = isEnabled, isDivided = false) { isTurnedOn ->
            if (isTurnedOn && groups.isEmpty()) Settings.updateDateGroups(setOf(DateGroup.MONTHS))
            Settings.updateHeaders(isTurnedOn)
        }
    }
}

// A setting with a few named answers side by side, the chosen one lit as the layout buttons are.
@Composable
private fun SettingsChoice(label: String, options: List<String>, chosen: Int, onChoose: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().rowDivider().padding(horizontal = 20.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = Type.cardTitle)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { index, option ->
                LayoutChip(option, isOn = index == chosen, isGreyed = false, modifier = Modifier.width(CHOICE_WIDTH)) { onChoose(index) }
            }
        }
    }
}

private val CHOICE_WIDTH = 76.dp

@Composable
private fun LayoutChip(label: String, isOn: Boolean, isGreyed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val fill by animateColorAsState(if (isOn) accent else Palette.sunkenDeep, tween(Motion.STATE_MS), label = "chip-fill")
    val ink by animateColorAsState(if (isOn) Palette.sunkenDeep else Palette.textMuted, tween(Motion.STATE_MS), label = "chip-ink")
    val shade by animateFloatAsState(if (isGreyed) DISABLED_ALPHA else 1f, tween(Motion.STATE_MS), label = "chip-shade")
    Box(
        modifier
            .pressable(onClick = onClick, pressedScale = 0.94f)
            .alpha(shade)
            .clip(Shapes.capsule)
            .background(fill)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label.uppercase(), style = Type.microLabel.copy(color = ink), maxLines = 1)
    }
}
