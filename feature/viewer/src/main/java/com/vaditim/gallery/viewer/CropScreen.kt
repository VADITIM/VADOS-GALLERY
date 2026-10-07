package com.vaditim.gallery.viewer

import android.graphics.PointF
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMillis
import com.vaditim.gallery.components.BackIcon
import com.vaditim.gallery.components.CheckIcon
import com.vaditim.gallery.components.CropIcon
import com.vaditim.gallery.components.MediaActions
import com.vaditim.gallery.components.NavBar
import com.vaditim.gallery.components.PenIcon
import com.vaditim.gallery.components.RedoIcon
import com.vaditim.gallery.components.RotateIcon
import com.vaditim.gallery.components.TopButton
import com.vaditim.gallery.components.UndoIcon
import com.vaditim.gallery.components.fitInside
import com.vaditim.gallery.media.DrawnStroke
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.settings.Settings
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.LocalHazeState
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.glass
import com.vaditim.gallery.vas.pressable
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HANDLE_REACH = 32.dp
private val MIN_CROP = 56.dp
private val CORNER_LENGTH = 18.dp
private const val MIN_TRIM_MS = 500L
private const val TRIM_FRAMES = 8
private const val PREVIEW_PIXELS = 2048
private val FULL = Rect(0f, 0f, 1f, 1f)
// Unavailable actions keep their accent, faded.
private const val UNAVAILABLE_ALPHA = 0.38f

// The pencil's thinnest and thickest line on screen; what is saved keeps the same share of the picture.
private val PENCIL_THINNEST = 2.dp
private val PENCIL_THICKEST = 28.dp
private val SWATCH = 20.dp

// What the editor's nav switches between: cutting the frame, or drawing on the photo.
private enum class EditMode { CROP, DRAW }

// One state of everything the editor can change, as the undo history keeps it. `turns` counts quarter turns clockwise and is never wrapped, so undoing a turn runs it back the way it came.
private data class CropEdit(val crop: Rect, val aspect: Aspect, val zoom: Float, val pan: Offset, val startMs: Long, val endMs: Long, val turns: Int, val strokes: List<DrawnStroke>)

// A glass capsule around one accent icon; it fades while there is nothing for it to do.
@Composable
private fun CropAction(onClick: () -> Unit, isEnabled: Boolean, onLongClick: (() -> Unit)? = null, icon: @Composable (Color) -> Unit) {
    val shade by animateFloatAsState(if (isEnabled) 1f else UNAVAILABLE_ALPHA, tween(Motion.STATE_MS), label = "crop-action")
    Box(
        Modifier
            .pressable(onClick = onClick, onLongClick = onLongClick)
            .glass(Shapes.capsule, Palette.viewerGround)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .graphicsLayer { alpha = shade },
    ) { icon(LocalAccent.current) }
}
private const val MAX_CROP_ZOOM = 8f
// One notch of a mouse wheel zooms by this much.
private const val WHEEL_ZOOM_STEP = 1.1f
// One tap on a trim arrow moves its end by a tenth of a second.
private const val TRIM_STEP_MS = 100L

// Width over height; ORIGINAL takes the picture's own.
private enum class Aspect(val label: String, val ratio: Float?) {
    FREE("Free", null),
    ORIGINAL("Original", -1f),
    SQUARE("1:1", 1f),
    PORTRAIT("4:5", 4f / 5f),
    LANDSCAPE("4:3", 4f / 3f),
    WIDE("16:9", 16f / 9f),
    TALL("9:16", 9f / 16f),
}

// A ratio follows the frame round a quarter turn; one the list has no turned twin for lets go and the frame is free.
private fun Aspect.turned(): Aspect = when (this) {
    Aspect.WIDE -> Aspect.TALL
    Aspect.TALL -> Aspect.WIDE
    Aspect.FREE, Aspect.ORIGINAL, Aspect.SQUARE -> this
    else -> Aspect.FREE
}

