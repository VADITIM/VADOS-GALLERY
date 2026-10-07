package com.vaditim.gallery.settings.sheet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import com.vaditim.gallery.components.FadingOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import kotlin.math.abs
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.backup.Backup
import com.vaditim.gallery.components.OverlaySheet
import com.vaditim.gallery.components.ReviewIcon
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
// Every group is a card of its own (VAS components/03-panel-and-field.md), so two settings never run into each other and the eye has an edge to hold on to.
private enum class SettingsTab { PLACE, GENERAL, INTERFACE }

// The first tab is named after the view it changes, since each view keeps its own grid and album settings.
private fun SettingsTab.label(): String = when (this) {
    SettingsTab.PLACE -> Settings.view.label.uppercase()
    SettingsTab.GENERAL -> "GENERAL"
    SettingsTab.INTERFACE -> "INTERFACE"
}

private const val DISABLED_ALPHA = 0.38f

// `isCovers`: the place shows albums or groups rather than photos, so only the album settings apply. `onReview` sorts through the photos of the place, when it has any. `placeName`: the album, group or location open inside the view, named under the first tab.
@Composable
fun SettingsSheet(visible: Boolean, isCovers: Boolean, placeName: String?, onReview: (() -> Unit)?, onDismiss: () -> Unit, onColumnsChanged: (Int) -> Unit, onAnnounce: (String) -> Unit, onPull: (Float) -> Unit = {}) {
    val pager = rememberPagerState { SettingsTab.entries.size }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val isSaved = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri)!!.use { Backup.write(context, it) } }.isSuccess }
            if (isSaved) Settings.updateLastBackup(System.currentTimeMillis())
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
    // The view's own name is already the tab's, so only a place inside it gets the second line.
    val caption = placeName?.takeUnless { it.equals(Settings.view.label, ignoreCase = true) }
    OverlaySheet(visible = visible, label = "SETTINGS", onDismiss = onDismiss, isFullHeight = true, onPull = onPull) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.padding(start = SHEET_MARGIN, end = SHEET_MARGIN, top = 4.dp, bottom = CARD_GAP)) {
                SettingsTabs(pager, SettingsTab.entries.map { it.label() }, caption)
            }
            // The tabs lie side by side and the finger drags between them; the pill above follows the same position, so a swipe and a tap move both together.
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = SettingsTab.entries.size - 1, verticalAlignment = Alignment.Top) { page ->
                SettingsTabContent(SettingsTab.entries[page], isCovers, onReview, onDismiss, onColumnsChanged, onBackup, onRestore)
            }
            // The running version at the foot's right end, in the V/AS mark's own faint style, so which release is on the phone is one look away.
            val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
            val footStyle = Type.microLabel.copy(color = Palette.textFaint.copy(alpha = 0.35f))
            Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                BasicText("V/AS", style = footStyle, modifier = Modifier.align(Alignment.Center))
                if (version != null) BasicText("V$version", style = footStyle, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp))
            }
        }
    }
}

