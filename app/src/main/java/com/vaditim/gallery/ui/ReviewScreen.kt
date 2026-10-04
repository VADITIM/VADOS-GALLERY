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
fun ReviewScreen(items: List<MediaItem>, isPrivate: Boolean, onDelete: (List<MediaItem>) -> Unit, onClose: () -> Unit) {
    // Held as they were when review began, so the library refreshing underneath does not reshuffle the stack.
    val order = remember { items.asReversed().toList() }
    val decisions = remember { mutableStateListOf<Pair<MediaItem, Boolean>>() }
    var isFinishing by remember { mutableStateOf(false) }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val marked = decisions.filter { it.second }.map { it.first }
    val position = decisions.size
    val isDone = position >= order.size

    // The card area's width, which a decision flies the photo past.
    var width by remember { mutableFloatStateOf(1f) }
    fun decide(isDelete: Boolean) {
        if (isDone || drag.isRunning) return
        scope.launch {
            drag.animateTo(if (isDelete) -width * 1.4f else width * 1.4f, tween(Motion.STATE_MS, easing = Motion.powerTwoIn))
            decisions += order[position] to isDelete
            Haptics.tick(context)
            drag.snapTo(0f)
            if (decisions.size >= order.size) isFinishing = true
        }
    }

    BackHandler {
        if (isFinishing && !isDone) isFinishing = false else if (marked.isNotEmpty() && !isFinishing) isFinishing = true else onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.viewerGround)
            // Swallows taps so nothing behind the review reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 64.dp, bottom = 96.dp, start = 16.dp, end = 16.dp)
                .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) },
        ) {
            order.getOrNull(position + 1)?.let { next ->
                val progress = (abs(drag.value) / (width * DECIDE_SHARE)).coerceIn(0f, 1f)
                ReviewCard(next, Modifier.graphicsLayer {
                    val scale = NEXT_SCALE + (1f - NEXT_SCALE) * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.5f + 0.5f * progress
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

        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.pressable(onClick = { if (marked.isNotEmpty()) isFinishing = true else onClose() }).clip(Shapes.capsule).background(Palette.panelSolid).padding(horizontal = 14.dp, vertical = 10.dp)) {
                CloseIcon(LocalAccent.current)
            }
            Box(Modifier.weight(1f))
            order.getOrNull(position)?.let { current ->
                ReviewChip(REVIEW_STAMP.format(Instant.ofEpochMilli(current.timestampMillis).atZone(ZoneId.systemDefault())).uppercase(Locale.ENGLISH))
            }
            ReviewChip("${(position + 1).coerceAtMost(order.size)} / ${order.size}")
        }

        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp).clip(Shapes.capsule).background(Palette.panelSolid).padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { decide(isDelete = true) }) { TrashIcon(Palette.danger) }
            IconButton(onClick = {
                if (decisions.isNotEmpty() && !drag.isRunning) {
                    decisions.removeAt(decisions.lastIndex)
                    scope.launch { drag.snapTo(0f) }
                }
            }) { RestoreIcon(if (decisions.isEmpty()) Palette.textFaint else Palette.textBody) }
            if (marked.isNotEmpty()) BasicText("${marked.size}", style = Type.value.copy(color = Palette.danger), modifier = Modifier.padding(horizontal = 6.dp))
            IconButton(onClick = { decide(isDelete = false) }) { CheckIcon(LocalAccent.current) }
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
                    onClose()
                }
            }
            if (!isDone) SheetRow("Keep reviewing", icon = { CheckIcon(it) }) { isFinishing = false }
            SheetRow(if (marked.isEmpty()) "Close" else "Close, delete nothing", color = Palette.textMuted, icon = { CloseIcon(it) }, onClick = onClose)
        }
    }
}

@Composable
private fun ReviewCard(item: MediaItem, modifier: Modifier = Modifier, verdict: Boolean? = null, verdictStrength: Float = 0f) {
    val context = LocalContext.current
    val request = remember(item.uri) {
        ImageRequest.Builder(context).data(item.uri).apply { if (item.isVideo && item.uri.scheme != "content") decoderFactory(VideoFrameDecoder.Factory()) }.build()
    }
    Box(modifier.fillMaxSize().clip(Shapes.viewerPhoto), contentAlignment = Alignment.Center) {
        AsyncImage(model = request, contentDescription = item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        if (verdict != null) {
            val color = if (verdict) LocalAccent.current else Palette.danger
            Box(Modifier.fillMaxSize().background(color.copy(alpha = 0.18f * verdictStrength)))
            Box(
                Modifier
                    .align(if (verdict) Alignment.TopStart else Alignment.TopEnd)
                    .padding(18.dp)
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
private fun ReviewChip(text: String) {
    Box(Modifier.clip(Shapes.capsule).background(Palette.panelSolid).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        MicroLabel(text)
    }
}
