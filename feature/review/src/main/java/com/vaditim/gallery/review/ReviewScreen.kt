package com.vaditim.gallery.review

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import com.vaditim.gallery.components.CheckIcon
import com.vaditim.gallery.components.CloseIcon
import com.vaditim.gallery.components.HighRangeWindow
import com.vaditim.gallery.components.IconButton
import com.vaditim.gallery.components.OverlaySheet
import com.vaditim.gallery.components.ResetIcon
import com.vaditim.gallery.components.RestoreIcon
import com.vaditim.gallery.components.SheetRow
import com.vaditim.gallery.components.TrashIcon
import com.vaditim.gallery.components.calendarWeekLabel
import com.vaditim.gallery.components.dayOf
import com.vaditim.gallery.components.formatDuration
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.vas.Haptics
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

// A swipe past this share of the screen width decides; less springs the photo back.
private const val DECIDE_SHARE = 0.28f
// Degrees the photo leans per screen width it has been dragged.
private const val LEAN_DEGREES = 14f
private const val NEXT_SCALE = 0.92f
// How much of the screen, from the bottom, the shade behind the controls covers.
private const val SHADE_SHARE = 0.45f
// How many photos ahead show in the corner.
private const val UPCOMING_COUNT = 3
private val UPCOMING_WIDTH = 60.dp
private val UPCOMING_HEIGHT = 80.dp
private val UPCOMING_STEP = 40.dp
// A swipe down of this share of the card brings the last photo all the way back; past half of it, letting go takes the decision back.
private const val UNDO_SHARE = 0.6f
private val REVIEW_STAMP = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

