package com.vaditim.gallery.ui

import android.app.Activity
import android.view.WindowManager
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.vault.PrivateGroup
import com.vaditim.gallery.vault.PrivateLock
import kotlinx.coroutines.launch
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
    data class InPrivateGroup(val name: String) : ViewerSource
}

// Where the Albums section stands: the folder list, one folder, the private groups, or one private group. Private screens are only reachable while unlocked.
private sealed interface AlbumsPlace {
    data object Folders : AlbumsPlace
    data class Folder(val albumId: Long) : AlbumsPlace
    data object PrivateGroups : AlbumsPlace
    data class PrivateFolder(val name: String) : AlbumsPlace
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
    val privateGroups by viewModel.privateGroups.collectAsStateWithLifecycle()
    val isPrivateUnlocked by viewModel.isPrivateUnlocked.collectAsStateWithLifecycle()
    val actions = rememberMediaActions(viewModel.repository, viewModel.vault, onPrivateChanged = { viewModel.refreshPrivate() })
    val context = LocalContext.current

    var section by remember { mutableStateOf(Section.RECENT) }
    var albumsPlace by remember { mutableStateOf<AlbumsPlace>(AlbumsPlace.Folders) }
    var isNamingGroup by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    var scrollToNewestRequest by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val recentMemory = remember { GridMemory() }
    val favoritesMemory = remember { GridMemory() }
    val albumMemories = remember { mutableMapOf<Long, GridMemory>() }
    val privateMemories = remember { mutableMapOf<String, GridMemory>() }
    val albumsListState = rememberLazyGridState()
    val hazeState = rememberHazeState()

    val accent by animateColorAsState(section.accent, tween(Motion.STATE_MS), label = "accent")
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val place = albumsPlace
    val openAlbum: Album? = if (section == Section.ALBUMS && place is AlbumsPlace.Folder) albums.firstOrNull { it.id == place.albumId } else null
    val openPrivateGroup: PrivateGroup? = if (section == Section.ALBUMS && place is AlbumsPlace.PrivateFolder) privateGroups.firstOrNull { it.name == place.name } else null
    val isInPrivate = section == Section.ALBUMS && (place is AlbumsPlace.PrivateGroups || place is AlbumsPlace.PrivateFolder)

