package com.vaditim.gallery.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.LocalHazeState
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.fadingGlass
import com.vaditim.gallery.vas.glass
import com.vaditim.gallery.vas.pressable
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private val BAR_ROOM = 84.dp
private val HEADER_ROOM = 56.dp

// Where an open viewer gets its items from. Resolved from the live library on every frame, so a photo moved, favourited or deleted while it is open is reflected without the viewer holding a stale copy.
sealed interface ViewerSource {
    data object Recent : ViewerSource
    data object Favorites : ViewerSource
    data class InAlbum(val albumId: Long) : ViewerSource
}

data class ViewerRequest(val source: ViewerSource, val startIndex: Int)

@Composable
fun GalleryApp(viewModel: GalleryViewModel = viewModel()) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAccess()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize().background(Palette.ground)) {
        if (!access.hasFileAccess) {
            AccessScreen(access)
        } else {
            Library(viewModel)
        }
    }
}

@Composable
private fun Library(viewModel: GalleryViewModel) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val actions = rememberMediaActions(viewModel.repository)

    var section by remember { mutableStateOf(Section.RECENT) }
    var openAlbumId by remember { mutableStateOf<Long?>(null) }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    var scrollToNewestRequest by remember { mutableIntStateOf(0) }

    val recentMemory = remember { GridMemory() }
    val favoritesMemory = remember { GridMemory() }
    val albumMemories = remember { mutableMapOf<Long, GridMemory>() }
    val albumsListState = rememberLazyGridState()
    val hazeState = rememberHazeState()

    val accent by animateColorAsState(section.accent, tween(Motion.STATE_MS), label = "accent")
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val openAlbum: Album? = if (section == Section.ALBUMS) albums.firstOrNull { it.id == openAlbumId } else null
    val insetPadding = PaddingValues(
        top = statusBarHeight + HEADER_ROOM,
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BAR_ROOM,
    )

    fun itemsFor(source: ViewerSource): List<MediaItem> = when (source) {
        ViewerSource.Recent -> library
        ViewerSource.Favorites -> favorites
        is ViewerSource.InAlbum -> albums.firstOrNull { it.id == source.albumId }?.items.orEmpty()
    }

    CompositionLocalProvider(LocalAccent provides accent, LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize()) {
            // A section change is a cut, not a dissolve: the outgoing section is gone fast and at once, the incoming one lands from just below on the overshoot.
            AnimatedContent(
                targetState = section,
                transitionSpec = {
                    (fadeIn(tween(Motion.SECTION_ENTER_MS, Motion.SECTION_ENTER_DELAY_MS, Motion.powerTwoOut)) +
                        slideInVertically(tween(Motion.SECTION_ENTER_MS, Motion.SECTION_ENTER_DELAY_MS, Motion.backOut)) { it / 40 })
                        .togetherWith(fadeOut(tween(Motion.SECTION_LEAVE_MS, easing = Motion.powerTwoIn)))
                },
                label = "section",
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            ) { shown ->
                when (shown) {
                    Section.RECENT -> MediaGrid(
                        items = library,
                        memory = recentMemory,
                        onOpen = { viewer = ViewerRequest(ViewerSource.Recent, it) },
                        contentPadding = insetPadding,
                        scrollToNewestRequest = scrollToNewestRequest,
                        emptyCaption = "No photos yet.",
                    )
                    Section.ALBUMS -> {
                        val album: Album? = albums.firstOrNull { it.id == openAlbumId }
                        if (album == null) {
                            AlbumsScreen(albums, albumsListState, onOpen = { openAlbumId = it.id }, contentPadding = insetPadding)
                        } else {
                            AlbumScreen(
                                album = album,
                                memory = albumMemories.getOrPut(album.id) { GridMemory() },
                                onOpen = { viewer = ViewerRequest(ViewerSource.InAlbum(album.id), it) },
                                onBack = { openAlbumId = null },
                                contentPadding = insetPadding,
                            )
                        }
                    }
                    Section.FAVORITES -> MediaGrid(
                        items = favorites,
                        memory = favoritesMemory,
                        onOpen = { viewer = ViewerRequest(ViewerSource.Favorites, it) },
                        contentPadding = insetPadding,
                        scrollToNewestRequest = scrollToNewestRequest,
                        emptyCaption = "Nothing favourited yet.",
                    )
                }
            }

            // Everything from here up floats over the content and blurs it; none of it is inside the haze source, or it would blur itself.
            Box(Modifier.fillMaxWidth().height(statusBarHeight + HEADER_ROOM + 24.dp).fadingGlass())
            TopRow(
                section = section,
                openAlbum = openAlbum,
                recentMonth = rememberVisibleMonth(library, recentMemory).value,
                favoritesMonth = rememberVisibleMonth(favorites, favoritesMemory).value,
                openAlbumMonth = openAlbum?.let { rememberVisibleMonth(it.items, albumMemories.getOrPut(it.id) { GridMemory() }).value } ?: "",
                onBack = { openAlbumId = null },
            )

            SectionBar(
                active = section,
                onSelect = { selected ->
                    if (selected == section) {
                        if (selected == Section.ALBUMS) openAlbumId = null else scrollToNewestRequest++
                    }
                    section = selected
                },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp),
            )

            // TODO(vaditim): replace with the shared-element zoom (the thumbnail grows into the photo and shrinks back into its cell) — docs/SPEC.md § Viewer.
            AnimatedContent(
                targetState = viewer,
                transitionSpec = {
                    if (targetState != null) {
                        (fadeIn(tween(Motion.VIEWER_ENTER_MS, easing = Motion.powerTwoOut)) +
                            scaleIn(tween(Motion.VIEWER_ENTER_MS, easing = Motion.backOut), initialScale = 0.9f))
                            .togetherWith(fadeOut(tween(Motion.VIEWER_LEAVE_MS)))
                    } else {
                        EnterTransition.None.togetherWith(
                            fadeOut(tween(Motion.VIEWER_LEAVE_MS, easing = Motion.powerTwoIn)) +
                                scaleOut(tween(Motion.VIEWER_LEAVE_MS, easing = Motion.powerTwoIn), targetScale = 0.94f),
                        )
                    }
                },
                label = "viewer",
                modifier = Modifier.fillMaxSize(),
            ) { request ->
                if (request != null) {
                    ViewerScreen(
                        items = itemsFor(request.source),
                        startIndex = request.startIndex,
                        albums = albums,
                        actions = actions,
                        onClose = { viewer = null },
                    )
                }
            }
        }
    }
}

// The top layer: the month you are looking at in a grid, the way back out of an album. Nothing here for the albums list, whose title scrolls with it.
@Composable
private fun TopRow(
    section: Section,
    openAlbum: Album?,
    recentMonth: String,
    favoritesMonth: String,
    openAlbumMonth: String,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            openAlbum != null -> {
                Box(Modifier.pressable(onClick = onBack).glass(Shapes.capsule).padding(horizontal = 16.dp, vertical = 11.dp)) {
                    BasicText("‹  ${openAlbum.name}", style = Type.cardTitle.copy(color = LocalAccent.current), maxLines = 1)
                }
                Box(Modifier.weight(1f))
                MonthChip(openAlbumMonth)
            }
            section == Section.RECENT -> MonthChip(recentMonth)
            section == Section.FAVORITES -> MonthChip(favoritesMonth)
        }
    }
}

@Composable
private fun MonthChip(month: String) {
    if (month.isEmpty()) return
    Box(Modifier.glass(Shapes.capsule).padding(horizontal = 14.dp, vertical = 10.dp)) {
        MicroLabel(month)
    }
}
