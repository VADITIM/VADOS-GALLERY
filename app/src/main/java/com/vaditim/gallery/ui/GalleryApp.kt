package com.vaditim.gallery.ui

import androidx.compose.ui.text.style.TextOverflow

import com.vaditim.gallery.CrashLog
import androidx.compose.ui.unit.sp
import android.content.Intent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.vaditim.gallery.AlbumStack
import com.vaditim.gallery.Settings
import com.vaditim.gallery.renamedAlbum
import com.vaditim.gallery.withPhotos
import com.vaditim.gallery.renamed
import com.vaditim.gallery.withAlbum
import com.vaditim.gallery.withoutAlbum
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.util.lerp
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.vaditim.gallery.media.newAlbumPath
import com.vaditim.gallery.vault.PrivateGroup
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.LocalHazeState
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.fadingGlass
import com.vaditim.gallery.vas.glass
import com.vaditim.gallery.vas.pressable
import com.vaditim.gallery.vault.PrivateLock
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

private val BAR_ROOM = 84.dp
private val HEADER_ROOM = 56.dp
private val MONTH_CHIP_WIDTH = 148.dp
// How far the viewer has grown into place before its buttons start arriving.
private const val VIEWER_CHROME_AT = 0.85f

// Where an open viewer gets its items from. Resolved from the live library on every frame, so a photo moved, favourited or deleted while it is open is reflected without the viewer holding a stale copy.
sealed interface ViewerSource {
    data object Recent : ViewerSource
    data object Favorites : ViewerSource
    data class InAlbum(val albumId: Long) : ViewerSource
    data class InPrivateGroup(val name: String) : ViewerSource
    data object PrivateFavorites : ViewerSource
    data object Trash : ViewerSource
    data class InLocation(val key: String) : ViewerSource
    data class InFavoriteAlbum(val name: String) : ViewerSource
}

data class ViewerRequest(val source: ViewerSource, val startIndex: Int)

// Where the Albums section stands. The private places are only reachable while Private is unlocked.
private sealed interface AlbumsPlace {
    data object Folders : AlbumsPlace
    data class Folder(val albumId: Long) : AlbumsPlace
    data object PrivateGroups : AlbumsPlace
    data class PrivateFolder(val name: String) : AlbumsPlace
    data object PrivateFavorites : AlbumsPlace
    data object Locations : AlbumsPlace
    data class Location(val key: String) : AlbumsPlace
    data object Trash : AlbumsPlace
}

private val AlbumsPlace.isPrivate: Boolean
    get() = this is AlbumsPlace.PrivateGroups || this is AlbumsPlace.PrivateFolder || this is AlbumsPlace.PrivateFavorites

// The sheets the app itself opens — for a selection or for a long-pressed album. The viewer has its own.
private enum class AppSheet {
    NONE,
    NEW_ALBUM,
    SETTINGS, ALBUM_RENAME, GROUP_RENAME, CONFIRM_PRIVATE, SELECTION_MOVE, SELECTION_NEW_ALBUM, SELECTION_GROUP, SELECTION_NEW_GROUP,
    ALBUM_MENU, ALBUM_GROUP, ALBUM_NEW_GROUP,
    GROUP_MENU, GROUP_MOVE_OUT, GROUP_MOVE_OUT_NEW_ALBUM,
    PRIVATE_NEW_GROUP,
    STACK_MENU, STACK_RENAME, ALBUM_STACK, ALBUM_NEW_STACK,
    FAVORITE_NEW_ALBUM, FAVORITE_ALBUM_PICK, FAVORITE_SELECTION_NEW_ALBUM, FAVORITE_ALBUM_MENU, FAVORITE_ALBUM_RENAME,
    FAVORITE_STACK_MENU, FAVORITE_STACK_RENAME, FAVORITE_ALBUM_STACK, FAVORITE_ALBUM_NEW_STACK,
}

// What Favorites shows: every favourite as one grid, the albums made inside it, or one of those albums.
private sealed interface FavoritesView {
    data object All : FavoritesView
    data object Albums : FavoritesView
    data class InAlbum(val name: String) : FavoritesView
}