    // Leaving the app locks Private again, and drops anyone standing in it back to the albums list.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.lockPrivate() }
    LaunchedEffect(isPrivateUnlocked) {
        if (!isPrivateUnlocked && (albumsPlace is AlbumsPlace.PrivateGroups || albumsPlace is AlbumsPlace.PrivateFolder)) {
            albumsPlace = AlbumsPlace.Folders
            if (viewer?.source is ViewerSource.InPrivateGroup) viewer = null
        }
    }
    // Private stays out of screenshots and the recent-apps preview while it is on screen.
    val isShowingPrivate = isInPrivate || viewer?.source is ViewerSource.InPrivateGroup
    DisposableEffect(isShowingPrivate) {
        val window = (context as? Activity)?.window
        if (isShowingPrivate) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    val openPrivate = {
        if (isPrivateUnlocked) {
            albumsPlace = AlbumsPlace.PrivateGroups
        } else {
            PrivateLock.unlock(context) {
                viewModel.unlockPrivate()
                albumsPlace = AlbumsPlace.PrivateGroups
            }
        }
    }
    val insetPadding = PaddingValues(
        top = statusBarHeight + HEADER_ROOM,
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BAR_ROOM,
    )

    fun itemsFor(source: ViewerSource): List<MediaItem> = when (source) {
        ViewerSource.Recent -> library
        ViewerSource.Favorites -> favorites
        is ViewerSource.InAlbum -> albums.firstOrNull { it.id == source.albumId }?.items.orEmpty()
        is ViewerSource.InPrivateGroup -> if (isPrivateUnlocked) privateGroups.firstOrNull { it.name == source.name }?.items.orEmpty() else emptyList()
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
                    Section.ALBUMS -> when (val shownPlace = albumsPlace) {
                        AlbumsPlace.Folders -> AlbumsScreen(
                            albums = albums,
                            state = albumsListState,
                            onOpen = { albumsPlace = AlbumsPlace.Folder(it.id) },
                            contentPadding = insetPadding,
                            footer = { PrivateEntry(isPrivateUnlocked, privateGroups.size, onClick = openPrivate) },
                        )
                        is AlbumsPlace.Folder -> albums.firstOrNull { it.id == shownPlace.albumId }?.let { album ->
                            AlbumScreen(
                                album = album,
                                memory = albumMemories.getOrPut(album.id) { GridMemory() },
                                onOpen = { viewer = ViewerRequest(ViewerSource.InAlbum(album.id), it) },
                                onBack = { albumsPlace = AlbumsPlace.Folders },
                                contentPadding = insetPadding,
                            )
                        }
                        AlbumsPlace.PrivateGroups -> PrivateGroupsScreen(
                            groups = privateGroups,
                            onOpen = { albumsPlace = AlbumsPlace.PrivateFolder(it.name) },
                            onNewGroup = { isNamingGroup = true },
                            onBack = { albumsPlace = AlbumsPlace.Folders },
                            contentPadding = insetPadding,
                        )
                        is AlbumsPlace.PrivateFolder -> privateGroups.firstOrNull { it.name == shownPlace.name }?.let { group ->
                            PrivateGroupScreen(
                                group = group,
                                memory = privateMemories.getOrPut(group.name) { GridMemory() },
                                onOpen = { viewer = ViewerRequest(ViewerSource.InPrivateGroup(group.name), it) },
                                onBack = { albumsPlace = AlbumsPlace.PrivateGroups },
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
                backLabel = openAlbum?.name ?: openPrivateGroup?.let { "Private · ${it.name}" },
                recentMonth = rememberVisibleMonth(library, recentMemory).value,
                favoritesMonth = rememberVisibleMonth(favorites, favoritesMemory).value,
                folderMonth = when {
                    openAlbum != null -> rememberVisibleMonth(openAlbum.items, albumMemories.getOrPut(openAlbum.id) { GridMemory() }).value
                    openPrivateGroup != null -> rememberVisibleMonth(openPrivateGroup.items, privateMemories.getOrPut(openPrivateGroup.name) { GridMemory() }).value
                    else -> ""
                },
                onBack = { albumsPlace = if (openPrivateGroup != null) AlbumsPlace.PrivateGroups else AlbumsPlace.Folders },
            )

            SectionBar(
                active = section,
                onSelect = { selected ->
                    if (selected == section) {
                        if (selected == Section.ALBUMS) albumsPlace = AlbumsPlace.Folders else scrollToNewestRequest++
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
                        privateGroups = privateGroups,
                        isPrivate = request.source is ViewerSource.InPrivateGroup,
                        actions = actions,
                        onClose = { viewer = null },
                    )
                }
            }

            if (isNamingGroup) {
                NameSheet(
                    label = "NEW PRIVATE GROUP",
                    action = "CREATE",
                    onConfirm = { name ->
                        isNamingGroup = false
                        scope.launch {
                            viewModel.vault.createGroup(name)
                            viewModel.refreshPrivate()
                        }
                    },
                    onDismiss = { isNamingGroup = false },
                )
            }
        }
    }
}

// The top layer: the month you are looking at in a grid, the way back out of an album. Nothing here for the albums list, whose title scrolls with it.
@Composable
private fun TopRow(
    section: Section,
    backLabel: String?,
    recentMonth: String,
    favoritesMonth: String,
    folderMonth: String,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            backLabel != null -> {
                Box(Modifier.weight(1f, fill = false).pressable(onClick = onBack).glass(Shapes.capsule).padding(horizontal = 16.dp, vertical = 11.dp)) {
                    BasicText("‹  $backLabel", style = Type.cardTitle.copy(color = LocalAccent.current), maxLines = 1)
                }
                Box(Modifier.weight(0.01f))
                MonthChip(folderMonth)
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