private enum class Handle { MOVE, LEFT, TOP, RIGHT, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

// Cropping, turning and drawing on a photo, and cropping, turning and trimming a video. What is kept is saved as a copy beside the original.
@Composable
fun CropScreen(item: MediaItem, actions: MediaActions, onClose: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val video = rememberVideoState(item)
    var imageRatio by remember { mutableStateOf<Float?>(null) }
    val fallbackRatio = if (item.width > 0 && item.height > 0) item.width.toFloat() / item.height else 1f
    val ratio = (if (video != null) video.ratio else imageRatio) ?: fallbackRatio
    // The frame on screen, as fractions of the picture's box; it stays put while the picture zooms under it.
    var crop by remember { mutableStateOf(FULL) }
    var aspect by remember { mutableStateOf(Aspect.FREE) }
    // The picture's zoom under the frame, and its shift from centre as fractions of its box; the box is always covered, so the frame never holds empty space.
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val duration = video?.durationMs ?: 0L
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(duration) { if (duration > 0 && endMs == 0L) endMs = duration }
    var savingJob by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var mode by remember { mutableStateOf(EditMode.CROP) }
    var turns by remember { mutableIntStateOf(0) }
    // Finished lines, and the one under the finger; both in the upright picture's fractions, so they turn, zoom and crop with it.
    var strokes by remember { mutableStateOf<List<DrawnStroke>>(emptyList()) }
    var drawing by remember { mutableStateOf<DrawnStroke?>(null) }
    val quarterTurns = Math.floorMod(turns, 4)
    // The picture as the frame shows it: turned on its side, it stands the other way up.
    val shownRatio = if (quarterTurns % 2 == 1) 1f / ratio else ratio
    val angle by animateFloatAsState(turns * 90f, tween(Motion.STATE_MS, easing = Motion.powerThreeInOut), label = "turn")
    val isTurning = angle != turns * 90f

    val lockedRatio = when (aspect.ratio) {
        null -> null
        -1f -> shownRatio
        else -> aspect.ratio
    }
    // What is kept, as fractions of the picture as it is turned: the frame seen through the zoom.
    val kept = Rect(
        0.5f + (crop.left - 0.5f - pan.x) / zoom,
        0.5f + (crop.top - 0.5f - pan.y) / zoom,
        0.5f + (crop.right - 0.5f - pan.x) / zoom,
        0.5f + (crop.bottom - 0.5f - pan.y) / zoom,
    )
    val isTrimmed = video != null && duration > 0 && (startMs > 0 || endMs < duration)
    val isChanged = kept != FULL || isTrimmed || quarterTurns != 0 || strokes.isNotEmpty()

    // A quarter turn clockwise: the frame, the shift and the ratio turn with the picture, so the same part stays framed.
    val turn = {
        crop = Rect(1f - crop.bottom, crop.left, 1f - crop.top, crop.right)
        pan = Offset(-pan.y, pan.x)
        aspect = aspect.turned()
        turns += 1
    }
    // A point on the frame, in its own pixels, as a fraction of the upright picture: the zoom and the turns taken back off.
    val toPicture = { at: Offset, box: Size ->
        var x = 0.5f + (at.x / box.width - 0.5f - pan.x) / zoom
        var y = 0.5f + (at.y / box.height - 0.5f - pan.y) / zoom
        repeat(quarterTurns) {
            val upright = y
            y = 1f - x
            x = upright
        }
        PointF(x, y)
    }

    // The trimmed part plays round and round, so the cut can be watched while it is set.
    if (video != null) {
        LaunchedEffect(video) {
            while (true) {
                if (!video.isScrubbing && endMs > 0 && video.player.currentPosition >= endMs) video.player.seekTo(startMs)
                delay(30)
            }
        }
    }

    // 0 is the viewer's layout, 1 the crop's: the picture travels from where the viewer showed it into the frame, and the controls come in from the edges; leaving runs it back.
    val arrival = remember { Animatable(0f) }
    val isMotionReduced = remember { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var isLeaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (isMotionReduced) arrival.snapTo(1f) else arrival.animateTo(1f, tween(Motion.VIEWER_ENTER_MS, easing = Motion.powerThreeInOut)) }
    val leave = leave@{
        if (isLeaving) return@leave
        isLeaving = true
        video?.player?.pause()
        scope.launch {
            if (isMotionReduced) arrival.snapTo(0f) else arrival.animateTo(0f, tween(Motion.VIEWER_ENTER_MS, easing = Motion.powerThreeInOut))
            onClose()
        }
    }

    BackHandler {
        if (savingJob != null) {
            savingJob?.cancel()
            savingJob = null
        } else {
            leave()
        }
    }

    val save = save@{
        if (!isChanged || savingJob != null) return@save
        video?.player?.pause()
        progress = 0f
        savingJob = actions.crop(
            item,
            RectF(kept.left, kept.top, kept.right, kept.bottom),
            startMs = if (video != null) startMs else 0L,
            endMs = if (video != null && endMs < duration) endMs else C.TIME_END_OF_SOURCE,
            onProgress = { progress = it },
            quarterTurns = quarterTurns,
            strokes = strokes,
        ) { isDone ->
            savingJob = null
            if (isDone) leave()
        }
    }
    // Every settled edit, oldest first; undo and redo walk it, and a new edit after an undo drops the steps ahead.
    val history = remember { mutableStateListOf<CropEdit>() }
    var step by remember { mutableIntStateOf(0) }
    val current = CropEdit(crop, aspect, zoom, pan, startMs, endMs, turns, strokes)
    val isReady = video == null || endMs > 0
    LaunchedEffect(current, isReady) {
        if (!isReady) return@LaunchedEffect
        if (history.isEmpty()) {
            history.add(current)
            return@LaunchedEffect
        }
        if (current == history[step]) return@LaunchedEffect
        delay(Motion.EDIT_SETTLE_MS)
        while (history.size > step + 1) history.removeAt(history.lastIndex)
        history.add(current)
        step = history.lastIndex
    }
    val apply: (CropEdit) -> Unit = { edit ->
        crop = edit.crop
        aspect = edit.aspect
        zoom = edit.zoom
        pan = edit.pan
        startMs = edit.startMs
        endMs = edit.endMs
        turns = edit.turns
        strokes = edit.strokes
    }
    // An edit still settling counts as a step of its own, so undo takes it back first.
    val isUnsettled = history.isNotEmpty() && current != history[step]
    val canUndo = step > 0 || isUnsettled
    val canRedo = !isUnsettled && step < history.lastIndex
    val undo = {
        if (isUnsettled) {
            apply(history[step])
        } else if (step > 0) {
            step--
            apply(history[step])
        }
    }
    val redo = {
        if (canRedo) {
            step++
            apply(history[step])
        }
    }
    // Holding undo reverts everything, as one more step that redo can take back.
    val revert = {
        if (history.isNotEmpty() && current != history.first()) {
            while (history.size > step + 1) history.removeAt(history.lastIndex)
            if (current != history[step]) history.add(current)
            history.add(history.first())
            step = history.lastIndex
            apply(history[step])
        }
    }

    // Where the screen and the frame are, so the picture can start on the viewer's spot and land in the frame.
    var screen by remember { mutableStateOf(Rect.Zero) }
    var frame by remember { mutableStateOf(Rect.Zero) }
    val hazeState = rememberHazeState()
    // Its own glass over its own picture: the viewer's photo lies under this screen and must not be blurred through.
    CompositionLocalProvider(LocalHazeState provides hazeState, LocalAccent provides Palette.cropViolet) {
    Box(Modifier.fillMaxSize().onGloballyPositioned { screen = it.boundsInWindow() }.background(Palette.viewerGround)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).graphicsLayer {
                    translationY = -(1f - arrival.value) * (size.height + 12.dp.toPx())
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopButton(leave) { BackIcon(LocalAccent.current) }
                Spacer(Modifier.weight(1f))
                // Turning belongs to cutting the frame; it pops in and out with that mode.
                AnimatedVisibility(
                    visible = mode == EditMode.CROP,
                    enter = scaleIn(tween(Motion.STATE_MS, easing = Motion.backOut), initialScale = 0f),
                    exit = scaleOut(tween(Motion.STATE_MS, easing = Motion.backIn), targetScale = 0f),
                ) {
                    TopButton(turn) { RotateIcon(LocalAccent.current) }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
            // A zoomed picture spills out of its frame, veiled, as far as the room between the controls; on its way in or out the picture's own rounded clip holds it instead.
            BoxWithConstraints(
                Modifier.fillMaxSize().graphicsLayer { clip = arrival.value == 1f }.padding(horizontal = 28.dp, vertical = 20.dp).hazeSource(hazeState),
                contentAlignment = Alignment.Center,
            ) {
                // The frame holds the picture at its present angle: mid-turn it is the box around the leaning picture, so the picture always fits the room while it swings round.
                val radians = Math.toRadians(angle.toDouble())
                val cosine = abs(cos(radians)).toFloat()
                val sine = abs(sin(radians)).toFloat()
                val boundWidth = ratio * cosine + sine
                val boundHeight = ratio * sine + cosine
                val pictureHeight = min(maxWidth.value / boundWidth, maxHeight.value / boundHeight).dp
                val pictureWidth = pictureHeight * ratio
                val frameWidth = pictureHeight * boundWidth
                val frameHeight = pictureHeight * boundHeight
                Box(
                    Modifier
                        .size(frameWidth, frameHeight)
                        .onGloballyPositioned { frame = it.boundsInWindow() }
                        .graphicsLayer {
                            // From the box the viewer fits the picture in to this one, as one move and one scale, so the picture never changes shape on the way.
                            val viewerSpot = fitInside(shownRatio, screen.width, screen.height).translate(screen.topLeft)
                            if (frame.width > 0f && viewerSpot.width > 0f) {
                                val away = 1f - arrival.value
                                transformOrigin = TransformOrigin(0f, 0f)
                                val travel = 1f + (viewerSpot.width / frame.width - 1f) * away
                                scaleX = travel
                                scaleY = travel
                                translationX = (viewerSpot.left - frame.left) * away
                                translationY = (viewerSpot.top - frame.top) * away
                            }
                        },
                ) {
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            // The viewer's rounded corners run down to square ones as the picture travels into the frame, and back as it leaves; the frame's own scale is taken off so the rounding on screen matches the viewer's.
                            val away = 1f - arrival.value
                            val viewerSpot = fitInside(shownRatio, screen.width, screen.height)
                            val travel = if (frame.width > 0f && viewerSpot.width > 0f) 1f + (viewerSpot.width / frame.width - 1f) * away else 1f
                            clip = away > 0f
                            shape = RoundedCornerShape(Shapes.viewerPhotoCorner.toPx() * away / travel)
                        },
                    ) {
                        // The zoom works on the picture as the frame shows it, turned; the turn works on the picture and its lines together.
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x * size.width
                                translationY = pan.y * size.height
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.requiredSize(pictureWidth, pictureHeight).graphicsLayer { rotationZ = angle }) {
                                if (video != null) {
                                    VideoSurface(video, Modifier.fillMaxSize())
                                } else {
                                    val request = remember(item.uri) { ImageRequest.Builder(context).data(item.uri).size(PREVIEW_PIXELS).build() }
                                    AsyncImage(
                                        model = request,
                                        contentDescription = item.name,
                                        contentScale = ContentScale.Fit,
                                        onSuccess = { success ->
                                            val size = success.painter.intrinsicSize
                                            if (size.width > 0f && size.height > 0f) imageRatio = size.width / size.height
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Canvas(Modifier.fillMaxSize()) {
                                        strokes.forEach { drawStroke(it) }
                                        drawing?.let { drawStroke(it) }
                                    }
                                }
                            }
                        }
                    }
                    // Zooming is for the photo; a video's frame is cropped as it is.
                    val zoomBy: ((Float, Offset, Offset) -> Unit)? = if (video != null) null else { { factor, shift, focus ->
                        val nextZoom = (zoom * factor).coerceIn(1f, MAX_CROP_ZOOM)
                        // The point under the fingers stays under them, as in the viewer.
                        val centre = Offset(0.5f, 0.5f)
                        val moved = (focus - centre) - (focus - centre - pan) * (nextZoom / zoom) + shift
                        val limit = (nextZoom - 1f) / 2f
                        zoom = nextZoom
                        pan = Offset(moved.x.coerceIn(-limit, limit), moved.y.coerceIn(-limit, limit))
                    } }
                    // Mid-turn the frame is not over the picture it marks, so it fades out while the picture swings and back once it lands.
                    val frameAlpha = { arrival.value * (1f - (abs(angle - turns * 90f) / 90f).coerceIn(0f, 1f)) }
                    if (mode == EditMode.CROP) {
                        CropFrame(
                            crop = crop,
                            lockedRatio = lockedRatio,
                            onChange = { crop = it },
                            onTap = { video?.let { if (it.player.isPlaying) it.player.pause() else it.player.play() } },
                            onZoom = zoomBy,
                            isZoomed = zoom > 1f,
                            modifier = Modifier.graphicsLayer { alpha = frameAlpha() },
                        )
                    } else {
                        DrawLayer(
                            crop = crop,
                            isEnabled = !isTurning,
                            onStart = { at, box ->
                                // The width is the pencil's on screen as a share of the picture's own width as it is shown now.
                                val shownWidth = (if (quarterTurns % 2 == 1) box.height else box.width) * zoom
                                val thickness = with(density) { (PENCIL_THINNEST + (PENCIL_THICKEST - PENCIL_THINNEST) * Settings.pencilThickness).toPx() }
                                drawing = DrawnStroke(listOf(toPicture(at, box)), Settings.pencilColor, thickness / shownWidth)
                            },
                            onMove = { at, box -> drawing = drawing?.let { it.copy(points = it.points + toPicture(at, box)) } },
                            onEnd = {
                                drawing?.let { strokes = strokes + it }
                                drawing = null
                            },
                            onCancel = { drawing = null },
                            onZoom = zoomBy,
                            modifier = Modifier.graphicsLayer { alpha = frameAlpha() },
                        )
                    }
                }
            }

            // Over the picture's bottom left, each mode stands its own column, the ratios for cutting or the pencil for drawing, its first choice at the bottom; one pops away and the other pops in.
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    scaleIn(tween(Motion.STATE_MS, delayMillis = Motion.STATE_MS, easing = Motion.backOut), initialScale = 0f, transformOrigin = TransformOrigin(0f, 1f))
                        .togetherWith(scaleOut(tween(Motion.STATE_MS, easing = Motion.backIn), targetScale = 0f, transformOrigin = TransformOrigin(0f, 1f)))
                        .using(SizeTransform(clip = false))
                },
                contentAlignment = Alignment.BottomStart,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 8.dp).graphicsLayer {
                    translationX = -(1f - arrival.value) * (size.width + 16.dp.toPx())
                    alpha = arrival.value
                },
                label = "edit-mode",
            ) { shownMode ->
                when (shownMode) {
                    EditMode.CROP -> NavBar(
                        Aspect.entries.reversed(),
                        aspect,
                        onSelect = { option ->
                            aspect = option
                            crop = fitted(option.ratio?.let { if (it < 0f) shownRatio else it }, shownRatio) ?: crop
                        },
                        isVertical = true,
                    ) { option ->
                        val ink by animateColorAsState(if (option == aspect) LocalAccent.current else Palette.textMuted, tween(Motion.STATE_MS), label = "aspect-ink")
                        BasicText(option.label.uppercase(), style = Type.microLabel.copy(color = ink), maxLines = 1, softWrap = false)
                    }
                    EditMode.DRAW -> PencilColumn(
                        color = Settings.pencilColor,
                        thickness = Settings.pencilThickness,
                        onColor = Settings::updatePencilColor,
                        onThickness = Settings::updatePencilThickness,
                    )
                }
            }
            }

            Column(
                Modifier.fillMaxWidth().graphicsLayer {
                    translationY = (1f - arrival.value) * (size.height + 12.dp.toPx())
                    alpha = arrival.value
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (video != null && duration > 0 && endMs > 0) {
                    TrimBar(
                        item = item,
                        video = video,
                        durationMs = duration,
                        startMs = startMs,
                        endMs = endMs,
                        onTrim = { start, end ->
                            startMs = start
                            endMs = end
                        },
                    )
                }

                // Bottom right, level with the nav: undo, redo and save; the nav takes the left of the foot.
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Drawing is for photos, so a video keeps to cutting and has no nav.
                    if (video == null) {
                        NavBar(
                            EditMode.entries,
                            mode,
                            onSelect = { chosen -> if (savingJob == null) mode = chosen },
                        ) { option ->
                            val ink by animateColorAsState(if (option == mode) LocalAccent.current else Palette.textMuted, tween(Motion.STATE_MS), label = "mode-ink")
                            when (option) {
                                EditMode.CROP -> CropIcon(ink)
                                EditMode.DRAW -> PenIcon(ink)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CropAction(onClick = undo, isEnabled = canUndo, onLongClick = revert) { UndoIcon(it) }
                        CropAction(onClick = redo, isEnabled = canRedo) { RedoIcon(it) }
                        CropAction(onClick = save, isEnabled = isChanged) { CheckIcon(it) }
                    }
                }
            }
        }

        if (savingJob != null) {
            // Nothing under it answers while the copy is written; back cancels it.
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false).consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.glass(Shapes.capsule, Palette.viewerGround).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    MicroLabel(if (video != null) "Saving ${(progress * 100).toInt()}%" else "Saving")
                }
            }
        }
    }
    }
}

