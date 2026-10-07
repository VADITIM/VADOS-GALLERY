package com.vaditim.gallery.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.albums.AlbumScreen
import com.vaditim.gallery.albums.AlbumsScreen
import com.vaditim.gallery.albums.FolderDivider
import com.vaditim.gallery.albums.FolderEntries
import com.vaditim.gallery.albums.LocationScreen
import com.vaditim.gallery.albums.LocationsScreen
import com.vaditim.gallery.albums.PrivateFavoriteGroupsScreen
import com.vaditim.gallery.albums.PrivateGroupsScreen
import com.vaditim.gallery.albums.PrivateItemsScreen
import com.vaditim.gallery.albums.TrashScreen
import com.vaditim.gallery.components.LocalSettingsView
import com.vaditim.gallery.components.MediaGrid
import com.vaditim.gallery.components.Section
import com.vaditim.gallery.settings.AlbumArrangement
import com.vaditim.gallery.settings.Settings
import com.vaditim.gallery.settings.SettingsView
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Motion

// Opening a folder (an album, Private, a private group) eases in from slightly small; closing it is the same, quieter.
private fun <S> AnimatedContentTransitionScope<S>.folderTransition(): ContentTransform =
    (fadeIn(tween(Motion.SECTION_ENTER_MS, Motion.SECTION_ENTER_DELAY_MS, Motion.powerTwoOut)) +
        scaleIn(tween(Motion.SECTION_ENTER_MS, Motion.SECTION_ENTER_DELAY_MS, Motion.powerTwoOut), initialScale = 0.96f))
        .togetherWith(fadeOut(tween(Motion.SECTION_LEAVE_MS, easing = Motion.powerTwoIn)))

// One section's screens, the one shown or the one leaving; each reads only what it shows.
@Composable
fun SectionContent(shown: Section, isPrivateShown: Boolean, controller: LibraryController, content: LibraryContent, screen: LibraryScreen, contentPadding: PaddingValues) {
    when {
        isPrivateShown && shown == Section.RECENT -> PrivateRecent(controller, content, contentPadding)
        isPrivateShown && shown == Section.FAVORITES -> PrivateFavorites(controller, content, contentPadding)
        shown == Section.RECENT -> MediaGrid(
            items = content.recent,
            memory = controller.memories.recent,
            onOpen = { controller.openViewer(ViewerSource.Recent, it) },
            contentPadding = contentPadding,
            selection = controller.selection.photos,
            scrollToNewestRequest = controller.navigation.scrollToNewestRequest,
            emptyCaption = "No photos yet.",
        )
        shown == Section.ALBUMS -> AlbumsSection(controller, content, screen, contentPadding)
        else -> FavoritesSection(controller, content, screen, contentPadding)
    }
}

@Composable
private fun PrivateRecent(controller: LibraryController, content: LibraryContent, contentPadding: PaddingValues) {
    MediaGrid(
        items = content.privateRecent,
        memory = controller.memories.privateRecent,
        onOpen = { controller.openViewer(ViewerSource.PrivateRecent, it) },
        contentPadding = contentPadding,
        selection = controller.selection.photos,
        scrollToNewestRequest = controller.navigation.scrollToNewestRequest,
        emptyCaption = "No photos yet.",
    )
}

@Composable
private fun PrivateFavorites(controller: LibraryController, content: LibraryContent, contentPadding: PaddingValues) {
    val navigation = controller.navigation
    AnimatedContent(
        targetState = navigation.openPrivateFavoriteGroup ?: if (Settings.privateFavoritesAsGroups) PRIVATE_FAVORITE_GROUPS else PRIVATE_FAVORITE_GRID,
        transitionSpec = { folderTransition() },
        label = "privateFavorites",
    ) { shownView ->
        when (shownView) {
            PRIVATE_FAVORITE_GRID -> MediaGrid(
                items = content.privateContents.favorites,
                memory = controller.memories.privateFavorites,
                onOpen = { controller.openViewer(ViewerSource.PrivateFavorites, it) },
                contentPadding = contentPadding,
                selection = controller.selection.photos,
                scrollToNewestRequest = navigation.scrollToNewestRequest,
                emptyCaption = "Nothing favourited yet.",
            )
            PRIVATE_FAVORITE_GROUPS -> PrivateFavoriteGroupsScreen(
                groups = content.privateFavoriteGroups,
                onOpen = { navigation.openPrivateFavoriteGroup = it.name },
                contentPadding = contentPadding,
                state = controller.memories.privateFavoriteGroupsList,
            )
            else -> content.privateFavoriteGroup(shownView)?.let { group ->
                MediaGrid(
                    items = group.items,
                    memory = controller.memories.privateFavoriteGroup(group.name),
                    onOpen = { controller.openViewer(ViewerSource.InPrivateFavoriteGroup(group.name), it) },
                    contentPadding = contentPadding,
                    selection = controller.selection.photos,
                )
            }
        }
    }
}

