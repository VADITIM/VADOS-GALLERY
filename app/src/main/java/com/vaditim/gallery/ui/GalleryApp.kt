package com.vaditim.gallery.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo


import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.rememberTextMeasurer
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.util.lerp
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
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
import com.vaditim.gallery.vas.glass
import com.vaditim.gallery.vas.pressable
import com.vaditim.gallery.vault.PrivateLock
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

private val BAR_ROOM = 84.dp
private val PILL_ROOM = 40.dp
private val HEADER_ROOM = 56.dp
private val MONTH_CHIP_WIDTH = 148.dp
private val FOLDER_LABEL_MAX_WIDTH = 240.dp
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

private fun settingsViewOf(place: AlbumsPlace): SettingsView = when (place) {
    AlbumsPlace.Locations, is AlbumsPlace.Location -> SettingsView.LOCATIONS
    AlbumsPlace.Trash -> SettingsView.TRASH
    AlbumsPlace.PrivateGroups, is AlbumsPlace.PrivateFolder -> SettingsView.PRIVATE
    else -> SettingsView.ALBUMS
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
    NEW_GROUP_NAME, NEW_GROUP_ALBUMS, NEW_GROUP_MOVE_CHECK, GROUP_DELETE_CHECK,
}

// A group about to be deleted that still holds photos: how many, and the delete itself, asked about once more after Confirm.
private class GroupDeleteCheck(val name: String, val photoCount: Int, val delete: () -> Unit)

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

