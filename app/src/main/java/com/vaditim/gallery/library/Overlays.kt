package com.vaditim.gallery.library

import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.vaditim.gallery.components.fitInside
import com.vaditim.gallery.components.revealItem
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.picker.PickerScreen
import com.vaditim.gallery.review.ReviewScreen
import com.vaditim.gallery.settings.AlbumArrangement
import com.vaditim.gallery.settings.Settings
import com.vaditim.gallery.settings.SettingsView
import com.vaditim.gallery.viewer.ViewerScreen
import com.vaditim.gallery.vas.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// The photo picker, over everything but the viewer.
@Composable
internal fun PickerOverlay(controller: LibraryController, content: LibraryContent) {
    val target = controller.navigation.picker ?: return
    val actions = controller.actions
    val stacks = AlbumArrangement.albumStacks.all
    val favoriteAlbums = AlbumArrangement.favoriteAlbums.all
    // Photos already in this album, or in any album sorted into a group (any Favorites album for a Favorites album), by the album's name; they are still offered, marked, and adding them asks first.
    val addedTo: Map<Long, String> = remember(target, content.albums, stacks, favoriteAlbums) {
        when (target) {
            is PickerTarget.IntoAlbum -> {
                val groupedPaths = if (Settings.groupedAlbumsIn(SettingsView.ALBUMS)) stacks.flatMap { it.paths }.toSet() else emptySet()
                content.albums.filter { it.relativePath == target.relativePath || it.relativePath in groupedPaths }.flatMap { album -> album.items.map { it.id to album.name } }.toMap()
            }
            is PickerTarget.IntoFavoriteAlbum -> favoriteAlbums.flatMap { album -> album.ids.map { it to album.name } }.toMap()
            is PickerTarget.IntoGroup -> emptyMap()
        }
    }
    PickerScreen(
        title = target.title,
        // A Favorites album gathers favourites, so only they are offered.
        items = if (target is PickerTarget.IntoFavoriteAlbum) content.favorites else content.recent,
        addedTo = { addedTo[it.id] },
        action = "Add",
        onDone = { picked ->
            when (target) {
                is PickerTarget.IntoAlbum -> actions.move(picked, target.relativePath, target.name)
                is PickerTarget.IntoGroup -> actions.hide(picked, target.name)
                is PickerTarget.IntoFavoriteAlbum -> AlbumArrangement.favoriteAlbums.addPhotos(target.name, picked.map { it.id })
            }
            controller.navigation.picker = null
        },
        onCancel = { controller.navigation.picker = null },
    )
}

// Review rises into view from slightly below and settles, and sinks back out on closing, rather than appearing at once.
@Composable
internal fun ReviewOverlay(controller: LibraryController, content: LibraryContent) {
    AnimatedContent(
        targetState = controller.navigation.review,
        transitionSpec = {
            (fadeIn(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.powerTwoOut)) +
                scaleIn(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.powerTwoOut), initialScale = 0.94f) +
                slideInVertically(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.powerTwoOut)) { it / 12 })
                .togetherWith(fadeOut(tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn)) + scaleOut(tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn), targetScale = 0.96f))
        },
        label = "review",
    ) { source ->
        if (source != null) {
            ReviewScreen(
                items = content.itemsFor(source),
                isPrivate = source.isPrivate,
                progressKey = source.reviewKey,
                onDelete = { picked -> if (source.isPrivate) controller.actions.deletePrivate(picked) else controller.actions.trash(picked) },
                onClose = { controller.navigation.review = null },
            )
        }
    }
}

// The viewer grows out of the tile it was opened from and shrinks back into the tile of the photo it ends on; when that tile is not on screen it falls back to a quiet fade.
@Composable
internal fun BoxScope.ViewerOverlay(controller: LibraryController, content: LibraryContent, screen: LibraryScreen, metrics: BarMetrics, scope: CoroutineScope) {
    val transition = controller.viewer
    val request = transition.shown ?: return
    ViewerScreen(
        // Progress is read inside the draw lambdas, so the animation never recomposes the library.
        photoModifier = Modifier.viewerFlight(transition),
        // Allowed from the moment it opens; the buttons pop in once the library's have popped away.
        isChromeAllowed = transition.isOpen,
        navigationHeight = metrics.navigationHeight,
        bar = transition.bar,
        isBehindNavigation = transition.isShrunk,
        items = content.itemsFor(request.source),
        startIndex = request.startIndex,
        albums = content.albums,
        privateGroups = content.privateContents.groups,
        isPrivate = request.source.isPrivate,
        isTrash = request.source == ViewerSource.Trash,
        actions = controller.actions,
        onClose = transition::close,
        onCurrentChanged = { transition.currentId = it },
        onPhotoRatio = transition::rememberRatio,
        onPull = { fraction -> transition.onPull(fraction) { id -> scope.launch { screen.folderMemory?.revealItem(screen.gridItems, id) } } },
        places = content.places,
        // Where the photos come from a folder with a cover, any of them can be made its cover from the viewer.
        onSetCover = when (request.source) {
            is ViewerSource.InAlbum, is ViewerSource.InPrivateGroup -> { item: MediaItem -> controller.setCover(request.source, item, content) }
            is ViewerSource.InFavoriteAlbum -> content.favoriteAlbum(request.source.name)?.let { { item: MediaItem -> controller.setCover(request.source, item, content) } }
            else -> null
        },
    )
}

// Frame by frame the photo's own box (fitted to the screen, no black around it) is cropped down to the tile's square and moved onto it, so the animation shows what the grid shows.
private fun Modifier.viewerFlight(transition: ViewerTransition): Modifier = this
    .drawWithContent {
        val progress = transition.growth()
        val tile = transition.tile
        if (tile == null || progress >= 1f) {
            drawContent()
        } else {
            val photo = fitInside(transition.ratio, size.width, size.height)
            val frame = lerp(tile, photo, progress)
            val full = Rect(0f, 0f, size.width, size.height)
            val expand = ((progress - 0.7f) / 0.3f).coerceIn(0f, 1f)
            // A pull rounds the corners within its first few pixels, so the black behind the photo is never a sharp rectangle that slowly rounds.
            val rounded = (transition.pull / ROUND_PULL).coerceIn(0f, 1f)
            val radius = lerp(8.dp.toPx(), 24.dp.toPx(), progress) * (1f - expand * (1f - rounded))
            val clip = Path().apply { addRoundRect(RoundRect(lerp(frame, full, expand), CornerRadius(radius))) }
            clipPath(clip) { this@drawWithContent.drawContent() }
        }
    }
    .graphicsLayer {
        val progress = transition.growth()
        val tile = transition.tile
        if (tile == null) {
            alpha = progress
            scaleX = 0.94f + 0.06f * progress
            scaleY = scaleX
        } else if (progress < 1f) {
            val photo = fitInside(transition.ratio, size.width, size.height)
            val frame = lerp(tile, photo, progress)
            val coverScale = maxOf(tile.width / photo.width, tile.height / photo.height)
            val scale = coverScale + (1f - coverScale) * progress
            val centre = Offset(size.width / 2f, size.height / 2f)
            scaleX = scale
            scaleY = scale
            // Moves the photo's centre onto the frame's centre; at full progress the frame is the photo and this is zero.
            translationX = frame.center.x - centre.x - (photo.center.x - centre.x) * scale
            translationY = frame.center.y - centre.y - (photo.center.y - centre.y) * scale
        }
    }

// The share of a pull by which the viewer's corners have reached their full rounding.
private const val ROUND_PULL = 0.03f