@Composable
// Every tab starts at the top under the tabs, so the first thing on it is where the eye already is.
private fun SettingsTabContent(shown: SettingsTab, isCovers: Boolean, onReview: (() -> Unit)?, onDismiss: () -> Unit, onColumnsChanged: (Int) -> Unit, onBackup: () -> Unit, onRestore: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = SHEET_MARGIN), verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
        when (shown) {
            SettingsTab.PLACE -> {
                if (onReview != null) {
                    SettingsButton("Review photos", Modifier.fillMaxWidth(), icon = { ReviewIcon(it, size = 18.dp) }) {
                        onDismiss()
                        onReview()
                    }
                }
                if (isCovers) {
                    SettingsCard("Albums") {
                        // Grouped albums lie as rows, so the column count only counts with grouping off.
                        val canGroup = Settings.view.canGroup
                        if (canGroup) {
                            SettingsToggle("Grouped albums", Settings.groupedAlbumsInView) { Settings.updateGroupedAlbums(it) }
                            CardDivider()
                        }
                        SettingsSteps("Album columns", Settings.MIN_COLUMNS..Settings.MAX_ALBUM_COLUMNS, Settings.albumColumnsInView, isEnabled = !(canGroup && Settings.groupedAlbumsInView)) {
                            Settings.updateAlbumColumns(it)
                        }
                    }
                } else {
                    // An open album shows Recent's settings, greyed, until it is given its own.
                    val isEditable = Settings.folder == null || Settings.hasOwnSettings(Settings.folder)
                    SettingsCard("Photos") {
                        // Stacking is the view's, not the album's, so it stays live while the rest is greyed; the trash never stacks.
                        if (Settings.view != SettingsView.TRASH) {
                            SettingsToggle("Stack similar shots", Settings.stackSimilarInView) { Settings.updateStackSimilar(it) }
                            CardDivider()
                        }
                        SettingsSteps("Image columns", Settings.MIN_COLUMNS..Settings.MAX_COLUMNS, Settings.defaultColumns, isEnabled = isEditable) {
                            Settings.updateDefaultColumns(it)
                            onColumnsChanged(it)
                        }
                        CardDivider()
                        HeadersLayout(Settings.headersInView, Settings.dateGroupsInView, isEnabled = isEditable)
                        if (Settings.folder != null) {
                            CardDivider()
                            SettingsToggle("Own settings", Settings.hasOwnSettings(Settings.folder)) {
                                Settings.updateOwnSettings(it)
                                onColumnsChanged(Settings.defaultColumns)
                            }
                        }
                    }
                }
                if (Settings.view == SettingsView.PRIVATE) {
                    SettingsCard("Private") {
                        SettingsToggle("Today's selection", Settings.todaysSelection) { Settings.updateTodaysSelection(it) }
                    }
                }
            }
            SettingsTab.GENERAL -> {
                SettingsCard("Videos") {
                    SettingsToggle("Autoplay videos", Settings.autoplayVideos) { Settings.updateAutoplayVideos(it) }
                }
                SettingsCard("Backup") {
                    Row(Modifier.fillMaxWidth().padding(start = CARD_INSET, end = CARD_INSET, top = 6.dp, bottom = CARD_INSET), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsButton("Back up", Modifier.weight(1f), onClick = onBackup)
                        SettingsButton("Restore", Modifier.weight(1f), onClick = onRestore)
                    }
                    if (Settings.lastBackupMillis > 0L) {
                        CardDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = CARD_INSET, vertical = ROW_PADDING), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            SettingName("Last backup")
                            SettingValue(remember(Settings.lastBackupMillis) { SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(Settings.lastBackupMillis)) })
                        }
                    }
                }
            }
            SettingsTab.INTERFACE -> {
                // How a photo tile and the folder's name look are part of the look, not of what the app does.
                SettingsCard("Tiles") {
                    SettingsToggle("Day stamps", Settings.dayStamps) { Settings.updateDayStamps(it) }
                    CardDivider()
                    SettingsChoice("Folder label", listOf("Top", "Bottom"), if (Settings.folderLabel) 1 else 0) { Settings.updateFolderLabel(it == 1) }
                }
                SettingsCard("Glass") {
                    SettingsSlider("Blur", Settings.blurDp / Settings.MAX_BLUR_DP, "${Settings.blurDp.toInt()}") { Settings.updateBlur(it * Settings.MAX_BLUR_DP) }
                    CardDivider()
                    SettingsSlider("Opacity", Settings.glassOpacity, "${(Settings.glassOpacity * 100).toInt()}%") { Settings.updateGlassOpacity(it) }
                }
                SettingsCard("Background") {
                    SettingsSlider("Brightness", Settings.groundBrightness, "${(Settings.groundBrightness * 100).toInt()}%") { Settings.updateGroundBrightness(it) }
                }
            }
        }
    }
}

