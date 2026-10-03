package com.vaditim.gallery.ui

import android.view.TextureView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.glass
import androidx.media3.common.MediaItem as PlayerMediaItem

// Holding the timeline and sliding up makes the drag finer. Distances are how far above the point where the finger went down; each step is a slower scrub, down to a tenth, for stepping through split seconds.
private val SCRUB_STEPS = listOf(0f to 1f, 40f to 0.5f, 110f to 0.25f, 190f to 0.1f)
private val JUMP_GRAB_RADIUS = 28.dp

// One playing video: the player plus the numbers the controls draw. Lives only while its page is the current one.
@Stable
class VideoState(val player: ExoPlayer) {
    var isPlaying by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var ratio by mutableStateOf<Float?>(null)
    var isScrubbing by mutableStateOf(false)
    var scrubSpeed by mutableFloatStateOf(1f)
    var isLooping by mutableStateOf(false)
    // Set while a hold on the picture is speeding playback up or slowing it down; null otherwise.
    var holdSpeed by mutableStateOf<Float?>(null)

    fun seekToFraction(fraction: Float) {
        positionMs = (fraction * durationMs).toLong()
        player.seekTo(positionMs)
    }
}

@Composable
fun rememberVideoState(item: MediaItem?): VideoState? {
    if (item == null || !item.isVideo) return null
    val context = LocalContext.current
    val state = remember(item.id) {
        VideoState(
            ExoPlayer.Builder(context).build().apply {
                // Exact seeks rather than the nearest keyframe: the timeline is for landing on a precise frame.
                setSeekParameters(SeekParameters.EXACT)
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                setMediaItem(PlayerMediaItem.fromUri(item.uri))
                prepare()
                playWhenReady = true
            },
        )
    }
    DisposableEffect(state) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.isPlaying = isPlaying
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) state.ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }
        state.player.addListener(listener)
        onDispose {
            state.player.removeListener(listener)
            state.player.release()
        }
    }
    LaunchedEffect(state) {
        while (true) {
            if (!state.isScrubbing) state.positionMs = state.player.currentPosition
            state.durationMs = state.player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            kotlinx.coroutines.delay(30)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { state.player.pause() }
    return state
}

// A TextureView rather than a SurfaceView, so the video takes the rounded frame and the zoom like a photo does.
@Composable
fun VideoSurface(state: VideoState, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context -> TextureView(context) },
        update = { state.player.setVideoTextureView(it) },
        onRelease = { state.player.clearVideoTextureView(it) },
        modifier = modifier,
    )
}

@Composable
fun VideoControls(state: VideoState, isFavorite: Boolean, onFavorite: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .glass(Shapes.panel, Palette.viewerGround)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Timeline(state, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val isFine = state.isScrubbing && state.scrubSpeed < 1f
            MicroLabel(formatTime(state.positionMs, withFraction = isFine))
            MicroLabel(formatTime(state.durationMs, withFraction = false))
        }
        val accent = LocalAccent.current
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                state.isLooping = !state.isLooping
                state.player.repeatMode = if (state.isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            }) { LoopIcon(if (state.isLooping) accent else Palette.textMuted) }
            IconButton(onClick = {
                if (state.isPlaying) {
                    state.player.pause()
                } else {
                    if (state.player.playbackState == Player.STATE_ENDED) state.player.seekTo(0)
                    state.player.play()
                }
            }) { PlayPauseIcon(state.isPlaying, Palette.textBright, size = 28.dp) }
            IconButton(onClick = onFavorite) { HeartIcon(isFavorite, if (isFavorite) accent else Palette.textMuted) }
        }
    }
}

@Composable
private fun Timeline(state: VideoState, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val density = LocalDensity.current
    var widthPixels by remember { mutableFloatStateOf(1f) }
    val fraction = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    val thickness by animateDpAsState(if (state.isScrubbing) 12.dp else 6.dp, tween(Motion.STATE_MS, easing = Motion.powerTwoOut), label = "timeline")

    // A tall touch area around a thin track: the track is easy to see, the hold is easy to land.
    Box(
        modifier
            .height(52.dp)
            .onSizeChanged { widthPixels = it.width.toFloat() }
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    if (state.durationMs <= 0L) return@awaitEachGesture
                    val wasPlaying = state.player.isPlaying
                    state.player.pause()
                    state.isScrubbing = true
                    // Touching away from the handle jumps there; touching the handle grabs it where it is, so a fine adjustment does not start with a jump.
                    var scrubFraction = (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                    if (kotlin.math.abs(down.position.x - scrubFraction * widthPixels) > JUMP_GRAB_RADIUS.toPx()) {
                        scrubFraction = (down.position.x / widthPixels).coerceIn(0f, 1f)
                        state.seekToFraction(scrubFraction)
                    }
                    val startY = down.position.y
                    var lastX = down.position.x
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        val liftDp = with(density) { (startY - change.position.y).coerceAtLeast(0f).toDp().value }
                        state.scrubSpeed = SCRUB_STEPS.last { liftDp >= it.first }.second
                        scrubFraction = (scrubFraction + (change.position.x - lastX) * state.scrubSpeed / widthPixels).coerceIn(0f, 1f)
                        lastX = change.position.x
                        state.seekToFraction(scrubFraction)
                        change.consume()
                    } while (event.changes.any { it.pressed })
                    state.scrubSpeed = 1f
                    state.isScrubbing = false
                    if (wasPlaying) state.player.play()
                }
            }
            .drawBehind {
                val trackHeight = thickness.toPx()
                val top = (size.height - trackHeight) / 2f
                val radius = CornerRadius(trackHeight / 2f)
                drawRoundRect(Color(0x33FFFFFF), Offset(0f, top), Size(size.width, trackHeight), radius)
                drawRoundRect(accent, Offset(0f, top), Size(size.width * fraction, trackHeight), radius)
                drawCircle(Color.White, radius = trackHeight / 2f + 4.dp.toPx(), center = Offset(size.width * fraction, size.height / 2f))
            },
    )
}

private fun formatTime(millis: Long, withFraction: Boolean): String {
    val totalSeconds = millis / 1000
    val base = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    return if (withFraction) "$base.%02d".format((millis % 1000) / 10) else base
}