// The largest centred part of the picture with the given width over height; null keeps the crop as it is.
private fun fitted(target: Float?, pictureRatio: Float): Rect? {
    if (target == null) return null
    val widthFraction = target / pictureRatio
    return if (widthFraction <= 1f) {
        Rect((1f - widthFraction) / 2f, 0f, (1f + widthFraction) / 2f, 1f)
    } else {
        val heightFraction = 1f / widthFraction
        Rect(0f, (1f - heightFraction) / 2f, 1f, (1f + heightFraction) / 2f)
    }
}

// The crop rectangle over the picture: corners and edges resize it, the inside moves it, the rest is veiled. Two fingers zoom and pan the picture under it, and a mouse wheel zooms; once zoomed, one finger anywhere but on an edge or corner swipes the picture.
@Composable
private fun CropFrame(crop: Rect, lockedRatio: Float?, onChange: (Rect) -> Unit, onTap: () -> Unit, onZoom: ((factor: Float, shift: Offset, focus: Offset) -> Unit)?, isZoomed: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val reach = with(density) { HANDLE_REACH.toPx() }
    val minimum = with(density) { MIN_CROP.toPx() }
    val accent = LocalAccent.current
    val current by rememberUpdatedState(crop)
    val lock by rememberUpdatedState(lockedRatio)
    val change by rememberUpdatedState(onChange)
    val tap by rememberUpdatedState(onTap)
    val zoom by rememberUpdatedState(onZoom)
    val zoomed by rememberUpdatedState(isZoomed)
    var handle by remember { mutableStateOf<Handle?>(null) }
    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { tap() } }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val zoomBy = zoom ?: continue
                        if (event.type != PointerEventType.Scroll) continue
                        val wheel = event.changes.first()
                        zoomBy(WHEEL_ZOOM_STEP.pow(-wheel.scrollDelta.y), Offset.Zero, Offset(wheel.position.x / size.width, wheel.position.y / size.height))
                        wheel.consume()
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var travelled = Offset.Zero
                    var isDragging = false
                    var isPinching = false
                    // Null with the drag started away from the frame: that drag pans the picture.
                    var held: Handle? = null
                    do {
                        val event = awaitPointerEvent()
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        val zoomBy = zoom
                        if (zoomBy != null && event.changes.count { it.pressed } >= 2) {
                            isPinching = true
                            handle = null
                        }
                        if (isPinching) {
                            val centroid = event.calculateCentroid(useCurrent = false)
                            if (zoomBy != null && centroid.isSpecified) {
                                val shift = event.calculatePan()
                                zoomBy(event.calculateZoom(), Offset(shift.x / width, shift.y / height), Offset(centroid.x / width, centroid.y / height))
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                            continue
                        }
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        val amount = pointer.position - pointer.previousPosition
                        if (!isDragging) {
                            travelled += amount
                            if (travelled.getDistance() <= viewConfiguration.touchSlop) continue
                            isDragging = true
                            // Zoomed in, a drag inside the frame swipes the picture under it; the frame then moves only by its edges and corners.
                            held = hit(down.position, current.scaledTo(width, height), reach).takeUnless { it == Handle.MOVE && zoomed && zoom != null }
                            handle = held
                        }
                        val grip = held
                        if (grip != null) {
                            val moved = dragged(current.scaledTo(width, height), grip, amount, Size(width, height), lock, minimum)
                            change(Rect(moved.left / width, moved.top / height, moved.right / width, moved.bottom / height))
                            pointer.consume()
                        } else if (zoomBy != null) {
                            zoomBy(1f, Offset(amount.x / width, amount.y / height), Offset(0.5f, 0.5f))
                            pointer.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    handle = null
                }
            },
    ) {
        val r = crop.scaledTo(size.width, size.height)
        veilOutside(r)
        // Thirds, only while it is being moved: a guide for placing, not decoration.
        if (handle != null) {
            val line = Palette.textBright.copy(alpha = 0.35f)
            for (third in 1..2) {
                val x = r.left + r.width * third / 3f
                val y = r.top + r.height * third / 3f
                drawLine(line, Offset(x, r.top), Offset(x, r.bottom), 1.dp.toPx())
                drawLine(line, Offset(r.left, y), Offset(r.right, y), 1.dp.toPx())
            }
        }
        drawRect(Palette.textBright, r.topLeft, r.size, style = Stroke(1.dp.toPx()))
        val length = min(CORNER_LENGTH.toPx(), min(r.width, r.height) / 2f)
        val thick = 3.dp.toPx()
        listOf(r.topLeft to Offset(1f, 1f), r.topRight to Offset(-1f, 1f), r.bottomLeft to Offset(1f, -1f), r.bottomRight to Offset(-1f, -1f)).forEach { (corner, inward) ->
            drawLine(accent, corner, corner + Offset(length * inward.x, 0f), thick, StrokeCap.Round)
            drawLine(accent, corner, corner + Offset(0f, length * inward.y), thick, StrokeCap.Round)
        }
    }
}

