package com.vaditim.gallery.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.exoplayer.SeekParameters
import com.vaditim.gallery.components.AlbumPickerSheet
import com.vaditim.gallery.components.CloseIcon
import com.vaditim.gallery.components.ConfirmPill
import com.vaditim.gallery.components.CropIcon
import com.vaditim.gallery.components.FullPhoto
import com.vaditim.gallery.components.GroupPickerSheet
import com.vaditim.gallery.components.HeartIcon
import com.vaditim.gallery.components.HighRangeWindow
import com.vaditim.gallery.components.IconButton
import com.vaditim.gallery.components.ImageIcon
import com.vaditim.gallery.components.InfoIcon
import com.vaditim.gallery.components.LockIcon
import com.vaditim.gallery.components.MediaActions
import com.vaditim.gallery.components.MoreIcon
import com.vaditim.gallery.components.MotionIcon
import com.vaditim.gallery.components.MoveIcon
import com.vaditim.gallery.components.NameSheet
import com.vaditim.gallery.components.OverlaySheet
import com.vaditim.gallery.components.RestoreIcon
import com.vaditim.gallery.components.ShareIcon
import com.vaditim.gallery.components.SheetRow
import com.vaditim.gallery.components.SplitSheetRow
import com.vaditim.gallery.components.TileImages
import com.vaditim.gallery.components.TrashIcon
import com.vaditim.gallery.components.calendarWeekLabel
import com.vaditim.gallery.components.dayOf
import com.vaditim.gallery.components.pendingMark
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.media.MotionPhoto
import com.vaditim.gallery.media.Place
import com.vaditim.gallery.media.newAlbumPath
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.LocalHazeState
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.glass
import com.vaditim.gallery.vault.PrivateGroup
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PAGE_GAP = 18.dp
private const val MAX_ZOOM = 5f
// A pull of this share of the screen height has shrunk the viewer all the way down to its tile.
private const val PULL_RANGE = 0.4f
// The share of the full pull by which the buttons have left completely.
private const val CHROME_PULL_SHARE = 0.35f
// A swipe up of this share of the screen height has raised the details all the way.
private const val LIFT_RANGE = 0.3f
// How many of its own heights a button travels on its way out.
private const val CHROME_TRAVEL = 1.6f
private const val HOLD_SPEED = 1.5f
private const val REVERSE_STEP_MS = 120L
private const val MIN_HOLD_SPEED = 0.25f
private const val MAX_HOLD_SPEED = 4f
// Sliding this far while holding changes the speed by 1x.
private val HOLD_SLIDE_DISTANCE = 150.dp
private const val DOUBLE_TAP_SCALE = 2.5f
private val STAMP_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.ENGLISH)

private enum class Overlay { NONE, MORE, MOVE, NEW_ALBUM, HIDE, NEW_GROUP, CONFIRM_HIDE, DETAILS }

