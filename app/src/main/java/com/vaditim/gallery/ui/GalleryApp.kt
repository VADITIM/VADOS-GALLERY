package com.vaditim.gallery.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay

import androidx.compose.ui.text.style.TextOverflow

import com.vaditim.gallery.CrashLog
import androidx.compose.ui.unit.sp
import android.content.Intent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.vaditim.gallery.AlbumStack
import com.vaditim.gallery.Settings
import androidx.compose.runtime.SideEffect
import com.vaditim.gallery.SettingsView
import com.vaditim.gallery.renamedAlbum
import com.vaditim.gallery.withPhotos
import com.vaditim.gallery.renamed
import com.vaditim.gallery.withAlbum
import com.vaditim.gallery.withoutAlbum
import com.vaditim.gallery.hiddenFavoriteKey
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.layout.RowScope
import androidx.compose.animation.AnimatedVisibility
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
    data object PrivateRecent : ViewerSource
    data class InPrivateFavoriteGroup(val name: String) : ViewerSource
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
    data object Locations : AlbumsPlace
    data class Location(val key: String) : AlbumsPlace
    data object Trash : AlbumsPlace
}

private val AlbumsPlace.isPrivate: Boolean
    get() = this is AlbumsPlace.PrivateGroups || this is AlbumsPlace.PrivateFolder

// The sheets the app itself opens — for a selection or for a long-pressed album. The viewer has its own.
private enum class AppSheet {
    NONE,
    NEW_ALBUM,
    SETTINGS, TRASH_RESTORE, ALBUM_RENAME, GROUP_RENAME, CONFIRM_PRIVATE, SELECTION_MOVE, SELECTION_NEW_ALBUM, SELECTION_GROUP, SELECTION_NEW_GROUP,
    ALBUM_MENU, ALBUM_GROUP, ALBUM_NEW_GROUP,
    GROUP_MENU, GROUP_MOVE_OUT, GROUP_MOVE_OUT_NEW_ALBUM,
    PRIVATE_NEW_GROUP,
    STACK_MENU, STACK_RENAME, ALBUM_STACK, ALBUM_NEW_STACK,
    FAVORITE_NEW_ALBUM, FAVORITE_ALBUM_PICK, FAVORITE_SELECTION_NEW_ALBUM, FAVORITE_ALBUM_MENU, FAVORITE_ALBUM_RENAME,
    FAVORITE_STACK_MENU, FAVORITE_STACK_RENAME, FAVORITE_ALBUM_STACK, FAVORITE_ALBUM_NEW_STACK,
}