private fun Rect.scaledTo(width: Float, height: Float) = Rect(left * width, top * height, right * width, bottom * height)

// Everything outside the frame is veiled, reaching well past the picture's box, so the part of a zoomed picture spilling out of it shows darkened, plainly not kept.
private fun DrawScope.veilOutside(r: Rect) {
    val veil = Color.Black.copy(alpha = 0.6f)
    val far = max(size.width, size.height) * MAX_CROP_ZOOM
    drawRect(veil, Offset(-far, -far), Size(size.width + far * 2f, r.top + far))
    drawRect(veil, Offset(-far, r.bottom), Size(size.width + far * 2f, size.height - r.bottom + far))
    drawRect(veil, Offset(-far, r.top), Size(r.left + far, r.height))
    drawRect(veil, Offset(r.right, r.top), Size(size.width - r.right + far, r.height))
}

// One line, drawn into a box the size of the upright picture.
private fun DrawScope.drawStroke(stroke: DrawnStroke) {
    val first = stroke.points.firstOrNull() ?: return
    val color = Color(stroke.color)
    val width = stroke.width * size.width
    if (stroke.points.size == 1) {
        drawCircle(color, width / 2f, Offset(first.x * size.width, first.y * size.height))
        return
    }
    val path = Path().apply {
        moveTo(first.x * size.width, first.y * size.height)
        for (index in 1 until stroke.points.size) lineTo(stroke.points[index].x * size.width, stroke.points[index].y * size.height)
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// Drawing over the photo: one finger draws, two zoom and pan the picture under the frame as in cutting. The part outside the frame stays veiled, so what is drawn there is plainly not kept.
@Composable
private fun DrawLayer(
    crop: Rect,
    isEnabled: Boolean,
    onStart: (Offset, Size) -> Unit,
    onMove: (Offset, Size) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
    onZoom: ((factor: Float, shift: Offset, focus: Offset) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val enabled by rememberUpdatedState(isEnabled)
    val start by rememberUpdatedState(onStart)
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onEnd)
    val cancel by rememberUpdatedState(onCancel)
    val zoom by rememberUpdatedState(onZoom)
    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!enabled) return@awaitEachGesture
                    val box = Size(size.width.toFloat(), size.height.toFloat())
                    start(down.position, box)
                    down.consume()
                    var isPinching = false
                    do {
                        val event = awaitPointerEvent()
                        // A second finger means the first was not drawing, so its line is taken back.
                        if (!isPinching && event.changes.count { it.pressed } >= 2) {
                            isPinching = true
                            cancel()
                        }
                        if (isPinching) {
                            val zoomBy = zoom
                            val centroid = event.calculateCentroid(useCurrent = false)
                            if (zoomBy != null && centroid.isSpecified) {
                                val shift = event.calculatePan()
                                zoomBy(event.calculateZoom(), Offset(shift.x / box.width, shift.y / box.height), Offset(centroid.x / box.width, centroid.y / box.height))
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                            continue
                        }
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (pointer.positionChanged()) {
                            move(pointer.position, box)
                            pointer.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    if (!isPinching) end()
                }
            },
    ) {
        val r = crop.scaledTo(size.width, size.height)
        veilOutside(r)
        drawRect(Palette.textBright.copy(alpha = 0.5f), r.topLeft, r.size, style = Stroke(1.dp.toPx()))
    }
}

// The pencil, standing at the bottom left: its colours as dots from the bottom up, the chosen one ringed, and beside them how thick it draws on a standing drag-only slider whose knob is the line itself.
@Composable
private fun PencilColumn(color: Int, thickness: Float, onColor: (Int) -> Unit, onThickness: (Float) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Row(
        modifier.glass(Shapes.capsule, Palette.viewerGround).padding(horizontal = 12.dp, vertical = 16.dp).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Palette.pencil.reversed().forEach { swatch ->
                val argb = swatch.toArgb()
                val ring by animateFloatAsState(if (argb == color) 1f else 0f, tween(Motion.STATE_MS, easing = Motion.backOut), label = "swatch")
                Canvas(Modifier.size(SWATCH).pressable(onClick = { onColor(argb) }, pressedScale = 0.85f)) {
                    val radius = size.minDimension / 2f
                    drawCircle(swatch, radius * (1f - 0.3f * ring))
                    // Black would vanish on the black behind it, so every dot carries a faint edge.
                    drawCircle(Palette.borderControl, radius * (1f - 0.3f * ring), style = Stroke(1.dp.toPx()))
                    if (ring > 0f) drawCircle(accent, radius - 1.dp.toPx(), style = Stroke(2.dp.toPx()), alpha = ring.coerceIn(0f, 1f))
                }
            }
        }
        ThicknessSlider(thickness, Color(color), onThickness, Modifier.fillMaxHeight().width(PENCIL_THICKEST))
    }
}