@Composable
private fun AlbumsSection(controller: LibraryController, content: LibraryContent, screen: LibraryScreen, contentPadding: PaddingValues) {
    val navigation = controller.navigation
    val selection = controller.selection
    val sheets = controller.sheets
    val memories = controller.memories
    AnimatedContent(targetState = navigation.albumsPlace, transitionSpec = { folderTransition() }, label = "place") { shownPlace ->
        CompositionLocalProvider(
            LocalAccent provides (shownPlace.accent ?: LocalAccent.current),
            // Each place keeps its own view's settings while it leaves, so the albums never take Locations' columns, nor Locations the albums'.
            LocalSettingsView provides shownPlace.settingsView,
        ) {
            when (shownPlace) {
                AlbumsPlace.Folders -> AlbumsScreen(
                    openStacks = navigation.openAlbumStacks,
                    onOpenStacksChange = { opened ->
                        // A group closing lets go of the selection, so no album stays picked out of sight.
                        if (screen.isSelectingCovers && !opened.containsAll(navigation.openAlbumStacks)) controller.clearSelection()
                        navigation.openAlbumStacks = opened
                    },
                    albums = content.arrangedAlbums,
                    isRearranging = selection.isRearranging,
                    onStartRearranging = controller::startRearranging,
                    onRegroup = { album, group -> controller.regroup(AlbumShelfKind.FOLDERS, album, group) },
                    onArrange = { AlbumArrangement.albumOrder.update(it) },
                    selectedPaths = selection.covers,
                    onToggle = { picked -> selection.toggleCovers(picked.map { it.relativePath }) },
                    state = memories.albumsList,
                    onOpen = { navigation.albumsPlace = AlbumsPlace.Folder(it.id) },
                    onLongPress = { album -> sheets.showAlbumMenu(album, AlbumShelfKind.FOLDERS) },
                    onStackLongPress = { name -> sheets.showStackMenu(name, AlbumShelfKind.FOLDERS) },
                    onNewAlbum = { sheets.show(AppSheet.NEW_ALBUM) },
                    onNewGroup = if (Settings.groupedAlbumsIn(SettingsView.ALBUMS)) { { sheets.startNewGroup(AlbumShelfKind.FOLDERS) } } else null,
                    contentPadding = contentPadding,
                    footer = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            FolderDivider()
                            FolderEntries(
                                onPrivate = controller::openPrivate,
                                onLocations = { navigation.albumsPlace = AlbumsPlace.Locations },
                                onTrash = { navigation.albumsPlace = AlbumsPlace.Trash },
                                trashCount = content.trash.size,
                            )
                        }
                    },
                )
                is AlbumsPlace.Folder -> content.album(shownPlace.albumId)?.let { album ->
                    AlbumScreen(
                        album = album,
                        memory = memories.album(album.id),
                        onOpen = { controller.openViewer(ViewerSource.InAlbum(album.id), it) },
                        onBack = { navigation.albumsPlace = AlbumsPlace.Folders },
                        contentPadding = contentPadding,
                        selection = selection.photos,
                    )
                }
                AlbumsPlace.Trash -> TrashScreen(
                    items = content.trash,
                    memory = memories.trash,
                    onOpen = { controller.openViewer(ViewerSource.Trash, it) },
                    onBack = { navigation.albumsPlace = AlbumsPlace.Folders },
                    contentPadding = contentPadding,
                    selection = selection.photos,
                )
                AlbumsPlace.Locations -> LocationsScreen(
                    groups = content.locations,
                    onOpen = { navigation.albumsPlace = AlbumsPlace.Location(it.key) },
                    onBack = { navigation.albumsPlace = AlbumsPlace.Folders },
                    contentPadding = contentPadding,
                    state = memories.locationsList,
                )
                is AlbumsPlace.Location -> content.location(shownPlace.key)?.let { group ->
                    LocationScreen(
                        items = group.items,
                        memory = memories.location(group.key),
                        onOpen = { controller.openViewer(ViewerSource.InLocation(group.key), it) },
                        onBack = { navigation.albumsPlace = AlbumsPlace.Locations },
                        contentPadding = contentPadding,
                        selection = selection.photos,
                    )
                }
                AlbumsPlace.PrivateGroups -> PrivateGroupsScreen(
                    groups = content.arrangedGroups,
                    isRearranging = selection.isRearranging,
                    onStartRearranging = controller::startRearranging,
                    selectedNames = selection.covers,
                    onToggle = { selection.toggleCovers(listOf(it.name)) },
                    onMove = { from, to ->
                        val names = content.arrangedGroups.map { it.name }.toMutableList()
                        names.add(to, names.removeAt(from))
                        AlbumArrangement.groupOrder.update(names)
                    },
                    favorites = content.privateContents.favorites,
                    onOpen = { navigation.albumsPlace = AlbumsPlace.PrivateFolder(it.name) },
                    onLongPress = sheets::showGroupMenu,
                    onOpenSelection = { controller.openViewer(ViewerSource.PrivateFavorites, it) },
                    onNewGroup = { sheets.show(AppSheet.PRIVATE_NEW_GROUP) },
                    onTrash = { navigation.albumsPlace = AlbumsPlace.PrivateTrash },
                    trashCount = content.privateContents.trash.size,
                    onBack = navigation::leavePrivate,
                    contentPadding = contentPadding,
                    state = memories.privateGroupsList,
                    isViewerOpen = controller.viewer.shown != null,
                )
                AlbumsPlace.PrivateTrash -> TrashScreen(
                    items = content.privateContents.trash,
                    memory = memories.privateTrash,
                    onOpen = { controller.openViewer(ViewerSource.PrivateTrash, it) },
                    onBack = { navigation.albumsPlace = AlbumsPlace.PrivateGroups },
                    contentPadding = contentPadding,
                    selection = selection.photos,
                )
                is AlbumsPlace.PrivateFolder -> content.privateGroup(shownPlace.name)?.let { group ->
                    PrivateItemsScreen(
                        items = group.items,
                        memory = memories.privateGroup(group.name),
                        onOpen = { controller.openViewer(ViewerSource.InPrivateGroup(group.name), it) },
                        onBack = { navigation.albumsPlace = AlbumsPlace.PrivateGroups },
                        contentPadding = contentPadding,
                        selection = selection.photos,
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritesSection(controller: LibraryController, content: LibraryContent, screen: LibraryScreen, contentPadding: PaddingValues) {
    val navigation = controller.navigation
    val selection = controller.selection
    val sheets = controller.sheets
    AnimatedContent(targetState = screen.favoritesView, transitionSpec = { folderTransition() }, label = "favorites") { shownView ->
        when (shownView) {
            FavoritesView.All -> MediaGrid(
                items = content.favorites,
                memory = controller.memories.favorites,
                onOpen = { controller.openViewer(ViewerSource.Favorites, it) },
                contentPadding = contentPadding,
                selection = selection.photos,
                scrollToNewestRequest = navigation.scrollToNewestRequest,
                emptyCaption = "Nothing favourited yet.",
            )
            FavoritesView.Albums -> AlbumsScreen(
                albums = content.favoriteAlbums,
                title = "Favorites",
                isAccented = true,
                stacks = if (Settings.groupedAlbumsIn(SettingsView.FAVORITES)) AlbumArrangement.favoriteStacks.all else emptyList(),
                openStacks = navigation.openFavoriteStacks,
                onOpenStacksChange = { opened ->
                    // A group closing lets go of the selection, so no album stays picked out of sight.
                    if (screen.isSelectingCovers && !opened.containsAll(navigation.openFavoriteStacks)) controller.clearSelection()
                    navigation.openFavoriteStacks = opened
                },
                isRearranging = selection.isRearranging,
                onStartRearranging = controller::startRearranging,
                onRegroup = { album, group -> controller.regroup(AlbumShelfKind.FAVORITES, album, group) },
                onArrange = { AlbumArrangement.favoriteAlbumOrder.update(it) },
                selectedPaths = selection.covers,
                onToggle = { picked -> selection.toggleCovers(picked.map { it.relativePath }) },
                state = controller.memories.favoriteAlbumsList,
                onOpen = { navigation.openFavoriteAlbum = it.name },
                onLongPress = { album -> sheets.showAlbumMenu(album, AlbumShelfKind.FAVORITES) },
                onStackLongPress = { name -> sheets.showStackMenu(name, AlbumShelfKind.FAVORITES) },
                onNewAlbum = { sheets.show(AppSheet.FAVORITE_NEW_ALBUM) },
                onNewGroup = if (Settings.groupedAlbumsIn(SettingsView.FAVORITES)) { { sheets.startNewGroup(AlbumShelfKind.FAVORITES) } } else null,
                contentPadding = contentPadding,
            )
            is FavoritesView.InAlbum -> content.favoriteAlbum(shownView.name)?.let { album ->
                AlbumScreen(
                    album = album,
                    memory = controller.memories.favoriteAlbum(album.name),
                    onOpen = { controller.openViewer(ViewerSource.InFavoriteAlbum(album.name), it) },
                    onBack = { navigation.openFavoriteAlbum = null },
                    contentPadding = contentPadding,
                    selection = selection.photos,
                )
            }
        }
    }
}