// Emitted straight into the screen's own box, so its photo can be drawn behind the navigation while the buttons and menus stay in front of it.
@Composable
fun BoxScope.ViewerScreen(
    items: List<MediaItem>,
    startIndex: Int,
    albums: List<Album>,
    privateGroups: List<PrivateGroup>,
    isPrivate: Boolean,
    isTrash: Boolean = false,
    actions: MediaActions,
    onClose: () -> Unit,
    onCurrentChanged: (Long) -> Unit = {},
    onPhotoRatio: (Long, Float) -> Unit = { _, _ -> },
    onPull: (Float) -> Unit = {},
    places: Map<Long, Place> = emptyMap(),
    isChromeAllowed: Boolean = true,
    photoModifier: Modifier = Modifier,
    // While it grows out of or shrinks back into its tile, the photo passes behind the navigation; open, it covers it.
    isBehindNavigation: Boolean = false,
    onSetCover: ((MediaItem) -> Unit)? = null,
) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    HighRangeWindow()
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    var isChromeVisible by remember { mutableStateOf(true) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    // A delete waiting on Confirm above the buttons, as in the grids.
    var pendingDelete by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(overlay) { if (overlay != Overlay.NONE) pendingDelete = null }
    // The group a photo is about to go into, held while the confirmation is open.
    var pendingGroup by remember { mutableStateOf<String?>(null) }
    var cropping by remember { mutableStateOf<MediaItem?>(null) }
    // How far a swipe down has gone, 0 to 1: the buttons slide out with it and come back as it is let go.
    var pull by remember { mutableFloatStateOf(0f) }
    // How far a swipe up has raised the details, 0 to 1: the sheet rises with the finger rather than appearing on release.
    var lift by remember { mutableFloatStateOf(0f) }
    val isChromeShown = isChromeVisible && isChromeAllowed
    val current = items[pagerState.currentPage.coerceIn(0, items.lastIndex)]
    val video = rememberVideoState(current)
    val context = LocalContext.current
    val isCurrentMotion by produceState(MotionPhoto.knownFor(current) == true, current.id) { value = MotionPhoto.isMotion(context, current) }
    LaunchedEffect(current.id) {
        pendingDelete = null
        onCurrentChanged(current.id)
    }

    BackHandler { if (overlay != Overlay.NONE) overlay = Overlay.NONE else onClose() }

    val hazeState = rememberHazeState()
    CompositionLocalProvider(LocalHazeState provides hazeState) {
        run {
            // Only the photos follow the finger and shrink into the grid; the buttons below are outside this layer and leave on their own.
            Box(photoModifier.zIndex(if (isBehindNavigation) -1f else 0f).fillMaxSize().background(Palette.viewerGround)) {
            HorizontalPager(
                state = pagerState,
                key = { items[it].id },
                beyondViewportPageCount = 1,
                pageSpacing = PAGE_GAP,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            ) { page ->
                ViewerPage(
                    items[page],
                    video = if (page == pagerState.currentPage) video else null,
                    onTap = { isChromeVisible = !isChromeVisible },
                    onSwipeDown = onClose,
                    onSwipeUp = { overlay = Overlay.DETAILS },
                    onPull = { fraction ->
                        pull = fraction
                        onPull(fraction)
                    },
                    onLift = { lift = it },
                    onRatio = { onPhotoRatio(items[page].id, it) },
                )
            }
            }

            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                // No back button: the system back gesture or a swipe down closes the viewer.
                horizontalArrangement = Arrangement.End,
            ) {
                // The date stays through a swipe up: the details rise below it and it is still there once they are open.
                ChromePiece(isChromeShown, isFromTop = true, order = 0, pull = { pull }) {
                    Row(
                        Modifier.glass(Shapes.capsule, Palette.viewerGround).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isCurrentMotion) MotionIcon(Palette.textBright, size = 14.dp)
                        MicroLabel(formatStamp(current))
                    }
                }
            }

                Column(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                ChromePiece(isChromeShown, isFromTop = false, order = 2, pull = { maxOf(pull, lift) }) { ConfirmPill(pendingDelete, onDone = { pendingDelete = null }) }
                if (video != null) ChromePiece(isChromeShown, isFromTop = false, order = 0, pull = { maxOf(pull, lift) }) { VideoControls(video, Modifier.padding(horizontal = 16.dp)) }
                ChromePiece(isChromeShown, isFromTop = false, order = 1, pull = { maxOf(pull, lift) }) {
                Row(
                    Modifier
                        .glass(Shapes.capsule, Palette.viewerGround)
                        .padding(5.dp),
                ) {
                    IconButton(onClick = { actions.share(listOf(current)) }) { ShareIcon(Palette.textBody) }
                    if (isTrash) {
                        IconButton(onClick = { actions.restore(listOf(current)) }) { RestoreIcon(LocalAccent.current) }
                        IconButton(
                            onClick = { pendingDelete = { actions.deleteForever(listOf(current)) } },
                            modifier = Modifier.pendingMark(pendingDelete != null),
                        ) { TrashIcon(Palette.danger) }
                    } else {
                    IconButton(onClick = { actions.toggleFavorite(current) }) { HeartIcon(current.isFavorite, if (current.isFavorite) Palette.favorite else Palette.textBody) }
                    IconButton(onClick = {
                        video?.player?.pause()
                        cropping = current
                    }) { CropIcon(Palette.textBody) }
                    if (isPrivate) {
                        IconButton(
                            onClick = { pendingDelete = { actions.deletePrivate(listOf(current)) } },
                            modifier = Modifier.pendingMark(pendingDelete != null),
                        ) { TrashIcon(Palette.danger) }
                    } else {
                        IconButton(
                            onClick = { pendingDelete = { actions.trash(listOf(current)) } },
                            modifier = Modifier.pendingMark(pendingDelete != null),
                        ) { TrashIcon(Palette.danger) }
                    }
                    IconButton(onClick = { overlay = Overlay.MORE }) { MoreIcon(Palette.textBody) }
                    }
                }
                }
                }

            OverlaySheet(visible = overlay == Overlay.MORE, label = "MORE", ground = Palette.viewerGround, onDismiss = { overlay = Overlay.NONE }) {
                if (isPrivate) {
                    SplitSheetRow("Move to group", icon = { MoveIcon(it) }, onClick = { overlay = Overlay.HIDE }, sideIcon = { LockIcon(it, isOpen = true) }, onSide = { overlay = Overlay.MOVE })
                } else {
                    SplitSheetRow("Move to album", icon = { MoveIcon(it) }, onClick = { overlay = Overlay.MOVE }, sideIcon = { LockIcon(it) }, onSide = { overlay = Overlay.HIDE })
                }
                if (video != null) {
                    SheetRow("Save frame", icon = { ImageIcon(it) }) {
                        overlay = Overlay.NONE
                        video.player.pause()
                        actions.saveFrame(current, video.player.currentPosition)
                    }
                }
                if (onSetCover != null) {
                    SheetRow("Set as cover", icon = { ImageIcon(it) }) {
                        overlay = Overlay.NONE
                        onSetCover(current)
                    }
                }
                SheetRow("Details", icon = { InfoIcon(it) }) { overlay = Overlay.DETAILS }
            }

            AlbumPickerSheet(
                visible = overlay == Overlay.MOVE,
                label = if (isPrivate) "MOVE OUT TO" else "MOVE TO",
                albums = albums,
                excludedAlbumId = if (isPrivate) null else current.bucketId,
                ground = Palette.viewerGround,
                onPick = { album ->
                    overlay = Overlay.NONE
                    if (isPrivate) actions.unhide(listOf(current), album) else actions.move(listOf(current), album)
                },
                onNewAlbum = { overlay = Overlay.NEW_ALBUM },
                onDismiss = { overlay = Overlay.NONE },
            )

            if (overlay == Overlay.NEW_ALBUM) {
                NameSheet(
                    label = "NEW ALBUM",
                    action = "MOVE HERE",
                    ground = Palette.viewerGround,
                    onConfirm = { name ->
                        overlay = Overlay.NONE
                        if (isPrivate) actions.unhide(listOf(current), newAlbumPath(name), name) else actions.move(listOf(current), newAlbumPath(name), name)
                    },
                    onDismiss = { overlay = Overlay.NONE },
                )
            }

            GroupPickerSheet(
                visible = overlay == Overlay.HIDE,
                label = if (isPrivate) "MOVE TO GROUP" else "MOVE TO PRIVATE",
                groups = privateGroups,
                excludedGroupName = if (isPrivate) current.bucketName else null,
                ground = Palette.viewerGround,
                onPick = { name ->
                    if (isPrivate) {
                        overlay = Overlay.NONE
                        actions.moveToGroup(listOf(current), name)
                    } else {
                        pendingGroup = name
                        overlay = Overlay.CONFIRM_HIDE
                    }
                },
                onNewGroup = { overlay = Overlay.NEW_GROUP },
                onDismiss = { overlay = Overlay.NONE },
            )

            if (overlay == Overlay.NEW_GROUP) {
                NameSheet(
                    label = "NEW PRIVATE GROUP",
                    action = "MOVE HERE",
                    ground = Palette.viewerGround,
                    onConfirm = { name ->
                        if (isPrivate) {
                            overlay = Overlay.NONE
                            actions.moveToGroup(listOf(current), name)
                        } else {
                            pendingGroup = name
                            overlay = Overlay.CONFIRM_HIDE
                        }
                    },
                    onDismiss = { overlay = Overlay.NONE },
                )
            }

            OverlaySheet(visible = overlay == Overlay.CONFIRM_HIDE, label = "PRIVATE · ${pendingGroup?.uppercase().orEmpty()}", ground = Palette.viewerGround, onDismiss = { overlay = Overlay.NONE }) {
                SheetRow("Move to Private", color = LocalAccent.current, icon = { LockIcon(it) }) {
                    pendingGroup?.let { actions.hide(listOf(current), it) }
                    pendingGroup = null
                    overlay = Overlay.NONE
                }
                SheetRow("Cancel", color = Palette.textMuted, icon = { CloseIcon(it) }) {
                    pendingGroup = null
                    overlay = Overlay.NONE
                }
            }

            OverlaySheet(visible = overlay == Overlay.DETAILS, label = "DETAILS", ground = Palette.viewerGround, onDismiss = { overlay = Overlay.NONE }, reveal = { lift }) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(current.name, style = Type.caption.copy(color = Palette.textBright))
                    BasicText(formatStamp(current), style = Type.value)
                    places[current.id]?.let { place ->
                        place.label?.let { BasicText(it, style = Type.caption.copy(color = Palette.textBright)) }
                        BasicText("%.5f, %.5f".format(java.util.Locale.ROOT, place.latitude, place.longitude), style = Type.value)
                    }
                    if (current.width > 0) BasicText("${current.width} × ${current.height}", style = Type.value)
                    BasicText(formatSize(current.sizeBytes), style = Type.value)
                    BasicText(if (isPrivate) "Private · ${current.bucketName}" else current.relativePath, style = Type.value)
                }
            }

            cropping?.let { CropScreen(it, actions, onClose = { cropping = null }) }
        }
    }
}