// The sheet's edge to its cards, the gap between cards, and a card's edge to its text: one spacing for every tab.
private val SHEET_MARGIN = 14.dp
private val CARD_GAP = 10.dp
private val CARD_INSET = 16.dp
private val ROW_PADDING = 13.dp

// One group of settings in a box of its own: a hairline edge and a darker fill than the glass, its name in the accent at the top left.
@Composable
private fun SettingsCard(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.field)
            .background(Palette.sunken.copy(alpha = CARD_FILL))
            .border(1.dp, Palette.border, Shapes.field),
    ) {
        BasicText(label.uppercase(), style = Type.microLabel.copy(color = LocalAccent.current), modifier = Modifier.padding(start = CARD_INSET, end = CARD_INSET, top = 12.dp, bottom = 2.dp))
        content()
    }
}

private const val CARD_FILL = 0.85f

// A hairline between two settings inside one card, inset to their text.
@Composable
private fun CardDivider() = Box(Modifier.padding(horizontal = CARD_INSET).fillMaxWidth().height(1.dp).background(Palette.border))

// A setting's name, always the same face, size and brightness, so the eye reads every card the same way.
@Composable
private fun SettingName(text: String) = BasicText(text, style = Type.cardTitle, maxLines = 1)

// The value a setting is at, plain bright beside its name in the accent card, as in the field card's head.
@Composable
private fun SettingValue(text: String) = BasicText(text, style = Type.value.copy(color = Palette.textBright))

// An outlined button for a move rather than a setting (review, back up, restore): the outline says it does something once, unlike the filled controls that hold a state.
@Composable
private fun SettingsButton(text: String, modifier: Modifier = Modifier, icon: (@Composable (Color) -> Unit)? = null, onClick: () -> Unit) {
    Row(
        modifier
            .pressable(onClick = onClick, pressedScale = 0.96f)
            .height(BUTTON_HEIGHT)
            .clip(Shapes.capsule)
            .background(Palette.sunkenDeep)
            .border(1.dp, Palette.borderControl, Shapes.capsule),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.invoke(LocalAccent.current)
        BasicText(text.uppercase(), style = Type.navigation.copy(color = Palette.textBright), maxLines = 1)
    }
}

private val BUTTON_HEIGHT = 46.dp

// A drag-only slider (VAS components/03-panel-and-field.md §3): the value moves by how far the finger travels sideways from where it went down, so a finger on its way down the sheet passes over it without changing anything, and a tap does nothing.
@Composable
private fun SettingsSlider(label: String, fraction: Float, value: String, onChange: (Float) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val accent = LocalAccent.current
    val currentFraction by rememberUpdatedState(fraction)
    val currentOnChange by rememberUpdatedState(onChange)
    var isHeld by remember { mutableStateOf(false) }
    // A finger resting on the track thickens it, so what is about to be dragged is plain before it moves.
    var isTouched by remember { mutableStateOf(false) }
    val thumb by animateDpAsState(if (isHeld) 26.dp else if (isTouched) 24.dp else 20.dp, tween(Motion.STATE_MS, easing = Motion.backOut), label = "slider-thumb")
    val trackHeight by animateDpAsState(if (isTouched) 14.dp else 6.dp, tween(Motion.STATE_MS, easing = Motion.backOut), label = "slider-track")
    Column(Modifier.fillMaxWidth().padding(horizontal = CARD_INSET, vertical = ROW_PADDING)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SettingName(label)
            SettingValue(value)
        }
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val from = currentFraction
                        var isArmed = false
                        isTouched = true
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val travelled = change.position - down.position
                            if (!isArmed) {
                                // Under the slop it is not a drag yet; going down the sheet first leaves it to the sheet's own pull.
                                if (abs(travelled.y) > viewConfiguration.touchSlop && abs(travelled.y) > abs(travelled.x)) break
                                if (abs(travelled.x) < viewConfiguration.touchSlop) continue
                                isArmed = true
                                isHeld = true
                            }
                            val next = (from + travelled.x / size.width).coerceIn(0f, 1f)
                            // A tick every twentieth of the track, so dragging it feels stepped.
                            if ((next * 20).toInt() != (currentFraction * 20).toInt()) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            currentOnChange(next)
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        isHeld = false
                        isTouched = false
                    }
                }
                .drawBehind {
                    val track = trackHeight.toPx()
                    val top = (size.height - track) / 2f
                    val radius = thumb.toPx() / 2f
                    val center = radius + (size.width - radius * 2f) * fraction.coerceIn(0f, 1f)
                    drawRoundRect(Palette.borderStrong, Offset(0f, top), Size(size.width, track), CornerRadius(track / 2f))
                    drawRoundRect(accent, Offset(0f, top), Size(center, track), CornerRadius(track / 2f))
                    drawCircle(accent, radius, Offset(center, size.height / 2f))
                    drawCircle(Palette.sunkenDeep, radius - 3.dp.toPx(), Offset(center, size.height / 2f))
                },
        )
    }
}

