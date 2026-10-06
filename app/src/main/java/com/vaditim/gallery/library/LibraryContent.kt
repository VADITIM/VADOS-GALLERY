package com.vaditim.gallery.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.media.LocationGroup
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.media.Place
import com.vaditim.gallery.settings.AlbumArrangement
import com.vaditim.gallery.vault.PrivateContents
import com.vaditim.gallery.vault.PrivateGroup

// Everything the library shows, read once per frame from the view model and the arrangement, already ordered and named as the user left them.
@Immutable
class LibraryContent(
    val recent: List<MediaItem>,
    // Renamed albums show the name given to them; the folder underneath keeps its own.
    val albums: List<Album>,
    // Albums in the order the user dragged them into; ones never arranged keep the default order after them.
    val arrangedAlbums: List<Album>,
    val favorites: List<MediaItem>,
    // Favorites albums hold favourites only: a photo unfavourited leaves them, and an album left empty is not shown. The name is the album's path, so groups and order hold names.
    val favoriteAlbums: List<Album>,
    val privateContents: PrivateContents,
    val arrangedGroups: List<PrivateGroup>,
    // Every private photo, oldest first like the library, and the favourites gathered by the group they are in.
    val privateRecent: List<MediaItem>,
    val privateFavoriteGroups: List<PrivateGroup>,
    val isPrivateUnlocked: Boolean,
    val locations: List<LocationGroup>,
    val places: Map<Long, Place>,
    val trash: List<MediaItem>,
) {
    fun album(id: Long): Album? = albums.firstOrNull { it.id == id }
    fun privateGroup(name: String): PrivateGroup? = privateContents.groups.firstOrNull { it.name == name }
    fun location(key: String): LocationGroup? = locations.firstOrNull { it.key == key }
    fun favoriteAlbum(name: String?): Album? = favoriteAlbums.firstOrNull { it.name == name }
    fun privateFavoriteGroup(name: String?): PrivateGroup? = privateFavoriteGroups.firstOrNull { it.name == name }

    // Private sources give nothing once Private locks, so a viewer left open on one empties instead of showing what it should no longer.
    fun itemsFor(source: ViewerSource): List<MediaItem> = when (source) {
        ViewerSource.Recent -> recent
        ViewerSource.Favorites -> favorites
        is ViewerSource.InAlbum -> album(source.albumId)?.items.orEmpty()
        is ViewerSource.InPrivateGroup -> if (isPrivateUnlocked) privateGroup(source.name)?.items.orEmpty() else emptyList()
        ViewerSource.PrivateFavorites -> if (isPrivateUnlocked) privateContents.favorites else emptyList()
        ViewerSource.PrivateRecent -> if (isPrivateUnlocked) privateRecent else emptyList()
        is ViewerSource.InPrivateFavoriteGroup -> if (isPrivateUnlocked) privateFavoriteGroup(source.name)?.items.orEmpty() else emptyList()
        is ViewerSource.InLocation -> location(source.key)?.items.orEmpty()
        is ViewerSource.InFavoriteAlbum -> favoriteAlbum(source.name)?.items.orEmpty()
        ViewerSource.Trash -> trash
    }
}

@Composable
fun rememberLibraryContent(viewModel: GalleryViewModel): LibraryContent {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val folderAlbums by viewModel.albums.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val privateContents by viewModel.privateContents.collectAsStateWithLifecycle()
    val isPrivateUnlocked by viewModel.isPrivateUnlocked.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val trash by viewModel.trash.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()

    val names = AlbumArrangement.albumNames.byPath
    val albums = remember(folderAlbums, names) {
        if (names.isEmpty()) folderAlbums else folderAlbums.map { album -> names[album.relativePath]?.let { album.copy(name = it) } ?: album }
    }
    val albumOrder = AlbumArrangement.albumOrder.names
    val arrangedAlbums = remember(albums, albumOrder) { AlbumArrangement.albumOrder.arrange(albums) { it.relativePath } }
    val groupOrder = AlbumArrangement.groupOrder.names
    val arrangedGroups = remember(privateContents.groups, groupOrder) { AlbumArrangement.groupOrder.arrange(privateContents.groups) { it.name } }
    val storedFavoriteAlbums = AlbumArrangement.favoriteAlbums.all
    val favoriteAlbumOrder = AlbumArrangement.favoriteAlbumOrder.names
    val favoriteAlbums = remember(favorites, storedFavoriteAlbums, favoriteAlbumOrder, covers) {
        // One pass over the favourites fills every album, in the favourites' own order, instead of one pass per album.
        val albumsOfPhoto = HashMap<Long, MutableList<Int>>()
        storedFavoriteAlbums.forEachIndexed { index, album -> album.ids.toSet().forEach { albumsOfPhoto.getOrPut(it) { mutableListOf() }.add(index) } }
        val itemsOfAlbum = List(storedFavoriteAlbums.size) { mutableListOf<MediaItem>() }
        for (item in favorites) albumsOfPhoto[item.id]?.forEach { itemsOfAlbum[it].add(item) }
        val views = storedFavoriteAlbums.mapIndexedNotNull { index, album ->
            val items = itemsOfAlbum[index]
            val id = album.name.hashCode().toLong()
            if (items.isEmpty()) null else Album(id, album.name, album.name, items, covers[id])
        }
        AlbumArrangement.favoriteAlbumOrder.arrange(views) { it.name }
    }
    val privateRecent = remember(privateContents) {
        privateContents.groups.flatMap { it.items }.sortedWith(compareBy<MediaItem> { it.timestampMillis }.thenBy { it.id })
    }
    val privateFavoriteGroups = remember(privateContents, arrangedGroups) {
        val favoriteIds = privateContents.favorites.map { it.id }.toSet()
        arrangedGroups.mapNotNull { group -> group.items.filter { it.id in favoriteIds }.takeIf { it.isNotEmpty() }?.let { group.copy(items = it) } }
    }
    return remember(library, albums, arrangedAlbums, favorites, favoriteAlbums, privateContents, arrangedGroups, privateRecent, privateFavoriteGroups, isPrivateUnlocked, locations, places, trash) {
        LibraryContent(
            recent = library,
            albums = albums,
            arrangedAlbums = arrangedAlbums,
            favorites = favorites,
            favoriteAlbums = favoriteAlbums,
            privateContents = privateContents,
            arrangedGroups = arrangedGroups,
            privateRecent = privateRecent,
            privateFavoriteGroups = privateFavoriteGroups,
            isPrivateUnlocked = isPrivateUnlocked,
            locations = locations,
            places = places,
            trash = trash,
        )
    }
}
