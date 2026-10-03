package com.vaditim.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.vaditim.gallery.media.newAlbumPath
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.LocalHazeState
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.glass
import com.vaditim.gallery.vas.pressable
import com.vaditim.gallery.vault.PrivateGroup
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val STAMP_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.ENGLISH)

private enum class Overlay { NONE, MORE, MOVE, NEW_ALBUM, HIDE, NEW_GROUP, DETAILS }

@Composable
fun ViewerScreen(
    items: List<MediaItem>,
    startIndex: Int,
    albums: List<Album>,
    privateGroups: List<PrivateGroup>,
    isPrivate: Boolean,
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
    var isDeleteArmed by remember { mutableStateOf(false) }
    val current = items[pagerState.currentPage.coerceIn(0, items.lastIndex)]
    LaunchedEffect(current.id) { isDeleteArmed = false }

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
                    ActionButton("SHARE") { actions.share(listOf(current)) }
                    ActionButton(if (current.isFavorite) "FAVORITED" else "FAVORITE", isLit = current.isFavorite) { actions.toggleFavorite(current) }
                    if (isPrivate) {
                        // Private photos are outside the system trash, so a delete here is final and takes a second tap to mean it.
                        ActionButton(if (isDeleteArmed) "FOREVER?" else "DELETE", color = Palette.danger) {
                            if (isDeleteArmed) actions.deletePrivate(listOf(current)) else isDeleteArmed = true
                        }
                    } else {
                        ActionButton("DELETE", color = Palette.danger) { actions.trash(listOf(current)) }
                    }
                    ActionButton("•••") { overlay = Overlay.MORE }
                }
            }

            OverlaySheet(visible = overlay == Overlay.MORE, label = "MORE", ground = Palette.viewerGround, onDismiss = { overlay = Overlay.NONE }) {
                if (isPrivate) {
                    SheetRow("Move to group") { overlay = Overlay.HIDE }
                    SheetRow("Move out to album") { overlay = Overlay.MOVE }
                } else {
                    SheetRow("Move to album") { overlay = Overlay.MOVE }
                    SheetRow("Move to private") { overlay = Overlay.HIDE }
                    SheetRow("Edit") { overlay = Overlay.NONE; actions.edit(current) }
                }
                SheetRow("Details") { overlay = Overlay.DETAILS }
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
                    overlay = Overlay.NONE
                    if (isPrivate) actions.moveToGroup(listOf(current), name) else actions.hide(listOf(current), name)
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
                        overlay = Overlay.NONE
                        if (isPrivate) actions.moveToGroup(listOf(current), name) else actions.hide(listOf(current), name)
                    },
                    onDismiss = { overlay = Overlay.NONE },
                )
            }

            OverlaySheet(visible = overlay == Overlay.DETAILS, label = "DETAILS", ground = Palette.viewerGround, onDismiss = { overlay = Overlay.NONE }) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(current.name, style = Type.caption.copy(color = Palette.textBright))
                    BasicText(formatStamp(current), style = Type.value)
                    if (current.width > 0) BasicText("${current.width} × ${current.height}", style = Type.value)
                    BasicText(formatSize(current.sizeBytes), style = Type.value)
                    BasicText(if (isPrivate) "Private · ${current.bucketName}" else current.relativePath, style = Type.value)
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
        if (item.isVideo) MicroLabel(if (item.durationMillis > 0) "VIDEO · ${formatDuration(item.durationMillis)}" else "VIDEO")
    }
}

@Composable
fun ActionButton(text: String, isLit: Boolean = false, color: Color? = null, onClick: () -> Unit) {
    val ink = color ?: if (isLit) LocalAccent.current else Palette.textBody
    Box(Modifier.pressable(onClick = onClick).clip(Shapes.capsule).padding(horizontal = 14.dp, vertical = 13.dp)) {
        BasicText(text, style = Type.action.copy(color = ink))
    }
}

private fun formatStamp(item: MediaItem): String =
    STAMP_FORMAT.format(Instant.ofEpochMilli(item.timestampMillis).atZone(ZoneId.systemDefault())).uppercase(Locale.ENGLISH)

private fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000) "%.1f MB".format(bytes / 1_000_000.0) else "%d KB".format(bytes / 1000)