@OptIn(ExperimentalAnimationApi::class)
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
    val recent = library
    val actions = rememberMediaActions(viewModel.repository, viewModel.vault, onPrivateChanged = { viewModel.refreshPrivate() }, samsungTrash = viewModel.samsungTrash, onTrashChanged = { viewModel.refreshTrash() })
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var section by remember { mutableStateOf(Section.RECENT) }
    var albumsPlace by remember { mutableStateOf<AlbumsPlace>(AlbumsPlace.Folders) }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    // `viewer` is what is wanted open; `shownViewer` stays composed through the closing animation.
    var shownViewer by remember { mutableStateOf<ViewerRequest?>(null) }
    // The app stands upright; only a photo or video being viewed turns with the phone.
    val activity = LocalContext.current.findActivity()
    val isViewing = viewer != null
    DisposableEffect(isViewing, activity) {
        activity?.requestedOrientation = if (isViewing) ActivityInfo.SCREEN_ORIENTATION_FULL_USER else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { }
    }
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
    // Anything short of full size is on its way to or from its tile, where the navigation lies in front of it.
    val isViewerShrunk by remember { derivedStateOf { viewerProgress.value * (1f - viewerPull) < 1f } }
    var scrollToNewestRequest by remember { mutableIntStateOf(0) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    // Albums (by folder path) or private groups (by name) picked in a cover grid; only one of the two grids is ever on screen.
    var selectedCovers by remember { mutableStateOf(emptySet<String>()) }
    // A delete that waits for the Confirm above the bar; anything else the user does lets it go.
    // The nav bar's own height, so what sits above it (the count in the corner) clears it.
    var navigationHeight by remember { mutableIntStateOf(0) }
    // The nav's width and the screen's, so the corner pill can stand centred in the room right of the nav.
    var navigationWidth by remember { mutableIntStateOf(0) }
    var screenWidth by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<(() -> Unit)?>(null) }
    // A group being made from New group: its name, and whether it is a Favorites group, while its albums are ticked.
    var newGroupName by remember { mutableStateOf("") }
    var isNewGroupInFavorites by remember { mutableStateOf(false) }
    // The albums ticked for it, kept while asking about those that already belong to another group.
    var newGroupKeys by remember { mutableStateOf(emptySet<String>()) }
    var groupDeleteCheck by remember { mutableStateOf<GroupDeleteCheck?>(null) }
    var sheet by remember { mutableStateOf(AppSheet.NONE) }
    // Makes the group from New group with the albums ticked for it, taking each out of any group it was in.
    fun createNewGroup() {
        if (isNewGroupInFavorites) {
            Settings.updateFavoriteStacks(newGroupKeys.fold(Settings.favoriteStacks) { stacks, key -> stacks.withAlbum(key, newGroupName) })
        } else {
            Settings.updateAlbumStacks(newGroupKeys.fold(Settings.albumStacks) { stacks, key -> stacks.withAlbum(key, newGroupName) })
        }
        sheet = AppSheet.NONE
    }
    // Deleting a group waits for Confirm as every delete does; one that still holds photos then asks again, naming how many.
    fun askDeleteGroup(name: String, photoCount: Int, delete: () -> Unit) {
        pendingDelete = {
            if (photoCount > 0) {
                groupDeleteCheck = GroupDeleteCheck(name, photoCount, delete)
                sheet = AppSheet.GROUP_DELETE_CHECK
            } else {
                delete()
            }
        }
    }
    var sheetAlbum by remember { mutableStateOf<Album?>(null) }
    var sheetGroup by remember { mutableStateOf<PrivateGroup?>(null) }
    var sheetStack by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    // Opening anything else lets a delete waiting on Confirm go.
    LaunchedEffect(sheet) { if (sheet != AppSheet.NONE) pendingDelete = null }
    // The folder being reviewed one photo at a time, if any.
    var review by remember { mutableStateOf<ViewerSource?>(null) }
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
    // The name of the photo grid on screen; a grid of covers is not inside anything, and the trash has its own pill.
    val folderName = when {
        section == Section.RECENT -> "RECENT"
        isPrivateMode && section == Section.FAVORITES -> openPrivateFavorite?.name ?: if (isPrivateFavoritesGrouped) null else "FAVORITES"
        section == Section.FAVORITES -> openFavorite?.name ?: if (favoritesView == FavoritesView.All) "FAVORITES" else null
        openAlbum != null -> openAlbum.name
        openPrivateGroup != null -> openPrivateGroup.name
        openLocation != null -> openLocation.city
        else -> null
    }?.uppercase()
    val isFolderLabelShown = Settings.folderLabel && folderName != null
    // The PRIVATE, LOCATIONS or TRASH pill and the folder label sit over the bar, so the content ends that much higher to stay clear of them.
    val isPlacePillShown = isPrivateMode || (section == Section.ALBUMS && (albumsPlace == AlbumsPlace.Locations || albumsPlace is AlbumsPlace.Location || albumsPlace == AlbumsPlace.Trash))
    val insetPadding = PaddingValues(
        top = statusBarHeight + HEADER_ROOM,
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BAR_ROOM +
            (if (isPlacePillShown) PILL_ROOM else 0.dp) + (if (isFolderLabelShown) PILL_ROOM else 0.dp),
    )
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
    // Only the favourites of the photo grid on screen; changing place lets it go.
    var isFavoritesOnly by remember { mutableStateOf(false) }
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
    }.favoritesOnlyIf(isFavoritesOnly)
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
        isFavoritesOnly = false
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

    // The screen softens behind the settings, so the sheet reads as the one thing in front.
    // Pulling the sheet down clears the blur with the finger.
    var settingsPull by remember { mutableFloatStateOf(0f) }
    val timelineAbove = remember { TimelineAbove() }
    val isViewerUp = shownViewer != null
    SideEffect { timelineAbove.isViewerShown = isViewerUp }
    // Read only where the layer draws, so the blur moving each frame never recomposes the app.
    val settingsBlur = animateDpAsState(if (sheet == AppSheet.SETTINGS) SETTINGS_BLUR else 0.dp, tween(Motion.OVERLAY_ENTER_MS, easing = Motion.powerTwoOut), label = "settings-blur")
    CompositionLocalProvider(LocalAccent provides accent, LocalAccentTarget provides accentTarget, LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize().onSizeChanged { screenWidth = it.width }) {
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
                modifier = Modifier.zIndex(-2f).fillMaxSize().graphicsLayer {
                    val radius = settingsBlur.value.toPx() * (1f - settingsPull)
                    renderEffect = if (radius > 0f) BlurEffect(radius, radius, TileMode.Clamp) else null
                    clip = true
                }.hazeSource(hazeState),
            ) { (shown, isPrivateShown) ->
                // While this section is the one shown it follows the current view; leaving, it keeps the last one it had.
                var ownView by remember { mutableStateOf(settingsView) }
                if (shown == section && isPrivateShown == (isPrivateMode && section != Section.ALBUMS)) ownView = settingsView
                // Each section keeps its own accent while it leaves, so the outgoing one never takes on the next one's colour.
                CompositionLocalProvider(
                    LocalAccent provides if (isPrivateShown || (shown == Section.ALBUMS && isPrivateMode)) Palette.privateRed else shown.accent,
                    LocalSettingsView provides ownView,
                    LocalFavoritesOnly provides isFavoritesOnly,
                    LocalScreenCovered provides (sheet == AppSheet.SETTINGS),
                    LocalTimelineAbove provides timelineAbove,
                    LocalViewerGrowth provides { if (shownViewer == null) 0f else viewerProgress.value * (1f - viewerPull) },
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
                    ) { shownPlace -> CompositionLocalProvider(
                        LocalAccent provides (placeAccentOf(shownPlace) ?: LocalAccent.current),
                        // Each place keeps its own view's settings while it leaves, so the albums never take Locations' columns, nor Locations the albums'.
                        LocalSettingsView provides settingsViewOf(shownPlace),
                    ) { when (shownPlace) {
                        AlbumsPlace.Folders -> AlbumsScreen(
                            openStacks = openAlbumStacks,
                            onOpenStacksChange = { opened ->
                                // A group closing lets go of the selection, so no album stays picked out of sight.
                                if (isSelectingCovers && !opened.containsAll(openAlbumStacks)) clearSelection()
                                openAlbumStacks = opened
                            },
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
                            onNewGroup = if (Settings.groupedAlbumsIn(SettingsView.ALBUMS)) { {
                                isNewGroupInFavorites = false
                                sheet = AppSheet.NEW_GROUP_NAME
                            } } else null,
                            contentPadding = insetPadding,
                            footer = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FolderDivider()
                                    FolderEntries(
                                        onPrivate = openPrivate,
                                        onLocations = { albumsPlace = AlbumsPlace.Locations },
                                        onTrash = { albumsPlace = AlbumsPlace.Trash },
                                        trashCount = trash.size,
                                    )
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
                            onOpenStacksChange = { opened ->
                                // A group closing lets go of the selection, so no album stays picked out of sight.
                                if (isSelectingCovers && !opened.containsAll(openFavoriteStacks)) clearSelection()
                                openFavoriteStacks = opened
                            },
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
                            onNewGroup = if (Settings.groupedAlbumsIn(SettingsView.FAVORITES)) { {
                                isNewGroupInFavorites = true
                                sheet = AppSheet.NEW_GROUP_NAME
                            } } else null,
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

            // The grid's timeline over the viewer's photo, under everything below.
            TimelineAboveHost(timelineAbove, Modifier.zIndex(-0.5f))

            // Everything from here up floats over the content and blurs it; none of it is inside the haze source, or it would blur itself.
            // As the photo grows the top row leaves off the top and the navigation off the bottom, with the photo, and both come back as it shrinks, so the photo passes behind them.
            val viewerRise = if (shownViewer == null) Modifier else Modifier.graphicsLayer { translationY = -viewerProgress.value * (1f - viewerPull) * (size.height + 12.dp.toPx()) }
            val viewerSink = if (shownViewer == null) Modifier else Modifier.graphicsLayer { translationY = viewerProgress.value * (1f - viewerPull) * (size.height + 12.dp.toPx()) }
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
            Box(viewerRise) { TopRow(
                month = visibleMonth,
                title = folderName.takeUnless { Settings.folderLabel },
                isMonthFilled = !Settings.folderLabel,
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
            ) }

            val bottomBar = when {
                isRearranging -> BottomBar.REARRANGING
                isSelectingCovers -> BottomBar.COVERS
                isSelecting -> BottomBar.PHOTOS
                else -> BottomBar.NAVIGATION
            }
            // The month's photos out of the whole view's; kept while it hides, so it leaves showing what it had.
            var lastMonthCount by remember { mutableIntStateOf(0) }
            var lastTotal by remember { mutableIntStateOf(0) }
            val isCountShown = visibleMonth.label.isNotEmpty()
            if (isCountShown) {
                lastMonthCount = visibleMonth.count
                lastTotal = gridItems.size
            }
            // Only favourites in the grid on screen; Favorites and the trash have nothing to narrow.
            val canNarrowToFavorites = folderMemory != null && !(section == Section.FAVORITES) && place !is AlbumsPlace.Trash
            // In the nav's row, centred in the room right of the nav: the favourites-only heart, and the count small under it, both straight on the photos with a shadow.
            val navigationRight = (screenWidth + navigationWidth) / 2f
            AnimatedVisibility(
                (canNarrowToFavorites || isCountShown) && bottomBar == BottomBar.NAVIGATION && navigationWidth > 0,
                enter = TOP_ENTER,
                exit = TOP_EXIT,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .then(viewerSink)
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(((navigationRight + screenWidth) / 2f - placeable.width / 2f).roundToInt(), 0) }
                    },
            ) {
                Box(Modifier.height(with(LocalDensity.current) { navigationHeight.toDp() }), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // No pill: the heart stands on the photos with a black shadow under it. Where there is nothing to narrow it still holds its room, unseen, so the count never climbs into its place.
                        Box(
                            Modifier
                                .then(if (canNarrowToFavorites) Modifier.pressable(onClick = { isFavoritesOnly = !isFavoritesOnly }) else Modifier)
                                .graphicsLayer { alpha = if (canNarrowToFavorites) 1f else 0f }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.offset(y = 1.dp).blur(3.dp, BlurredEdgeTreatment.Unbounded)) { HeartIcon(isFilled = isFavoritesOnly, color = Color.Black, size = 18.dp) }
                            HeartIcon(isFilled = isFavoritesOnly, color = if (isFavoritesOnly) Palette.favorite else Palette.textBright, size = 18.dp)
                        }
                        if (isCountShown) PhotoCount(lastMonthCount, lastTotal)
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).then(viewerSink).navigationBarsPadding().padding(bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            ConfirmPill(pendingDelete, onDone = { pendingDelete = null })
            // The pills above the nav go with it, popping away as a selection's bar comes.
            AnimatedVisibility(bottomBar == BottomBar.NAVIGATION, enter = TOP_ENTER, exit = TOP_EXIT) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Inside Private, Locations or the trash the bar belongs to that place, so it says so, with the way out beside it.
                        val isInLocations = place == AlbumsPlace.Locations || place is AlbumsPlace.Location
                        val placePill = when {
                            isPrivateMode -> "PRIVATE"
                            isInLocations -> "LOCATIONS"
                            place is AlbumsPlace.Trash -> "TRASH"
                            else -> null
                        }
                        var lastPill by remember { mutableStateOf(placePill ?: "") }
                        if (placePill != null) lastPill = placePill
                        val pillAccent = rememberOwnAccent(placePill != null)
                        // Out of Private; out of Locations entirely, from a location as from the list; out of the trash. The label is the way out as much as the arrow beside it.
                        val leavePlace: () -> Unit = { if (isPrivateMode) { leavePrivate() } else { albumsPlace = AlbumsPlace.Folders } }
                        // Where you are, in the section's colour; not a control, so no arrow and no press.
                        var lastFolderName by remember { mutableStateOf(folderName.orEmpty()) }
                        var lastFolderAccent by remember { mutableStateOf(accentTarget) }
                        if (isFolderLabelShown) {
                            lastFolderName = folderName.orEmpty()
                            lastFolderAccent = accentTarget
                        }
                        AnimatedVisibility(isFolderLabelShown, enter = TOP_ENTER, exit = TOP_EXIT) {
                            TypedLabel(lastFolderName, lastFolderAccent, Modifier.padding(bottom = 8.dp), maxWidth = FOLDER_LABEL_MAX_WIDTH)
                        }
                        // Empties the whole trash for good, so it waits for Confirm.
                        AnimatedVisibility(place is AlbumsPlace.Trash && trash.isNotEmpty(), enter = TOP_ENTER, exit = TOP_EXIT) {
                            Box(
                                Modifier.padding(bottom = 8.dp)
                                    .pressable(onClick = { pendingDelete = { actions.deleteForever(trash) } })
                                    .glass(Shapes.capsule)
                                    .pendingMark(pendingDelete != null)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                BasicText("DELETE NOW", style = Type.microLabel.copy(color = Palette.danger))
                            }
                        }
                        AnimatedVisibility(placePill != null, enter = TOP_ENTER, exit = TOP_EXIT) {
                            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                // The arrow in the label's own colours, ink on the place colour, so the two read as one way out.
                                Box(Modifier.pressable(onClick = leavePlace).background(pillAccent, Shapes.capsule).padding(horizontal = 10.dp, vertical = 5.dp)) { BackIcon(Palette.sunkenDeep, size = 16.dp) }
                                Box(Modifier.pressable(onClick = leavePlace).background(pillAccent, Shapes.capsule).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    BasicText(lastPill, style = Type.microLabel.copy(color = Palette.sunkenDeep))
                                }
                            }
                        }
                    }
            }
            // The nav's wash fades with its buttons, coming back only once they pop in.
            val washAlpha by animateFloatAsState(
                if (bottomBar == BottomBar.NAVIGATION) 1f else 0f,
                tween(Motion.STATE_MS, delayMillis = if (bottomBar == BottomBar.NAVIGATION) Motion.STATE_MS else 0),
                label = "nav-wash",
            )
            // One glass pill for every kind of bar: the old buttons pop away and the new ones pop in, each on its own, while the pill's width follows from one to the other.
            Box(Modifier.glass(Shapes.capsule)) {
            AnimatedContent(
                targetState = bottomBar,
                transitionSpec = { EnterTransition.None.togetherWith(ExitTransition.None).using(SizeTransform(clip = false) { _, _ -> tween(Motion.STATE_MS * 2, easing = Motion.powerThreeInOut) }) },
                contentAlignment = Alignment.Center,
                label = "bottomBar",
            ) { shownBar ->
                val pop = Modifier.animateEnterExit(enter = TOP_POP_IN, exit = TOP_POP_OUT)
                CompositionLocalProvider(LocalButtonPop provides pop) {
                if (shownBar == BottomBar.REARRANGING) {
                    Box(pop.pressable(onClick = { isRearranging = false }).padding(horizontal = 22.dp, vertical = 13.dp)) {
                        CheckIcon(accent)
                    }
                } else if (shownBar == BottomBar.COVERS) {
                    Row(Modifier.padding(5.dp)) {
                        val deleteModifier = Modifier.pendingMark(pendingDelete != null)
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
                            // Only the selected albums that sit in a group can leave one; a Favorites album is held by its name, which is its path.
                            val stacks = if (isFavorites) Settings.favoriteStacks else Settings.albumStacks
                            val groupedPaths = selectedAlbums.map { it.relativePath }.filter { path -> stacks.any { path in it.paths } }
                            if (Settings.groupedAlbums && groupedPaths.isNotEmpty()) {
                                IconButton(onClick = {
                                    val ungrouped = groupedPaths.fold(stacks) { remaining, path -> remaining.withoutAlbum(path) }
                                    if (isFavorites) Settings.updateFavoriteStacks(ungrouped) else Settings.updateAlbumStacks(ungrouped)
                                    clearSelection()
                                }) { CloseIcon(Palette.textBody) }
                            }
                            IconButton(onClick = { sheet = AppSheet.ALBUM_GROUP }) { LockIcon(Palette.textBody) }
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
                    Row(Modifier.padding(5.dp)) {
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
                            modifier = Modifier.pendingMark(pendingDelete != null),
                        ) { TrashIcon(Palette.danger) }
                      } else {
                        IconButton(onClick = { actions.share(selectedItems) }) { ShareIcon(Palette.textBody) }
                        // Favourites every selected photo, or takes them all out once all of them are favourites.
                        // An emptied selection counts as none, or the heart flashes red while the bar leaves.
                        val isAllFavorite = selectedItems.isNotEmpty() && selectedItems.all { it.isFavorite }
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
                                modifier = Modifier.pendingMark(pendingDelete != null),
                            ) { TrashIcon(Palette.danger) }
                        } else {
                            // The same bar as in albums; inside Favorites, moving goes between its own albums.
                            IconButton(onClick = { sheet = if (section == Section.FAVORITES) AppSheet.FAVORITE_ALBUM_PICK else AppSheet.SELECTION_MOVE }) { MoveIcon(Palette.textBody) }
                            IconButton(onClick = { sheet = AppSheet.SELECTION_GROUP }) { LockIcon(Palette.textBody) }
                            // Every delete waits for Confirm, as in the trash.
                            IconButton(
                                onClick = {
                                    pendingDelete = {
                                        actions.trash(selectedItems)
                                        clearSelection()
                                    }
                                },
                                modifier = Modifier.pendingMark(pendingDelete != null),
                            ) { TrashIcon(Palette.danger) }
                        }
                      }
                    }
                } else {
                        SectionBar(
                            hasGlass = false,
                            itemModifier = pop,
                            washAlpha = { washAlpha },
                            modifier = Modifier.onSizeChanged {
                                navigationHeight = it.height
                                navigationWidth = it.width
                            },
                            active = section,
                            accentOf = { if (isPrivateMode) Palette.privateRed else if (it == Section.ALBUMS) placeAccentOf(albumsPlace) ?: it.accent else it.accent },
                            albumsGlyph = when {
                                isPrivateMode -> PlaceGlyph.PRIVATE
                                albumsPlace == AlbumsPlace.Locations || albumsPlace is AlbumsPlace.Location -> PlaceGlyph.LOCATIONS
                                albumsPlace == AlbumsPlace.Trash -> PlaceGlyph.TRASH
                                else -> PlaceGlyph.ALBUMS
                            },
                            onSelect = { selected ->
                                if (selected == section) {
                                    when {
                                        // Back to the start of the place it is in, not out to the albums.
                                        selected == Section.ALBUMS -> {
                                            val start = when {
                                                isPrivateMode -> AlbumsPlace.PrivateGroups
                                                albumsPlace == AlbumsPlace.Locations || albumsPlace is AlbumsPlace.Location -> AlbumsPlace.Locations
                                                albumsPlace == AlbumsPlace.Trash -> AlbumsPlace.Trash
                                                else -> AlbumsPlace.Folders
                                            }
                                            if (albumsPlace == start) scrollToNewestRequest++
                                            albumsPlace = start
                                        }
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
                    onAddPhotos = {
                        sheetAlbum?.let { picker = PickerTarget.IntoAlbum(it.relativePath, it.name) }
                        sheet = AppSheet.NONE
                    },
                    // The menu goes and the delete waits for Confirm above the bar, as every delete does.
                    onDelete = {
                        val album = sheetAlbum
                        sheet = AppSheet.NONE
                        pendingDelete = { album?.let { actions.trash(it.items) } }
                    },
                )
            }

            // A long-pressed album group. Ungrouping only lays its albums back into the grid; no photo is touched.
            OverlaySheet(visible = sheet == AppSheet.STACK_MENU, label = sheetStack?.uppercase().orEmpty(), onDismiss = { sheet = AppSheet.NONE }) {
                val stackPaths = Settings.albumStacks.firstOrNull { it.name == sheetStack }?.paths.orEmpty()
                GroupMenuRows(
                    // Deleting a group of albums sends their photos to the trash.
                    onDelete = {
                        val name = sheetStack.orEmpty()
                        val photos = albums.filter { it.relativePath in stackPaths }.flatMap { it.items }
                        sheet = AppSheet.NONE
                        askDeleteGroup(name, photos.size) {
                            if (photos.isNotEmpty()) actions.trash(photos)
                            Settings.updateAlbumStacks(Settings.albumStacks.filter { it.name != name })
                        }
                    },
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
                    onAddPhotos = {
                        picker = PickerTarget.IntoFavoriteAlbum(albumName)
                        sheet = AppSheet.NONE
                    },
                    onDelete = {
                        sheet = AppSheet.NONE
                        pendingDelete = { Settings.updateFavoriteAlbums(Settings.favoriteAlbums.filter { it.name != albumName }) }
                    },
                )
            }
            OverlaySheet(visible = sheet == AppSheet.FAVORITE_STACK_MENU, label = sheetStack?.uppercase().orEmpty(), onDismiss = { sheet = AppSheet.NONE }) {
                val stackNames = Settings.favoriteStacks.firstOrNull { it.name == sheetStack }?.paths.orEmpty()
                GroupMenuRows(
                    // Deleting a group of Favorites albums lets the albums go; their photos stay favourites.
                    onDelete = {
                        val name = sheetStack.orEmpty()
                        val photoCount = favoriteAlbumViews.filter { it.name in stackNames }.sumOf { it.items.size }
                        sheet = AppSheet.NONE
                        askDeleteGroup(name, photoCount) {
                            Settings.updateFavoriteAlbums(Settings.favoriteAlbums.filter { it.name !in stackNames })
                            Settings.updateFavoriteStacks(Settings.favoriteStacks.filter { it.name != name })
                        }
                    },
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

            AlbumChoiceSheet(
                visible = sheet == AppSheet.NEW_GROUP_ALBUMS,
                label = newGroupName.uppercase(),
                initiallyTicked = newGroupKeys,
                choices = if (isNewGroupInFavorites) {
                    favoriteAlbumViews.map { it.name to (it.name to it.items.size) }
                } else {
                    albums.map { it.relativePath to (it.name to it.items.size) }
                },
                onCreate = { keys ->
                    newGroupKeys = keys
                    // An album already in another group would leave it, so that is asked first.
                    val stacks = if (isNewGroupInFavorites) Settings.favoriteStacks else Settings.albumStacks
                    if (stacks.any { stack -> stack.name != newGroupName && stack.paths.any { it in keys } }) sheet = AppSheet.NEW_GROUP_MOVE_CHECK else createNewGroup()
                },
                onDismiss = { sheet = AppSheet.NONE },
            )
            run {
                val stacks = if (isNewGroupInFavorites) Settings.favoriteStacks else Settings.albumStacks
                val elsewhere = stacks.filter { stack -> stack.name != newGroupName && stack.paths.any { it in newGroupKeys } }
                val movedCount = elsewhere.sumOf { stack -> stack.paths.count { it in newGroupKeys } }
                OverlaySheet(
                    visible = sheet == AppSheet.NEW_GROUP_MOVE_CHECK,
                    label = "${if (movedCount == 1) "ALBUM" else "$movedCount ALBUMS"} ALREADY IN ${elsewhere.joinToString(", ") { it.name }.uppercase()}",
                    onDismiss = { sheet = AppSheet.NEW_GROUP_ALBUMS },
                ) {
                    SheetRow("Add anyway", trailing = newGroupKeys.size.toString()) { createNewGroup() }
                    SheetRow("Cancel", color = Palette.textMuted) { sheet = AppSheet.NEW_GROUP_ALBUMS }
                }
            }
            // After Confirm, a group that still holds photos asks once more, saying how many.
            var lastCheck by remember { mutableStateOf<GroupDeleteCheck?>(null) }
            groupDeleteCheck?.let { lastCheck = it }
            OverlaySheet(
                visible = sheet == AppSheet.GROUP_DELETE_CHECK,
                label = "${lastCheck?.photoCount ?: 0} PHOTOS IN ${lastCheck?.name?.uppercase().orEmpty()}",
                onDismiss = {
                    sheet = AppSheet.NONE
                    groupDeleteCheck = null
                },
            ) {
                SheetRow("Delete anyway", color = Palette.danger, icon = { TrashIcon(it) }) {
                    groupDeleteCheck?.delete?.invoke()
                    groupDeleteCheck = null
                    sheet = AppSheet.NONE
                }
                SheetRow("Cancel", color = Palette.textMuted) {
                    groupDeleteCheck = null
                    sheet = AppSheet.NONE
                }
            }

            SettingsSheet(
                visible = sheet == AppSheet.SETTINGS,
                isCovers = folderMemory == null,
                onReview = reviewAction,
                onDismiss = { sheet = AppSheet.NONE },
                onPull = { settingsPull = it },
                onAnnounce = actions::announce,
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
                    onDelete = {
                        val group = sheetGroup
                        sheet = AppSheet.NONE
                        if (group != null) askDeleteGroup(group.name, group.items.size) { actions.deleteGroup(group) }
                    },
                    onRename = { sheet = AppSheet.GROUP_RENAME },
                    onSelect = {
                        sheetGroup?.let { selectedCovers = setOf(it.name) }
                        sheet = AppSheet.NONE
                    },
                    onRearrange = {
                        isRearranging = true
                        sheet = AppSheet.NONE
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
                AppSheet.NEW_GROUP_NAME -> NameSheet(
                    label = "NEW GROUP",
                    action = "CHOOSE ALBUMS",
                    onConfirm = { name ->
                        newGroupName = AlbumStack.cleanName(name)
                        newGroupKeys = emptySet()
                        sheet = if (newGroupName.isEmpty()) AppSheet.NONE else AppSheet.NEW_GROUP_ALBUMS
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
                // Photos already in this album, or in any album sorted into a group (any Favorites album for a Favorites album), by the album's name; they are still offered, marked, and adding them asks first.
                val addedTo: Map<Long, String> = remember(target, albums, Settings.albumStacks, Settings.favoriteAlbums) {
                    when (target) {
                        is PickerTarget.IntoAlbum -> {
                            val groupedPaths = if (Settings.groupedAlbumsIn(SettingsView.ALBUMS)) Settings.albumStacks.flatMap { it.paths }.toSet() else emptySet()
                            albums.filter { it.relativePath == target.relativePath || it.relativePath in groupedPaths }.flatMap { album -> album.items.map { it.id to album.name } }.toMap()
                        }
                        is PickerTarget.IntoFavoriteAlbum -> Settings.favoriteAlbums.flatMap { album -> album.ids.map { it to album.name } }.toMap()
                        is PickerTarget.IntoGroup -> emptyMap()
                    }
                }
                PickerScreen(
                    title = target.title,
                    // A Favorites album gathers favourites, so only they are offered.
                    items = if (target is PickerTarget.IntoFavoriteAlbum) favorites else library,
                    addedTo = { addedTo[it.id] },
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
                    isBehindNavigation = isViewerShrunk,
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
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun TopRow(month: VisibleMonth, title: String?, isMonthFilled: Boolean, selectedCount: Int, onBack: (() -> Unit)?, onAdd: (() -> Unit)?, onCancelSelection: () -> Unit, onSettings: () -> Unit, onToggleView: (() -> Unit)? = null, isAlbumsView: Boolean = false) {
    // Selecting swaps the whole row: what stands there pops away, each on its own, then the new buttons pop in; otherwise each button comes and goes on its own as the place changes, the others sliding to make room.
    AnimatedContent(
        targetState = selectedCount > 0,
        transitionSpec = { EnterTransition.None.togetherWith(ExitTransition.None).using(SizeTransform(clip = false)) },
        label = "topRow",
    ) { isSelecting ->
        val pop = Modifier.animateEnterExit(enter = TOP_POP_IN, exit = TOP_POP_OUT)
        // As tall as a button, whether or not one is shown, so the month pill never moves up or down as back and add come and go.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).height(TOP_ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            if (isSelecting) {
                Box(pop) { TopButton(onCancelSelection) { CloseIcon(LocalAccent.current) } }
                Box(Modifier.weight(1f))
                Chip("$selectedCount selected", pop)
            } else {
                // Back sits at the far left, then the month, or the folder's name in its place, which types itself over as it changes; the buttons gather at the right.
                val isMonthShown = month.label.isNotEmpty()
                var lastLabel by remember { mutableStateOf(title ?: month.label.uppercase()) }
                if (isMonthShown) lastLabel = title ?: month.label.uppercase()
                val monthVisibility = remember { MutableTransitionState(isMonthShown) }
                monthVisibility.targetState = isMonthShown
                // Back arriving pushes the pill aside only when it was already showing; arriving together, it pops in where it ends up.
                Box(pop) { MakeRoomButton(onBack, isNeighbourShown = monthVisibility.currentState && isMonthShown) { BackIcon(LocalAccent.current) } }
                AnimatedVisibility(monthVisibility, modifier = pop, enter = TOP_ENTER, exit = TOP_EXIT) {
                    // A section-coloured pill while it names the folder; with the folder named above the nav, the glass pill with the month in the section colour.
                    TypedLabel(
                        lastLabel,
                        LocalAccentTarget.current ?: LocalAccent.current,
                        isFilled = isMonthFilled,
                        fixedWidth = MONTH_CHIP_WIDTH,
                        padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                        isTypedIn = true,
                    )
                }
                Box(Modifier.weight(1f))
                // The toggle's icon shows where a tap goes: the grid of every favourite, or the albums.
                val action = when {
                    onAdd != null -> TopAction.ADD
                    onToggleView != null -> if (isAlbumsView) TopAction.SHOW_GRID else TopAction.SHOW_ALBUMS
                    else -> null
                }
                Box(pop) { TopActionButton(action, onAdd ?: onToggleView) }
                Box(pop.padding(start = 8.dp)) { TopButton(onSettings) { SettingsIcon(Palette.textBright) } }
            }
        }
    }
}

// The back button takes its room and gives it back in two steps: leaving, it pops away and then the month slides into its place; arriving, the month slides over and then it pops in, unless the month arrives with it, when the room is taken at once.
@Composable
private fun MakeRoomButton(onClick: (() -> Unit)?, isNeighbourShown: Boolean, icon: @Composable () -> Unit) {
    var lastClick by remember { mutableStateOf(onClick) }
    if (onClick != null) lastClick = onClick
    val isShown = onClick != null
    val ownAccent = rememberOwnAccent(isShown)
    val room = remember { Animatable(if (isShown) 1f else 0f) }
    val pop = remember { Animatable(if (isShown) 1f else 0f) }
    LaunchedEffect(isShown) {
        if (isShown) {
            if (isNeighbourShown) room.animateTo(1f, tween(Motion.STATE_MS, easing = Motion.powerThreeInOut)) else room.snapTo(1f)
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
            }) { CompositionLocalProvider(LocalAccent provides ownAccent) { TopButton({ lastClick?.invoke() }, icon) } }
        },
    ) { measurables, constraints ->
        val button = measurables.first().measure(constraints.copy(minWidth = 0))
        val width = (button.width * room.value).roundToInt()
        layout(width, button.height) { button.place(0, 0) }
    }
}

// What the bottom bar is showing: the sections, a selection's actions, or the end of rearranging.
private enum class BottomBar { NAVIGATION, PHOTOS, COVERS, REARRANGING }

private val SETTINGS_BLUR = 6.dp
// A top button's height: its 22dp icon and 10dp above and below.
private val TOP_ROW_HEIGHT = 42.dp

// Top buttons pop: they grow in past full size and settle, and shrink away to nothing, in place rather than sliding.
private val TOP_ENTER = fadeIn(tween(Motion.STATE_MS)) + scaleIn(tween(Motion.STATE_MS, easing = Motion.backOut), initialScale = 0f)
private val TOP_EXIT = fadeOut(tween(Motion.STATE_MS, easing = Motion.powerTwoIn)) + scaleOut(tween(Motion.STATE_MS, easing = Motion.backIn), targetScale = 0f)

// What the button beside settings does.
private enum class TopAction { ADD, SHOW_GRID, SHOW_ALBUMS }

// One button beside settings for adding and for switching Favorites' view, so one never leaves while another arrives; a new action or colour pops it away and back in, as the nav's albums icon does.
@Composable
private fun TopActionButton(action: TopAction?, onClick: (() -> Unit)?) {
    var lastClick by remember { mutableStateOf(onClick) }
    if (onClick != null) lastClick = onClick
    val target = LocalAccentTarget.current ?: LocalAccent.current
    // Held while it leaves, so the button goes as it was instead of popping to a new look on its way out.
    var shown by remember { mutableStateOf(action?.let { it to target }) }
    if (action != null) shown = action to target
    AnimatedVisibility(action != null, enter = TOP_ENTER, exit = TOP_EXIT) {
        Box(Modifier.padding(start = 8.dp)) {
            AnimatedContent(
                targetState = shown,
                transitionSpec = { TOP_POP_IN.togetherWith(TOP_POP_OUT).using(SizeTransform(clip = false)) },
                contentAlignment = Alignment.Center,
                label = "top-action",
            ) { look ->
                val (kind, accent) = look ?: return@AnimatedContent
                CompositionLocalProvider(LocalAccent provides accent) {
                    TopButton({ lastClick?.invoke() }) {
                        when (kind) {
                            TopAction.ADD -> PlusIcon(accent)
                            TopAction.SHOW_GRID -> GridIcon(accent)
                            TopAction.SHOW_ALBUMS -> AlbumsIcon(accent)
                        }
                    }
                }
            }
        }
    }
}

// The old look pops away to nothing, then the new one pops in past full size.
private val TOP_POP_IN = scaleIn(tween(Motion.STATE_MS, delayMillis = Motion.STATE_MS, easing = Motion.backOut), initialScale = 0f)
private val TOP_POP_OUT = scaleOut(tween(Motion.STATE_MS, easing = Motion.backIn), targetScale = 0f)

// The round glass button of the top row; the crop screen's back is this same button.
@Composable
fun TopButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(Modifier.pressable(onClick = onClick).glass(Shapes.capsule).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) { icon() }
}

@Composable
private fun Chip(text: String, modifier: Modifier = Modifier) {
    Box(modifier.glass(Shapes.capsule).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        // One line always: a long month must not grow the pill into two.
        BasicText(text.uppercase(), style = Type.microLabel, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

// The count small: the month's number in a slot of its own against the slash, the total in one after it, both as wide as the total's digits, so a number gaining or losing a digit never moves the slash or the other number. Each types itself over when it changes, as the month does.
@Composable
private fun PhotoCount(monthCount: Int, total: Int, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val digits = total.toString().length
    val slotWidth = with(LocalDensity.current) { remember(digits) { measurer.measure("0".repeat(digits), COUNT_STYLE).size.width }.toDp() }
    val numberStyle = COUNT_STYLE.copy(color = Palette.textBright, shadow = Type.dropShadow)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(slotWidth), contentAlignment = Alignment.CenterEnd) { TypewriterText(monthCount.toString(), numberStyle, isCaretShown = false) }
        BasicText("/", style = COUNT_STYLE.copy(color = Palette.textMuted, shadow = Type.dropShadow), modifier = Modifier.padding(horizontal = 2.dp))
        Box(Modifier.width(slotWidth), contentAlignment = Alignment.CenterStart) { TypewriterText(total.toString(), numberStyle, isCaretShown = false) }
    }
}

// Every digit as wide as every other, so a number's width depends only on how many digits it has.
private val COUNT_STYLE = Type.microLabel.copy(fontSize = 8.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum")

private fun List<MediaItem>.favoritesOnlyIf(isFavoritesOnly: Boolean): List<MediaItem> = if (isFavoritesOnly) filter { it.isFavorite } else this

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
