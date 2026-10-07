package com.vaditim.gallery.library

import androidx.compose.ui.graphics.Color
import com.vaditim.gallery.components.GridMemory
import com.vaditim.gallery.components.PlaceGlyph
import com.vaditim.gallery.components.Section
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.media.LocationGroup
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.settings.Settings
import com.vaditim.gallery.settings.SettingsView
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vault.PrivateGroup

// What is on screen, worked out again only when something it reads changes, from where the user stands and what the library holds: the folder open, its photos, its name, and what the bars around it offer.
class LibraryScreen(
    private val navigation: LibraryNavigation,
    private val content: LibraryContent,
    private val selection: LibrarySelection,
    private val memories: GridMemories,
) {
    val section: Section = navigation.section
    val isPrivateMode: Boolean = navigation.isPrivateMode
    val place: AlbumsPlace? = navigation.place

    val openAlbum: Album? = (place as? AlbumsPlace.Folder)?.let { content.album(it.albumId) }
    val openPrivateGroup: PrivateGroup? = (place as? AlbumsPlace.PrivateFolder)?.let { content.privateGroup(it.name) }
    val openLocation: LocationGroup? = (place as? AlbumsPlace.Location)?.let { content.location(it.key) }
    val openFavorite: Album? = navigation.openFavoriteAlbum?.let { content.favoriteAlbum(it) }
    val openPrivateFavorite: PrivateGroup? = navigation.openPrivateFavoriteGroup?.let { content.privateFavoriteGroup(it) }
    val isPrivateFavoritesGrouped: Boolean = Settings.privateFavoritesAsGroups && openPrivateFavorite == null
    val isInFavoriteAlbum: Boolean = section == Section.FAVORITES && openFavorite != null

    val favoritesView: FavoritesView = when {
        openFavorite != null -> FavoritesView.InAlbum(openFavorite.name)
        Settings.favoritesAsAlbums -> FavoritesView.Albums
        else -> FavoritesView.All
    }

    // Private has a colour of its own across every section.
    val accentTarget: Color = if (isPrivateMode) Palette.privateRed else (if (section == Section.ALBUMS) navigation.albumsPlace.accent else null) ?: section.accent

    fun accentOf(shown: Section, isPrivate: Boolean = isPrivateMode): Color = when {
        isPrivate -> Palette.privateRed
        shown == Section.ALBUMS -> navigation.albumsPlace.accent ?: shown.accent
        else -> shown.accent
    }

    // The view whose own settings apply and which the settings sheet names.
    val settingsView: SettingsView = when {
        isPrivateMode -> SettingsView.PRIVATE
        section == Section.RECENT -> SettingsView.RECENT
        section == Section.FAVORITES -> SettingsView.FAVORITES
        place != null && place.isInLocations -> SettingsView.LOCATIONS
        place == AlbumsPlace.Trash -> SettingsView.TRASH
        else -> SettingsView.ALBUMS
    }

    // The name of the photo grid on screen; a grid of covers is not inside anything, and the trash has its own pill.
    val folderName: String? = when {
        section == Section.RECENT -> "RECENT"
        isPrivateMode && section == Section.FAVORITES -> openPrivateFavorite?.name ?: if (isPrivateFavoritesGrouped) null else "FAVORITES"
        section == Section.FAVORITES -> openFavorite?.name ?: if (favoritesView == FavoritesView.All) "FAVORITES" else null
        openAlbum != null -> openAlbum.name
        openPrivateGroup != null -> openPrivateGroup.name
        openLocation != null -> openLocation.city
        else -> null
    }?.uppercase()
    val isFolderLabelShown: Boolean = Settings.folderLabel && folderName != null

    // Inside Private, Locations or the trash the bar belongs to that place, so it says so, with the way out beside it.
    val placePill: String? = when {
        isPrivateMode -> "PRIVATE"
        place != null && place.isInLocations -> "LOCATIONS"
        place == AlbumsPlace.Trash -> "TRASH"
        else -> null
    }

    val albumsGlyph: PlaceGlyph = when {
        isPrivateMode -> PlaceGlyph.PRIVATE
        navigation.albumsPlace.isInLocations -> PlaceGlyph.LOCATIONS
        navigation.albumsPlace == AlbumsPlace.Trash -> PlaceGlyph.TRASH
        else -> PlaceGlyph.ALBUMS
    }

    // The source of the photo grid on screen: what the viewer opens, review goes through and the selection is made of; a place of covers has none.
    val gridSource: ViewerSource? = when {
        isPrivateMode && section == Section.RECENT -> ViewerSource.PrivateRecent
        isPrivateMode && section == Section.FAVORITES -> when {
            openPrivateFavorite != null -> ViewerSource.InPrivateFavoriteGroup(openPrivateFavorite.name)
            isPrivateFavoritesGrouped -> null
            else -> ViewerSource.PrivateFavorites
        }
        section == Section.RECENT -> ViewerSource.Recent
        section == Section.FAVORITES -> when {
            openFavorite != null -> ViewerSource.InFavoriteAlbum(openFavorite.name)
            favoritesView == FavoritesView.All -> ViewerSource.Favorites
            else -> null
        }
        openAlbum != null -> ViewerSource.InAlbum(openAlbum.id)
        openPrivateGroup != null -> ViewerSource.InPrivateGroup(openPrivateGroup.name)
        openLocation != null -> ViewerSource.InLocation(openLocation.key)
        place is AlbumsPlace.Trash -> ViewerSource.Trash
        place is AlbumsPlace.PrivateTrash -> ViewerSource.PrivateTrash
        else -> null
    }

    // Either trash is on screen, with what it holds; both restore and delete the same way.
    val isInTrash: Boolean = place?.isTrash == true
    val trashItems: List<MediaItem> = when (place) {
        AlbumsPlace.Trash -> content.trash
        AlbumsPlace.PrivateTrash -> content.privateContents.trash
        else -> emptyList()
    }

    // The items of the grid on screen, which is what a selection is made of; narrowed to favourites when asked.
    val gridItems: List<MediaItem> = gridSource?.let(content::itemsFor).orEmpty().let { items -> if (selection.isFavoritesOnly) items.filter { it.isFavorite } else items }

    val folderMemory: GridMemory? = when (val source = gridSource) {
        ViewerSource.PrivateRecent -> memories.privateRecent
        ViewerSource.PrivateFavorites -> memories.privateFavorites
        is ViewerSource.InPrivateFavoriteGroup -> memories.privateFavoriteGroup(source.name)
        is ViewerSource.InAlbum -> memories.album(source.albumId)
        is ViewerSource.InPrivateGroup -> memories.privateGroup(source.name)
        is ViewerSource.InLocation -> memories.location(source.key)
        ViewerSource.Trash -> memories.trash
        ViewerSource.PrivateTrash -> memories.privateTrash
        ViewerSource.Recent -> memories.recent
        is ViewerSource.InFavoriteAlbum -> memories.favoriteAlbum(source.name)
        ViewerSource.Favorites -> memories.favorites
        null -> null
    }

    // A trash has nothing to review from the settings sheet; every other photo grid does.
    val reviewSource: ViewerSource? = gridSource.takeUnless { it.isTrash }

    // The folder open takes new photos straight from here; a location only gathers by place, so it has none.
    val addTarget: PickerTarget? = when {
        isPrivateMode && section != Section.ALBUMS -> null
        openAlbum != null -> PickerTarget.IntoAlbum(openAlbum.relativePath, openAlbum.name)
        openPrivateGroup != null -> PickerTarget.IntoGroup(openPrivateGroup.name)
        isInFavoriteAlbum -> PickerTarget.IntoFavoriteAlbum(openFavorite!!.name)
        else -> null
    }

    // Out of a folder, the same as the system back; Private's own groups leave by the Private pill instead.
    val canGoBack: Boolean = if (isPrivateMode) {
        (section == Section.ALBUMS && (place is AlbumsPlace.PrivateFolder || place == AlbumsPlace.PrivateTrash)) || (section == Section.FAVORITES && navigation.openPrivateFavoriteGroup != null)
    } else {
        (section == Section.ALBUMS && place != AlbumsPlace.Folders) || (section == Section.FAVORITES && navigation.openFavoriteAlbum != null)
    }

    // Favorites switches between every favourite in one grid and the albums made inside it; inside Private the same button switches Private's own Favorites, which remembers its choice apart.
    val toggleView: (() -> Unit)? = when {
        isPrivateMode && section == Section.FAVORITES -> if (openPrivateFavorite == null) { { Settings.updatePrivateFavoritesAsGroups(!Settings.privateFavoritesAsGroups) } } else null
        section == Section.FAVORITES && openFavorite == null -> { { Settings.updateFavoritesAsAlbums(!Settings.favoritesAsAlbums) } }
        else -> null
    }
    val isAlbumsView: Boolean = if (isPrivateMode) Settings.privateFavoritesAsGroups else Settings.favoritesAsAlbums

    // Only favourites in the grid on screen; Favorites and the trash have nothing to narrow.
    val canNarrowToFavorites: Boolean = folderMemory != null && section != Section.FAVORITES && !isInTrash

    // The cover grid on screen, which picked covers belong to: the albums in Albums, those inside Favorites, or Private's groups. A Favorites album's path is its name, so both album grids pick by path.
    val coverShelf: AlbumShelfKind? = when {
        place == AlbumsPlace.Folders -> AlbumShelfKind.FOLDERS
        section == Section.FAVORITES && favoritesView == FavoritesView.Albums -> AlbumShelfKind.FAVORITES
        else -> null
    }
    val selectedAlbums: List<Album> = when (coverShelf) {
        AlbumShelfKind.FOLDERS -> content.arrangedAlbums.filter { it.relativePath in selection.covers }
        AlbumShelfKind.FAVORITES -> content.favoriteAlbums.filter { it.relativePath in selection.covers }
        null -> emptyList()
    }
    val selectedGroups: List<PrivateGroup> = if (place == AlbumsPlace.PrivateGroups) content.arrangedGroups.filter { it.name in selection.covers } else emptyList()
    val isSelectingCovers: Boolean = selectedAlbums.isNotEmpty() || selectedGroups.isNotEmpty()
    val selectedItems: List<MediaItem> = gridItems.filter { it.id in selection.ids }
    val isSelecting: Boolean = selectedItems.isNotEmpty()
    val selectedCount: Int = selectedItems.size + selectedAlbums.size + selectedGroups.size

    val bottomBar: BottomBar = when {
        selection.isRearranging -> BottomBar.REARRANGING
        isSelectingCovers -> BottomBar.COVERS
        isSelecting -> BottomBar.PHOTOS
        else -> BottomBar.NAVIGATION
    }

    // Anything showing private photos stays out of screenshots and the recent-apps preview.
    val isShowingPrivate: Boolean = isPrivateMode || navigation.review.isPrivate || navigation.duplicates == DuplicatesScope.PRIVATE
}

// What the bottom bar is showing: the sections, a selection's actions, the end of rearranging, or nothing while a photo is open.
enum class BottomBar { NAVIGATION, PHOTOS, COVERS, REARRANGING, VIEWER }