// One photo at a time, newest first: swipe left to let it go, right to keep it. Nothing is touched until the end, where the photos let go are deleted in one go — to the trash, or for good inside Private, which is why that last step is always shown.
@Composable
fun ReviewScreen(items: List<MediaItem>, isPrivate: Boolean, progressKey: String, onDelete: (List<MediaItem>) -> Unit, onClose: () -> Unit) {
    HighRangeWindow()
    val context = LocalContext.current
    val progress = remember { ReviewProgress(context) }
    // Held as they were when review began, so the library refreshing underneath does not reshuffle the stack.
    val order = remember { items.asReversed().toList() }
    val saved = remember { progress.read(progressKey) }
    val savedMarks = remember { saved?.markedIds.orEmpty().let { ids -> order.filter { it.id in ids } } }
    // Every sitting picks up where the last one left off; a folder gone through to the end with nothing left marked begins again from the newest.
    val savedReach = remember { progress.reachedCount(saved, order).let { if (it >= order.size && savedMarks.isEmpty()) 0 else it } }
    // The furthest any sitting has got, as a count from the newest.
    var furthest by remember { mutableIntStateOf(savedReach) }
    var startIndex by remember { mutableIntStateOf(savedReach) }
    var carriedMarks by remember { mutableStateOf(savedMarks) }
    val decisions = remember { mutableStateListOf<Pair<MediaItem, Boolean>>() }
    var isFinishing by remember { mutableStateOf(savedReach >= order.size && order.isNotEmpty()) }
    var isResetArmed by remember { mutableStateOf(false) }
    var isDoneArmed by remember { mutableStateOf(false) }
    val drag = remember { Animatable(0f) }
    // How far the last decided photo has come back down over the current one, 0 to 1.
    val comeback = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val marked = carriedMarks + decisions.filter { it.second }.map { it.first }
    val position = startIndex + decisions.size
    val isDone = position >= order.size

    fun saveProgress(markedNow: List<MediaItem>) {
        progress.save(progressKey, order.getOrNull(furthest - 1), markedNow)
    }

    // The card area's width, which a decision flies the photo past.
    var width by remember { mutableFloatStateOf(1f) }
    fun decide(isDelete: Boolean) {
        if (isDone || drag.isRunning) return
        scope.launch {
            drag.animateTo(if (isDelete) -width * 1.4f else width * 1.4f, tween(Motion.STATE_MS, easing = Motion.powerTwoIn))
            decisions += order[position] to isDelete
            isDoneArmed = false
            isResetArmed = false
            furthest = maxOf(furthest, position + 1)
            saveProgress(carriedMarks + decisions.filter { it.second }.map { it.first })
            Haptics.tick(context)
            drag.snapTo(0f)
            if (decisions.size >= order.size) isFinishing = true
        }
    }

    BackHandler {
        if (isFinishing && !isDone) isFinishing = false else if (marked.isNotEmpty() && !isFinishing) isFinishing = true else onClose()
    }

    fun undo() {
        if (decisions.isEmpty() || drag.isRunning) return
        decisions.removeAt(decisions.lastIndex)
        saveProgress(carriedMarks + decisions.filter { it.second }.map { it.first })
        Haptics.tick(context)
        scope.launch { drag.snapTo(0f) }
    }

    // Back to the newest photo with nothing marked; with photos marked it asks for a second tap, since those marks would be lost.
    fun reset() {
        if (carriedMarks.isNotEmpty() || decisions.any { it.second }) {
            if (!isResetArmed) {
                isResetArmed = true
                return
            }
        }
        if (drag.isRunning) return
        progress.clear(progressKey)
        decisions.clear()
        carriedMarks = emptyList()
        furthest = 0
        startIndex = 0
        isResetArmed = false
        isDoneArmed = false
        isFinishing = false
        Haptics.tick(context)
    }

    val shown = order.getOrNull(position)
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.viewerGround)
            // Swallows taps so nothing behind the review reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        // The photo fills the whole screen on black; everything else floats over it.
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) },
        ) {
            run {
                order.getOrNull(position + 1)?.let { next ->
                    val reveal = (abs(drag.value) / (width * DECIDE_SHARE)).coerceIn(0f, 1f)
                    ReviewCard(next, Modifier.graphicsLayer {
                        val scale = NEXT_SCALE + (1f - NEXT_SCALE) * reveal
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.5f + 0.5f * reveal
                    })
                }
                order.getOrNull(position)?.let { current ->
                    val lean = drag.value / width
                    ReviewCard(
                        current,
                        Modifier
                            .graphicsLayer {
                                translationX = drag.value
                                rotationZ = lean * LEAN_DEGREES
                            }
                            // A tap on the left half lets the photo go, on the right half keeps it.
                            .pointerInput(position) {
                                detectTapGestures { point -> decide(isDelete = point.x < size.width / 2f) }
                            }
                            .pointerInput(position) {
                                var across = 0f
                                var down = 0f
                                val settle = {
                                    scope.launch { drag.animateTo(0f, tween(Motion.STATE_MS, easing = Motion.backOut)) }
                                    scope.launch { comeback.animateTo(0f, tween(Motion.STATE_MS, easing = Motion.powerTwoOut)) }
                                    Unit
                                }
                                detectDragGestures(
                                    onDragStart = {
                                        across = 0f
                                        down = 0f
                                    },
                                    onDragEnd = {
                                        when {
                                            // Past halfway the last photo finishes coming back from where the finger left it, and only then is the decision taken back.
                                            comeback.value >= 0.5f -> scope.launch {
                                                comeback.animateTo(1f, tween(Motion.STATE_MS, easing = Motion.powerTwoOut))
                                                undo()
                                                comeback.snapTo(0f)
                                            }
                                            down > abs(across) -> settle()
                                            drag.value < -width * DECIDE_SHARE -> decide(true)
                                            drag.value > width * DECIDE_SHARE -> decide(false)
                                            else -> settle()
                                        }
                                    },
                                    onDragCancel = { settle() },
                                ) { change, amount ->
                                    change.consume()
                                    across += amount.x
                                    down += amount.y
                                    if (abs(down) <= abs(across)) {
                                        scope.launch { drag.snapTo(drag.value + amount.x) }
                                    } else if (decisions.isNotEmpty()) {
                                        // The last photo decided comes down from above with the finger.
                                        scope.launch { comeback.snapTo((down / (size.height * UNDO_SHARE)).coerceIn(0f, 1f)) }
                                    }
                                }
                            },
                        // The verdict shows on the photo as it leans: red for letting go, the accent for keeping.
                        verdict = when {
                            lean < -0.05f -> false
                            lean > 0.05f -> true
                            else -> null
                        },
                        verdictStrength = (abs(drag.value) / (width * DECIDE_SHARE)).coerceIn(0f, 1f),
                    )
                }
                decisions.lastOrNull()?.first?.let { previous ->
                    if (comeback.value > 0f) ReviewCard(previous, Modifier.graphicsLayer { translationY = -(1f - comeback.value) * size.height })
                }
            }
        }
        // A shade behind the controls at the bottom, so they read over any photo.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(SHADE_SHARE).background(Brush.verticalGradient(listOf(Color.Transparent, Palette.viewerGround.copy(alpha = 0.85f)))))
        // The controls float over the photo.
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp)) {
            // The next photos sit in a row of their own above the card, so the photo under them can never cover them.
            run {
                Box(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                    // Starting over from the newest photo; red while it waits for the second tap.
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .pressable(onClick = { reset() })
                            .clip(Shapes.capsule)
                            .background(if (isResetArmed) Palette.danger.copy(alpha = 0.22f) else Palette.panelSolid)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) { ResetIcon(if (isResetArmed) Palette.danger else Palette.textBright) }
                    Box(Modifier.align(Alignment.TopEnd)) { UpcomingPile(order.drop(position + 1).take(UPCOMING_COUNT)) }
                }
            }
            Spacer(Modifier.weight(1f))

            // Done deletes everything marked so far in one go, or with nothing marked simply ends the sitting. Inside Private deleting is final, so there it takes a second tap.
            run {
                val doneColor = if (isDoneArmed) Palette.danger else Palette.textBright
                Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier
                            .pressable(onClick = {
                                if (isPrivate && marked.isNotEmpty() && !isDoneArmed) {
                                    isDoneArmed = true
                                } else {
                                    onDelete(marked)
                                    saveProgress(emptyList())
                                    onClose()
                                }
                            })
                            .clip(Shapes.capsule)
                            .background(if (isDoneArmed) Palette.danger.copy(alpha = 0.22f) else Palette.panelSolid)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (marked.isEmpty()) CheckIcon(LocalAccent.current, size = 18.dp) else TrashIcon(Palette.danger, size = 18.dp)
                        BasicText(
                            when {
                                marked.isEmpty() -> "DONE"
                                isDoneArmed -> "TAP AGAIN · ${marked.size}"
                                else -> "DONE · ${marked.size}"
                            },
                            style = Type.action.copy(color = doneColor),
                        )
                    }
                }
            }

            // The decision buttons under the photo.
            run {
                Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.clip(Shapes.capsule).background(Palette.panelSolid).padding(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { decide(isDelete = true) }) { TrashIcon(Palette.danger) }
                        IconButton(onClick = { undo() }) { RestoreIcon(if (decisions.isEmpty()) Palette.textFaint else Palette.textBody) }
                        if (marked.isNotEmpty()) BasicText("${marked.size}", style = Type.value.copy(color = Palette.danger), modifier = Modifier.padding(horizontal = 6.dp))
                        IconButton(onClick = { decide(isDelete = false) }) { CheckIcon(LocalAccent.current) }
                    }
                }
            }

            ReviewInfo(
                shown = shown,
                number = (position + 1).coerceAtMost(order.size),
                total = order.size,
                furthest = furthest,
                markedCount = marked.size,
                modifier = Modifier.padding(top = 18.dp),
            )

        }

        OverlaySheet(
            visible = isFinishing,
            label = if (marked.isEmpty()) "REVIEWED ${decisions.size}" else "REVIEWED ${decisions.size} · ${marked.size} TO DELETE",
            ground = Palette.viewerGround,
            onDismiss = { if (isDone) onClose() else isFinishing = false },
        ) {
            if (marked.isNotEmpty()) {
                SheetRow(
                    if (isPrivate) "Delete ${marked.size} forever" else "Move ${marked.size} to trash",
                    color = Palette.danger,
                    icon = { TrashIcon(it) },
                ) {
                    onDelete(marked)
                    saveProgress(emptyList())
                    onClose()
                }
            }
            if (!isDone) SheetRow("Keep reviewing", icon = { CheckIcon(it) }) { isFinishing = false }
            // The marks are already saved, so closing keeps them for the next sitting.
            SheetRow(if (marked.isEmpty()) "Close" else "Close, keep ${marked.size} marked", color = Palette.textMuted, icon = { CloseIcon(it) }, onClick = onClose)
            if (marked.isNotEmpty()) {
                SheetRow("Close, unmark all", color = Palette.textMuted, icon = { RestoreIcon(it) }) {
                    saveProgress(emptyList())
                    onClose()
                }
            }
        }
    }
}

