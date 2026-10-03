package com.vaditim.gallery.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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

// Where an open viewer gets its items from. Resolved from the live library on every frame, so a photo moved, favourited or deleted while it is open is reflected without the viewer holding a stale copy.
sealed interface ViewerSource {
    data object Recent : ViewerSource
    data object Favorites : ViewerSource
    data class InAlbum(val albumId: Long) : ViewerSource
    data class InPrivateGroup(val name: String) : ViewerSource
    data object PrivateFavorites : ViewerSource
}

data class ViewerRequest(val source: ViewerSource, val startIndex: Int)

// Where the Albums section stands. The private places are only reachable while Private is unlocked.
private sealed interface AlbumsPlace {
    data object Folders : AlbumsPlace
    data class Folder(val albumId: Long) : AlbumsPlace
    data object PrivateGroups : AlbumsPlace
    data class PrivateFolder(val name: String) : AlbumsPlace
    data object PrivateFavorites : AlbumsPlace
}

private val AlbumsPlace.isPrivate: Boolean
    get() = this is AlbumsPlace.PrivateGroups || this is AlbumsPlace.PrivateFolder || this is AlbumsPlace.PrivateFavorites

// The sheets the app itself opens — for a selection or for a long-pressed album. The viewer has its own.
private enum class AppSheet {
    NONE,
    NEW_ALBUM,
    ITEM_MENU,
    SELECTION_MOVE, SELECTION_NEW_ALBUM, SELECTION_GROUP, SELECTION_NEW_GROUP,
    ALBUM_MENU, ALBUM_GROUP, ALBUM_NEW_GROUP,
    GROUP_MENU, GROUP_MOVE_OUT, GROUP_MOVE_OUT_NEW_ALBUM,
    PRIVATE_NEW_GROUP,
}

// Where the photo picker puts what is picked: an album folder (new or existing), or a private group.
private sealed interface PickerTarget {
    val title: String
    data class IntoAlbum(val relativePath: String, val name: String) : PickerTarget { override val title get() = "Add to $name" }
    data class IntoGroup(val name: String) : PickerTarget { override val title get() = "Add to Private · $name" }
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
    val actions = rememberMediaActions(viewModel.repository, viewModel.vault, onPrivateChanged = { viewModel.refreshPrivate() })
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var section by remember { mutableStateOf(Section.RECENT) }
    var albumsPlace by remember { mutableStateOf<AlbumsPlace>(AlbumsPlace.Folders) }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    // `viewer` is what is wanted open; `shownViewer` stays composed through the closing animation.
    var shownViewer by remember { mutableStateOf<ViewerRequest?>(null) }
    var viewerCurrentId by remember { mutableStateOf<Long?>(null) }
    var viewerRect by remember { mutableStateOf<Rect?>(null) }
    val viewerProgress = remember { Animatable(0f) }
    var scrollToNewestRequest by remember { mutableIntStateOf(0) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var isDeleteArmed by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(AppSheet.NONE) }
    var sheetAlbum by remember { mutableStateOf<Album?>(null) }
    var sheetGroup by remember { mutableStateOf<PrivateGroup?>(null) }
    var sheetItem by remember { mutableStateOf<MediaItem?>(null) }
    var isMenuDeleteArmed by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    LaunchedEffect(sheet) { if (sheet != AppSheet.ALBUM_MENU && sheet != AppSheet.GROUP_MENU) isMenuDeleteArmed = false }

    val recentMemory = remember { GridMemory() }
    val favoritesMemory = remember { GridMemory() }
    val privateFavoritesMemory = remember { GridMemory() }
    val albumMemories = remember { mutableMapOf<Long, GridMemory>() }
    val privateMemories = remember { mutableMapOf<String, GridMemory>() }
    val albumsListState = rememberLazyGridState()
    val hazeState = rememberHazeState()

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