// Where the photo picker puts what is picked: an album folder (new or existing), or a private group.
private sealed interface PickerTarget {
    val title: String
    data class IntoAlbum(val relativePath: String, val name: String) : PickerTarget { override val title get() = "Add to $name" }
    data class IntoGroup(val name: String) : PickerTarget { override val title get() = "Add to Private · $name" }
    data class IntoFavoriteAlbum(val name: String) : PickerTarget { override val title get() = "Add to Favorites · $name" }
}

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
    val privateContents by viewModel.privateContents.collectAsStateWithLifecycle()
    val isPrivateUnlocked by viewModel.isPrivateUnlocked.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val trash by viewModel.trash.collectAsStateWithLifecycle()
    // GPS in photos is stripped by the system unless this is granted; it is asked once, the first time the library shows.
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshLocationPermission() }
    LaunchedEffect(Unit) { locationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) }
    // Recent without the folders kept out of it; everything else (albums, the picker, locations) still sees the whole library.
    val recent = remember(library, Settings.hiddenFromRecent) {
        val hidden = Settings.hiddenFromRecent
        if (hidden.isEmpty()) library else library.filter { it.relativePath !in hidden }
    }
    val actions = rememberMediaActions(viewModel.repository, viewModel.vault, onPrivateChanged = { viewModel.refreshPrivate() }, samsungTrash = viewModel.samsungTrash, onTrashChanged = { viewModel.refreshTrash() })
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var section by remember { mutableStateOf(Section.RECENT) }
    var albumsPlace by remember { mutableStateOf<AlbumsPlace>(AlbumsPlace.Folders) }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    // `viewer` is what is wanted open; `shownViewer` stays composed through the closing animation.
    var shownViewer by remember { mutableStateOf<ViewerRequest?>(null) }
    var viewerCurrentId by remember { mutableStateOf<Long?>(null) }
    var viewerRect by remember { mutableStateOf<Rect?>(null) }
    var viewerRatio by remember { mutableFloatStateOf(1f) }
    // How far a swipe down has already pulled the viewer towards its tile (0 to 1); the draw lambdas fold it into the animation's progress.
    var viewerPull by remember { mutableFloatStateOf(0f) }
    // The photo whose tile a pull has already scrolled into view, so it is asked for once.
    var revealingId by remember { mutableStateOf<Long?>(null) }
    val photoRatios = remember { HashMap<Long, Float>() }
    val viewerProgress = remember { Animatable(0f) }
    // The viewer's buttons come in once the photo has nearly grown into place, and leave as soon as it starts closing.
    val isViewerSettled by remember { derivedStateOf { viewer != null && viewerProgress.value > VIEWER_CHROME_AT } }
    var scrollToNewestRequest by remember { mutableIntStateOf(0) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    // Albums (by folder path) or private groups (by name) picked in a cover grid; only one of the two grids is ever on screen.
    var selectedCovers by remember { mutableStateOf(emptySet<String>()) }
    var isDeleteArmed by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(AppSheet.NONE) }
    var sheetAlbum by remember { mutableStateOf<Album?>(null) }
    var sheetGroup by remember { mutableStateOf<PrivateGroup?>(null) }
    var sheetStack by remember { mutableStateOf<String?>(null) }
    var isMenuDeleteArmed by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    // The folder being reviewed one photo at a time, if any.
    var review by remember { mutableStateOf<ViewerSource?>(null) }
    LaunchedEffect(sheet) { if (sheet != AppSheet.ALBUM_MENU && sheet != AppSheet.GROUP_MENU && sheet != AppSheet.FAVORITE_ALBUM_MENU) isMenuDeleteArmed = false }
    // The Favorites album open, the one long-pressed, and its groups left open.
    var openFavoriteAlbum by remember { mutableStateOf<String?>(null) }
    var sheetFavoriteAlbum by remember { mutableStateOf<String?>(null) }
    var openFavoriteStacks by remember { mutableStateOf(emptySet<String>()) }
    val favoriteAlbumsListState = rememberLazyGridState()
    val favoriteAlbumMemories = remember { mutableMapOf<String, GridMemory>() }

    val recentMemory = remember { GridMemory() }
    val favoritesMemory = remember { GridMemory() }
    val privateFavoritesMemory = remember { GridMemory() }
    // The trash keeps every shot on its own, since each one there is about to go.
    val trashMemory = remember { GridMemory(isStacking = false) }
    // Photos about to go into Private, held while the confirmation is open.
    var pendingPrivate by remember { mutableStateOf<PendingPrivate?>(null) }
    var isRearranging by remember { mutableStateOf(false) }
    // The previous run's crash, if it had one; shown once so it can be sent on.
    var lastCrash by remember { mutableStateOf(CrashLog.read(context)) }
    val haptic = LocalHapticFeedback.current
    // Albums in the order the user dragged them into; ones never arranged keep the default order after them.
    val arrangedAlbums = remember(albums, Settings.albumOrder) {
        val order = Settings.albumOrder
        albums.sortedBy { album -> order.indexOf(album.relativePath).let { if (it < 0) Int.MAX_VALUE else it } }
    }
    val arrangedGroups = remember(privateContents.groups, Settings.groupOrder) {
        val order = Settings.groupOrder
        privateContents.groups.sortedBy { group -> order.indexOf(group.name).let { if (it < 0) Int.MAX_VALUE else it } }
    }
    // Album groups left open stay open while an album from one of them is looked at.
    var openAlbumStacks by remember { mutableStateOf(emptySet<String>()) }
    val albumMemories = remember { mutableMapOf<Long, GridMemory>() }
    val locationMemories = remember { mutableMapOf<String, GridMemory>() }
    val privateMemories = remember { mutableMapOf<String, GridMemory>() }
    // Lists you go back to keep their scroll position: the state lives here, above the screens that come and go.
    val albumsListState = rememberLazyGridState()
    val privateGroupsListState = rememberLazyGridState()
    val locationsListState = rememberLazyGridState()
    val hazeState = rememberHazeState()

    // Favorites albums hold favourites only: a photo unfavourited leaves them, and an album left empty is not shown. The name is the album's path, so groups and order hold names.
    val favoriteAlbumViews = remember(favorites, Settings.favoriteAlbums, Settings.favoriteAlbumOrder) {
        val order = Settings.favoriteAlbumOrder
        Settings.favoriteAlbums.mapNotNull { album ->
            val ids = album.ids.toSet()
            val items = favorites.filter { it.id in ids }
            if (items.isEmpty()) null else Album(album.name.hashCode().toLong(), album.name, album.name, items)
        }.sortedBy { album -> order.indexOf(album.name).let { if (it < 0) Int.MAX_VALUE else it } }
    }
    val openFavorite = openFavoriteAlbum?.let { name -> favoriteAlbumViews.firstOrNull { it.name == name } }
    // An album emptied while open (its last photo unfavourited) closes itself.
    LaunchedEffect(openFavorite == null) { if (openFavorite == null) openFavoriteAlbum = null }
    val favoritesView = when {
        openFavorite != null -> FavoritesView.InAlbum(openFavorite.name)
        Settings.favoritesAsAlbums -> FavoritesView.Albums
        else -> FavoritesView.All
    }

    val accent by animateColorAsState(section.accent, tween(Motion.STATE_MS), label = "accent")
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val insetPadding = PaddingValues(
        top = statusBarHeight + HEADER_ROOM,
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BAR_ROOM,
    )

    val place = if (section == Section.ALBUMS) albumsPlace else null
    val openAlbum = (place as? AlbumsPlace.Folder)?.let { folder -> albums.firstOrNull { it.id == folder.albumId } }
    val openPrivateGroup = (place as? AlbumsPlace.PrivateFolder)?.let { folder -> privateContents.groups.firstOrNull { it.name == folder.name } }
    val isInPrivate = place?.isPrivate == true
    val openLocation = (place as? AlbumsPlace.Location)?.let { shown -> locations.firstOrNull { it.key == shown.key } }
    val selectedAlbums = if (place == AlbumsPlace.Folders) arrangedAlbums.filter { it.relativePath in selectedCovers } else emptyList()
    val selectedGroups = if (place == AlbumsPlace.PrivateGroups) arrangedGroups.filter { it.name in selectedCovers } else emptyList()
    val isSelectingCovers = selectedAlbums.isNotEmpty() || selectedGroups.isNotEmpty()
    // What an album or group sheet acts on: the picked covers while picking, else the one long-pressed.
    val targetAlbums = selectedAlbums.ifEmpty { listOfNotNull(sheetAlbum) }
    val targetGroups = selectedGroups.ifEmpty { listOfNotNull(sheetGroup) }
    val targetAlbumsLabel = targetAlbums.singleOrNull()?.name?.uppercase() ?: "${targetAlbums.size} ALBUMS"
    val targetGroupsLabel = targetGroups.singleOrNull()?.name?.uppercase() ?: "${targetGroups.size} GROUPS"

    // The items of the grid on screen, which is what a selection is made of.
    val gridItems: List<MediaItem> = when {
        section == Section.RECENT -> recent
        section == Section.FAVORITES -> when {
            openFavorite != null -> openFavorite.items
            favoritesView == FavoritesView.All -> favorites
            else -> emptyList()
        }
        openAlbum != null -> openAlbum.items
        openPrivateGroup != null -> openPrivateGroup.items
        openLocation != null -> openLocation.items
        place is AlbumsPlace.Trash -> trash
        place is AlbumsPlace.PrivateFavorites -> privateContents.favorites
        else -> emptyList()
    }
    val selectedItems = gridItems.filter { it.id in selectedIds }
    val isSelecting = selectedItems.isNotEmpty()
    val selection = Selection(selectedIds) { item ->
        selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
        Haptics.tick(context)
        isDeleteArmed = false
    }
    val clearSelection = {
        selectedIds = emptySet()
        selectedCovers = emptySet()
        isDeleteArmed = false
        sheet = AppSheet.NONE
    }
    LaunchedEffect(section, albumsPlace, favoritesView) {
        clearSelection()
        isRearranging = false
    }
    BackHandler(enabled = isRearranging) { isRearranging = false }
    BackHandler(enabled = isSelecting || isSelectingCovers) { clearSelection() }
    val toggleCovers: (List<String>) -> Unit = { keys ->
        selectedCovers = if (keys.all { it in selectedCovers }) selectedCovers - keys.toSet() else selectedCovers + keys
        Haptics.tick(context)
        isDeleteArmed = false
    }

    // Leaving the app locks Private again, and drops anyone standing in it back to the albums list.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.lockPrivate() }
    LaunchedEffect(isPrivateUnlocked) {
        if (!isPrivateUnlocked) {
            if (albumsPlace.isPrivate) albumsPlace = AlbumsPlace.Folders
            if (viewer?.source.isPrivateSource()) viewer = null
            if (review.isPrivateSource()) review = null
        }
    }
    // Private stays out of screenshots and the recent-apps preview while it is on screen.
    val isShowingPrivate = isInPrivate || viewer?.source.isPrivateSource() || review.isPrivateSource()
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

    fun itemsFor(source: ViewerSource): List<MediaItem> = when (source) {
        ViewerSource.Recent -> recent
        ViewerSource.Favorites -> favorites
        is ViewerSource.InAlbum -> albums.firstOrNull { it.id == source.albumId }?.items.orEmpty()
        is ViewerSource.InPrivateGroup -> if (isPrivateUnlocked) privateContents.groups.firstOrNull { it.name == source.name }?.items.orEmpty() else emptyList()
        ViewerSource.PrivateFavorites -> if (isPrivateUnlocked) privateContents.favorites else emptyList()
        is ViewerSource.InLocation -> locations.firstOrNull { it.key == source.key }?.items.orEmpty()
        is ViewerSource.InFavoriteAlbum -> favoriteAlbumViews.firstOrNull { it.name == source.name }?.items.orEmpty()
        ViewerSource.Trash -> trash
    }

    val folderMemory = when {
        openAlbum != null -> albumMemories.getOrPut(openAlbum.id) { GridMemory() }
        openPrivateGroup != null -> privateMemories.getOrPut(openPrivateGroup.name) { GridMemory() }
        openLocation != null -> locationMemories.getOrPut(openLocation.key) { GridMemory() }
        place is AlbumsPlace.Trash -> trashMemory
        place is AlbumsPlace.PrivateFavorites -> privateFavoritesMemory
        section == Section.RECENT -> recentMemory
        section == Section.FAVORITES -> when {
            openFavorite != null -> favoriteAlbumMemories.getOrPut(openFavorite.name) { GridMemory() }
            favoritesView == FavoritesView.All -> favoritesMemory
            else -> null
        }
        else -> null
    }

    // A photo's proportions as the viewer last decoded them (the stored width and height ignore rotation), else as stored, else square.
    fun ratioOf(item: MediaItem?): Float =
        item?.let { photoRatios[it.id] ?: if (it.width > 0 && it.height > 0) it.width.toFloat() / it.height else null } ?: 1f

    LaunchedEffect(viewer) {
        val request = viewer
        if (request != null) {
            val openedId = itemsFor(request.source).getOrNull(request.startIndex)?.id
            viewerCurrentId = openedId
            viewerRect = TileBounds.of(openedId)
            viewerRatio = ratioOf(itemsFor(request.source).getOrNull(request.startIndex))
            viewerPull = 0f
            revealingId = null
            shownViewer = request
            viewerProgress.snapTo(0f)
            viewerProgress.animateTo(1f, tween(Motion.VIEWER_ENTER_MS, easing = Motion.powerTwoOut))
        } else if (shownViewer != null) {
            val currentId = viewerCurrentId
            if (currentId != null && TileBounds.of(currentId) == null) {
                folderMemory?.revealItem(gridItems, currentId)
                withFrameNanos { }
                withFrameNanos { }
            }
            viewerRect = TileBounds.of(currentId)
            viewerRatio = ratioOf(shownViewer?.let { shown -> itemsFor(shown.source).firstOrNull { it.id == currentId } })
            if (viewerPull > 0f) {
                // Closed by a pull: the shrink carries on from where the finger let go, already moving, instead of restarting from full size.
                viewerProgress.snapTo(viewerProgress.value * (1f - viewerPull))
                viewerPull = 0f
                viewerProgress.animateTo(0f, tween(Motion.VIEWER_CLOSE_MS, easing = Motion.powerTwoOut))
            } else {
                viewerProgress.animateTo(0f, tween(Motion.VIEWER_CLOSE_MS, easing = Motion.powerThreeInOut))
            }
            shownViewer = null
            viewerPull = 0f
        }
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
                // Each section keeps its own accent while it leaves, so the outgoing one never takes on the next one's colour.
                CompositionLocalProvider(LocalAccent provides shown.accent) {
                when (shown) {
                    Section.RECENT -> MediaGrid(
                        items = recent,
                        memory = recentMemory,
                        onOpen = { viewer = ViewerRequest(ViewerSource.Recent, it) },
                        contentPadding = insetPadding,
                        selection = selection,
                        scrollToNewestRequest = scrollToNewestRequest,
                        emptyCaption = "No photos yet.",
                    )
                    // Opening a folder (an album, Private, a private group) eases in from slightly small; closing it is the same, quieter.
                    Section.ALBUMS -> AnimatedContent(
                        targetState = albumsPlace,
                        transitionSpec = {
                            (fadeIn(tween(Motion.SECTION_ENTER_MS, Motion.SECTION_ENTER_DELAY_MS, Motion.powerTwoOut)) +
                                scaleIn(tween(Motion.SECTION_ENTER_MS, Motion.SECTION_ENTER_DELAY_MS, Motion.powerTwoOut), initialScale = 0.96f))
                                .togetherWith(fadeOut(tween(Motion.SECTION_LEAVE_MS, easing = Motion.powerTwoIn)))
                        },
                        label = "place",
                    ) { shownPlace -> when (shownPlace) {
                        AlbumsPlace.Folders -> AlbumsScreen(
                            openStacks = openAlbumStacks,
                            onOpenStacksChange = { openAlbumStacks = it },
                            albums = arrangedAlbums,
                            isRearranging = isRearranging,
                            onArrange = { Settings.updateAlbumOrder(it) },
                            selectedPaths = selectedCovers,
                            onToggle = { picked -> toggleCovers(picked.map { it.relativePath }) },
                            state = albumsListState,
                            onOpen = { albumsPlace = AlbumsPlace.Folder(it.id) },
                            onLongPress = { album ->
                                sheetAlbum = album
                                sheet = AppSheet.ALBUM_MENU
                            },
                            onStackLongPress = { name ->
                                sheetStack = name
                                sheet = AppSheet.STACK_MENU
                            },
                            onNewAlbum = { sheet = AppSheet.NEW_ALBUM },
                            contentPadding = insetPadding,
                            footer = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FolderEntry("Locations", icon = { PinIcon(it) }, onClick = { albumsPlace = AlbumsPlace.Locations })
                                    FolderEntry("Trash", icon = { TrashIcon(it) }, onClick = { albumsPlace = AlbumsPlace.Trash }, count = trash.size)
                                    PrivateEntry(onClick = openPrivate)
                                }
                            },
                        )
                        is AlbumsPlace.Folder -> albums.firstOrNull { it.id == shownPlace.albumId }?.let { album ->
                            AlbumScreen(
                                album = album,
                                memory = albumMemories.getOrPut(album.id) { GridMemory() },
                                onOpen = { viewer = ViewerRequest(ViewerSource.InAlbum(album.id), it) },
                                onBack = { albumsPlace = AlbumsPlace.Folders },
                                contentPadding = insetPadding,
                                selection = selection,
                            )
                        }
                        AlbumsPlace.Trash -> TrashScreen(
                            items = trash,
                            memory = trashMemory,
                            onOpen = { viewer = ViewerRequest(ViewerSource.Trash, it) },
                            onBack = { albumsPlace = AlbumsPlace.Folders },
                            contentPadding = insetPadding,
                            selection = selection,
                        )
                        AlbumsPlace.Locations -> LocationsScreen(
                            groups = locations,
                            onOpen = { albumsPlace = AlbumsPlace.Location(it.key) },
                            onBack = { albumsPlace = AlbumsPlace.Folders },
                            contentPadding = insetPadding,
                            state = locationsListState,
                        )
                        is AlbumsPlace.Location -> locations.firstOrNull { it.key == shownPlace.key }?.let { group ->
                            LocationScreen(
                                items = group.items,
                                memory = locationMemories.getOrPut(group.key) { GridMemory() },
                                onOpen = { viewer = ViewerRequest(ViewerSource.InLocation(group.key), it) },
                                onBack = { albumsPlace = AlbumsPlace.Locations },
                                contentPadding = insetPadding,
                                selection = selection,
                            )
                        }
                        AlbumsPlace.PrivateGroups -> PrivateGroupsScreen(
                            groups = arrangedGroups,
                            isRearranging = isRearranging,
                            selectedNames = selectedCovers,
                            onToggle = { toggleCovers(listOf(it.name)) },
                            onMove = { from, to ->
                                val names = arrangedGroups.map { it.name }.toMutableList()
                                names.add(to, names.removeAt(from))
                                Settings.updateGroupOrder(names)
                            },
                            favorites = privateContents.favorites,
                            onOpen = { albumsPlace = AlbumsPlace.PrivateFolder(it.name) },
                            onLongPress = { group ->
                                sheetGroup = group
                                sheet = AppSheet.GROUP_MENU
                            },
                            onOpenFavorites = { albumsPlace = AlbumsPlace.PrivateFavorites },
                            onOpenSelection = { viewer = ViewerRequest(ViewerSource.PrivateFavorites, it) },
                            onNewGroup = { sheet = AppSheet.PRIVATE_NEW_GROUP },
                            onBack = { albumsPlace = AlbumsPlace.Folders },
                            contentPadding = insetPadding,
                            state = privateGroupsListState,
                            isViewerOpen = shownViewer != null,
                        )
                        is AlbumsPlace.PrivateFolder -> privateContents.groups.firstOrNull { it.name == shownPlace.name }?.let { group ->
                            PrivateItemsScreen(
                                items = group.items,
                                memory = privateMemories.getOrPut(group.name) { GridMemory() },
                                onOpen = { viewer = ViewerRequest(ViewerSource.InPrivateGroup(group.name), it) },
                                onBack = { albumsPlace = AlbumsPlace.PrivateGroups },
                                contentPadding = insetPadding,
                                selection = selection,
                            )
                        }
                        AlbumsPlace.PrivateFavorites -> PrivateItemsScreen(
                            items = privateContents.favorites,
                            memory = privateFavoritesMemory,
                            onOpen = { viewer = ViewerRequest(ViewerSource.PrivateFavorites, it) },
                            onBack = { albumsPlace = AlbumsPlace.PrivateGroups },
                            contentPadding = insetPadding,
                            selection = selection,
                        )
                    } }
                    Section.FAVORITES -> AnimatedContent(
                        targetState = favoritesView,
                        transitionSpec = {
                            (fadeIn(tween(Motion.SECTION_ENTER_MS, Motion.SECTION_ENTER_DELAY_MS, Motion.powerTwoOut)) +
                                scaleIn(tween(Motion.SECTION_ENTER_MS, Motion.SECTION_ENTER_DELAY_MS, Motion.powerTwoOut), initialScale = 0.96f))
                                .togetherWith(fadeOut(tween(Motion.SECTION_LEAVE_MS, easing = Motion.powerTwoIn)))
                        },
                        label = "favorites",
                    ) { shownView -> when (shownView) {
                        FavoritesView.All -> MediaGrid(
                            items = favorites,
                            memory = favoritesMemory,
                            onOpen = { viewer = ViewerRequest(ViewerSource.Favorites, it) },
                            contentPadding = insetPadding,
                            selection = selection,
                            scrollToNewestRequest = scrollToNewestRequest,
                            emptyCaption = "Nothing favourited yet.",
                        )
                        FavoritesView.Albums -> AlbumsScreen(
                            albums = favoriteAlbumViews,
                            title = "Favorites",
                            stacks = Settings.favoriteStacks,
                            openStacks = openFavoriteStacks,
                            onOpenStacksChange = { openFavoriteStacks = it },
                            isRearranging = isRearranging,
                            onArrange = { Settings.updateFavoriteAlbumOrder(it) },
                            state = favoriteAlbumsListState,
                            onOpen = { openFavoriteAlbum = it.name },
                            onLongPress = { album ->
                                sheetFavoriteAlbum = album.name
                                sheet = AppSheet.FAVORITE_ALBUM_MENU
                            },
                            onStackLongPress = { name ->
                                sheetStack = name
                                sheet = AppSheet.FAVORITE_STACK_MENU
                            },
                            onNewAlbum = { sheet = AppSheet.FAVORITE_NEW_ALBUM },
                            contentPadding = insetPadding,
                        )
                        is FavoritesView.InAlbum -> favoriteAlbumViews.firstOrNull { it.name == shownView.name }?.let { album ->
                            AlbumScreen(
                                album = album,
                                memory = favoriteAlbumMemories.getOrPut(album.name) { GridMemory() },
                                onOpen = { viewer = ViewerRequest(ViewerSource.InFavoriteAlbum(album.name), it) },
                                onBack = { openFavoriteAlbum = null },
                                contentPadding = insetPadding,
                                selection = selection,
                            )
                        }
                    } }
                }
                }
            }

            // Everything from here up floats over the content and blurs it; none of it is inside the haze source, or it would blur itself.
            Box(Modifier.fillMaxWidth().height((statusBarHeight + HEADER_ROOM + 24.dp) * 0.8f).fadingGlass())

            TopRow(
                month = folderMemory?.let { rememberVisibleMonth(gridItems, it).value } ?: "",
                selectedCount = selectedItems.size + selectedAlbums.size + selectedGroups.size,
                onReview = when {
                    openAlbum != null -> { { review = ViewerSource.InAlbum(openAlbum.id) } }
                    openPrivateGroup != null -> { { review = ViewerSource.InPrivateGroup(openPrivateGroup.name) } }
                    place is AlbumsPlace.PrivateFavorites -> { { review = ViewerSource.PrivateFavorites } }
                    openLocation != null -> { { review = ViewerSource.InLocation(openLocation.key) } }
                    section == Section.FAVORITES && openFavorite != null -> { { review = ViewerSource.InFavoriteAlbum(openFavorite.name) } }
                    else -> null
                },
                // The folder open takes new photos straight from here; a location only gathers by place, so it has none.
                onAdd = when {
                    openAlbum != null -> { { picker = PickerTarget.IntoAlbum(openAlbum.relativePath, openAlbum.name) } }
                    openPrivateGroup != null -> { { picker = PickerTarget.IntoGroup(openPrivateGroup.name) } }
                    section == Section.FAVORITES && openFavorite != null -> { { picker = PickerTarget.IntoFavoriteAlbum(openFavorite.name) } }
                    else -> null
                },
                // Favorites switches between every favourite in one grid and the albums made inside it.
                onToggleView = if (section == Section.FAVORITES && openFavorite == null) { { Settings.updateFavoritesAsAlbums(!Settings.favoritesAsAlbums) } } else null,
                isAlbumsView = Settings.favoritesAsAlbums,
                onCancelSelection = clearSelection,
                onSettings = { sheet = AppSheet.SETTINGS },
            )

            val barModifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp)
            if (isRearranging) {
                Box(barModifier.pressable(onClick = { isRearranging = false }).glass(Shapes.capsule).padding(horizontal = 22.dp, vertical = 13.dp)) {
                    CheckIcon(accent)
                }
            } else if (isSelectingCovers) {
                Row(barModifier.glass(Shapes.capsule).padding(5.dp)) {
                    val deleteModifier = if (isDeleteArmed) Modifier.background(Palette.danger.copy(alpha = 0.22f), Shapes.capsule) else Modifier
                    if (selectedGroups.isNotEmpty()) {
                        IconButton(onClick = { sheet = AppSheet.GROUP_MOVE_OUT }) { LockIcon(Palette.textBody, isOpen = true) }
                        // Private groups are outside the system trash, so deleting them takes a second tap.
                        IconButton(onClick = {
                            if (isDeleteArmed) {
                                selectedGroups.forEach { actions.deleteGroup(it) }
                                clearSelection()
                            } else {
                                isDeleteArmed = true
                            }
                        }, modifier = deleteModifier) { TrashIcon(Palette.danger) }
                    } else {
                        if (Settings.groupedAlbums) IconButton(onClick = { sheet = AppSheet.ALBUM_STACK }) { MoveIcon(Palette.textBody) }
                        IconButton(onClick = { sheet = AppSheet.ALBUM_GROUP }) { LockIcon(Palette.textBody) }
                        // Whole albums at once, so it takes a second tap even though the trash can give them back.
                        IconButton(onClick = {
                            if (isDeleteArmed) {
                                actions.trash(selectedAlbums.flatMap { it.items })
                                clearSelection()
                            } else {
                                isDeleteArmed = true
                            }
                        }, modifier = deleteModifier) { TrashIcon(Palette.danger) }
                    }
                }
            } else if (isSelecting) {
                Row(barModifier.glass(Shapes.capsule).padding(5.dp)) {
                  if (place is AlbumsPlace.Trash) {
                    IconButton(onClick = {
                        actions.restore(selectedItems)
                        clearSelection()
                    }) { RestoreIcon(accent) }
                    // Out of the trash there is no coming back, so it takes a second tap.
                    IconButton(
                        onClick = {
                            if (isDeleteArmed) {
                                actions.deleteForever(selectedItems)
                                clearSelection()
                            } else {
                                isDeleteArmed = true
                            }
                        },
                        modifier = if (isDeleteArmed) Modifier.background(Palette.danger.copy(alpha = 0.22f), Shapes.capsule) else Modifier,
                    ) { TrashIcon(Palette.danger) }
                  } else {
                    IconButton(onClick = { actions.share(selectedItems) }) { ShareIcon(Palette.textBody) }
                    if (selectedItems.size == 1 && (openAlbum != null || openPrivateGroup != null)) {
                        IconButton(onClick = {
                            when {
                                openAlbum != null -> viewModel.setAlbumCover(openAlbum.id, selectedItems.first())
                                openPrivateGroup != null -> viewModel.setGroupCover(openPrivateGroup.name, selectedItems.first())
                            }
                            clearSelection()
                        }) { ImageIcon(Palette.textBody) }
                    }
                    if (isInPrivate) {
                        IconButton(onClick = { sheet = AppSheet.SELECTION_GROUP }) { MoveIcon(Palette.textBody) }
                        IconButton(onClick = { sheet = AppSheet.SELECTION_MOVE }) { LockIcon(Palette.textBody, isOpen = true) }
                        IconButton(
                            onClick = {
                                if (isDeleteArmed) {
                                    actions.deletePrivate(selectedItems)
                                    clearSelection()
                                } else {
                                    isDeleteArmed = true
                                }
                            },
                            modifier = if (isDeleteArmed) Modifier.background(Palette.danger.copy(alpha = 0.22f), Shapes.capsule) else Modifier,
                        ) { TrashIcon(Palette.danger) }
                    } else {
                        if (section == Section.FAVORITES) {
                            IconButton(onClick = { sheet = AppSheet.FAVORITE_ALBUM_PICK }) { AlbumsIcon(Palette.textBody) }
                            if (openFavorite != null) {
                                IconButton(onClick = {
                                    val removed = selectedItems.map { it.id }.toSet()
                                    Settings.updateFavoriteAlbums(Settings.favoriteAlbums.map { if (it.name == openFavorite.name) it.copy(ids = it.ids.filter { id -> id !in removed }) else it })
                                    clearSelection()
                                }) { CloseIcon(Palette.textBody) }
                            }
                        }
                        IconButton(onClick = { sheet = AppSheet.SELECTION_MOVE }) { MoveIcon(Palette.textBody) }
                        IconButton(onClick = { sheet = AppSheet.SELECTION_GROUP }) { LockIcon(Palette.textBody) }
                        IconButton(onClick = {
                            actions.trash(selectedItems)
                            clearSelection()
                        }) { TrashIcon(Palette.danger) }
                    }
                  }
                }
            } else {
                SectionBar(
                    active = section,
                    onSelect = { selected ->
                        if (selected == section) {
                            when {
                                selected == Section.ALBUMS -> albumsPlace = AlbumsPlace.Folders
                                selected == Section.FAVORITES && openFavoriteAlbum != null -> openFavoriteAlbum = null
                                else -> scrollToNewestRequest++
                            }
                        }
                        section = selected
                    },
                    modifier = barModifier,
                )
            }

            // The selection's pickers. Moving out of Private goes to an album; moving within it goes to a group.
            AlbumPickerSheet(
                visible = sheet == AppSheet.SELECTION_MOVE,
                label = if (isInPrivate) "MOVE OUT TO" else "MOVE TO",
                albums = albums,
                excludedAlbumId = openAlbum?.id,
                onPick = { album ->
                    if (isInPrivate) actions.unhide(selectedItems, album) else actions.move(selectedItems, album)
                    clearSelection()
                },
                onNewAlbum = { sheet = AppSheet.SELECTION_NEW_ALBUM },
                onDismiss = { sheet = AppSheet.NONE },
            )
            GroupPickerSheet(
                visible = sheet == AppSheet.SELECTION_GROUP,
                label = if (isInPrivate) "MOVE TO GROUP" else "MOVE TO PRIVATE",
                groups = privateContents.groups,
                excludedGroupName = openPrivateGroup?.name,
                onPick = { name ->
                    if (isInPrivate) {
                        actions.moveToGroup(selectedItems, name)
                        clearSelection()
                    } else {
                        pendingPrivate = PendingPrivate(selectedItems, name, isSelection = true)
                        sheet = AppSheet.CONFIRM_PRIVATE
                    }
                },
                onNewGroup = { sheet = AppSheet.SELECTION_NEW_GROUP },
                onDismiss = { sheet = AppSheet.NONE },
            )

            // A long-pressed album: one entry, because moving the whole folder into Private is the thing it is for.
            OverlaySheet(visible = sheet == AppSheet.ALBUM_MENU, label = sheetAlbum?.name?.uppercase().orEmpty(), onDismiss = { sheet = AppSheet.NONE }) {
                SheetRow("Rename", icon = { PenIcon(it) }) { sheet = AppSheet.ALBUM_RENAME }
                SheetRow("Select", icon = { CheckIcon(it) }) {
                    sheetAlbum?.let { selectedCovers = setOf(it.relativePath) }
                    sheet = AppSheet.NONE
                }
                SheetRow("Rearrange albums", icon = { GripIcon(it) }) {
                    isRearranging = true
                    sheet = AppSheet.NONE
                }
                if (Settings.groupedAlbums) {
                    val albumPath = sheetAlbum?.relativePath.orEmpty()
                    val currentStack = Settings.albumStacks.firstOrNull { it.paths.contains(albumPath) }
                    SheetRow(if (currentStack == null) "Add to group" else "Move to group", trailing = currentStack?.name, icon = { MoveIcon(it) }) { sheet = AppSheet.ALBUM_STACK }
                    if (currentStack != null) {
                        SheetRow("Remove from group", icon = { CloseIcon(it) }) {
                            Settings.updateAlbumStacks(Settings.albumStacks.withoutAlbum(albumPath))
                            sheet = AppSheet.NONE
                        }
                    }
                }
                val isHiddenFromRecent = sheetAlbum?.relativePath in Settings.hiddenFromRecent
                SheetRow(if (isHiddenFromRecent) "Show in Recent" else "Hide from Recent", icon = { EyeIcon(it, isCrossed = !isHiddenFromRecent) }) {
                    sheetAlbum?.let { album -> Settings.updateHiddenFromRecent(if (isHiddenFromRecent) Settings.hiddenFromRecent - album.relativePath else Settings.hiddenFromRecent + album.relativePath) }
                    sheet = AppSheet.NONE
                }
                SheetRow("Add photos", icon = { PlusIcon(it) }) {
                    sheetAlbum?.let { picker = PickerTarget.IntoAlbum(it.relativePath, it.name) }
                    sheet = AppSheet.NONE
                }
                SheetRow("Move album to private", trailing = sheetAlbum?.items?.size?.toString(), icon = { LockIcon(it) }) { sheet = AppSheet.ALBUM_GROUP }
                SheetRow(
                    if (isMenuDeleteArmed) "Tap again: ${sheetAlbum?.items?.size ?: 0} photos to the trash" else "Delete album",
                    color = Palette.danger,
                    icon = { TrashIcon(it) },
                ) {
                    if (isMenuDeleteArmed) {
                        sheetAlbum?.let { actions.trash(it.items) }
                        sheet = AppSheet.NONE
                    } else {
                        isMenuDeleteArmed = true
                    }
                }
            }

            // A long-pressed album group. Ungrouping only lays its albums back into the grid; no photo is touched.
            OverlaySheet(visible = sheet == AppSheet.STACK_MENU, label = sheetStack?.uppercase().orEmpty(), onDismiss = { sheet = AppSheet.NONE }) {
                SheetRow("Rename", icon = { PenIcon(it) }) { sheet = AppSheet.STACK_RENAME }
                SheetRow("Select", icon = { CheckIcon(it) }) {
                    selectedCovers = Settings.albumStacks.firstOrNull { it.name == sheetStack }?.paths.orEmpty().toSet()
                    sheet = AppSheet.NONE
                }
                SheetRow("Rearrange albums", icon = { GripIcon(it) }) {
                    isRearranging = true
                    sheet = AppSheet.NONE
                }
                val stackPaths = Settings.albumStacks.firstOrNull { it.name == sheetStack }?.paths.orEmpty()
                val isStackHidden = stackPaths.isNotEmpty() && stackPaths.all { it in Settings.hiddenFromRecent }
                SheetRow(if (isStackHidden) "Show in Recent" else "Hide from Recent", icon = { EyeIcon(it, isCrossed = !isStackHidden) }) {
                    Settings.updateHiddenFromRecent(if (isStackHidden) Settings.hiddenFromRecent - stackPaths.toSet() else Settings.hiddenFromRecent + stackPaths)
                    sheet = AppSheet.NONE
                }
                SheetRow("Ungroup", trailing = Settings.albumStacks.firstOrNull { it.name == sheetStack }?.paths?.size?.toString(), icon = { CloseIcon(it) }) {
                    Settings.updateAlbumStacks(Settings.albumStacks.filter { it.name != sheetStack })
                    sheet = AppSheet.NONE
                }
            }
            StackPickerSheet(
                visible = sheet == AppSheet.ALBUM_STACK,
                label = "MOVE $targetAlbumsLabel TO",
                stacks = Settings.albumStacks.filter { stack -> !targetAlbums.all { stack.paths.contains(it.relativePath) } },
                onPick = { name ->
                    Settings.updateAlbumStacks(targetAlbums.fold(Settings.albumStacks) { stacks, album -> stacks.withAlbum(album.relativePath, name) })
                    if (isSelectingCovers) clearSelection() else sheet = AppSheet.NONE
                },
                onNewStack = { sheet = AppSheet.ALBUM_NEW_STACK },
                onDismiss = { sheet = AppSheet.NONE },
            )

            // Favorites albums only gather favourites; nothing here moves or changes a photo.
            NamePickerSheet(
                visible = sheet == AppSheet.FAVORITE_ALBUM_PICK,
                label = "ADD TO ALBUM",
                choices = favoriteAlbumViews.filter { it.name != openFavorite?.name }.map { it.name to it.items.size },
                newLabel = "+ New album",
                onPick = { name ->
                    Settings.updateFavoriteAlbums(Settings.favoriteAlbums.withPhotos(name, selectedItems.map { it.id }))
                    clearSelection()
                },
                onNew = { sheet = AppSheet.FAVORITE_SELECTION_NEW_ALBUM },
                onDismiss = { sheet = AppSheet.NONE },
            )
            OverlaySheet(visible = sheet == AppSheet.FAVORITE_ALBUM_MENU, label = sheetFavoriteAlbum?.uppercase().orEmpty(), onDismiss = { sheet = AppSheet.NONE }) {
                val albumName = sheetFavoriteAlbum.orEmpty()
                SheetRow("Rename", icon = { PenIcon(it) }) { sheet = AppSheet.FAVORITE_ALBUM_RENAME }
                SheetRow("Rearrange albums", icon = { GripIcon(it) }) {
                    isRearranging = true
                    sheet = AppSheet.NONE
                }
                val currentStack = Settings.favoriteStacks.firstOrNull { it.paths.contains(albumName) }
                SheetRow(if (currentStack == null) "Add to group" else "Move to group", trailing = currentStack?.name, icon = { MoveIcon(it) }) { sheet = AppSheet.FAVORITE_ALBUM_STACK }
                if (currentStack != null) {
                    SheetRow("Remove from group", icon = { CloseIcon(it) }) {
                        Settings.updateFavoriteStacks(Settings.favoriteStacks.withoutAlbum(albumName))
                        sheet = AppSheet.NONE
                    }
                }
                SheetRow("Add photos", icon = { PlusIcon(it) }) {
                    picker = PickerTarget.IntoFavoriteAlbum(albumName)
                    sheet = AppSheet.NONE
                }
                // The photos stay favourites; only the album goes, but it cannot be brought back, so it takes a second tap.
                SheetRow(if (isMenuDeleteArmed) "Tap again: delete album" else "Delete album", color = Palette.danger, icon = { TrashIcon(it) }) {
                    if (isMenuDeleteArmed) {
                        Settings.updateFavoriteAlbums(Settings.favoriteAlbums.filter { it.name != albumName })
                        sheet = AppSheet.NONE
                    } else {
                        isMenuDeleteArmed = true
                    }
                }
            }
            OverlaySheet(visible = sheet == AppSheet.FAVORITE_STACK_MENU, label = sheetStack?.uppercase().orEmpty(), onDismiss = { sheet = AppSheet.NONE }) {
                SheetRow("Rename", icon = { PenIcon(it) }) { sheet = AppSheet.FAVORITE_STACK_RENAME }
                SheetRow("Rearrange albums", icon = { GripIcon(it) }) {
                    isRearranging = true
                    sheet = AppSheet.NONE
                }
                SheetRow("Ungroup", trailing = Settings.favoriteStacks.firstOrNull { it.name == sheetStack }?.paths?.size?.toString(), icon = { CloseIcon(it) }) {
                    Settings.updateFavoriteStacks(Settings.favoriteStacks.filter { it.name != sheetStack })
                    sheet = AppSheet.NONE
                }
            }
            StackPickerSheet(
                visible = sheet == AppSheet.FAVORITE_ALBUM_STACK,
                label = "MOVE ${sheetFavoriteAlbum?.uppercase().orEmpty()} TO",
                stacks = Settings.favoriteStacks.filter { !it.paths.contains(sheetFavoriteAlbum) },
                onPick = { name ->
                    sheetFavoriteAlbum?.let { Settings.updateFavoriteStacks(Settings.favoriteStacks.withAlbum(it, name)) }
                    sheet = AppSheet.NONE
                },
                onNewStack = { sheet = AppSheet.FAVORITE_ALBUM_NEW_STACK },
                onDismiss = { sheet = AppSheet.NONE },
            )

            SettingsSheet(
                visible = sheet == AppSheet.SETTINGS,
                onDismiss = { sheet = AppSheet.NONE },
                onColumnsChanged = { columns ->
                    (listOf(recentMemory, favoritesMemory, privateFavoritesMemory) + albumMemories.values + privateMemories.values + favoriteAlbumMemories.values).forEach { it.columns = columns }
                },
            )

            // A long-pressed private group. Deleting one is final — private photos are outside the system trash — so it takes a second tap.
            OverlaySheet(visible = sheet == AppSheet.GROUP_MENU, label = sheetGroup?.name?.uppercase().orEmpty(), onDismiss = { sheet = AppSheet.NONE }) {
                SheetRow("Rename", icon = { PenIcon(it) }) { sheet = AppSheet.GROUP_RENAME }
                SheetRow("Select", icon = { CheckIcon(it) }) {
                    sheetGroup?.let { selectedCovers = setOf(it.name) }
                    sheet = AppSheet.NONE
                }
                SheetRow("Rearrange groups", icon = { GripIcon(it) }) {
                    isRearranging = true
                    sheet = AppSheet.NONE
                }
                SheetRow("Add photos", icon = { PlusIcon(it) }) {
                    sheetGroup?.let { picker = PickerTarget.IntoGroup(it.name) }
                    sheet = AppSheet.NONE
                }
                SheetRow("Move group out to album", trailing = sheetGroup?.items?.size?.toString(), icon = { MoveIcon(it) }) { sheet = AppSheet.GROUP_MOVE_OUT }
                SheetRow(
                    if (isMenuDeleteArmed) "Tap again: delete ${sheetGroup?.items?.size ?: 0} photos forever" else "Delete group",
                    color = Palette.danger,
                    icon = { TrashIcon(it) },
                ) {
                    if (isMenuDeleteArmed) {
                        sheetGroup?.let { actions.deleteGroup(it) }
                        sheet = AppSheet.NONE
                    } else {
                        isMenuDeleteArmed = true
                    }
                }
            }
            AlbumPickerSheet(
                visible = sheet == AppSheet.GROUP_MOVE_OUT,
                label = "MOVE $targetGroupsLabel OUT TO",
                albums = albums,
                excludedAlbumId = null,
                onPick = { album ->
                    targetGroups.forEach { group -> actions.unhide(group.items, album) { viewModel.vault.removeGroupIfEmpty(group.name) } }
                    if (isSelectingCovers) clearSelection() else sheet = AppSheet.NONE
                },
                onNewAlbum = { sheet = AppSheet.GROUP_MOVE_OUT_NEW_ALBUM },
                onDismiss = { sheet = AppSheet.NONE },
            )
            GroupPickerSheet(
                visible = sheet == AppSheet.ALBUM_GROUP,
                label = "MOVE $targetAlbumsLabel TO",
                groups = privateContents.groups,
                excludedGroupName = null,
                onPick = { name ->
                    pendingPrivate = PendingPrivate(targetAlbums.flatMap { it.items }, name, isSelection = isSelectingCovers)
                    sheet = AppSheet.CONFIRM_PRIVATE
                },
                onNewGroup = { sheet = AppSheet.ALBUM_NEW_GROUP },
                onDismiss = { sheet = AppSheet.NONE },
            )

            OverlaySheet(visible = lastCrash != null, label = "LAST CRASH", onDismiss = {
                CrashLog.clear(context)
                lastCrash = null
            }) {
                BasicText(
                    lastCrash.orEmpty().lines().take(14).joinToString("\n"),
                    style = Type.value.copy(fontSize = 10.sp),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                SheetRow("Share", icon = { ShareIcon(it) }) {
                    val text = lastCrash.orEmpty()
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                    CrashLog.clear(context)
                    lastCrash = null
                }
                SheetRow("Close", color = Palette.textMuted, icon = { CloseIcon(it) }) {
                    CrashLog.clear(context)
                    lastCrash = null
                }
            }

            // Going into Private moves files out of every other app's reach, so it is confirmed once more.
            OverlaySheet(
                visible = sheet == AppSheet.CONFIRM_PRIVATE,
                label = "PRIVATE · ${pendingPrivate?.groupName?.uppercase().orEmpty()}",
                onDismiss = { sheet = AppSheet.NONE },
            ) {
                val count = pendingPrivate?.items?.size ?: 0
                SheetRow(if (count == 1) "Move 1 photo to Private" else "Move $count photos to Private", color = accent, icon = { LockIcon(it) }) {
                    pendingPrivate?.let { pending ->
                        actions.hide(pending.items, pending.groupName)
                        if (pending.isSelection) clearSelection()
                    }
                    pendingPrivate = null
                    sheet = AppSheet.NONE
                }
                SheetRow("Cancel", color = Palette.textMuted, icon = { CloseIcon(it) }) {
                    pendingPrivate = null
                    sheet = AppSheet.NONE
                }
            }

            when (sheet) {
                AppSheet.ALBUM_RENAME -> NameSheet(
                    label = "RENAME ALBUM",
                    action = "RENAME",
                    initialName = sheetAlbum?.name.orEmpty(),
                    onConfirm = { name ->
                        sheetAlbum?.let { if (name != it.name) actions.renameAlbum(it, name) }
                        sheet = AppSheet.NONE
                    },
                    onDismiss = { sheet = AppSheet.NONE },
                )
                AppSheet.GROUP_RENAME -> NameSheet(
                    label = "RENAME GROUP",
                    action = "RENAME",
                    initialName = sheetGroup?.name.orEmpty(),
                    onConfirm = { name ->
                        sheetGroup?.let { if (name != it.name) actions.renameGroup(it, name) }
                        sheet = AppSheet.NONE
                    },
                    onDismiss = { sheet = AppSheet.NONE },
                )
                AppSheet.STACK_RENAME -> NameSheet(
                    label = "RENAME GROUP",
                    action = "RENAME",
                    initialName = sheetStack.orEmpty(),
                    onConfirm = { name ->
                        sheetStack?.let { Settings.updateAlbumStacks(Settings.albumStacks.renamed(it, name)) }
                        sheet = AppSheet.NONE
                    },
                    onDismiss = { sheet = AppSheet.NONE },
                )
                AppSheet.ALBUM_NEW_STACK -> NameSheet(
                    label = "NEW GROUP",
                    action = "CREATE",
                    onConfirm = { name ->
                        Settings.updateAlbumStacks(targetAlbums.fold(Settings.albumStacks) { stacks, album -> stacks.withAlbum(album.relativePath, name) })
                        if (isSelectingCovers) clearSelection() else sheet = AppSheet.NONE
                    },
                    onDismiss = { sheet = AppSheet.NONE },
                )
                AppSheet.FAVORITE_NEW_ALBUM -> NameSheet(
                    label = "NEW ALBUM",
                    action = "CHOOSE PHOTOS",
                    onConfirm = { name ->
                        sheet = AppSheet.NONE
                        picker = PickerTarget.IntoFavoriteAlbum(AlbumStack.cleanName(name))
                    },
                    onDismiss = { sheet = AppSheet.NONE },
                )
                AppSheet.FAVORITE_SELECTION_NEW_ALBUM -> NameSheet(
                    label = "NEW ALBUM",
                    action = "ADD HERE",
                    onConfirm = { name ->
                        Settings.updateFavoriteAlbums(Settings.favoriteAlbums.withPhotos(name, selectedItems.map { it.id }))
                        clearSelection()
                    },
                    onDismiss = { sheet = AppSheet.NONE },
                )
                AppSheet.FAVORITE_ALBUM_RENAME -> NameSheet(
                    label = "RENAME ALBUM",
                    action = "RENAME",
                    initialName = sheetFavoriteAlbum.orEmpty(),
                    onConfirm = { name ->
                        sheetFavoriteAlbum?.let { old ->
                            val new = AlbumStack.cleanName(name)
                            if (new.isNotEmpty() && new != old) {
                                Settings.updateFavoriteAlbums(Settings.favoriteAlbums.renamedAlbum(old, new))
                                // The album's place in its group and in the order follow the new name.
                                Settings.updateFavoriteStacks(Settings.favoriteStacks.map { stack -> stack.copy(paths = stack.paths.map { if (it == old) new else it }.distinct()) })
                                Settings.updateFavoriteAlbumOrder(Settings.favoriteAlbumOrder.map { if (it == old) new else it }.distinct())
                                if (openFavoriteAlbum == old) openFavoriteAlbum = new
                            }
                        }
                        sheet = AppSheet.NONE
                    },
                    onDismiss = { sheet = AppSheet.NONE },
                )
                AppSheet.FAVORITE_STACK_RENAME -> NameSheet(
                    label = "RENAME GROUP",
                    action = "RENAME",
                    initialName = sheetStack.orEmpty(),
                    onConfirm = { name ->
                        sheetStack?.let { Settings.updateFavoriteStacks(Settings.favoriteStacks.renamed(it, name)) }
                        sheet = AppSheet.NONE
                    },
                    onDismiss = { sheet = AppSheet.NONE },
                )
                AppSheet.FAVORITE_ALBUM_NEW_STACK -> NameSheet(
                    label = "NEW GROUP",
                    action = "CREATE",
                    onConfirm = { name ->
                        sheetFavoriteAlbum?.let { Settings.updateFavoriteStacks(Settings.favoriteStacks.withAlbum(it, name)) }
                        sheet = AppSheet.NONE
                    },
                    onDismiss = { sheet = AppSheet.NONE },
                )
                AppSheet.NEW_ALBUM -> NameSheet(
                    label = "NEW ALBUM",
                    action = "CHOOSE PHOTOS",
                    onConfirm = { name ->
                        sheet = AppSheet.NONE
                        picker = PickerTarget.IntoAlbum(newAlbumPath(name), name)
                    },
                    onDismiss = { sheet = AppSheet.NONE },
                )
                AppSheet.SELECTION_NEW_ALBUM -> NameSheet(
                    label = "NEW ALBUM",
                    action = "MOVE HERE",
                    onConfirm = { name ->
                        if (isInPrivate) actions.unhide(selectedItems, newAlbumPath(name), name) else actions.move(selectedItems, newAlbumPath(name), name)
                        clearSelection()
                    },
                    onDismiss = { sheet = AppSheet.NONE },
                )
                AppSheet.GROUP_MOVE_OUT_NEW_ALBUM -> NameSheet(
                    label = "NEW ALBUM",
                    action = "MOVE HERE",
                    initialName = targetGroups.singleOrNull()?.name.orEmpty(),
                    onConfirm = { name ->
                        targetGroups.forEach { group -> actions.unhide(group.items, newAlbumPath(name), name) { viewModel.vault.removeGroupIfEmpty(group.name) } }
                        if (isSelectingCovers) clearSelection() else sheet = AppSheet.NONE
                    },
                    onDismiss = { sheet = AppSheet.NONE },
                )
                AppSheet.SELECTION_NEW_GROUP -> NameSheet(
                    label = "NEW PRIVATE GROUP",
                    action = "MOVE HERE",
                    onConfirm = { name ->
                        if (isInPrivate) {
                            actions.moveToGroup(selectedItems, name)
                            clearSelection()
                        } else {
                            pendingPrivate = PendingPrivate(selectedItems, name, isSelection = true)
                            sheet = AppSheet.CONFIRM_PRIVATE
                        }
                    },
                    onDismiss = { sheet = AppSheet.NONE },
                )
                AppSheet.ALBUM_NEW_GROUP -> NameSheet(
                    label = "NEW PRIVATE GROUP",
                    action = "MOVE HERE",
                    initialName = targetAlbums.singleOrNull()?.name.orEmpty(),
                    onConfirm = { name ->
                        pendingPrivate = PendingPrivate(targetAlbums.flatMap { it.items }, name, isSelection = isSelectingCovers)
                        sheet = AppSheet.CONFIRM_PRIVATE
                    },
                    onDismiss = { sheet = AppSheet.NONE },
                )
                AppSheet.PRIVATE_NEW_GROUP -> NameSheet(
                    label = "NEW PRIVATE GROUP",
                    action = "CREATE",
                    onConfirm = { name ->
                        sheet = AppSheet.NONE
                        scope.launch {
                            viewModel.vault.createGroup(name)
                            viewModel.refreshPrivate()
                        }
                    },
                    onDismiss = { sheet = AppSheet.NONE },
                )
                else -> Unit
            }

            picker?.let { target ->
                val alreadyThere = when (target) {
                    is PickerTarget.IntoAlbum -> albums.firstOrNull { it.relativePath == target.relativePath }?.items?.map { it.id }?.toSet().orEmpty()
                    // A favourite already in any Favorites album is not offered again.
                    is PickerTarget.IntoFavoriteAlbum -> Settings.favoriteAlbums.flatMap { it.ids }.toSet()
                    is PickerTarget.IntoGroup -> emptySet()
                }
                PickerScreen(
                    title = target.title,
                    // A Favorites album gathers favourites, so only they are offered.
                    items = (if (target is PickerTarget.IntoFavoriteAlbum) favorites else library).filter { it.id !in alreadyThere },
                    action = "Add",
                    onDone = { picked ->
                        when (target) {
                            is PickerTarget.IntoAlbum -> actions.move(picked, target.relativePath, target.name)
                            is PickerTarget.IntoGroup -> actions.hide(picked, target.name)
                            is PickerTarget.IntoFavoriteAlbum -> Settings.updateFavoriteAlbums(Settings.favoriteAlbums.withPhotos(target.name, picked.map { it.id }))
                        }
                        picker = null
                    },
                    onCancel = { picker = null },
                )
            }

            // Review rises into view from slightly below and settles, and sinks back out on closing, rather than appearing at once.
            AnimatedContent(
                targetState = review,
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
                        items = itemsFor(source),
                        isPrivate = source.isPrivateSource(),
                        progressKey = source.reviewKey(),
                        onDelete = { picked -> if (source.isPrivateSource()) actions.deletePrivate(picked) else actions.trash(picked) },
                        onClose = { review = null },
                    )
                }
            }

            // The viewer grows out of the tile it was opened from and shrinks back into the tile of the photo it ends on; when that tile is not on screen it falls back to a quiet fade.
            shownViewer?.let { request ->
                // Frame by frame the photo's own box (fitted to the screen, no black around it) is cropped down to the tile's square and moved onto it, so the animation shows what the grid shows. The chrome around the photo returns in the last stretch.
                ViewerScreen(
                    // Progress is read inside the draw lambdas, so the animation never recomposes the library.
                    photoModifier = Modifier
                        .drawWithContent {
                            val p = viewerProgress.value * (1f - viewerPull)
                            val tile = viewerRect
                            if (tile == null || p >= 1f) {
                                drawContent()
                            } else {
                                val photo = fitInside(viewerRatio, size.width, size.height)
                                val frame = lerp(tile, photo, p)
                                val full = Rect(0f, 0f, size.width, size.height)
                                val expand = ((p - 0.7f) / 0.3f).coerceIn(0f, 1f)
                                val radius = (lerp(8.dp.toPx(), 24.dp.toPx(), p)) * (1f - expand)
                                val clip = Path().apply { addRoundRect(RoundRect(lerp(frame, full, expand), CornerRadius(radius))) }
                                clipPath(clip) { this@drawWithContent.drawContent() }
                            }
                        }
                        .graphicsLayer {
                            val p = viewerProgress.value * (1f - viewerPull)
                            val tile = viewerRect
                            if (tile == null) {
                                alpha = p
                                scaleX = 0.94f + 0.06f * p
                                scaleY = scaleX
                            } else if (p < 1f) {
                                val photo = fitInside(viewerRatio, size.width, size.height)
                                val frame = lerp(tile, photo, p)
                                val coverScale = maxOf(tile.width / photo.width, tile.height / photo.height)
                                val scale = coverScale + (1f - coverScale) * p
                                val centre = Offset(size.width / 2f, size.height / 2f)
                                scaleX = scale
                                scaleY = scale
                                // Moves the photo's centre onto the frame's centre; at p = 1 the frame is the photo and this is zero.
                                translationX = frame.center.x - centre.x - (photo.center.x - centre.x) * scale
                                translationY = frame.center.y - centre.y - (photo.center.y - centre.y) * scale
                            }
                        },
                    isChromeAllowed = isViewerSettled,
                    items = itemsFor(request.source),
                    startIndex = request.startIndex,
                    albums = albums,
                    privateGroups = privateContents.groups,
                    isPrivate = request.source.isPrivateSource(),
                    isTrash = request.source == ViewerSource.Trash,
                    actions = actions,
                    onClose = { viewer = null },
                    onCurrentChanged = { viewerCurrentId = it },
                    onPhotoRatio = { id, ratio -> photoRatios[id] = ratio },
                    onPull = { fraction ->
                        val currentId = viewerCurrentId
                        viewerRect = TileBounds.of(currentId)
                        viewerRatio = photoRatios[currentId] ?: viewerRatio
                        viewerPull = fraction
                        // The tile is brought on screen as soon as the pull starts, so the shrink aims at it from the first frame instead of switching target on release.
                        if (fraction > 0f && viewerRect == null && currentId != null && revealingId != currentId) {
                            revealingId = currentId
                            scope.launch { folderMemory?.revealItem(gridItems, currentId) }
                        }
                    },
                    places = places,
                )
            }

            // Above the viewer too, since a photo can be deleted or moved from there.
            UndoPill(
                offer = actions.undoOffer,
                onUndo = actions::undo,
                onExpired = actions::expireUndo,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = BAR_ROOM),
            )
        }
    }
}