// A switch whose knob slides across on the overshoot and stretches while pressed, the track taking the accent as it goes; off, it is an outline with a bright knob, so off still reads as a control and not as a gap.
@Composable
private fun SettingsToggle(label: String, isOn: Boolean, isEnabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    val travel by animateFloatAsState(if (isOn) 1f else 0f, tween(Motion.STATE_MS, easing = Motion.backOut), label = "toggle-travel")
    val track by animateColorAsState(if (isOn) accent else Palette.sunkenDeep, tween(Motion.STATE_MS), label = "toggle-track")
    val edge by animateColorAsState(if (isOn) accent else Palette.borderControl, tween(Motion.STATE_MS), label = "toggle-edge")
    val knob by animateColorAsState(if (isOn) Palette.sunkenDeep else Palette.textMuted, tween(Motion.STATE_MS), label = "toggle-knob")
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (isEnabled) Modifier.pressable(onClick = { onChange(!isOn) }, pressedScale = 0.98f) else Modifier.alpha(DISABLED_ALPHA))
            .padding(horizontal = CARD_INSET, vertical = ROW_PADDING),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingName(label)
        Box(
            Modifier.size(width = TOGGLE_WIDTH, height = TOGGLE_HEIGHT).clip(Shapes.capsule).background(track).border(1.dp, edge, Shapes.capsule),
            contentAlignment = Alignment.CenterStart,
        ) {
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

private val TOGGLE_WIDTH = 50.dp
private val TOGGLE_HEIGHT = 30.dp
private val TOGGLE_KNOB = 22.dp
private val TOGGLE_INSET = 4.dp

// A setting with a number to pick: its name above, every value as a stop of one segmented track under it.
@Composable
private fun SettingsSteps(label: String, range: IntRange, value: Int, isEnabled: Boolean = true, onChange: (Int) -> Unit) {
    val stops = range.toList()
    Column(
        Modifier.fillMaxWidth().then(if (isEnabled) Modifier else Modifier.alpha(DISABLED_ALPHA)).padding(horizontal = CARD_INSET, vertical = ROW_PADDING),
    ) {
        SettingName(label)
        Box(Modifier.padding(top = 10.dp)) {
            SettingsSegments(stops.map { it.toString() }, stops.indexOf(value).coerceAtLeast(0), isEnabled = isEnabled, style = Type.value) { onChange(stops[it]) }
        }
    }
}

// A setting with a few named answers, laid out as the steps are, so every one-of-many pick in the sheet is the same control.
@Composable
private fun SettingsChoice(label: String, options: List<String>, chosen: Int, onChoose: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = CARD_INSET, vertical = ROW_PADDING)) {
        SettingName(label)
        Box(Modifier.padding(top = 10.dp)) {
            SettingsSegments(options.map { it.uppercase() }, chosen, onChoose = onChoose)
        }
    }
}

