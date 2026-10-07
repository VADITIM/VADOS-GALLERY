package com.vaditim.gallery.library

import androidx.compose.ui.graphics.Color
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.settings.SettingsView
import com.vaditim.gallery.vas.Palette

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
    data object PrivateTrash : ViewerSource
    data class InLocation(val key: String) : ViewerSource
    data class InFavoriteAlbum(val name: String) : ViewerSource
}

data class ViewerRequest(val source: ViewerSource, val startIndex: Int)

// What a folder's review progress is saved under; stable across launches, unlike the source object itself.
val ViewerSource.reviewKey: String
    get() = when (this) {
        is ViewerSource.InAlbum -> "album:$albumId"
        is ViewerSource.InPrivateGroup -> "group:$name"
        is ViewerSource.InLocation -> "location:$key"
        ViewerSource.PrivateFavorites -> "private-favorites"
        ViewerSource.PrivateRecent -> "private-recent"
        is ViewerSource.InPrivateFavoriteGroup -> "private-favorite-group:$name"
        ViewerSource.Recent -> "recent"
        ViewerSource.Favorites -> "favorites"
        ViewerSource.Trash -> "trash"
        ViewerSource.PrivateTrash -> "private-trash"
        is ViewerSource.InFavoriteAlbum -> "favorite-album:$name"
    }

// Albums of any kind may keep settings of their own, under the same key as their review progress.
val ViewerSource?.folderKey: String?
    get() = when (this) {
        is ViewerSource.InAlbum, is ViewerSource.InPrivateGroup, is ViewerSource.InFavoriteAlbum -> reviewKey
        else -> null
    }

val ViewerSource?.isPrivate: Boolean
    get() = this is ViewerSource.InPrivateGroup || this is ViewerSource.PrivateFavorites || this == ViewerSource.PrivateRecent || this is ViewerSource.InPrivateFavoriteGroup || this == ViewerSource.PrivateTrash

// Either trash: the system's (with Samsung's) or Private's own, which work alike.
val ViewerSource?.isTrash: Boolean
    get() = this == ViewerSource.Trash || this == ViewerSource.PrivateTrash

// Where the Albums section stands. The private places are only reachable while Private is unlocked.
sealed interface AlbumsPlace {
    data object Folders : AlbumsPlace
    data class Folder(val albumId: Long) : AlbumsPlace
    data object PrivateGroups : AlbumsPlace
    data class PrivateFolder(val name: String) : AlbumsPlace
    data object Locations : AlbumsPlace
    data class Location(val key: String) : AlbumsPlace
    data object Trash : AlbumsPlace
    // Private's own trash, reached from the foot of its albums.
    data object PrivateTrash : AlbumsPlace

    val isPrivate: Boolean get() = this is PrivateGroups || this is PrivateFolder || this == PrivateTrash
    val isTrash: Boolean get() = this == Trash || this == PrivateTrash
    val isInLocations: Boolean get() = this is Locations || this is Location

    val settingsView: SettingsView
        get() = when {
            isInLocations -> SettingsView.LOCATIONS
            this == Trash -> SettingsView.TRASH
            isPrivate -> SettingsView.PRIVATE
            else -> SettingsView.ALBUMS
        }

    // Locations and the trash carry their own colour, in their views and on their buttons; every other place takes its section's.
    val accent: Color?
        get() = when {
            isInLocations -> Palette.locationBlue
            this == Trash -> Palette.trashGray
            else -> null
        }
}

// What Favorites shows: every favourite as one grid, the albums made inside it, or one of those albums.
sealed interface FavoritesView {
    data object All : FavoritesView
    data object Albums : FavoritesView
    data class InAlbum(val name: String) : FavoritesView
}

// Where the photo picker puts what is picked: an album folder (new or existing), or a private group.
sealed interface PickerTarget {
    val title: String

    data class IntoAlbum(val relativePath: String, val name: String) : PickerTarget {
        override val title get() = "Add to $name"
    }

    data class IntoGroup(val name: String) : PickerTarget {
        override val title get() = "Add to Private · $name"
    }

    data class IntoFavoriteAlbum(val name: String) : PickerTarget {
        override val title get() = "Add to Favorites · $name"
    }
}

// Photos about to go into Private, held while the confirmation is open.
data class PendingPrivate(val items: List<MediaItem>, val groupName: String, val isSelection: Boolean)

// A group about to be deleted that still holds photos: how many, and the delete itself, asked about once more after Confirm.
class GroupDeleteCheck(val name: String, val photoCount: Int, val delete: () -> Unit)

// What Private's Favorites shows besides an opened group, keyed apart from any group name (a group's name never holds a tab).
const val PRIVATE_FAVORITE_GRID = "\tgrid"
const val PRIVATE_FAVORITE_GROUPS = "\tgroups"