    // The items of the grid on screen, which is what a selection is made of.
    val gridItems: List<MediaItem> = when {
        section == Section.RECENT -> library
        section == Section.FAVORITES -> favorites
        openAlbum != null -> openAlbum.items
        openPrivateGroup != null -> openPrivateGroup.items
        place is AlbumsPlace.PrivateFavorites -> privateContents.favorites
        else -> emptyList()
    }
    val selectedItems = gridItems.filter { it.id in selectedIds }
    val isSelecting = selectedItems.isNotEmpty()
    // Inside an album or private group a long press opens the item menu (Select / Set as Cover) rather than starting a selection straight away.
    val onStartSelection: ((MediaItem) -> Unit)? = if (openAlbum != null || openPrivateGroup != null) { item ->
        sheetItem = item
        sheet = AppSheet.ITEM_MENU
    } else null
    val selection = Selection(selectedIds, onStart = onStartSelection) { item ->
        selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
        isDeleteArmed = false
    }
    val clearSelection = {
        selectedIds = emptySet()
        isDeleteArmed = false
        sheet = AppSheet.NONE
    }
    LaunchedEffect(section, albumsPlace) { clearSelection() }
    BackHandler(enabled = isSelecting) { clearSelection() }

    // Leaving the app locks Private again, and drops anyone standing in it back to the albums list.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.lockPrivate() }
    LaunchedEffect(isPrivateUnlocked) {
        if (!isPrivateUnlocked) {
            if (albumsPlace.isPrivate) albumsPlace = AlbumsPlace.Folders
            if (viewer?.source.isPrivateSource()) viewer = null
        }
    }
    // Private stays out of screenshots and the recent-apps preview while it is on screen.
    val isShowingPrivate = isInPrivate || viewer?.source.isPrivateSource()
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
        ViewerSource.Recent -> library
        ViewerSource.Favorites -> favorites
        is ViewerSource.InAlbum -> albums.firstOrNull { it.id == source.albumId }?.items.orEmpty()
        is ViewerSource.InPrivateGroup -> if (isPrivateUnlocked) privateContents.groups.firstOrNull { it.name == source.name }?.items.orEmpty() else emptyList()
        ViewerSource.PrivateFavorites -> if (isPrivateUnlocked) privateContents.favorites else emptyList()
    }

    val folderMemory = when {
        openAlbum != null -> albumMemories.getOrPut(openAlbum.id) { GridMemory() }
        openPrivateGroup != null -> privateMemories.getOrPut(openPrivateGroup.name) { GridMemory() }
        place is AlbumsPlace.PrivateFavorites -> privateFavoritesMemory
        section == Section.RECENT -> recentMemory
        section == Section.FAVORITES -> favoritesMemory
        else -> null
    }

    LaunchedEffect(viewer) {
        val request = viewer
        if (request != null) {
            val openedId = itemsFor(request.source).getOrNull(request.startIndex)?.id
            viewerCurrentId = openedId
            viewerRect = TileBounds.of(openedId)
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
            viewerProgress.animateTo(0f, tween(Motion.VIEWER_CLOSE_MS, easing = Motion.powerThreeInOut))
            shownViewer = null
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
                when (shown) {
                    Section.RECENT -> MediaGrid(
                        items = library,
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
                            albums = albums,
                            state = albumsListState,
                            onOpen = { albumsPlace = AlbumsPlace.Folder(it.id) },
                            onLongPress = { album ->
                                sheetAlbum = album
                                sheet = AppSheet.ALBUM_MENU
                            },
                            onNewAlbum = { sheet = AppSheet.NEW_ALBUM },
                            contentPadding = insetPadding,
                            footer = { PrivateEntry(isPrivateUnlocked, privateContents.groups.size, onClick = openPrivate) },
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
                        AlbumsPlace.PrivateGroups -> PrivateGroupsScreen(
                            groups = privateContents.groups,
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
                    Section.FAVORITES -> MediaGrid(
                        items = favorites,
                        memory = favoritesMemory,
                        onOpen = { viewer = ViewerRequest(ViewerSource.Favorites, it) },
                        contentPadding = insetPadding,
                        selection = selection,
                        scrollToNewestRequest = scrollToNewestRequest,
                        emptyCaption = "Nothing favourited yet.",
                    )
                }
            }

            // Everything from here up floats over the content and blurs it; none of it is inside the haze source, or it would blur itself.
            Box(Modifier.fillMaxWidth().height(statusBarHeight + HEADER_ROOM + 24.dp).fadingGlass())

            TopRow(
                backLabel = when {
                    openAlbum != null -> openAlbum.name
                    openPrivateGroup != null -> "Private · ${openPrivateGroup.name}"
                    place is AlbumsPlace.PrivateFavorites -> "Private · Favorites"
                    else -> null
                },
                month = folderMemory?.let { rememberVisibleMonth(gridItems, it).value } ?: "",
                selectedCount = selectedItems.size,
                onAdd = when {
                    openAlbum != null -> { { picker = PickerTarget.IntoAlbum(openAlbum.relativePath, openAlbum.name) } }
                    openPrivateGroup != null -> { { picker = PickerTarget.IntoGroup(openPrivateGroup.name) } }
                    else -> null
                },
                onBack = {
                    albumsPlace = if (place is AlbumsPlace.Folder) AlbumsPlace.Folders else AlbumsPlace.PrivateGroups
                },
                onCancelSelection = clearSelection,
            )

            val barModifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp)
            if (isSelecting) {
                Row(barModifier.glass(Shapes.capsule).padding(5.dp)) {
                    ActionButton("SHARE") { actions.share(selectedItems) }
                    if (isInPrivate) {
                        ActionButton("GROUP") { sheet = AppSheet.SELECTION_GROUP }
                        ActionButton("OUT") { sheet = AppSheet.SELECTION_MOVE }
                        ActionButton(if (isDeleteArmed) "FOREVER?" else "DELETE", color = Palette.danger) {
                            if (isDeleteArmed) {
                                actions.deletePrivate(selectedItems)
                                clearSelection()
                            } else {
                                isDeleteArmed = true
                            }
                        }
                    } else {
                        ActionButton("MOVE") { sheet = AppSheet.SELECTION_MOVE }
                        ActionButton("PRIVATE") { sheet = AppSheet.SELECTION_GROUP }
                        ActionButton("DELETE", color = Palette.danger) {
                            actions.trash(selectedItems)
                            clearSelection()
                        }
                    }
                }
            } else {
                SectionBar(
                    active = section,
                    onSelect = { selected ->
                        if (selected == section) {
                            if (selected == Section.ALBUMS) albumsPlace = AlbumsPlace.Folders else scrollToNewestRequest++
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
                    if (isInPrivate) actions.moveToGroup(selectedItems, name) else actions.hide(selectedItems, name)
                    clearSelection()
                },
                onNewGroup = { sheet = AppSheet.SELECTION_NEW_GROUP },
                onDismiss = { sheet = AppSheet.NONE },
            )

            // A long-pressed album: one entry, because moving the whole folder into Private is the thing it is for.
            OverlaySheet(visible = sheet == AppSheet.ALBUM_MENU, label = sheetAlbum?.name?.uppercase().orEmpty(), onDismiss = { sheet = AppSheet.NONE }) {
                SheetRow("Add photos") {
                    sheetAlbum?.let { picker = PickerTarget.IntoAlbum(it.relativePath, it.name) }
                    sheet = AppSheet.NONE
                }
                SheetRow("Move album to private", trailing = sheetAlbum?.items?.size?.toString()) { sheet = AppSheet.ALBUM_GROUP }
                SheetRow(
                    if (isMenuDeleteArmed) "Tap again: ${sheetAlbum?.items?.size ?: 0} photos to the trash" else "Delete album",
                    color = Palette.danger,
                ) {
                    if (isMenuDeleteArmed) {
                        sheetAlbum?.let { actions.trash(it.items) }
                        sheet = AppSheet.NONE
                    } else {
                        isMenuDeleteArmed = true
                    }
                }
            }

            // A long-pressed photo inside an album or private group: start selecting, or below the divider make it the cover.
            OverlaySheet(visible = sheet == AppSheet.ITEM_MENU, label = "PHOTO", onDismiss = { sheet = AppSheet.NONE }) {
                SheetRow("Select") {
                    sheetItem?.let { selection.onToggle(it) }
                    sheet = AppSheet.NONE
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).height(1.dp).background(Palette.borderStrong))
                SheetRow("Set as Cover") {
                    sheetItem?.let { item ->
                        when {
                            openAlbum != null -> viewModel.setAlbumCover(openAlbum.id, item)
                            openPrivateGroup != null -> viewModel.setGroupCover(openPrivateGroup.name, item)
                        }
                    }
                    sheet = AppSheet.NONE
                }
            }

            // A long-pressed private group. Deleting one is final — private photos are outside the system trash — so it takes a second tap.
            OverlaySheet(visible = sheet == AppSheet.GROUP_MENU, label = sheetGroup?.name?.uppercase().orEmpty(), onDismiss = { sheet = AppSheet.NONE }) {
                SheetRow("Add photos") {
                    sheetGroup?.let { picker = PickerTarget.IntoGroup(it.name) }
                    sheet = AppSheet.NONE
                }
                SheetRow("Move group out to album", trailing = sheetGroup?.items?.size?.toString()) { sheet = AppSheet.GROUP_MOVE_OUT }
                SheetRow(
                    if (isMenuDeleteArmed) "Tap again: delete ${sheetGroup?.items?.size ?: 0} photos forever" else "Delete group",
                    color = Palette.danger,
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
                label = "MOVE ${sheetGroup?.name?.uppercase().orEmpty()} OUT TO",
                albums = albums,
                excludedAlbumId = null,
                onPick = { album ->
                    sheetGroup?.let { group -> actions.unhide(group.items, album) { viewModel.vault.removeGroupIfEmpty(group.name) } }
                    sheet = AppSheet.NONE
                },
                onNewAlbum = { sheet = AppSheet.GROUP_MOVE_OUT_NEW_ALBUM },
                onDismiss = { sheet = AppSheet.NONE },
            )
            GroupPickerSheet(
                visible = sheet == AppSheet.ALBUM_GROUP,
                label = "MOVE ${sheetAlbum?.name?.uppercase().orEmpty()} TO",
                groups = privateContents.groups,
                excludedGroupName = null,
                onPick = { name ->
                    sheetAlbum?.let { actions.hide(it.items, name) }
                    sheet = AppSheet.NONE
                },
                onNewGroup = { sheet = AppSheet.ALBUM_NEW_GROUP },
                onDismiss = { sheet = AppSheet.NONE },
            )

            when (sheet) {
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
                    initialName = sheetGroup?.name.orEmpty(),
                    onConfirm = { name ->
                        sheetGroup?.let { group -> actions.unhide(group.items, newAlbumPath(name), name) { viewModel.vault.removeGroupIfEmpty(group.name) } }
                        sheet = AppSheet.NONE
                    },
                    onDismiss = { sheet = AppSheet.NONE },
                )
                AppSheet.SELECTION_NEW_GROUP -> NameSheet(
                    label = "NEW PRIVATE GROUP",
                    action = "MOVE HERE",
                    onConfirm = { name ->
                        if (isInPrivate) actions.moveToGroup(selectedItems, name) else actions.hide(selectedItems, name)
                        clearSelection()
                    },
                    onDismiss = { sheet = AppSheet.NONE },
                )
                AppSheet.ALBUM_NEW_GROUP -> NameSheet(
                    label = "NEW PRIVATE GROUP",
                    action = "MOVE HERE",
                    initialName = sheetAlbum?.name.orEmpty(),
                    onConfirm = { name ->
                        sheetAlbum?.let { actions.hide(it.items, name) }
                        sheet = AppSheet.NONE
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
                val alreadyThere = (target as? PickerTarget.IntoAlbum)?.let { into -> albums.firstOrNull { it.relativePath == into.relativePath }?.items?.map { it.id }?.toSet() }.orEmpty()
                PickerScreen(
                    title = target.title,
                    items = library.filter { it.id !in alreadyThere },
                    action = "Add",
                    onDone = { picked ->
                        when (target) {
                            is PickerTarget.IntoAlbum -> actions.move(picked, target.relativePath, target.name)
                            is PickerTarget.IntoGroup -> actions.hide(picked, target.name)
                        }
                        picker = null
                    },
                    onCancel = { picker = null },
                )
            }

            // The viewer grows out of the tile it was opened from and shrinks back into the tile of the photo it ends on; when that tile is not on screen it falls back to a quiet fade.
            shownViewer?.let { request ->
                Box(
                    Modifier
                        .fillMaxSize()
                        // Progress is read inside the draw lambdas, so the animation never recomposes the library.
                        .graphicsLayer {
                            val p = viewerProgress.value
                            val rect = viewerRect
                            if (rect == null) {
                                alpha = p
                                scaleX = 0.94f + 0.06f * p
                                scaleY = scaleX
                            } else {
                                val startScale = rect.width / size.width
                                scaleX = startScale + (1f - startScale) * p
                                scaleY = scaleX
                                translationX = (rect.center.x - size.width / 2f) * (1f - p)
                                translationY = (rect.center.y - size.height / 2f) * (1f - p)
                                alpha = (p * 4f).coerceAtMost(1f)
                            }
                        }
                        .drawWithContent {
                            val p = viewerProgress.value
                            val rect = viewerRect
                            if (rect == null) {
                                drawContent()
                            } else {
                                val startScale = rect.width / size.width
                                val clipHeight = rect.height / startScale + (size.height - rect.height / startScale) * p
                                val radius = 8.dp.toPx() / startScale * (1f - p)
                                val clip = Path().apply {
                                    addRoundRect(RoundRect(0f, (size.height - clipHeight) / 2f, size.width, (size.height + clipHeight) / 2f, CornerRadius(radius)))
                                }
                                clipPath(clip) { this@drawWithContent.drawContent() }
                            }
                        },
                ) {
                    ViewerScreen(
                        items = itemsFor(request.source),
                        startIndex = request.startIndex,
                        albums = albums,
                        privateGroups = privateContents.groups,
                        isPrivate = request.source.isPrivateSource(),
                        actions = actions,
                        onClose = { viewer = null },
                        onCurrentChanged = { viewerCurrentId = it },
                    )
                }
            }
        }
    }
}

private fun ViewerSource?.isPrivateSource(): Boolean = this is ViewerSource.InPrivateGroup || this is ViewerSource.PrivateFavorites

// The top layer: the month you are looking at, the way back out of a folder, or — while selecting — the count and the way out of the selection.
@Composable
private fun TopRow(backLabel: String?, month: String, selectedCount: Int, onAdd: (() -> Unit)?, onBack: () -> Unit, onCancelSelection: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectedCount > 0) {
            Box(Modifier.pressable(onClick = onCancelSelection).glass(Shapes.capsule).padding(horizontal = 16.dp, vertical = 11.dp)) {
                BasicText("Cancel", style = Type.cardTitle.copy(color = LocalAccent.current))
            }
            Box(Modifier.weight(1f))
            Chip("$selectedCount selected")
        } else {
            if (backLabel != null) {
                Box(Modifier.weight(1f, fill = false).pressable(onClick = onBack).glass(Shapes.capsule).padding(horizontal = 16.dp, vertical = 11.dp)) {
                    BasicText("‹  $backLabel", style = Type.cardTitle.copy(color = LocalAccent.current), maxLines = 1)
                }
                Box(Modifier.weight(0.01f))
            }
            if (month.isNotEmpty()) Chip(month)
            if (onAdd != null) {
                Box(Modifier.padding(start = 8.dp).pressable(onClick = onAdd).glass(Shapes.capsule).padding(horizontal = 16.dp, vertical = 11.dp)) {
                    BasicText("+ Add", style = Type.cardTitle.copy(color = LocalAccent.current))
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Box(Modifier.glass(Shapes.capsule).padding(horizontal = 14.dp, vertical = 10.dp)) {
        MicroLabel(text)
    }
}
