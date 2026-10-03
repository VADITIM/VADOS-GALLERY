package com.vaditim.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.LocalHazeState
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.glass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val STAMP_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.ENGLISH)

private enum class Overlay { NONE, MORE, MOVE, DETAILS }

@Composable
fun ViewerScreen(
    items: List<MediaItem>,
    startIndex: Int,
    albums: List<Album>,
    actions: MediaActions,
    onClose: () -> Unit,
) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    var isChromeVisible by remember { mutableStateOf(true) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    val current = items[pagerState.currentPage.coerceIn(0, items.lastIndex)]

    BackHandler { if (overlay != Overlay.NONE) overlay = Overlay.NONE else onClose() }

    val hazeState = rememberHazeState()
    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize().background(Palette.viewerGround)) {
            HorizontalPager(
                state = pagerState,
                key = { items[it].id },
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            ) { page ->
                ViewerPage(items[page], onTap = { isChromeVisible = !isChromeVisible })
            }

            AnimatedVisibility(
                visible = isChromeVisible,
                enter = fadeIn(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.powerTwoOut)),
                exit = fadeOut(tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn)),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(Modifier.pressable(onClick = onClose).glass(Shapes.capsule, Palette.viewerGround).padding(horizontal = 18.dp, vertical = 11.dp)) {
                        BasicText("‹", style = Type.cardTitle.copy(color = LocalAccent.current))
                    }
                    Box(Modifier.glass(Shapes.capsule, Palette.viewerGround).padding(horizontal = 14.dp, vertical = 10.dp)) {
                        MicroLabel(formatStamp(current))
                    }
                }
            }

            AnimatedVisibility(
                visible = isChromeVisible,
                enter = fadeIn(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.powerTwoOut)),
                exit = fadeOut(tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Row(
                    Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 14.dp)
                        .glass(Shapes.capsule, Palette.viewerGround)
                        .padding(5.dp),
                ) {
                    ActionButton("SHARE") { actions.share(current) }
                    ActionButton(if (current.isFavorite) "FAVORITED" else "FAVORITE", isLit = current.isFavorite) { actions.toggleFavorite(current) }
                    ActionButton("DELETE", color = Palette.danger) { actions.trash(current) }
                    ActionButton("•••") { overlay = Overlay.MORE }
                }
            }

            OverlaySheet(visible = overlay == Overlay.MORE, label = "MORE", onDismiss = { overlay = Overlay.NONE }) {
                SheetRow("Move to album") { overlay = Overlay.MOVE }
                SheetRow("Edit") { overlay = Overlay.NONE; actions.edit(current) }
                SheetRow("Details") { overlay = Overlay.DETAILS }
            }

            OverlaySheet(visible = overlay == Overlay.MOVE, label = "MOVE TO", onDismiss = { overlay = Overlay.NONE }) {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(albums.filter { it.id != current.bucketId }, key = { it.id }) { album ->
                        SheetRow(album.name, trailing = album.items.size.toString()) {
                            overlay = Overlay.NONE
                            actions.move(current, album)
                        }
                    }
                }
            }

            OverlaySheet(visible = overlay == Overlay.DETAILS, label = "DETAILS", onDismiss = { overlay = Overlay.NONE }) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(current.name, style = Type.caption.copy(color = Palette.textBright))
                    BasicText(formatStamp(current), style = Type.value)
                    BasicText("${current.width} × ${current.height}", style = Type.value)
                    BasicText(formatSize(current.sizeBytes), style = Type.value)
                    BasicText(current.relativePath, style = Type.value)
                }
            }
        }
    }
}

@Composable
private fun ViewerPage(item: MediaItem, onTap: () -> Unit) {
    val context = LocalContext.current
    val request = remember(item.uri) { ImageRequest.Builder(context).data(item.uri).build() }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(model = request, contentDescription = item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        if (item.isVideo) MicroLabel("VIDEO · ${formatDuration(item.durationMillis)}")
    }
}

@Composable
private fun ActionButton(text: String, isLit: Boolean = false, color: Color? = null, onClick: () -> Unit) {
    val ink = color ?: if (isLit) LocalAccent.current else Palette.textBody
    Box(Modifier.pressable(onClick = onClick).clip(Shapes.capsule).padding(horizontal = 14.dp, vertical = 13.dp)) {
        BasicText(text, style = Type.action.copy(color = ink))
    }
}

// A menu over a photo: a pane of glass that arrives from just below on the overshoot and leaves straight down and quicker (dna/05-motion.md §4). Tapping anywhere outside it closes it, so it never traps the photo.
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun OverlaySheet(visible: Boolean, label: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.powerTwoOut)),
        exit = fadeOut(tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x4D000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .animateEnterExit(
                        enter = slideInVertically(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.backOut)) { it / 6 } +
                            scaleIn(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.backOut), initialScale = 0.96f),
                        exit = slideOutVertically(tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn)) { it / 8 } +
                            scaleOut(tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn), targetScale = 0.98f),
                    )
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .fillMaxWidth()
                    .glass(Shapes.sheet, Palette.viewerGround)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .padding(top = 18.dp, bottom = 10.dp),
            ) {
                MicroLabel(label, Modifier.padding(start = 20.dp, bottom = 6.dp))
                content()
            }
        }
    }
}

@Composable
private fun SheetRow(text: String, trailing: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick, pressedScale = 0.98f).padding(horizontal = 20.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text, style = Type.cardTitle, maxLines = 1)
        if (trailing != null) BasicText(trailing, style = Type.value)
    }
}

private fun formatStamp(item: MediaItem): String =
    STAMP_FORMAT.format(Instant.ofEpochMilli(item.timestampMillis).atZone(ZoneId.systemDefault())).uppercase(Locale.ENGLISH)

private fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000) "%.1f MB".format(bytes / 1_000_000.0) else "%d KB".format(bytes / 1000)