// A standing drag-only slider, as in the settings (VAS components/03-panel-and-field.md §3) but upright: the value moves by how far the finger travels up from where it went down, and a tap changes nothing.
@Composable
private fun ThicknessSlider(fraction: Float, ink: Color, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val accent = LocalAccent.current
    val currentFraction by rememberUpdatedState(fraction)
    val currentOnChange by rememberUpdatedState(onChange)
    var isTouched by remember { mutableStateOf(false) }
    val track by animateDpAsState(if (isTouched) 10.dp else 4.dp, tween(Motion.STATE_MS, easing = Motion.backOut), label = "pencil-track")
    Box(
        modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val from = currentFraction
                    var isArmed = false
                    isTouched = true
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val travelled = down.position.y - change.position.y
                        if (!isArmed) {
                            if (abs(travelled) < viewConfiguration.touchSlop) continue
                            isArmed = true
                        }
                        val next = (from + travelled / size.height).coerceIn(0f, 1f)
                        // A tick every tenth of the track, so dragging it feels stepped.
                        if ((next * 10).toInt() != (currentFraction * 10).toInt()) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        currentOnChange(next)
                        change.consume()
                    } while (event.changes.any { it.pressed })
                    isTouched = false
                }
            }
            .drawBehind {
                val thickest = PENCIL_THICKEST.toPx().coerceAtMost(size.width)
                val radius = (PENCIL_THINNEST.toPx() + (thickest - PENCIL_THINNEST.toPx()) * fraction.coerceIn(0f, 1f)) / 2f
                val trackWidth = track.toPx()
                val left = (size.width - trackWidth) / 2f
                // Thin at the foot, thick at the top.
                val centre = size.height - thickest / 2f - (size.height - thickest) * fraction.coerceIn(0f, 1f)
                drawRoundRect(Palette.borderStrong, Offset(left, 0f), Size(trackWidth, size.height), CornerRadius(trackWidth / 2f))
                drawRoundRect(accent, Offset(left, centre), Size(trackWidth, size.height - centre), CornerRadius(trackWidth / 2f))
                drawCircle(ink, radius, Offset(size.width / 2f, centre))
                drawCircle(Palette.borderControl, radius, Offset(size.width / 2f, centre), style = Stroke(1.dp.toPx()))
            },
    )
}