// One-of-many as a sunken track with every option as a stop; the accent pill under the chosen one follows the finger across them and settles on the nearest when let go, which is when the choice is made.
@Composable
private fun SettingsSegments(options: List<String>, chosen: Int, isEnabled: Boolean = true, height: Dp = SEGMENTS_HEIGHT, style: TextStyle = Type.navigation, onChoose: (Int) -> Unit) {
    val count = options.size
    val accent = LocalAccent.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentChosen by rememberUpdatedState(chosen)
    val currentOnChoose by rememberUpdatedState(onChoose)
    val position = remember { Animatable(chosen.toFloat()) }
    var isHeld by remember { mutableStateOf(false) }
    // A choice made elsewhere (a pinch on the grid) moves the pill too.
    LaunchedEffect(chosen) {
        if (!isHeld) position.animateTo(chosen.toFloat(), tween(Motion.STATE_MS, easing = Motion.backOut))
    }
    val nearest = position.value.roundToInt().coerceIn(0, count - 1)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(Shapes.capsule)
            .background(Palette.sunkenDeep)
            .border(1.dp, Palette.borderStrong, Shapes.capsule)
            .then(
                if (!isEnabled) Modifier else Modifier.pointerInput(count) {
                    val inset = SEGMENTS_INSET.toPx()
                    val stopWidth = (size.width - inset * 2f) / count
                    fun stopAt(x: Float) = ((x - inset) / stopWidth - 0.5f).coerceIn(0f, (count - 1).toFloat())
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
                        val picked = lastStop
                        scope.launch {
                            position.animateTo(picked.toFloat(), tween(Motion.STATE_MS, easing = Motion.backOut))
                            isHeld = false
                        }
                        if (picked != currentChosen) currentOnChoose(picked)
                    }
                },
            )
            .drawBehind {
                val inset = SEGMENTS_INSET.toPx()
                val width = (size.width - inset * 2f) / count
                drawRoundRect(accent, Offset(inset + width * position.value, inset), Size(width, size.height - inset * 2f), CornerRadius((size.height - inset * 2f) / 2f))
            }
            .padding(horizontal = SEGMENTS_INSET),
    ) {
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, option ->
                val ink by animateColorAsState(if (index == nearest) Palette.sunkenDeep else Palette.textMuted, tween(Motion.PRESS_MS), label = "segment-ink")
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    BasicText(option, style = style.copy(color = ink), maxLines = 1)
                }
            }
        }
    }
}

private val SEGMENTS_HEIGHT = 38.dp
private val TABS_HEIGHT = 42.dp
private val TABS_CAPTIONED_HEIGHT = 52.dp