// Locations and the trash carry their own colour, in their views and on their buttons; every other place takes its section's.
private fun placeAccentOf(place: AlbumsPlace): Color? = when (place) {
    AlbumsPlace.Locations, is AlbumsPlace.Location -> Palette.locationBlue
    AlbumsPlace.Trash -> Palette.trashGray
    else -> null
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
    val folderAlbums by viewModel.albums.collectAsStateWithLifecycle()
    // Renamed albums show the name given to them; the folder underneath keeps its own.
    val albums = remember(folderAlbums, Settings.albumNames) {
        val names = Settings.albumNames
        if (names.isEmpty()) folderAlbums else folderAlbums.map { album -> names[album.relativePath]?.let { album.copy(name = it) } ?: album }
    }
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val privateContents by viewModel.privateContents.collectAsStateWithLifecycle()
    val isPrivateUnlocked by viewModel.isPrivateUnlocked.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val trash by viewModel.trash.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()
    // GPS in photos is stripped by the system unless this is granted; it is asked once, the first time the library shows.
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshLocationPermission() }
    LaunchedEffect(Unit) { locationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) }
    // Recent without the folders kept out of it; everything else (albums, the picker, locations) still sees the whole library.
    val recent = remember(library, Settings.hiddenFromRecent, Settings.favoriteAlbums) {
        val hidden = Settings.hiddenFromRecent
        val hiddenIds = Settings.favoriteAlbums.filter { hiddenFavoriteKey(it.name) in hidden }.flatMap { it.ids }.toSet()
        if (hidden.isEmpty()) library else library.filter { it.relativePath !in hidden && it.id !in hiddenIds }
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
    // A delete that waits for the Confirm above the bar; anything else the user does lets it go.
    // The nav bar's own height, so what sits above it (the count in the corner) clears it.
    var navigationHeight by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<(() -> Unit)?>(null) }
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

    val recentMemory = remember { GridMemory(SettingsView.RECENT) }
    val favoritesMemory = remember { GridMemory(SettingsView.FAVORITES) }
    val privateFavoritesMemory = remember { GridMemory(SettingsView.PRIVATE) }
    val privateRecentMemory = remember { GridMemory(SettingsView.PRIVATE) }
    val privateFavoriteGroupMemories = remember { mutableMapOf<String, GridMemory>() }
    val privateFavoriteGroupsListState = rememberLazyGridState()
    // Inside Private the bar's sections show Private's own photos: Recent all of them, Favorites its favourites, Albums its groups. Entered from Albums, left by backing out of the groups or by locking.
    var isPrivateMode by remember { mutableStateOf(false) }
    // The private group opened from Private's Favorites, as a grid of only its favourites.
    var openPrivateFavoriteGroup by remember { mutableStateOf<String?>(null) }
    // The trash keeps every shot on its own, since each one there is about to go.
    val trashMemory = remember { GridMemory(SettingsView.TRASH, isStacking = false) }
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
    val favoriteAlbumViews = remember(favorites, Settings.favoriteAlbums, Settings.favoriteAlbumOrder, covers) {
        val order = Settings.favoriteAlbumOrder
        Settings.favoriteAlbums.mapNotNull { album ->
            val ids = album.ids.toSet()
            val items = favorites.filter { it.id in ids }
            val id = album.name.hashCode().toLong()
            if (items.isEmpty()) null else Album(id, album.name, album.name, items, covers[id])
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

    // Private has a colour of its own across every section.
    val accentTarget = if (isPrivateMode) Palette.privateRed else (if (section == Section.ALBUMS) placeAccentOf(albumsPlace) else null) ?: section.accent
    // The buttons change colour on the cut, once the outgoing view has left, never while it is still on screen.
    var accent by remember { mutableStateOf(accentTarget) }
    LaunchedEffect(accentTarget) {
        delay(Motion.SECTION_LEAVE_MS.toLong())
        accent = accentTarget
    }
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val insetPadding = PaddingValues(
        top = statusBarHeight + HEADER_ROOM,
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BAR_ROOM,
    )

    val place = if (section == Section.ALBUMS) albumsPlace else null
    val openAlbum = (place as? AlbumsPlace.Folder)?.let { folder -> albums.firstOrNull { it.id == folder.albumId } }
    val openPrivateGroup = (place as? AlbumsPlace.PrivateFolder)?.let { folder -> privateContents.groups.firstOrNull { it.name == folder.name } }
    val isInPrivate = isPrivateMode
    // The view whose own settings apply and which the settings sheet names.
    val settingsView = when {
        isPrivateMode -> SettingsView.PRIVATE
        section == Section.RECENT -> SettingsView.RECENT
        section == Section.FAVORITES -> SettingsView.FAVORITES
        place == AlbumsPlace.Locations || place is AlbumsPlace.Location -> SettingsView.LOCATIONS
        place == AlbumsPlace.Trash -> SettingsView.TRASH
        else -> SettingsView.ALBUMS
    }
    SideEffect { Settings.view = settingsView }
    // Every private photo, oldest first like the library, and the favourites gathered by the group they are in.
    val privateRecent = remember(privateContents) { privateContents.groups.flatMap { it.items }.sortedWith(compareBy<MediaItem> { it.timestampMillis }.thenBy { it.id }) }
    val privateFavoriteGroups = remember(privateContents, arrangedGroups) {
        val favoriteIds = privateContents.favorites.map { it.id }.toSet()
        arrangedGroups.mapNotNull { group -> group.items.filter { it.id in favoriteIds }.takeIf { it.isNotEmpty() }?.let { group.copy(items = it) } }
    }
    val openPrivateFavorite = openPrivateFavoriteGroup?.let { name -> privateFavoriteGroups.firstOrNull { it.name == name } }
    LaunchedEffect(openPrivateFavorite == null) { if (openPrivateFavorite == null) openPrivateFavoriteGroup = null }
    val isPrivateFavoritesGrouped = Settings.privateFavoritesAsGroups && openPrivateFavorite == null
    val openLocation = (place as? AlbumsPlace.Location)?.let { shown -> locations.firstOrNull { it.key == shown.key } }
    // A Favorites album's path is its name, so both cover grids pick by path.
    val selectedAlbums = when {
        place == AlbumsPlace.Folders -> arrangedAlbums.filter { it.relativePath in selectedCovers }
        section == Section.FAVORITES && favoritesView == FavoritesView.Albums -> favoriteAlbumViews.filter { it.relativePath in selectedCovers }
        else -> emptyList()
    }
    val selectedGroups = if (place == AlbumsPlace.PrivateGroups) arrangedGroups.filter { it.name in selectedCovers } else emptyList()
    val isSelectingCovers = selectedAlbums.isNotEmpty() || selectedGroups.isNotEmpty()
    // What an album or group sheet acts on: the picked covers while picking, else the one long-pressed.
    val targetAlbums = selectedAlbums.ifEmpty { listOfNotNull(sheetAlbum) }
    val targetGroups = selectedGroups.ifEmpty { listOfNotNull(sheetGroup) }
    val targetAlbumsLabel = targetAlbums.singleOrNull()?.name?.uppercase() ?: "${targetAlbums.size} ALBUMS"
    val targetGroupsLabel = targetGroups.singleOrNull()?.name?.uppercase() ?: "${targetGroups.size} GROUPS"

    // The items of the grid on screen, which is what a selection is made of.
    val gridItems: List<MediaItem> = when {
        isPrivateMode && section == Section.RECENT -> privateRecent
        isPrivateMode && section == Section.FAVORITES -> when {
            openPrivateFavorite != null -> openPrivateFavorite.items
            isPrivateFavoritesGrouped -> emptyList()
            else -> privateContents.favorites
        }
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
        else -> emptyList()
    }
    val selectedItems = gridItems.filter { it.id in selectedIds }
    val isSelecting = selectedItems.isNotEmpty()
    val selection = Selection(selectedIds) { item ->
        selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
        Haptics.tick(context)
        pendingDelete = null
    }
    val clearSelection = {
        selectedIds = emptySet()
        selectedCovers = emptySet()
        pendingDelete = null
        sheet = AppSheet.NONE
    }
    LaunchedEffect(section, albumsPlace, favoritesView, isPrivateMode, openPrivateFavoriteGroup, isPrivateFavoritesGrouped) {
        clearSelection()
        isRearranging = false
    }
    // Inside Private, back from its Recent or Favorites goes to its groups, and from a group opened in its Favorites back to those; backing out of the groups leaves Private.
    BackHandler(enabled = isPrivateMode && section != Section.ALBUMS) {
        if (section == Section.FAVORITES && openPrivateFavoriteGroup != null) {
            openPrivateFavoriteGroup = null
        } else {
            section = Section.ALBUMS
            albumsPlace = AlbumsPlace.PrivateGroups
        }
    }
    BackHandler(enabled = isRearranging) { isRearranging = false }
    BackHandler(enabled = isSelecting || isSelectingCovers) { clearSelection() }
    val toggleCovers: (List<String>) -> Unit = { keys ->
        selectedCovers = if (keys.all { it in selectedCovers }) selectedCovers - keys.toSet() else selectedCovers + keys
        Haptics.tick(context)
        pendingDelete = null
    }

    // Leaving the app locks Private again, and drops anyone standing in it back to the albums list.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.lockPrivate() }
    LaunchedEffect(isPrivateUnlocked) {
        if (!isPrivateUnlocked) {
            isPrivateMode = false
            openPrivateFavoriteGroup = null
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
            isPrivateMode = true
            albumsPlace = AlbumsPlace.PrivateGroups
        } else {
            PrivateLock.unlock(context) {
                viewModel.unlockPrivate()
                isPrivateMode = true
                albumsPlace = AlbumsPlace.PrivateGroups
            }
        }
    }
    val leavePrivate = {
        isPrivateMode = false
        openPrivateFavoriteGroup = null
        albumsPlace = AlbumsPlace.Folders
    }

    fun itemsFor(source: ViewerSource): List<MediaItem> = when (source) {
        ViewerSource.Recent -> recent
        ViewerSource.Favorites -> favorites
        is ViewerSource.InAlbum -> albums.firstOrNull { it.id == source.albumId }?.items.orEmpty()
        is ViewerSource.InPrivateGroup -> if (isPrivateUnlocked) privateContents.groups.firstOrNull { it.name == source.name }?.items.orEmpty() else emptyList()
        ViewerSource.PrivateFavorites -> if (isPrivateUnlocked) privateContents.favorites else emptyList()
        ViewerSource.PrivateRecent -> if (isPrivateUnlocked) privateRecent else emptyList()
        is ViewerSource.InPrivateFavoriteGroup -> if (isPrivateUnlocked) privateFavoriteGroups.firstOrNull { it.name == source.name }?.items.orEmpty() else emptyList()
        is ViewerSource.InLocation -> locations.firstOrNull { it.key == source.key }?.items.orEmpty()
        is ViewerSource.InFavoriteAlbum -> favoriteAlbumViews.firstOrNull { it.name == source.name }?.items.orEmpty()
        ViewerSource.Trash -> trash
    }

    val folderMemory = when {
        isPrivateMode && section == Section.RECENT -> privateRecentMemory
        isPrivateMode && section == Section.FAVORITES -> when {
            openPrivateFavorite != null -> privateFavoriteGroupMemories.getOrPut(openPrivateFavorite.name) { GridMemory(SettingsView.PRIVATE) }
            isPrivateFavoritesGrouped -> null
            else -> privateFavoritesMemory
        }
        openAlbum != null -> albumMemories.getOrPut(openAlbum.id) { GridMemory(SettingsView.ALBUMS) }
        openPrivateGroup != null -> privateMemories.getOrPut(openPrivateGroup.name) { GridMemory(SettingsView.PRIVATE) }
        openLocation != null -> locationMemories.getOrPut(openLocation.key) { GridMemory(SettingsView.LOCATIONS) }
        place is AlbumsPlace.Trash -> trashMemory
        section == Section.RECENT -> recentMemory
        section == Section.FAVORITES -> when {
            openFavorite != null -> favoriteAlbumMemories.getOrPut(openFavorite.name) { GridMemory(SettingsView.FAVORITES) }
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
            // Recent and Favorites inside Private are their own screens, so going in or out of Private changes them as a section change does.
            AnimatedContent(
                targetState = section to (isPrivateMode && section != Section.ALBUMS),
                transitionSpec = {
                    (fadeIn(tween(Motion.SECTION_ENTER_MS, Motion.SECTION_ENTER_DELAY_MS, Motion.powerTwoOut)) +
                        slideInVertically(tween(Motion.SECTION_ENTER_MS, Motion.SECTION_ENTER_DELAY_MS, Motion.backOut)) { it / 40 })
                        .togetherWith(fadeOut(tween(Motion.SECTION_LEAVE_MS, easing = Motion.powerTwoIn)))
                },
                label = "section",
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            ) { (shown, isPrivateShown) ->
                // While this section is the one shown it follows the current view; leaving, it keeps the last one it had.
                var ownView by remember { mutableStateOf(settingsView) }
                if (shown == section && isPrivateShown == (isPrivateMode && section != Section.ALBUMS)) ownView = settingsView
                // Each section keeps its own accent while it leaves, so the outgoing one never takes on the next one's colour.
                CompositionLocalProvider(
                    LocalAccent provides if (isPrivateShown || (shown == Section.ALBUMS && isPrivateMode)) Palette.privateRed else shown.accent,
                    LocalSettingsView provides ownView,
                ) {
                if (isPrivateShown && shown == Section.RECENT) {
                    MediaGrid(
                        items = privateRecent,
                        memory = privateRecentMemory,
                        onOpen = { viewer = ViewerRequest(ViewerSource.PrivateRecent, it) },
                        contentPadding = insetPadding,
                        selection = selection,
                        scrollToNewestRequest = scrollToNewestRequest,
                        emptyCaption = "No photos yet.",
                    )
                } else if (isPrivateShown && shown == Section.FAVORITES) {
                    AnimatedContent(
                        targetState = openPrivateFavoriteGroup ?: if (Settings.privateFavoritesAsGroups) PRIVATE_FAVORITE_GROUPS else PRIVATE_FAVORITE_GRID,
                        transitionSpec = {
                            (fadeIn(tween(Motion.SECTION_ENTER_MS, Motion.SECTION_ENTER_DELAY_MS, Motion.powerTwoOut)) +
                                scaleIn(tween(Motion.SECTION_ENTER_MS, Motion.SECTION_ENTER_DELAY_MS, Motion.powerTwoOut), initialScale = 0.96f))
                                .togetherWith(fadeOut(tween(Motion.SECTION_LEAVE_MS, easing = Motion.powerTwoIn)))
                        },
                        label = "privateFavorites",
                    ) { shownView ->
                        when (shownView) {
                            PRIVATE_FAVORITE_GRID -> MediaGrid(
                                items = privateContents.favorites,
                                memory = privateFavoritesMemory,
                                onOpen = { viewer = ViewerRequest(ViewerSource.PrivateFavorites, it) },
                                contentPadding = insetPadding,
                                selection = selection,
                                scrollToNewestRequest = scrollToNewestRequest,
                                emptyCaption = "Nothing favourited yet.",
                            )
                            PRIVATE_FAVORITE_GROUPS -> PrivateFavoriteGroupsScreen(
                                groups = privateFavoriteGroups,
                                onOpen = { openPrivateFavoriteGroup = it.name },
                                contentPadding = insetPadding,
                                state = privateFavoriteGroupsListState,
                            )
                            else -> privateFavoriteGroups.firstOrNull { it.name == shownView }?.let { group ->
                                MediaGrid(
                                    items = group.items,
                                    memory = privateFavoriteGroupMemories.getOrPut(group.name) { GridMemory(SettingsView.PRIVATE) },
                                    onOpen = { viewer = ViewerRequest(ViewerSource.InPrivateFavoriteGroup(group.name), it) },
                                    contentPadding = insetPadding,
                                    selection = selection,
                                )
                            }
                        }
                    }
                } else when (shown) {
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
                    ) { shownPlace -> CompositionLocalProvider(LocalAccent provides (placeAccentOf(shownPlace) ?: LocalAccent.current)) { when (shownPlace) {
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
                                    FolderDivider()
                                    FolderEntry("Locations", Palette.locationBlue, icon = { PinIcon(it) }, onClick = { albumsPlace = AlbumsPlace.Locations })
                                    FolderEntry("Trash", Palette.trashGray, icon = { TrashIcon(it) }, onClick = { albumsPlace = AlbumsPlace.Trash }, count = trash.size)
                                    PrivateEntry(onClick = openPrivate)
                                }
                            },
                        )
                        is AlbumsPlace.Folder -> albums.firstOrNull { it.id == shownPlace.albumId }?.let { album ->
                            AlbumScreen(
                                album = album,
                                memory = albumMemories.getOrPut(album.id) { GridMemory(SettingsView.ALBUMS) },
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
                                memory = locationMemories.getOrPut(group.key) { GridMemory(SettingsView.LOCATIONS) },
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
                            onOpenSelection = { viewer = ViewerRequest(ViewerSource.PrivateFavorites, it) },
                            onNewGroup = { sheet = AppSheet.PRIVATE_NEW_GROUP },
                            onBack = leavePrivate,
                            contentPadding = insetPadding,
                            state = privateGroupsListState,
                            isViewerOpen = shownViewer != null,
                        )
                        is AlbumsPlace.PrivateFolder -> privateContents.groups.firstOrNull { it.name == shownPlace.name }?.let { group ->
                            PrivateItemsScreen(
                                items = group.items,
                                memory = privateMemories.getOrPut(group.name) { GridMemory(SettingsView.PRIVATE) },
                                onOpen = { viewer = ViewerRequest(ViewerSource.InPrivateGroup(group.name), it) },
                                onBack = { albumsPlace = AlbumsPlace.PrivateGroups },
                                contentPadding = insetPadding,
                                selection = selection,
                            )
                        }
                    } } }
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
                            isAccented = true,
                            stacks = if (Settings.groupedAlbumsIn(SettingsView.FAVORITES)) Settings.favoriteStacks else emptyList(),
                            openStacks = openFavoriteStacks,
                            onOpenStacksChange = { openFavoriteStacks = it },
                            isRearranging = isRearranging,
                            onArrange = { Settings.updateFavoriteAlbumOrder(it) },
                            selectedPaths = selectedCovers,
                            onToggle = { picked -> toggleCovers(picked.map { it.relativePath }) },
                            state = favoriteAlbumsListState,
                            onOpen = { openFavoriteAlbum = it.name },
                            onLongPress = { album ->
                                sheetFavoriteAlbum = album.name
                                sheetAlbum = album
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
                                memory = favoriteAlbumMemories.getOrPut(album.name) { GridMemory(SettingsView.FAVORITES) },
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

            // Sorting through the photos on screen, from the settings sheet; a place of covers has none to go through.
            val reviewAction: (() -> Unit)? = when {
                isPrivateMode && section == Section.RECENT -> { { review = ViewerSource.PrivateRecent } }
                isPrivateMode && section == Section.FAVORITES -> when {
                    openPrivateFavorite != null -> { { review = ViewerSource.InPrivateFavoriteGroup(openPrivateFavorite.name) } }
                    isPrivateFavoritesGrouped -> null
                    else -> { { review = ViewerSource.PrivateFavorites } }
                }
                section == Section.RECENT -> { { review = ViewerSource.Recent } }
                section == Section.FAVORITES && favoritesView == FavoritesView.All -> { { review = ViewerSource.Favorites } }
                openAlbum != null -> { { review = ViewerSource.InAlbum(openAlbum.id) } }
                openPrivateGroup != null -> { { review = ViewerSource.InPrivateGroup(openPrivateGroup.name) } }
                openLocation != null -> { { review = ViewerSource.InLocation(openLocation.key) } }
                section == Section.FAVORITES && openFavorite != null -> { { review = ViewerSource.InFavoriteAlbum(openFavorite.name) } }
                else -> null
            }
            val visibleMonth = folderMemory?.let { rememberVisibleMonth(gridItems, it).value } ?: VisibleMonth("", 0)
            // Out of a folder, the same as the system back; Private's own groups leave by the Private pill instead.
            val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            val canGoBack = if (isPrivateMode) {
                (section == Section.ALBUMS && place is AlbumsPlace.PrivateFolder) || (section == Section.FAVORITES && openPrivateFavoriteGroup != null)
            } else {
                (section == Section.ALBUMS && place != AlbumsPlace.Folders) || (section == Section.FAVORITES && openFavoriteAlbum != null)
            }
            TopRow(
                month = visibleMonth,
                selectedCount = selectedItems.size + selectedAlbums.size + selectedGroups.size,
                onBack = if (canGoBack) { { backDispatcher?.onBackPressed() } } else null,
                // The folder open takes new photos straight from here; a location only gathers by place, so it has none.
                onAdd = when {
                    isPrivateMode && section != Section.ALBUMS -> null
                    openAlbum != null -> { { picker = PickerTarget.IntoAlbum(openAlbum.relativePath, openAlbum.name) } }
                    openPrivateGroup != null -> { { picker = PickerTarget.IntoGroup(openPrivateGroup.name) } }
                    section == Section.FAVORITES && openFavorite != null -> { { picker = PickerTarget.IntoFavoriteAlbum(openFavorite.name) } }
                    else -> null
                },
                // Favorites switches between every favourite in one grid and the albums made inside it.
                // Inside Private the same button switches Private's own Favorites, which remembers its choice apart.
                onToggleView = when {
                    isPrivateMode && section == Section.FAVORITES -> if (openPrivateFavorite == null) { { Settings.updatePrivateFavoritesAsGroups(!Settings.privateFavoritesAsGroups) } } else null
                    section == Section.FAVORITES && openFavorite == null -> { { Settings.updateFavoritesAsAlbums(!Settings.favoritesAsAlbums) } }
                    else -> null
                },
                isAlbumsView = if (isPrivateMode) Settings.privateFavoritesAsGroups else Settings.favoritesAsAlbums,
                onCancelSelection = clearSelection,
                onSettings = { sheet = AppSheet.SETTINGS },
            )

            val bottomBar = when {
                isRearranging -> BottomBar.REARRANGING
                isSelectingCovers -> BottomBar.COVERS
                isSelecting -> BottomBar.PHOTOS
                else -> BottomBar.NAVIGATION
            }
            // The month's photos out of the whole view's, in the corner just above the nav.
            var lastCount by remember { mutableStateOf("") }
            if (visibleMonth.label.isNotEmpty()) lastCount = "${visibleMonth.count}/${gridItems.size}"
            AnimatedVisibility(
                visibleMonth.label.isNotEmpty(),
                enter = TOP_ENTER,
                exit = TOP_EXIT,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 14.dp + with(LocalDensity.current) { navigationHeight.toDp() } + 8.dp),
            ) {
                Box(Modifier.glass(Shapes.capsule).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    TypewriterText(lastCount, style = Type.microLabel.copy(fontSize = 9.sp))
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            // A delete waits here, above everything else over the bar, until it is confirmed.
            AnimatedVisibility(pendingDelete != null, enter = TOP_ENTER, exit = TOP_EXIT) {
                Box(
                    Modifier.padding(bottom = 8.dp)
                        .pressable(onClick = {
                            val delete = pendingDelete
                            pendingDelete = null
                            delete?.invoke()
                        })
                        .background(Palette.danger, Shapes.capsule)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    BasicText("CONFIRM", style = Type.microLabel.copy(color = Palette.sunkenDeep))
                }
            }
            // The bar pops from one kind to the next instead of cutting, as the top buttons do.
            AnimatedContent(
                targetState = bottomBar,
                transitionSpec = { BAR_ENTER.togetherWith(BAR_EXIT).using(SizeTransform(clip = false)) },
                contentAlignment = Alignment.BottomCenter,
                label = "bottomBar",
            ) { shownBar ->
                if (shownBar == BottomBar.REARRANGING) {
                    Box(Modifier.pressable(onClick = { isRearranging = false }).glass(Shapes.capsule).padding(horizontal = 22.dp, vertical = 13.dp)) {
                        CheckIcon(accent)
                    }
                } else if (shownBar == BottomBar.COVERS) {
                    Row(Modifier.glass(Shapes.capsule).padding(5.dp)) {
                        val deleteModifier = if (pendingDelete != null) Modifier.background(Palette.danger.copy(alpha = 0.22f), Shapes.capsule) else Modifier
                        if (selectedGroups.isNotEmpty()) {
                            IconButton(onClick = { sheet = AppSheet.GROUP_MOVE_OUT }) { LockIcon(Palette.textBody, isOpen = true) }
                            // Private groups are outside the system trash, so deleting them waits for Confirm.
                            IconButton(onClick = {
                                pendingDelete = {
                                    selectedGroups.forEach { actions.deleteGroup(it) }
                                    clearSelection()
                                }
                            }, modifier = deleteModifier) { TrashIcon(Palette.danger) }
                        } else {
                            val isFavorites = section == Section.FAVORITES
                            if (Settings.groupedAlbums) IconButton(onClick = { sheet = if (isFavorites) AppSheet.FAVORITE_ALBUM_STACK else AppSheet.ALBUM_STACK }) { MoveIcon(Palette.textBody) }
                            IconButton(onClick = { sheet = AppSheet.ALBUM_GROUP }) { LockIcon(Palette.textBody) }
                            // Hides every selected album from Recent, or shows them again once all of them are hidden.
                            val hiddenKeys = selectedAlbums.map { if (isFavorites) hiddenFavoriteKey(it.name) else it.relativePath }.toSet()
                            val isAllHidden = Settings.hiddenFromRecent.containsAll(hiddenKeys)
                            IconButton(onClick = {
                                Settings.updateHiddenFromRecent(if (isAllHidden) Settings.hiddenFromRecent - hiddenKeys else Settings.hiddenFromRecent + hiddenKeys)
                                clearSelection()
                            }) { EyeIcon(Palette.textBody, isCrossed = !isAllHidden) }
                            // Whole albums at once, so it waits for Confirm even though the trash can give them back; a Favorites album only lets its photos go, but cannot be brought back.
                            IconButton(onClick = {
                                pendingDelete = {
                                    if (isFavorites) {
                                        val names = selectedAlbums.map { it.name }.toSet()
                                        Settings.updateFavoriteAlbums(Settings.favoriteAlbums.filter { it.name !in names })
                                    } else {
                                        actions.trash(selectedAlbums.flatMap { it.items })
                                    }
                                    clearSelection()
                                }
                            }, modifier = deleteModifier) { TrashIcon(Palette.danger) }
                        }
                    }
                } else if (shownBar == BottomBar.PHOTOS) {
                    Row(Modifier.glass(Shapes.capsule).padding(5.dp)) {
                      if (place is AlbumsPlace.Trash) {
                        IconButton(onClick = { sheet = AppSheet.TRASH_RESTORE }) { RestoreIcon(accent) }
                        // Out of the trash there is no coming back, so it waits for Confirm.
                        IconButton(
                            onClick = {
                                pendingDelete = {
                                    actions.deleteForever(selectedItems)
                                    clearSelection()
                                }
                            },
                            modifier = if (pendingDelete != null) Modifier.background(Palette.danger.copy(alpha = 0.22f), Shapes.capsule) else Modifier,
                        ) { TrashIcon(Palette.danger) }
                      } else {
                        IconButton(onClick = { actions.share(selectedItems) }) { ShareIcon(Palette.textBody) }
                        // Favourites every selected photo, or takes them all out once all of them are favourites.
                        val isAllFavorite = selectedItems.all { it.isFavorite }
                        IconButton(onClick = {
                            actions.setFavorite(selectedItems, !isAllFavorite)
                            clearSelection()
                        }) { HeartIcon(isFilled = isAllFavorite, color = if (isAllFavorite) Palette.favorite else Palette.textBody, size = 22.dp) }
                        val isInFavoriteAlbum = section == Section.FAVORITES && openFavorite != null
                        if (selectedItems.size == 1 && (openAlbum != null || openPrivateGroup != null || isInFavoriteAlbum)) {
                            IconButton(onClick = {
                                when {
                                    openAlbum != null -> viewModel.setAlbumCover(openAlbum.id, selectedItems.first())
                                    openPrivateGroup != null -> viewModel.setGroupCover(openPrivateGroup.name, selectedItems.first())
                                    openFavorite != null -> viewModel.setAlbumCover(openFavorite.id, selectedItems.first())
                                }
                                actions.announce("Set as cover")
                                clearSelection()
                            }) { ImageIcon(Palette.textBody) }
                        }
                        if (isInPrivate) {
                            IconButton(onClick = { sheet = AppSheet.SELECTION_GROUP }) { MoveIcon(Palette.textBody) }
                            IconButton(onClick = { sheet = AppSheet.SELECTION_MOVE }) { LockIcon(Palette.textBody, isOpen = true) }
                            IconButton(
                                onClick = {
                                    pendingDelete = {
                                        actions.deletePrivate(selectedItems)
                                        clearSelection()
                                    }
                                },
                                modifier = if (pendingDelete != null) Modifier.background(Palette.danger.copy(alpha = 0.22f), Shapes.capsule) else Modifier,
                            ) { TrashIcon(Palette.danger) }
                        } else {
                            // The same bar as in albums; inside Favorites, moving goes between its own albums.
                            IconButton(onClick = { sheet = if (section == Section.FAVORITES) AppSheet.FAVORITE_ALBUM_PICK else AppSheet.SELECTION_MOVE }) { MoveIcon(Palette.textBody) }
                            IconButton(onClick = { sheet = AppSheet.SELECTION_GROUP }) { LockIcon(Palette.textBody) }
                            IconButton(onClick = {
                                actions.trash(selectedItems)
                                clearSelection()
                            }) { TrashIcon(Palette.danger) }
                        }
                      }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Inside Private or Locations the bar belongs to that place, so it says so, with the way out beside it.
                        val isInLocations = place == AlbumsPlace.Locations || place is AlbumsPlace.Location
                        val placePill = when {
                            isPrivateMode -> "PRIVATE"
                            isInLocations -> "LOCATIONS"
                            else -> null
                        }
                        var lastPill by remember { mutableStateOf(placePill ?: "") }
                        if (placePill != null) lastPill = placePill
                        AnimatedVisibility(placePill != null, enter = TOP_ENTER, exit = TOP_EXIT) {
                            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.pressable(onClick = {
                                    when {
                                        isPrivateMode -> leavePrivate()
                                        place is AlbumsPlace.Location -> albumsPlace = AlbumsPlace.Locations
                                        else -> albumsPlace = AlbumsPlace.Folders
                                    }
                                }).glass(Shapes.capsule).padding(horizontal = 10.dp, vertical = 5.dp)) { BackIcon(accent, size = 16.dp) }
                                Box(Modifier.background(accent, Shapes.capsule).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    BasicText(lastPill, style = Type.microLabel.copy(color = Palette.sunkenDeep))
                                }
                            }
                        }
                        // Empties the whole trash for good, so it waits for Confirm.
                        AnimatedVisibility(place is AlbumsPlace.Trash && trash.isNotEmpty(), enter = TOP_ENTER, exit = TOP_EXIT) {
                            Box(
                                Modifier.padding(bottom = 8.dp)
                                    .pressable(onClick = { pendingDelete = { actions.deleteForever(trash) } })
                                    .glass(Shapes.capsule)
                                    .then(if (pendingDelete != null) Modifier.background(Palette.danger.copy(alpha = 0.22f), Shapes.capsule) else Modifier)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                BasicText("DELETE NOW", style = Type.microLabel.copy(color = Palette.danger))
                            }
                        }
                        SectionBar(
                            modifier = Modifier.onSizeChanged { navigationHeight = it.height },
                            active = section,
                            accentOf = { if (isPrivateMode) Palette.privateRed else if (it == Section.ALBUMS) placeAccentOf(albumsPlace) ?: it.accent else it.accent },
                            onSelect = { selected ->
                                if (selected == section) {
                                    when {
                                        selected == Section.ALBUMS -> albumsPlace = if (isPrivateMode) AlbumsPlace.PrivateGroups else AlbumsPlace.Folders
                                        selected == Section.FAVORITES && isPrivateMode && openPrivateFavoriteGroup != null -> openPrivateFavoriteGroup = null
                                        selected == Section.FAVORITES && !isPrivateMode && openFavoriteAlbum != null -> openFavoriteAlbum = null
                                        else -> scrollToNewestRequest++
                                    }
                                }
                                section = selected
                            },
                        )
                    }
                }
            }
            }

            // Restoring asks where to: back where each came from, or into one album.
            OverlaySheet(visible = sheet == AppSheet.TRASH_RESTORE, label = "RESTORE", onDismiss = { sheet = AppSheet.NONE }) {
                Column {
                    SheetRow("Restore", icon = { RestoreIcon(it) }) {
                        actions.restore(selectedItems)
                        clearSelection()
                    }
                    SheetRow("Restore to album", icon = { MoveIcon(it) }) { sheet = AppSheet.SELECTION_MOVE }
                }
            }

            // The selection's pickers. Moving out of Private goes to an album; moving within it goes to a group.
            AlbumPickerSheet(
                visible = sheet == AppSheet.SELECTION_MOVE,
                label = when {
                    isInPrivate -> "MOVE OUT TO"
                    place is AlbumsPlace.Trash -> "RESTORE TO"
                    else -> "MOVE TO"
                },
                albums = albums,
                excludedAlbumId = openAlbum?.id,
                onPick = { album ->
                    when {
                        isInPrivate -> actions.unhide(selectedItems, album)
                        place is AlbumsPlace.Trash -> actions.restoreTo(selectedItems, album)
                        else -> actions.move(selectedItems, album)
                    }
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
                val albumPath = sheetAlbum?.relativePath.orEmpty()
                val isHiddenFromRecent = albumPath in Settings.hiddenFromRecent
                AlbumMenuRows(
                    onRename = { sheet = AppSheet.ALBUM_RENAME },
                    onSelect = {
                        sheetAlbum?.let { selectedCovers = setOf(it.relativePath) }
                        sheet = AppSheet.NONE
                    },
                    onRearrange = {
                        isRearranging = true
                        sheet = AppSheet.NONE
                    },
                    group = if (Settings.groupedAlbums) GroupRow(
                        name = Settings.albumStacks.firstOrNull { it.paths.contains(albumPath) }?.name,
                        onMove = { sheet = AppSheet.ALBUM_STACK },
                        onRemove = {
                            Settings.updateAlbumStacks(Settings.albumStacks.withoutAlbum(albumPath))
                            sheet = AppSheet.NONE
                        },
                    ) else null,
                    onPrivate = { sheet = AppSheet.ALBUM_GROUP },
                    isHiddenFromRecent = isHiddenFromRecent,
                    onToggleRecent = {
                        Settings.updateHiddenFromRecent(if (isHiddenFromRecent) Settings.hiddenFromRecent - albumPath else Settings.hiddenFromRecent + albumPath)
                        sheet = AppSheet.NONE
                    },
                    onAddPhotos = {
                        sheetAlbum?.let { picker = PickerTarget.IntoAlbum(it.relativePath, it.name) }
                        sheet = AppSheet.NONE
                    },
                    isDeleteArmed = isMenuDeleteArmed,
                    armedDeleteText = "Tap again: ${sheetAlbum?.items?.size ?: 0} photos to the trash",
                    onDelete = {
                        if (isMenuDeleteArmed) {
                            sheetAlbum?.let { actions.trash(it.items) }
                            sheet = AppSheet.NONE
                        } else {
                            isMenuDeleteArmed = true
                        }
                    },
                )
            }

            // A long-pressed album group. Ungrouping only lays its albums back into the grid; no photo is touched.
            OverlaySheet(visible = sheet == AppSheet.STACK_MENU, label = sheetStack?.uppercase().orEmpty(), onDismiss = { sheet = AppSheet.NONE }) {
                val stackPaths = Settings.albumStacks.firstOrNull { it.name == sheetStack }?.paths.orEmpty()
                GroupMenuRows(
                    onRename = { sheet = AppSheet.STACK_RENAME },
                    onSelect = {
                        selectedCovers = stackPaths.toSet()
                        sheet = AppSheet.NONE
                    },
                    onRearrange = {
                        isRearranging = true
                        sheet = AppSheet.NONE
                    },
                    groups = {
                        SheetRow("Ungroup all", trailing = stackPaths.size.toString(), icon = { CloseIcon(it) }) {
                            Settings.updateAlbumStacks(Settings.albumStacks.filter { it.name != sheetStack })
                            sheet = AppSheet.NONE
                        }
                    },
                )
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

            // Favorites albums only gather favourites; moving between them never touches the photo's folder.
            NamePickerSheet(
                visible = sheet == AppSheet.FAVORITE_ALBUM_PICK,
                label = "MOVE TO",
                choices = favoriteAlbumViews.filter { it.name != openFavorite?.name }.map { it.name to it.items.size },
                newLabel = "+ New album",
                onPick = { name ->
                    Settings.updateFavoriteAlbums(Settings.favoriteAlbums.withPhotos(name, selectedItems.map { it.id }))
                    clearSelection()
                },
                onNew = { sheet = AppSheet.FAVORITE_SELECTION_NEW_ALBUM },
                onDismiss = { sheet = AppSheet.NONE },
            )
            // The same menu as an album's in Albums; deleting takes only the album, the photos stay favourites, but it cannot be brought back, so it takes a second tap.
            OverlaySheet(visible = sheet == AppSheet.FAVORITE_ALBUM_MENU, label = sheetFavoriteAlbum?.uppercase().orEmpty(), onDismiss = { sheet = AppSheet.NONE }) {
                val albumName = sheetFavoriteAlbum.orEmpty()
                val hiddenKey = hiddenFavoriteKey(albumName)
                val isHiddenFromRecent = hiddenKey in Settings.hiddenFromRecent
                AlbumMenuRows(
                    onRename = { sheet = AppSheet.FAVORITE_ALBUM_RENAME },
                    onSelect = {
                        selectedCovers = setOf(albumName)
                        sheet = AppSheet.NONE
                    },
                    onRearrange = {
                        isRearranging = true
                        sheet = AppSheet.NONE
                    },
                    group = if (Settings.groupedAlbumsIn(SettingsView.FAVORITES)) GroupRow(
                        name = Settings.favoriteStacks.firstOrNull { it.paths.contains(albumName) }?.name,
                        onMove = { sheet = AppSheet.FAVORITE_ALBUM_STACK },
                        onRemove = {
                            Settings.updateFavoriteStacks(Settings.favoriteStacks.withoutAlbum(albumName))
                            sheet = AppSheet.NONE
                        },
                    ) else null,
                    onPrivate = { sheet = AppSheet.ALBUM_GROUP },
                    isHiddenFromRecent = isHiddenFromRecent,
                    onToggleRecent = {
                        Settings.updateHiddenFromRecent(if (isHiddenFromRecent) Settings.hiddenFromRecent - hiddenKey else Settings.hiddenFromRecent + hiddenKey)
                        sheet = AppSheet.NONE
                    },
                    onAddPhotos = {
                        picker = PickerTarget.IntoFavoriteAlbum(albumName)
                        sheet = AppSheet.NONE
                    },
                    isDeleteArmed = isMenuDeleteArmed,
                    armedDeleteText = "Tap again: delete album",
                    onDelete = {
                        if (isMenuDeleteArmed) {
                            Settings.updateFavoriteAlbums(Settings.favoriteAlbums.filter { it.name != albumName })
                            sheet = AppSheet.NONE
                        } else {
                            isMenuDeleteArmed = true
                        }
                    },
                )
            }
            OverlaySheet(visible = sheet == AppSheet.FAVORITE_STACK_MENU, label = sheetStack?.uppercase().orEmpty(), onDismiss = { sheet = AppSheet.NONE }) {
                val stackNames = Settings.favoriteStacks.firstOrNull { it.name == sheetStack }?.paths.orEmpty()
                GroupMenuRows(
                    onRename = { sheet = AppSheet.FAVORITE_STACK_RENAME },
                    onSelect = {
                        selectedCovers = stackNames.toSet()
                        sheet = AppSheet.NONE
                    },
                    onRearrange = {
                        isRearranging = true
                        sheet = AppSheet.NONE
                    },
                    groups = {
                        SheetRow("Ungroup all", trailing = stackNames.size.toString(), icon = { CloseIcon(it) }) {
                            Settings.updateFavoriteStacks(Settings.favoriteStacks.filter { it.name != sheetStack })
                            sheet = AppSheet.NONE
                        }
                    },
                )
            }
            StackPickerSheet(
                visible = sheet == AppSheet.FAVORITE_ALBUM_STACK,
                label = "MOVE $targetAlbumsLabel TO",
                stacks = Settings.favoriteStacks.filter { stack -> !targetAlbums.all { stack.paths.contains(it.name) } },
                onPick = { name ->
                    Settings.updateFavoriteStacks(targetAlbums.fold(Settings.favoriteStacks) { stacks, album -> stacks.withAlbum(album.name, name) })
                    if (isSelectingCovers) clearSelection() else sheet = AppSheet.NONE
                },
                onNewStack = { sheet = AppSheet.FAVORITE_ALBUM_NEW_STACK },
                onDismiss = { sheet = AppSheet.NONE },
            )

            SettingsSheet(
                visible = sheet == AppSheet.SETTINGS,
                isCovers = folderMemory == null,
                onReview = reviewAction,
                onDismiss = { sheet = AppSheet.NONE },
                // Only the grids of the view whose setting changed take the new count.
                onColumnsChanged = { columns ->
                    (listOf(recentMemory, favoritesMemory, privateFavoritesMemory, privateRecentMemory, trashMemory) + albumMemories.values + privateMemories.values + favoriteAlbumMemories.values + privateFavoriteGroupMemories.values + locationMemories.values)
                        .filter { it.view == settingsView }
                        .forEach { it.columns = columns }
                },
            )

            // A long-pressed private group. Deleting one is final — private photos are outside the system trash — so it takes a second tap.
            OverlaySheet(visible = sheet == AppSheet.GROUP_MENU, label = sheetGroup?.name?.uppercase().orEmpty(), onDismiss = { sheet = AppSheet.NONE }) {
                GroupMenuRows(
                    onRename = { sheet = AppSheet.GROUP_RENAME },
                    onSelect = {
                        sheetGroup?.let { selectedCovers = setOf(it.name) }
                        sheet = AppSheet.NONE
                    },
                    onRearrange = {
                        isRearranging = true
                        sheet = AppSheet.NONE
                    },
                    groups = {
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
                    },
                    edit = {
                        SheetRow("Move group out to album", trailing = sheetGroup?.items?.size?.toString(), icon = { MoveIcon(it) }) { sheet = AppSheet.GROUP_MOVE_OUT }
                        SheetRow("Add photos", icon = { PlusIcon(it) }) {
                            sheetGroup?.let { picker = PickerTarget.IntoGroup(it.name) }
                            sheet = AppSheet.NONE
                        }
                    },
                )
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
                                viewModel.moveAlbumCover(old.hashCode().toLong(), new.hashCode().toLong())
                                if (hiddenFavoriteKey(old) in Settings.hiddenFromRecent) Settings.updateHiddenFromRecent(Settings.hiddenFromRecent - hiddenFavoriteKey(old) + hiddenFavoriteKey(new))
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
                        Settings.updateFavoriteStacks(targetAlbums.fold(Settings.favoriteStacks) { stacks, album -> stacks.withAlbum(album.name, name) })
                        if (isSelectingCovers) clearSelection() else sheet = AppSheet.NONE
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
                    action = if (place is AlbumsPlace.Trash) "RESTORE HERE" else "MOVE HERE",
                    onConfirm = { name ->
                        when {
                            isInPrivate -> actions.unhide(selectedItems, newAlbumPath(name), name)
                            place is AlbumsPlace.Trash -> actions.restoreTo(selectedItems, newAlbumPath(name), name)
                            else -> actions.move(selectedItems, newAlbumPath(name), name)
                        }
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
                    // Photos already in this album, or in any album sorted into a group, are not offered again.
                    is PickerTarget.IntoAlbum -> {
                        val groupedPaths = if (Settings.groupedAlbumsIn(SettingsView.ALBUMS)) Settings.albumStacks.flatMap { it.paths }.toSet() else emptySet()
                        albums.filter { it.relativePath == target.relativePath || it.relativePath in groupedPaths }.flatMap { album -> album.items.map { it.id } }.toSet()
                    }
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
                    // Where the photos come from a folder with a cover, any of them can be made its cover from the viewer.
                    onSetCover = run {
                        val setCover: ((MediaItem) -> Unit)? = when (val source = request.source) {
                            is ViewerSource.InAlbum -> { item -> viewModel.setAlbumCover(source.albumId, item) }
                            is ViewerSource.InPrivateGroup -> { item -> viewModel.setGroupCover(source.name, item) }
                            is ViewerSource.InFavoriteAlbum -> favoriteAlbumViews.firstOrNull { it.name == source.name }?.let { album -> { item: MediaItem -> viewModel.setAlbumCover(album.id, item) } }
                            else -> null
                        }
                        setCover?.let { set -> { item: MediaItem -> set(item); actions.announce("Set as cover") } }
                    },
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
    ViewerSource.PrivateRecent -> "private-recent"
    is ViewerSource.InPrivateFavoriteGroup -> "private-favorite-group:$name"
    ViewerSource.Recent -> "recent"
    ViewerSource.Favorites -> "favorites"
    ViewerSource.Trash -> "trash"
    is ViewerSource.InFavoriteAlbum -> "favorite-album:$name"
}

private fun ViewerSource?.isPrivateSource(): Boolean =
    this is ViewerSource.InPrivateGroup || this is ViewerSource.PrivateFavorites || this == ViewerSource.PrivateRecent || this is ViewerSource.InPrivateFavoriteGroup

// What Private's Favorites shows besides an opened group, keyed apart from any group name (a group's name never holds a tab).
private const val PRIVATE_FAVORITE_GRID = "\tgrid"
private const val PRIVATE_FAVORITE_GROUPS = "\tgroups"

// The top layer: the way back out of a folder and the month you are looking at, or — while selecting — the count and the way out of the selection.
// The month chip has a fixed width, so a month with a longer name never shifts or resizes the buttons.
@Composable
private fun TopRow(month: VisibleMonth, selectedCount: Int, onBack: (() -> Unit)?, onAdd: (() -> Unit)?, onCancelSelection: () -> Unit, onSettings: () -> Unit, onToggleView: (() -> Unit)? = null, isAlbumsView: Boolean = false) {
    // Selecting swaps the whole row; otherwise each button comes and goes on its own as the place changes, the others sliding to make room.
    AnimatedContent(
        targetState = selectedCount > 0,
        transitionSpec = { fadeIn(tween(Motion.STATE_MS)).togetherWith(fadeOut(tween(Motion.STATE_MS))).using(SizeTransform(clip = false)) },
        label = "topRow",
    ) { isSelecting ->
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isSelecting) {
                TopButton(onCancelSelection) { CloseIcon(LocalAccent.current) }
                Box(Modifier.weight(1f))
                Chip("$selectedCount selected")
            } else {
                // Back sits at the far left, then the month, which types itself over as it changes; the buttons gather at the right.
                val isMonthShown = month.label.isNotEmpty()
                var lastMonth by remember { mutableStateOf(month) }
                if (isMonthShown) lastMonth = month
                MakeRoomButton(onBack) { BackIcon(LocalAccent.current) }
                AnimatedVisibility(isMonthShown, enter = TOP_ENTER, exit = TOP_EXIT) {
                    Box(Modifier.width(MONTH_CHIP_WIDTH).glass(Shapes.capsule).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                        TypewriterText(lastMonth.label.uppercase(), style = Type.microLabel)
                    }
                }
                Box(Modifier.weight(1f))
                ShownTopButton(onAdd) { PlusIcon(LocalAccent.current) }
                // The icon shows where a tap goes: the grid of every favourite, or the albums.
                ShownTopButton(onToggleView) {
                    AnimatedContent(isAlbumsView, transitionSpec = { (fadeIn(tween(Motion.STATE_MS)) + scaleIn(tween(Motion.STATE_MS), initialScale = 0.6f)).togetherWith(fadeOut(tween(Motion.STATE_MS))) }, label = "toggle") { isAlbums ->
                        if (isAlbums) GridIcon(LocalAccent.current) else AlbumsIcon(LocalAccent.current)
                    }
                }
                Box(Modifier.padding(start = 8.dp)) { TopButton(onSettings) { SettingsIcon(Palette.textBright) } }
            }
        }
    }
}

// The back button takes its room and gives it back in two steps: leaving, it pops away first and then the month slides into its place; arriving, the month slides over first and then it pops in.
@Composable
private fun MakeRoomButton(onClick: (() -> Unit)?, icon: @Composable () -> Unit) {
    var lastClick by remember { mutableStateOf(onClick) }
    if (onClick != null) lastClick = onClick
    val isShown = onClick != null
    val room = remember { Animatable(if (isShown) 1f else 0f) }
    val pop = remember { Animatable(if (isShown) 1f else 0f) }
    LaunchedEffect(isShown) {
        if (isShown) {
            room.animateTo(1f, tween(Motion.STATE_MS, easing = Motion.powerThreeInOut))
            pop.animateTo(1f, tween(Motion.STATE_MS, easing = Motion.backOut))
        } else {
            pop.animateTo(0f, tween(Motion.STATE_MS, easing = Motion.backIn))
            room.animateTo(0f, tween(Motion.STATE_MS, easing = Motion.powerThreeInOut))
        }
    }
    if (room.value == 0f && pop.value == 0f && !isShown) return
    Layout(
        content = {
            Box(Modifier.padding(end = 8.dp).graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                alpha = pop.value.coerceIn(0f, 1f)
            }) { TopButton({ lastClick?.invoke() }, icon) }
        },
    ) { measurables, constraints ->
        val button = measurables.first().measure(constraints.copy(minWidth = 0))
        val width = (button.width * room.value).roundToInt()
        layout(width, button.height) { button.place(0, 0) }
    }
}

// What the bottom bar is showing: the sections, a selection's actions, or the end of rearranging.
private enum class BottomBar { NAVIGATION, PHOTOS, COVERS, REARRANGING }

// The bottom bar swaps with a smaller pop than a single button, being wide.
private val BAR_ENTER = fadeIn(tween(Motion.STATE_MS)) + scaleIn(tween(Motion.STATE_MS, easing = Motion.backOut), initialScale = 0.8f)
private val BAR_EXIT = fadeOut(tween(Motion.STATE_MS, easing = Motion.powerTwoIn)) + scaleOut(tween(Motion.STATE_MS, easing = Motion.powerTwoIn), targetScale = 0.8f)

// Top buttons pop: they grow in past full size and settle, and shrink away to nothing, in place rather than sliding.
private val TOP_ENTER = fadeIn(tween(Motion.STATE_MS)) + scaleIn(tween(Motion.STATE_MS, easing = Motion.backOut), initialScale = 0f)
private val TOP_EXIT = fadeOut(tween(Motion.STATE_MS, easing = Motion.powerTwoIn)) + scaleOut(tween(Motion.STATE_MS, easing = Motion.backIn), targetScale = 0f)

// A top button that is there only while it has something to do; it keeps its last action while it leaves.
@Composable
private fun RowScope.ShownTopButton(onClick: (() -> Unit)?, isGapAfter: Boolean = false, icon: @Composable () -> Unit) {
    var lastClick by remember { mutableStateOf(onClick) }
    if (onClick != null) lastClick = onClick
    AnimatedVisibility(onClick != null, enter = TOP_ENTER, exit = TOP_EXIT) {
        // The gap rides inside, so it comes and goes with the button.
        Box(if (isGapAfter) Modifier.padding(end = 8.dp) else Modifier.padding(start = 8.dp)) { TopButton({ lastClick?.invoke() }, icon) }
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