// The date, how far through the folder, and a bar for it.
@Composable
private fun ReviewInfo(shown: MediaItem?, number: Int, total: Int, furthest: Int, markedCount: Int, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (shown != null) MicroLabel(REVIEW_STAMP.format(Instant.ofEpochMilli(shown.timestampMillis).atZone(ZoneId.systemDefault())).uppercase(Locale.ENGLISH) + " · " + calendarWeekLabel(dayOf(shown.timestampMillis)))
        // The room is kept even when nothing is marked, so the lines below never jump.
        BasicText("$markedCount marked", style = Type.value.copy(color = Palette.danger), modifier = Modifier.graphicsLayer { alpha = if (markedCount > 0) 1f else 0f })
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText("$number", style = Type.title.copy(color = accent))
            BasicText("/ $total", style = Type.title.copy(color = Palette.textMuted))
            // The furthest point stays shown while swiping behind it.
            if (furthest > number) BasicText("MAX $furthest", style = Type.value.copy(color = Palette.textMuted), modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(Shapes.capsule).background(Palette.borderStrong)) {
            Box(Modifier.fillMaxWidth(if (total > 0) number.toFloat() / total else 0f).height(4.dp).clip(Shapes.capsule).background(accent))
        }
    }
}

@Composable
private fun ReviewCard(item: MediaItem, modifier: Modifier = Modifier, verdict: Boolean? = null, verdictStrength: Float = 0f) {
    val context = LocalContext.current
    val request = remember(item.uri) {
        ImageRequest.Builder(context).data(item.uri).apply { if (item.isVideo && item.uri.scheme != "content") decoderFactory(VideoFrameDecoder.Factory()) }.build()
    }
    Box(modifier.fillMaxSize().background(Palette.viewerGround), contentAlignment = Alignment.Center) {
        AsyncImage(model = request, contentDescription = item.name, contentScale = ContentScale.Fit, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
        if (verdict != null) {
            val color = if (verdict) LocalAccent.current else Palette.danger
            Box(Modifier.fillMaxSize().background(color.copy(alpha = 0.18f * verdictStrength)))
            Box(
                Modifier
                    .align(if (verdict) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(24.dp)
                    .graphicsLayer { alpha = verdictStrength }
                    .clip(Shapes.capsule)
                    .background(Palette.panelSolid)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (verdict) CheckIcon(color) else TrashIcon(color)
            }
        }
        if (item.isVideo && item.durationMillis > 0) {
            BasicText(
                formatDuration(item.durationMillis),
                style = Type.value.copy(color = Palette.textBright),
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(Shapes.capsule).background(Palette.panel).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ReviewChip(text: String, color: androidx.compose.ui.graphics.Color = Palette.textBright) {
    Box(Modifier.clip(Shapes.capsule).background(Palette.panelSolid).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        BasicText(text, style = Type.microLabel.copy(color = color), maxLines = 1)
    }
}

// The next photos ahead, overlapped in the corner with the nearest in front, so what comes after this one can be weighed already.
@Composable
private fun UpcomingPile(items: List<MediaItem>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier.size(width = UPCOMING_WIDTH + UPCOMING_STEP * (UPCOMING_COUNT - 1), height = UPCOMING_HEIGHT)) {
        items.asReversed().forEachIndexed { reversed, item ->
            val depth = items.size - 1 - reversed
            key(item.id) {
                val request = remember(item.uri) {
                    ImageRequest.Builder(context).data(item.uri).apply { if (item.isVideo && item.uri.scheme != "content") decoderFactory(VideoFrameDecoder.Factory()) }.build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = -UPCOMING_STEP * depth)
                        .size(UPCOMING_WIDTH, UPCOMING_HEIGHT)
                        .graphicsLayer {
                            rotationZ = -3f * depth
                            // The further ahead, the dimmer, so the nearest reads first.
                            alpha = 1f - 0.2f * depth
                        }
                        .clip(Shapes.tile)
                        .background(Palette.surface)
                        .border(1.5.dp, if (depth == 0) Palette.textMuted else Palette.borderControl, Shapes.tile),
                )
            }
        }
    }
}
