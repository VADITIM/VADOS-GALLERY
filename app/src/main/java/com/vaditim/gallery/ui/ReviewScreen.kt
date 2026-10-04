package com.vaditim.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// A swipe past this share of the screen width decides; less springs the photo back.
private const val DECIDE_SHARE = 0.28f
// Degrees the photo leans per screen width it has been dragged.
private const val LEAN_DEGREES = 14f
private const val NEXT_SCALE = 0.92f
private val REVIEW_STAMP = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

// One photo at a time, newest first: swipe left to let it go, right to keep it. Nothing is touched until the end, where the photos let go are deleted in one go — to the trash, or for good inside Private, which is why that last step is always shown.
@Composable
fun ReviewScreen(items: List<MediaItem>, isPrivate: Boolean, progressKey: String, onDelete: (List<MediaItem>) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val progress = remember { ReviewProgress(context) }
    // Held as they were when review began, so the library refreshing underneath does not reshuffle the stack.
    val order = remember { items.asReversed().toList() }
    val saved = remember { progress.read(progressKey) }
    // The furthest any sitting has got, as a count from the newest; starting fresh never lowers it.
    var furthest by remember { mutableIntStateOf(progress.reachedCount(saved, order)) }
    val savedMarks = remember { saved?.markedIds.orEmpty().let { ids -> order.filter { it.id in ids } } }
    // Null until a sitting with saved progress has chosen where to begin.
    var startIndex by remember { mutableStateOf(if (furthest == 0 && savedMarks.isEmpty()) 0 else null) }
    var carriedMarks by remember { mutableStateOf(emptyList<MediaItem>()) }
    val decisions = remember { mutableStateListOf<Pair<MediaItem, Boolean>>() }
    var isFinishing by remember { mutableStateOf(false) }
    var isDoneArmed by remember { mutableStateOf(false) }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val marked = carriedMarks + decisions.filter { it.second }.map { it.first }
    val position = (startIndex ?: 0) + decisions.size
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
            furthest = maxOf(furthest, position + 1)
            saveProgress(carriedMarks + decisions.filter { it.second }.map { it.first })
            Haptics.tick(context)
            drag.snapTo(0f)
            if (decisions.size >= order.size) isFinishing = true
        }
    }

    BackHandler {
        if (startIndex == null) onClose() else if (isFinishing && !isDone) isFinishing = false else if (marked.isNotEmpty() && !isFinishing) isFinishing = true else onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.viewerGround)
            // Swallows taps so nothing behind the review reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        // Every card fills the whole screen on black, so a photo of another shape never shows the next one around its edges.
        Box(Modifier.fillMaxSize().onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }) {
            if (startIndex != null) order.getOrNull(position + 1)?.let { next ->
                val reveal = (abs(drag.value) / (width * DECIDE_SHARE)).coerceIn(0f, 1f)
                ReviewCard(next, Modifier.graphicsLayer {
                    val scale = NEXT_SCALE + (1f - NEXT_SCALE) * reveal
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.5f + 0.5f * reveal
                })
            }
            if (startIndex != null) order.getOrNull(position)?.let { current ->
                val lean = drag.value / width
                ReviewCard(
                    current,
                    Modifier
                        .graphicsLayer {
                            translationX = drag.value
                            rotationZ = lean * LEAN_DEGREES
                        }
                        .pointerInput(position) {
                            detectDragGestures(
                                onDragEnd = {
                                    when {
                                        drag.value < -width * DECIDE_SHARE -> decide(true)
                                        drag.value > width * DECIDE_SHARE -> decide(false)
                                        else -> scope.launch { drag.animateTo(0f, tween(Motion.STATE_MS, easing = Motion.backOut)) }
                                    }
                                },
                                onDragCancel = { scope.launch { drag.animateTo(0f, tween(Motion.STATE_MS, easing = Motion.backOut)) } },
                            ) { change, amount ->
                                change.consume()
                                scope.launch { drag.snapTo(drag.value + amount.x) }
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
        }

        if (startIndex != null) Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f))
            order.getOrNull(position)?.let { current ->
                ReviewChip(REVIEW_STAMP.format(Instant.ofEpochMilli(current.timestampMillis).atZone(ZoneId.systemDefault())).uppercase(Locale.ENGLISH))
            }
            ReviewChip("${(position + 1).coerceAtMost(order.size)} / ${order.size}")
            if (furthest > position + 1) ReviewChip("MAX $furthest", Palette.textMuted)
        }

        if (startIndex != null) Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        // Done deletes everything marked so far in one go. Inside Private that is final, so there it takes a second tap.
        if (marked.isNotEmpty()) {
            val doneColor = if (isDoneArmed) Palette.danger else Palette.textBright
            Row(
                Modifier
                    .pressable(onClick = {
                        if (isPrivate && !isDoneArmed) {
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
                TrashIcon(Palette.danger, size = 18.dp)
                BasicText(if (isDoneArmed) "TAP AGAIN · ${marked.size}" else "DONE · ${marked.size}", style = Type.action.copy(color = doneColor))
            }
        }
        Row(
            Modifier.clip(Shapes.capsule).background(Palette.panelSolid).padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { decide(isDelete = true) }) { TrashIcon(Palette.danger) }
            IconButton(onClick = {
                if (decisions.isNotEmpty() && !drag.isRunning) {
                    decisions.removeAt(decisions.lastIndex)
                    saveProgress(carriedMarks + decisions.filter { it.second }.map { it.first })
                    scope.launch { drag.snapTo(0f) }
                }
            }) { RestoreIcon(if (decisions.isEmpty()) Palette.textFaint else Palette.textBody) }
            if (marked.isNotEmpty()) BasicText("${marked.size}", style = Type.value.copy(color = Palette.danger), modifier = Modifier.padding(horizontal = 6.dp))
            IconButton(onClick = { decide(isDelete = false) }) { CheckIcon(LocalAccent.current) }
        }
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

        // A folder reviewed before opens on where it was left: the last photo looked at, how far that is, and the choice to carry on or begin again from the newest.
        if (startIndex == null) {
            ResumePane(
                lastSeen = order.getOrNull(furthest - 1) ?: savedMarks.firstOrNull(),
                reached = furthest,
                total = order.size,
                markedCount = savedMarks.size,
                canContinue = furthest < order.size || savedMarks.isNotEmpty(),
                onContinue = {
                    carriedMarks = savedMarks
                    startIndex = furthest
                    if (furthest >= order.size) isFinishing = true
                },
                onStartFresh = {
                    startIndex = 0
                    saveProgress(emptyList())
                },
            )
        }
    }
}

@Composable
private fun ResumePane(lastSeen: MediaItem?, reached: Int, total: Int, markedCount: Int, canContinue: Boolean, onContinue: () -> Unit, onStartFresh: () -> Unit) {
    val accent = LocalAccent.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (lastSeen != null) {
                val context = LocalContext.current
                val request = remember(lastSeen.uri) { ImageRequest.Builder(context).data(lastSeen.uri).build() }
                AsyncImage(
                    model = request,
                    contentDescription = lastSeen.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clip(Shapes.viewerPhoto),
                )
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (lastSeen != null) MicroLabel(REVIEW_STAMP.format(Instant.ofEpochMilli(lastSeen.timestampMillis).atZone(ZoneId.systemDefault())).uppercase(Locale.ENGLISH))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("$reached", style = Type.title.copy(color = accent))
                BasicText("/ $total", style = Type.title.copy(color = Palette.textMuted))
            }
            // How far through the folder the furthest sitting got.
            Box(Modifier.fillMaxWidth().height(4.dp).clip(Shapes.capsule).background(Palette.borderStrong)) {
                Box(Modifier.fillMaxWidth(if (total > 0) reached.toFloat() / total else 0f).height(4.dp).clip(Shapes.capsule).background(accent))
            }
            if (markedCount > 0) BasicText("$markedCount marked", style = Type.value.copy(color = Palette.danger))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResumeButton("START FRESH", Palette.panelSolid, Palette.textBright, Modifier.weight(1f), onStartFresh)
            if (canContinue) ResumeButton("CONTINUE", accent, Palette.viewerGround, Modifier.weight(1f), onContinue)
        }
    }
}

@Composable
private fun ResumeButton(label: String, background: androidx.compose.ui.graphics.Color, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.pressable(onClick = onClick).clip(Shapes.capsule).background(background).padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = Type.action.copy(color = color))
    }
}

@Composable
private fun ReviewCard(item: MediaItem, modifier: Modifier = Modifier, verdict: Boolean? = null, verdictStrength: Float = 0f) {
    val context = LocalContext.current
    val request = remember(item.uri) {
        ImageRequest.Builder(context).data(item.uri).apply { if (item.isVideo && item.uri.scheme != "content") decoderFactory(VideoFrameDecoder.Factory()) }.build()
    }
    Box(modifier.fillMaxSize().background(Palette.viewerGround), contentAlignment = Alignment.Center) {
        AsyncImage(model = request, contentDescription = item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
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