private fun hit(at: Offset, r: Rect, reach: Float): Handle? {
    val nearLeft = abs(at.x - r.left) < reach
    val nearRight = abs(at.x - r.right) < reach
    val nearTop = abs(at.y - r.top) < reach
    val nearBottom = abs(at.y - r.bottom) < reach
    val alongX = at.x > r.left - reach && at.x < r.right + reach
    val alongY = at.y > r.top - reach && at.y < r.bottom + reach
    return when {
        nearLeft && nearTop -> Handle.TOP_LEFT
        nearRight && nearTop -> Handle.TOP_RIGHT
        nearLeft && nearBottom -> Handle.BOTTOM_LEFT
        nearRight && nearBottom -> Handle.BOTTOM_RIGHT
        nearLeft && alongY -> Handle.LEFT
        nearRight && alongY -> Handle.RIGHT
        nearTop && alongX -> Handle.TOP
        nearBottom && alongX -> Handle.BOTTOM
        r.contains(at) -> Handle.MOVE
        else -> null
    }
}

// One drag step on the rectangle, in pixels. With a ratio locked, the side or corner opposite the one held stays put and the rectangle keeps its shape.
private fun dragged(r: Rect, handle: Handle, amount: Offset, bounds: Size, lock: Float?, minimum: Float): Rect {
    if (handle == Handle.MOVE) {
        val dx = amount.x.coerceIn(-r.left, bounds.width - r.right)
        val dy = amount.y.coerceIn(-r.top, bounds.height - r.bottom)
        return r.translate(dx, dy)
    }
    val movesLeft = handle == Handle.LEFT || handle == Handle.TOP_LEFT || handle == Handle.BOTTOM_LEFT
    val movesRight = handle == Handle.RIGHT || handle == Handle.TOP_RIGHT || handle == Handle.BOTTOM_RIGHT
    val movesTop = handle == Handle.TOP || handle == Handle.TOP_LEFT || handle == Handle.TOP_RIGHT
    val movesBottom = handle == Handle.BOTTOM || handle == Handle.BOTTOM_LEFT || handle == Handle.BOTTOM_RIGHT
    var left = r.left
    var top = r.top
    var right = r.right
    var bottom = r.bottom
    if (movesLeft) left = (left + amount.x).coerceIn(0f, right - minimum)
    if (movesRight) right = (right + amount.x).coerceIn(left + minimum, bounds.width)
    if (movesTop) top = (top + amount.y).coerceIn(0f, bottom - minimum)
    if (movesBottom) bottom = (bottom + amount.y).coerceIn(top + minimum, bounds.height)
    if (lock == null) return Rect(left, top, right, bottom)

    val isCorner = (movesLeft || movesRight) && (movesTop || movesBottom)
    if (isCorner) {
        val anchorX = if (movesLeft) r.right else r.left
        val anchorY = if (movesTop) r.bottom else r.top
        val roomWidth = if (movesLeft) anchorX else bounds.width - anchorX
        val roomHeight = if (movesTop) anchorY else bounds.height - anchorY
        val width = max(right - left, (bottom - top) * lock).coerceAtMost(min(roomWidth, roomHeight * lock)).coerceAtLeast(min(minimum, roomWidth))
        val height = width / lock
        val newLeft = if (movesLeft) anchorX - width else anchorX
        val newTop = if (movesTop) anchorY - height else anchorY
        return Rect(newLeft, newTop, newLeft + width, newTop + height)
    }
    return if (movesLeft || movesRight) {
        val centreY = (r.top + r.bottom) / 2f
        val roomHeight = 2f * min(centreY, bounds.height - centreY)
        val height = ((right - left) / lock).coerceAtMost(roomHeight)
        val width = height * lock
        val newLeft = if (movesLeft) r.right - width else r.left
        Rect(newLeft, centreY - height / 2f, newLeft + width, centreY + height / 2f)
    } else {
        val centreX = (r.left + r.right) / 2f
        val roomWidth = 2f * min(centreX, bounds.width - centreX)
        val width = ((bottom - top) * lock).coerceAtMost(roomWidth)
        val height = width / lock
        val newTop = if (movesTop) r.bottom - height else r.top
        Rect(centreX - width / 2f, newTop, centreX + width / 2f, newTop + height)
    }
}