// The tabs as the same sunken track as every pick, the accent pill riding the pager's own position: it slides with a tap and follows the finger through a swipe. The first tab can carry the place open inside the view on a second line.
@Composable
private fun SettingsTabs(pager: PagerState, labels: List<String>, caption: String?) {
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    val count = labels.size
    val height by animateDpAsState(if (caption != null) TABS_CAPTIONED_HEIGHT else TABS_HEIGHT, tween(Motion.STATE_MS, easing = Motion.powerTwoOut), label = "tabs-height")
    val nearest by remember { derivedStateOf { (pager.currentPage + pager.currentPageOffsetFraction).roundToInt().coerceIn(0, count - 1) } }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(Shapes.capsule)
            .background(Palette.sunkenDeep)
            .border(1.dp, Palette.borderStrong, Shapes.capsule)
            .drawBehind {
                val inset = SEGMENTS_INSET.toPx()
                val width = (size.width - inset * 2f) / count
                val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (count - 1).toFloat())
                drawRoundRect(accent, Offset(inset + width * position, inset), Size(width, size.height - inset * 2f), CornerRadius((size.height - inset * 2f) / 2f))
            }
            .padding(horizontal = SEGMENTS_INSET),
    ) {
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            labels.forEachIndexed { index, label ->
                val ink by animateColorAsState(if (index == nearest) Palette.sunkenDeep else Palette.textMuted, tween(Motion.PRESS_MS), label = "tab-ink")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable(onClick = { scope.launch { pager.animateScrollToPage(index, animationSpec = tween(Motion.STATE_MS * 2, easing = Motion.powerThreeInOut)) } }, pressedScale = 0.96f)
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(label, style = Type.navigation.copy(color = ink), maxLines = 1)
                    if (index == 0) {
                        AnimatedVisibility(caption != null, enter = fadeIn(tween(Motion.STATE_MS)) + expandVertically(tween(Motion.STATE_MS)), exit = fadeOut(tween(Motion.STATE_MS)) + shrinkVertically(tween(Motion.STATE_MS))) {
                            // Kept through its own exit, so the name fades out rather than blanking first.
                            val shown = rememberLastCaption(caption)
                            FadingOverflow(Modifier.padding(top = 2.dp)) {
                                BasicText(shown.uppercase(), style = Type.microLabel.copy(fontSize = 8.sp, letterSpacing = 1.5.sp, color = ink.copy(alpha = 0.8f)), maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberLastCaption(caption: String?): String {
    var last by remember { mutableStateOf(caption.orEmpty()) }
    if (caption != null) last = caption
    return last
}
private val SEGMENTS_INSET = 3.dp

// Days, weeks, months and years can be on together, at least one; the switch under them turns every cut off and greys them, keeping the pick for when it comes back.
// An empty pick stored before it could not be emptied reads as off, and turning on from it starts at months.
@Composable
private fun HeadersLayout(isOn: Boolean, groups: Set<DateGroup>, isEnabled: Boolean = true) {
    val isActive = isOn && groups.isNotEmpty()
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().then(if (isEnabled) Modifier else Modifier.alpha(DISABLED_ALPHA)).padding(start = CARD_INSET, end = CARD_INSET, top = ROW_PADDING)) {
            SettingName("Headers - Layout")
            Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DateGroup.entries.forEach { group ->
                    LayoutChip(group.label, isOn = group in groups, isGreyed = !isActive, modifier = Modifier.weight(1f)) {
                        if (!isEnabled) return@LayoutChip
                        val next = if (group in groups) groups - group else groups + group
                        if (next.isNotEmpty()) Settings.updateDateGroups(next)
                    }
                }
            }
        }
        SettingsToggle("Headers", isActive, isEnabled = isEnabled) { isTurnedOn ->
            if (isTurnedOn && groups.isEmpty()) Settings.updateDateGroups(setOf(DateGroup.MONTHS))
            Settings.updateHeaders(isTurnedOn)
        }
    }
}

// Any-of-many, one chip each: outlined while off, filled with the accent while on, like the portfolio's option buttons.
@Composable
private fun LayoutChip(label: String, isOn: Boolean, isGreyed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val fill by animateColorAsState(if (isOn) accent else Palette.sunkenDeep, tween(Motion.STATE_MS), label = "chip-fill")
    val edge by animateColorAsState(if (isOn) accent else Palette.borderStrong, tween(Motion.STATE_MS), label = "chip-edge")
    val ink by animateColorAsState(if (isOn) Palette.sunkenDeep else Palette.textMuted, tween(Motion.STATE_MS), label = "chip-ink")
    val shade by animateFloatAsState(if (isGreyed) DISABLED_ALPHA else 1f, tween(Motion.STATE_MS), label = "chip-shade")
    Box(
        modifier
            .pressable(onClick = onClick, pressedScale = 0.94f)
            .alpha(shade)
            .height(SEGMENTS_HEIGHT)
            .clip(Shapes.capsule)
            .background(fill)
            .border(1.dp, edge, Shapes.capsule),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label.uppercase(), style = Type.navigation.copy(color = ink), maxLines = 1)
    }
}