private data class PendingPrivate(val items: List<MediaItem>, val groupName: String, val isSelection: Boolean)

// What a folder's review progress is saved under; stable across launches, unlike the source object itself.
private fun ViewerSource.reviewKey(): String = when (this) {
    is ViewerSource.InAlbum -> "album:$albumId"
    is ViewerSource.InPrivateGroup -> "group:$name"
    is ViewerSource.InLocation -> "location:$key"
    ViewerSource.PrivateFavorites -> "private-favorites"
    ViewerSource.Recent -> "recent"
    ViewerSource.Favorites -> "favorites"
    ViewerSource.Trash -> "trash"
    is ViewerSource.InFavoriteAlbum -> "favorite-album:$name"
}

private fun ViewerSource?.isPrivateSource(): Boolean = this is ViewerSource.InPrivateGroup || this is ViewerSource.PrivateFavorites

// The top layer: the month you are looking at, or — while selecting — the count and the way out of the selection. Leaving a folder is the system back gesture.
// The month chip has a fixed width, so a month with a longer name never shifts or resizes the buttons.
@Composable
private fun TopRow(month: String, selectedCount: Int, onReview: (() -> Unit)?, onAdd: (() -> Unit)?, onCancelSelection: () -> Unit, onSettings: () -> Unit, onToggleView: (() -> Unit)? = null, isAlbumsView: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selectedCount > 0) {
            TopButton(onCancelSelection) { CloseIcon(LocalAccent.current) }
            Box(Modifier.weight(1f))
            Chip("$selectedCount selected")
        } else {
            // The month sits at the left end; the buttons gather at the right.
            if (month.isNotEmpty()) Chip(month, Modifier.width(MONTH_CHIP_WIDTH))
            Box(Modifier.weight(1f))
            if (onReview != null) TopButton(onReview) { ReviewIcon(LocalAccent.current) }
            if (onAdd != null) TopButton(onAdd) { PlusIcon(LocalAccent.current) }
            // The icon shows where a tap goes: the grid of every favourite, or the albums.
            if (onToggleView != null) TopButton(onToggleView) { if (isAlbumsView) GridIcon(LocalAccent.current) else AlbumsIcon(LocalAccent.current) }
            TopButton(onSettings) { SettingsIcon(Palette.textBright) }
        }
    }
}

@Composable
private fun TopButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(Modifier.pressable(onClick = onClick).glass(Shapes.capsule).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) { icon() }
}

@Composable
private fun Chip(text: String, modifier: Modifier = Modifier) {
    Box(modifier.glass(Shapes.capsule).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        // One line always: a long month must not grow the pill into two.
        BasicText(text.uppercase(), style = Type.microLabel, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}