// Pinch or double-tap zooms a photo. While it is zoomed the page keeps every drag for panning, so the pager only swipes at normal size.
@Composable
private fun ViewerPage(item: MediaItem, video: VideoState?, onTap: () -> Unit, onSwipeDown: () -> Unit, onSwipeUp: () -> Unit, onPull: (Float) -> Unit, onLift: (Float) -> Unit, onRatio: (Float) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    // The picture's own proportions, so the rounded frame hugs the photo rather than the screen; read off the decoded image because the stored width and height ignore rotation.
    var ratio by remember(item.id) { mutableStateOf(if (item.width > 0 && item.height > 0) item.width.toFloat() / item.height else null) }
    // A motion photo's clip, playing while the photo is held.
    var motion by remember(item.id) { mutableStateOf<VideoState?>(null) }
    DisposableEffect(item.id) { onDispose { motion?.player?.release() } }

    LaunchedEffect(ratio) { ratio?.let(onRatio) }

    fun clamp(candidate: Offset, forScale: Float): Offset {
        val limitX = size.width * (forScale - 1f) / 2f
        val limitY = size.height * (forScale - 1f) / 2f
        return Offset(candidate.x.coerceIn(-limitX, limitX), candidate.y.coerceIn(-limitY, limitY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapAt ->
                        val fromScale = scale
                        val fromOffset = offset
                        val toScale = if (fromScale > 1.05f) 1f else DOUBLE_TAP_SCALE
                        val centre = Offset(size.width / 2f, size.height / 2f)
                        scope.launch {
                            animate(0f, 1f, animationSpec = tween(Motion.STATE_MS, easing = Motion.powerTwoOut)) { progress, _ ->
                                val nextScale = fromScale + (toScale - fromScale) * progress
                                // Zooming in lands on the tapped point; zooming out returns to centre.
                                val target = if (toScale == 1f) Offset.Zero else (tapAt - centre) * (1f - toScale)
                                scale = nextScale
                                offset = clamp(fromOffset + (target - fromOffset) * progress, nextScale)
                            }
                        }
                    },
                )
            }
            // Holding on a video: the right half plays it forward at 1.5x, the left half plays it backwards at 1.5x, for as long as the finger stays down. Sliding towards the middle speeds it up; sliding towards the edge slows it down.
            .then(
                if (video == null) Modifier else Modifier.pointerInput(video) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (scale > 1.01f) return@awaitEachGesture
                        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val isReverse = down.position.x < size.width / 2f
                        val wasPlaying = video.player.isPlaying
                        var speed = HOLD_SPEED
                        video.holdSpeed = speed
                        video.isHoldReverse = isReverse
                        var reverseJob: Job? = null
                        if (isReverse) {
                            // ExoPlayer cannot play backwards, so rewinding is a run of seeks to the keyframe at or before a position this loop keeps itself — reading the player's own position back would snap to the keyframe it landed on and stutter.
                            video.player.pause()
                            video.player.setSeekParameters(SeekParameters.PREVIOUS_SYNC)
                            reverseJob = scope.launch {
                                var position = video.player.currentPosition
                                var lastTick = System.nanoTime()
                                while (position > 0L) {
                                    delay(REVERSE_STEP_MS)
                                    val now = System.nanoTime()
                                    position = (position - ((now - lastTick) / 1_000_000L * (video.holdSpeed ?: HOLD_SPEED)).toLong()).coerceAtLeast(0L)
                                    lastTick = now
                                    video.player.seekTo(position)
                                    video.positionMs = position
                                }
                            }
                        } else {
                            if (!wasPlaying) video.player.play()
                            video.player.setPlaybackSpeed(speed)
                        }
                        var lastX = held.position.x
                        held.consume()
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            val towardMiddle = if (isReverse) change.position.x - lastX else lastX - change.position.x
                            speed = (speed + towardMiddle / HOLD_SLIDE_DISTANCE.toPx()).coerceIn(MIN_HOLD_SPEED, MAX_HOLD_SPEED)
                            lastX = change.position.x
                            video.holdSpeed = speed
                            if (!isReverse) video.player.setPlaybackSpeed(speed)
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        reverseJob?.cancel()
                        video.player.setSeekParameters(SeekParameters.EXACT)
                        video.player.setPlaybackSpeed(1f)
                        video.holdSpeed = null
                        // Rewinding leaves the video paused; a video that was playing carries on from where the rewind stopped.
                        if (isReverse && wasPlaying) video.player.play()
                        if (!isReverse && !wasPlaying) video.player.pause()
                    }
                },
            )
            // Holding a motion photo plays its clip on a loop until the finger lifts.
            .then(
                if (item.isVideo) Modifier else Modifier.pointerInput(item.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (scale > 1.01f) return@awaitEachGesture
                        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        val start = scope.launch {
                            if (!MotionPhoto.isMotion(context, item)) return@launch
                            val clip = MotionPhoto.findClip(context, item) ?: return@launch
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            motion = VideoState(motionPlayer(context, item, clip))
                        }
                        held.consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        start.cancel()
                        val playing = motion ?: return@awaitEachGesture
                        motion = null
                        // Released a moment later, once the surface showing it has left the screen.
                        scope.launch {
                            delay(Motion.STATE_MS.toLong())
                            playing.player.release()
                        }
                    }
                },
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // At normal size a mostly-vertical drag is a swipe: down closes, up shows the details. It is only claimed once it is clearly vertical, so horizontal swipes still reach the pager.
                    var total = Offset.Zero
                    var isVerticalSwipe = false
                    var isDirectionDecided = false
                    // Zoomed in, a finger only pans once it has really moved; the small wobble of a double tap is left to the tap detector, so it can zoom back out.
                    var panTotal = Offset.Zero
                    var isPanning = false
                    do {
                        val event = awaitPointerEvent()
                        val isPinching = event.changes.count { it.pressed } >= 2
                        if (isPinching) isDirectionDecided = true
                        if (!isPinching && scale <= 1.01f && !isDirectionDecided || isVerticalSwipe) {
                            val change = event.changes.first()
                            total += change.position - change.previousPosition
                            if (!isDirectionDecided && total.getDistance() > viewConfiguration.touchSlop) {
                                isDirectionDecided = true
                                isVerticalSwipe = kotlin.math.abs(total.y) > kotlin.math.abs(total.x) * 1.5f
                            }
                            if (isVerticalSwipe) {
                                swipeOffset += change.position.y - change.previousPosition.y
                                // Both directions are reported as the finger goes: down shrinks the viewer towards its tile, up raises the details.
                                onPull((swipeOffset.coerceAtLeast(0f) / (size.height * PULL_RANGE)).coerceIn(0f, 1f))
                                onLift((-swipeOffset / (size.height * LIFT_RANGE)).coerceIn(0f, 1f))
                                change.consume()
                            }
                        } else if (isPinching || scale > 1.01f) {
                            if (!isPinching && !isPanning) {
                                val change = event.changes.first()
                                panTotal += change.position - change.previousPosition
                                if (panTotal.getDistance() <= viewConfiguration.touchSlop) continue
                                isPanning = true
                            }
                            val zoom = if (isPinching) event.calculateZoom() else 1f
                            val nextScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val focus = if (centroid.isSpecified) centroid - Offset(size.width / 2f, size.height / 2f) else Offset.Zero
                            val moved = if (nextScale <= 1.01f) Offset.Zero else clamp(focus - (focus - offset) * (nextScale / scale) + pan, nextScale)
                            // A non-finite offset would blank the photo, so a bad frame is dropped rather than applied.
                            offset = if (moved.isFinite) moved else offset
                            scale = nextScale
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (isVerticalSwipe) {
                        val distance = 100.dp.toPx()
                        val released = swipeOffset
                        if (released > distance) {
                            onSwipeDown()
                        } else if (released < -distance) {
                            // The details finish rising from where the finger left them, and only then count as open.
                            scope.launch {
                                val lifted = (-released / (size.height * LIFT_RANGE)).coerceIn(0f, 1f)
                                animate(lifted, 1f, animationSpec = tween(Motion.STATE_MS, easing = Motion.powerTwoOut)) { value, _ -> onLift(value) }
                                onSwipeUp()
                                swipeOffset = 0f
                                onLift(0f)
                            }
                        } else {
                            scope.launch {
                                animate(released, 0f, animationSpec = tween(Motion.STATE_MS, easing = Motion.powerTwoOut)) { value, _ ->
                                    swipeOffset = value
                                    onPull((value.coerceAtLeast(0f) / (size.height * PULL_RANGE)).coerceIn(0f, 1f))
                                    onLift((-value / (size.height * LIFT_RANGE)).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .then((video?.ratio ?: ratio)?.let { Modifier.aspectRatio(it) } ?: Modifier.fillMaxSize())
                // One layer does the zoom and the rounding, so the clip scales with the photo instead of living in a layer of its own.
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    shape = Shapes.viewerPhoto
                    clip = true
                },
        ) {
            FullPhoto(item.uri, item.name, isZoomed = scale > 1.01f, placeholderKey = TileImages.of(item.id), onRatio = { ratio = it }, modifier = Modifier.fillMaxSize())
            // The still frame shows until the video has its first picture, then the video draws over it.
            if (video != null) VideoSurface(video, Modifier.fillMaxSize())
            motion?.let { VideoSurface(it, Modifier.fillMaxSize()) }
        }
    }
}

// One of the viewer's floating buttons: each slides off its own edge and fades, a step after the one before, instead of the whole set going as one sheet.
@Composable
private fun ChromePiece(isShown: Boolean, isFromTop: Boolean, order: Int, pull: () -> Float = { 0f }, content: @Composable () -> Unit) {
    val delay = order * Motion.CHROME_STAGGER_MS
    AnimatedVisibility(
        // Follows the pull frame by frame, so the buttons move exactly as far as the finger has.
        modifier = Modifier.graphicsLayer {
            val out = (pull() / CHROME_PULL_SHARE).coerceIn(0f, 1f)
            translationY = (if (isFromTop) -1f else 1f) * size.height * CHROME_TRAVEL * out
            alpha = 1f - out
        },
        visible = isShown,
        enter = fadeIn(tween(Motion.OVERLAY_ENTER_MS, delay, Motion.powerTwoOut)) +
            slideInVertically(tween(Motion.OVERLAY_ENTER_MS, delay, Motion.backOut)) { if (isFromTop) -it else it },
        exit = fadeOut(tween(Motion.OVERLAY_LEAVE_MS, delay, Motion.powerTwoIn)) +
            slideOutVertically(tween(Motion.OVERLAY_LEAVE_MS, delay, Motion.powerTwoIn)) { if (isFromTop) -it else it },
    ) { content() }
}

private fun formatStamp(item: MediaItem): String =
    STAMP_FORMAT.format(Instant.ofEpochMilli(item.timestampMillis).atZone(ZoneId.systemDefault())).uppercase(Locale.ENGLISH) + " · " + calendarWeekLabel(dayOf(item.timestampMillis))

private fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000) "%.1f MB".format(bytes / 1_000_000.0) else "%d KB".format(bytes / 1000)