// Frames of the video along a strip; the two ends of the kept part are dragged, a tap or a drag between them moves the playhead.
@Composable
private fun TrimBar(item: MediaItem, video: VideoState, durationMs: Long, startMs: Long, endMs: Long, onTrim: (Long, Long) -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val density = LocalDensity.current
    val reach = with(density) { HANDLE_REACH.toPx() }
    val start by rememberUpdatedState(startMs)
    val end by rememberUpdatedState(endMs)
    val trim by rememberUpdatedState(onTrim)
    val frames = remember(item.uri, durationMs) {
        (0 until TRIM_FRAMES).map { index ->
            ImageRequest.Builder(context)
                .data(item.uri)
                .videoFrameMillis(durationMs * (2 * index + 1) / (2 * TRIM_FRAMES))
                .decoderFactory(VideoFrameDecoder.Factory())
                .size(256)
                .build()
        }
    }
    // At most one seek a frame, always to where the finger is now: a drag reports more often than the screen draws, and seeks queued behind each other leave the picture trailing the finger.
    val pendingSeek = remember { mutableLongStateOf(-1L) }
    LaunchedEffect(video) {
        while (true) {
            withFrameMillis { }
            val target = pendingSeek.longValue
            if (target >= 0L) {
                pendingSeek.longValue = -1L
                video.player.seekTo(target)
            }
        }
    }
    val show = { ms: Long ->
        video.positionMs = ms
        pendingSeek.longValue = ms
    }
    // A tenth of a second at a time, never past either end of the video nor across the other end of the cut.
    val stepStart = { by: Long -> (start + by).coerceIn(0L, end - MIN_TRIM_MS).also { trim(it, end); show(it) } }
    val stepEnd = { by: Long -> (end + by).coerceIn(start + MIN_TRIM_MS, durationMs).also { trim(start, it); show(it) } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TrimStepper(trimTime(startMs), onEarlier = { stepStart(-TRIM_STEP_MS) }, onLater = { stepStart(TRIM_STEP_MS) })
            BasicText(trimTime(endMs - startMs), style = Type.microLabel.copy(color = accent))
            TrimStepper(trimTime(endMs), onEarlier = { stepEnd(-TRIM_STEP_MS) }, onLater = { stepEnd(TRIM_STEP_MS) })
        }
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(Shapes.cover)
                .background(Palette.sunken)
                .pointerInput(durationMs) {
                    detectTapGestures { at -> show((at.x / size.width * durationMs).toLong().coerceIn(start, end)) }
                }
                .pointerInput(durationMs) {
                    var held = 0
                    detectDragGestures(
                        onDragStart = { at ->
                            val startX = start.toFloat() / durationMs * size.width
                            val endX = end.toFloat() / durationMs * size.width
                            val toStart = abs(at.x - startX)
                            val toEnd = abs(at.x - endX)
                            held = when {
                                min(toStart, toEnd) > reach -> 0
                                toStart <= toEnd -> -1
                                else -> 1
                            }
                            video.isScrubbing = true
                        },
                        onDragEnd = { video.isScrubbing = false },
                        onDragCancel = { video.isScrubbing = false },
                    ) { pointer, _ ->
                        pointer.consume()
                        val ms = (pointer.position.x / size.width * durationMs).toLong().coerceIn(0L, durationMs)
                        val shown = when (held) {
                            -1 -> ms.coerceAtMost(end - MIN_TRIM_MS).coerceAtLeast(0L).also { trim(it, end) }
                            1 -> ms.coerceAtLeast(start + MIN_TRIM_MS).coerceAtMost(durationMs).also { trim(start, it) }
                            else -> ms.coerceIn(start, end)
                        }
                        show(shown)
                    }
                },
        ) {
            Row(Modifier.fillMaxSize()) {
                frames.forEach { request ->
                    AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val startX = startMs.toFloat() / durationMs * size.width
                val endX = endMs.toFloat() / durationMs * size.width
                val veil = Color.Black.copy(alpha = 0.65f)
                drawRect(veil, Offset.Zero, Size(startX, size.height))
                drawRect(veil, Offset(endX, 0f), Size(size.width - endX, size.height))
                val border = 2.5.dp.toPx()
                drawRect(accent, Offset(startX, border / 2f), Size(endX - startX, size.height - border), style = Stroke(border))
                val grip = 6.dp.toPx()
                drawRect(accent, Offset(startX, 0f), Size(grip, size.height))
                drawRect(accent, Offset(endX - grip, 0f), Size(grip, size.height))
                val playX = video.positionMs.toFloat() / durationMs * size.width
                drawLine(Palette.textBright, Offset(playX, 0f), Offset(playX, size.height), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

private fun trimTime(millis: Long): String {
    val tenths = (millis / 100) % 10
    val seconds = millis / 1000
    return "%d:%02d.%d".format(seconds / 60, seconds % 60, tenths)
}

// One end of the cut: its time between an arrow back and an arrow on.
@Composable
private fun TrimStepper(time: String, onEarlier: () -> Unit, onLater: () -> Unit) {
    val accent = LocalAccent.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.pressable(onClick = onEarlier).padding(6.dp)) { BackIcon(accent, size = 16.dp) }
        MicroLabel(time)
        Box(Modifier.pressable(onClick = onLater).padding(6.dp).graphicsLayer { rotationZ = 180f }) { BackIcon(accent, size = 16.dp) }
    }
}
